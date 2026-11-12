package com.gravol.hermesclone.feature.sessions

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gravol.hermesclone.feature.settings.SettingsRepository
import com.gravol.hermesclone.di.AppModule
import com.gravol.hermesclone.core.network.DebugLog
import com.gravol.hermesclone.core.network.InsightsUsage
import com.gravol.hermesclone.core.network.JsonRpcClient
import com.gravol.hermesclone.core.network.RpcNotification
import com.gravol.hermesclone.core.network.SessionSummary
import com.gravol.hermesclone.core.network.WsConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject

/**
 * v0.1.902: the Insights panel's own view of the session list.
 *
 * It used to read [SessionsUiState.sessions] — the drawer's page — which made the DAY/WEEK/MONTH
 * figures only as wide as whatever the drawer happened to have loaded (25 rows since v0.1.901, so
 * a "month" that could see two days), and left the panel blank with no explanation whenever the
 * drawer had not finished loading. The panel now asks for its own window-sized snapshot on open,
 * and carries the two facts the UI has to be honest about: whether a fetch is in flight, and
 * whether the snapshot stopped short of the full history.
 */
data class InsightsSnapshot(
    val sessions: List<SessionSummary> = emptyList(),
    val isLoading: Boolean = false,
    /** False when the last fetch returned a FULL page — older conversations exist beyond it. */
    val complete: Boolean = true,
    val fetchedAt: Long = 0L,
    val error: String? = null,
)

/**
 * v0.5.1: one window's server-computed usage rollup (`insights.get`). Keyed by period in days
 * (1/7/30) because the panel's DAY/WEEK/MONTH selector asks for exactly those windows.
 *
 * [usage] == null is a capability signal, not a zero: gateways before the rollup answer with only
 * {days, sessions, messages}, and [unavailableReason] carries why a newer gateway could not answer.
 * The panel then says it is approximating from session rows instead of passing off main-agent-only
 * counters as the whole picture.
 */
data class WindowUsage(
    val usage: InsightsUsage? = null,
    val unavailableReason: String? = null,
    val isLoading: Boolean = false,
    val fetchedAt: Long = 0L,
    val error: String? = null,
)

data class SessionsUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val isLoading: Boolean = false,
    // v0.1.901: true once a fetch came back SHORTER than the limit we asked for —
    // that is proof there is nothing older to get, not a guess. Gates the drawer's
    // "Load older conversations" footer. Default loads are 25 rows (~9 KB) because
    // session.list is refetched on every sessions.changed; the full list costs
    // hundreds of KB and is only fetched when the user asks for it.
    val fullListLoaded: Boolean = false,
    // v0.1.902: the Insights panel's window-sized snapshot — see [InsightsSnapshot]. Kept apart
    // from `sessions` on purpose: the drawer page is small and refetched on every
    // sessions.changed, while an insights figure has to cover the whole window it claims.
    val insights: InsightsSnapshot = InsightsSnapshot(),
    // v0.5.1: per-window server usage rollup, keyed by period in days — see [WindowUsage].
    val windowUsage: Map<Int, WindowUsage> = emptyMap(),
    val isCreating: Boolean = false,
    val error: String? = null,
    val deleting: Boolean = false,
)

class SessionsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    private val settingsRepo = SettingsRepository(application)

    /**
     * v0.1.885: the app-wide shared connection. One WebSocket for the whole app —
     * this ViewModel subscribes to its notification broadcast for `sessions.changed`.
     * The old per-ViewModel observer opened its own socket, so every screen had a
     * socket and the gateway delivered every event to all of them.
     */
    private val sharedWs = AppModule.provideWsConnection()
    private val sharedRpc = AppModule.provideRpcClient()

    /** Locally pinned session ids (desktop-style client-side pinning). */
    val pinnedIds: StateFlow<Set<String>> = settingsRepo.pinnedSessionIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        loadSessions()
        // Establish the shared connection and watch `sessions.changed` broadcasts.
        // The dashboard fires this after any external session-list change (cron runs,
        // other clients, turn completion). Without refetching on it the cached list
        // goes stale — which is exactly why Insights showed "no usage data" even
        // after the gateway started returning token counts.
        viewModelScope.launch {
            try {
                sharedWs.connect()
                sharedRpc.start()
            } catch (e: Exception) {
                DebugLog.log("ERROR", "SessionsVM", "shared connect failed: ${e.message}")
            }
            var lastReloadAt = 0L
            sharedRpc.notifications.collect { n ->
                if (n is RpcNotification.SessionChanged) {
                    // v0.1.886 — debounce. `sessions.changed` fires constantly
                    // (cron runs, other clients, turn completion); field logs show
                    // session.list in identical pairs every 2-10s. Collapse bursts.
                    val now = System.currentTimeMillis()
                    if (now - lastReloadAt < 1500) {
                        DebugLog.log("INFO", "SessionsVM", "sessions.changed → debounced")
                    } else {
                        lastReloadAt = now
                        DebugLog.log("INFO", "SessionsVM", "sessions.changed → reload")
                        loadDashboardSessions()
                    }
                }
            }
        }
    }

    /** Ensure the shared connection is live, reconnecting in place if it died. */
    private suspend fun ensureShared(): JsonRpcClient? {
        if (sharedWs.isConnected) return sharedRpc
        return try {
            sharedWs.connect()
            sharedRpc.start()
            DebugLog.log("INFO", "SessionsVM", "shared connection reconnected — live socket")
            sharedRpc
        } catch (e: Exception) {
            Log.e("Hermex", "SessionsViewModel: shared reconnect failed", e)
            DebugLog.log("ERROR", "SessionsVM", "shared reconnect failed: ${e.message}")
            null
        }
    }

    // v0.1.901: session.list tiers. With no limit param the server default applied —
    // 200 rich rows / 72.6 KB per fetch against a 1,227-session db, refetched on every
    // sessions.changed — and the drawer's filter only sees loaded rows, so 1,027
    // sessions were invisible AND unsearchable. Now: cheap by default, each explicit
    // tap one tier bigger, and the raised limit sticks for this ViewModel's life so an
    // auto-reload never shrinks the list back out from under the user.
    private var listLimit = LIST_TIER_START
    private val listTiers = intArrayOf(25, 200, 2000)

    /** "Load older conversations" — user-invoked; raises the limit exactly one tier. */
    fun loadMoreSessions() {
        val next = listTiers.firstOrNull { it > listLimit } ?: return
        DebugLog.log("INFO", "SessionsVM",
            "loadMoreSessions: raising session.list limit $listLimit -> $next")
        listLimit = next
        loadSessions()
    }

    /**
     * v0.1.902: fetch the snapshot the Insights panel totals.
     *
     * The panel must not total the drawer's page. Measured on this db against the live gateway:
     * limit=25 → 8.7 KB and a page that spans two days, while the true window figures were
     * DAY 12 sessions / 2.1M in, WEEK 75 / 32.8M, MONTH 147 / 58.4M — a 25-row page can only
     * ever report a fraction of that, and an unloaded drawer reported nothing at all. So Insights
     * asks for its own wide snapshot; tiers exist so a full page (older history exists) can be
     * retried wider instead of silently under-reporting.
     */
    private var insightsTier = 0
    private val insightTiers = intArrayOf(500, 2000)
    private var insightsInFlight = false

    fun loadInsights(force: Boolean = false) {
        if (insightsInFlight) return
        val current = _uiState.value.insights
        if (!force && current.sessions.isNotEmpty() &&
            System.currentTimeMillis() - current.fetchedAt < INSIGHTS_TTL_MS
        ) {
            DebugLog.log("INFO", "SessionsVM", "loadInsights: reusing ${current.sessions.size}-row snapshot")
            return
        }
        viewModelScope.launch {
            insightsInFlight = true
            _uiState.value = _uiState.value.copy(insights = current.copy(isLoading = true, error = null))
            val client = sharedRpc.takeIf { sharedWs.isConnected } ?: ensureShared()
            if (client == null) {
                DebugLog.log("ERROR", "SessionsVM", "loadInsights: no observer socket — keeping last snapshot")
                _uiState.value = _uiState.value.copy(
                    insights = current.copy(isLoading = false, error = "connection unavailable"),
                )
                insightsInFlight = false
                return@launch
            }
            val asked = insightTiers[insightsTier.coerceAtMost(insightTiers.lastIndex)]
            try {
                var rows = client.sessionList(limit = asked)
                // A FULL page means older history exists beyond it. Rather than show a short
                // total and wait for a tap on Refresh, escalate once inside this call: the
                // window figure either covers the window or we genuinely have thousands of rows.
                if (rows.size >= asked && insightsTier < insightTiers.lastIndex) {
                    val wider = insightTiers[++insightsTier]
                    DebugLog.log("INFO", "SessionsVM",
                        "loadInsights: page full at $asked — escalating to $wider in-place")
                    rows = client.sessionList(limit = wider)
                }
                val completeLimit = insightTiers[insightsTier]
                // A short page is proof the history is fully covered; a full page is not.
                val complete = rows.size < completeLimit
                DebugLog.log(
                    "INFO", "SessionsVM",
                    "loadInsights: session.list(limit=$completeLimit) → ${rows.size} rows " +
                        if (complete) "(window fully covered)" else "(page FULL: older rows exist)",
                )
                val mapped = rows.map { it.toSessionSummary() }
                _uiState.value = _uiState.value.copy(
                    insights = InsightsSnapshot(
                        sessions = mapped,
                        isLoading = false,
                        complete = complete,
                        fetchedAt = System.currentTimeMillis(),
                    ),
                )
            } catch (e: Exception) {
                Log.e("Hermex", "SessionsViewModel: insights load failed", e)
                DebugLog.log("ERROR", "SessionsVM", "loadInsights failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    insights = current.copy(isLoading = false, error = e.message ?: "session.list failed"),
                )
            } finally {
                insightsInFlight = false
            }
        }
    }

    /**
     * v0.5.1: ask the gateway for one window's usage rollup (`insights.get`, days = 1/7/30).
     *
     * This is the honest path. Summing `session.list` rows on the phone can only ever see the
     * `sessions` counters: main-agent-loop calls, uncached prompt tokens. Measured against a real
     * store over 30 days that hid 1,572 API calls / 9.8M input tokens (+13%) of auxiliary work and
     * every one of 2.47B cached prompt tokens — which is what made the panel's in/out ratio look
     * absurd. The server computes this from `session_model_usage`, so totals and breakdowns agree.
     *
     * A gateway without the rollup returns no `usage`; that is recorded as [WindowUsage.usage]=null
     * with a reason, never as zeros, so the panel can say it is approximating.
     */
    private val usageInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    fun loadWindowUsage(days: Int, force: Boolean = false) {
        val period = days.coerceIn(1, 365)
        if (!usageInFlight.add(period)) return
        val current = _uiState.value.windowUsage[period]
        if (!force && current?.usage != null &&
            System.currentTimeMillis() - current.fetchedAt < INSIGHTS_TTL_MS
        ) {
            usageInFlight.remove(period)
            DebugLog.log("INFO", "SessionsVM", "loadWindowUsage(${period}d): reusing cached rollup")
            return
        }
        viewModelScope.launch {
            fun put(w: WindowUsage) {
                _uiState.value = _uiState.value.copy(
                    windowUsage = _uiState.value.windowUsage + (period to w),
                )
            }
            put(current?.copy(isLoading = true, error = null) ?: WindowUsage(isLoading = true))
            val client = sharedRpc.takeIf { sharedWs.isConnected } ?: ensureShared()
            if (client == null) {
                DebugLog.log("ERROR", "SessionsVM", "loadWindowUsage(${period}d): no observer socket")
                put((current ?: WindowUsage()).copy(isLoading = false, error = "connection unavailable"))
                usageInFlight.remove(period)
                return@launch
            }
            try {
                val result = client.insightsUsage(period)
                val usage = result.usage
                DebugLog.log(
                    "INFO", "SessionsVM",
                    "insights.get(days=$period) → " + if (usage == null)
                        "no usage rollup (${result.usageUnavailable ?: "server predates it"}); " +
                            "legacy counts sessions=${result.sessions}"
                    else "prompt=${usage.totals.promptTokens} in=${usage.totals.inputTokens} " +
                        "out=${usage.totals.outputTokens} calls=${usage.totals.apiCallCount} " +
                        "tasks=${usage.tasks.size}",
                )
                put(
                    WindowUsage(
                        usage = usage,
                        unavailableReason = result.usageUnavailable,
                        isLoading = false,
                        fetchedAt = System.currentTimeMillis(),
                    ),
                )
            } catch (e: Exception) {
                Log.e("Hermex", "SessionsViewModel: usage rollup failed (${period}d)", e)
                DebugLog.log("ERROR", "SessionsVM", "loadWindowUsage(${period}d) failed: ${e.message}")
                put((current ?: WindowUsage()).copy(isLoading = false, error = e.message ?: "insights.get failed"))
            } finally {
                usageInFlight.remove(period)
            }
        }
    }

    // ── Official home bot model ──────────────────────────────────────────────
    /** profiles.list row + its sessions, ready for the home tree. */
    data class BotGroup(
        val name: String,
        val displayName: String,
        val description: String,
        val hasAvatar: Boolean,
        val lastSessionTs: Double?,
        val sessions: List<JsonRpcClient.SessionInfo>,
        // Server total for the profile (REST `total`), the base of the official
        // "N more sessions" count. Null when the REST page failed.
        val totalSessions: Int? = null,
    )

    private val _bots = MutableStateFlow<List<BotGroup>>(emptyList())
    val bots: StateFlow<List<BotGroup>> = _bots.asStateFlow()
    private var botsLoadedOnce = false

    /** Roster + per-bot sessions, in parallel. Mirrors the official home fetch. */
    private val _working = MutableStateFlow<Set<String>>(emptySet())
    val working: StateFlow<Set<String>> = _working.asStateFlow()

    /** One poll of live sessions; ids + session_keys whose status is streaming. */
    fun refreshWorking() {
        viewModelScope.launch {
            val client = sharedRpc.takeIf { sharedWs.isConnected } ?: return@launch
            runCatching {
                _working.value = client.activeList()
                    .filter { it.status == "streaming" || it.status == "working" }
                    .flatMap { listOf(it.id) + listOfNotNull(it.sessionKey) }
                    .toSet()
            }
        }
    }

    fun loadBots(force: Boolean = false) {
        if (botsLoadedOnce && !force) return
        viewModelScope.launch {
            val client = sharedRpc.takeIf { sharedWs.isConnected } ?: ensureShared() ?: return@launch
            runCatching {
                val profiles = client.profilesList()
                // Official parity: the home rows come from the dashboard REST list, NOT the
                // session.list RPC — REST rows carry `unread` (the blue-dot watermark),
                // `archived`, and the per-profile `total` behind "N more sessions". The RPC
                // summary omits all three (verified in tui_gateway _session_row_summary).
                suspend fun restBotSessions(profile: String): Pair<List<JsonRpcClient.SessionInfo>, Int?> {
                    val app = getApplication<android.app.Application>()
                    val cacheOn = runCatching {
                        com.gravol.hermesclone.di.AppModule.provideDataStoreManager(app)
                            .getBoolean(com.gravol.hermesclone.feature.settings.SettingsKeys.CACHE_CHAT_LIST)
                            .first() ?: true
                    }.getOrDefault(true)
                    val cacheFile = com.gravol.hermesclone.feature.settings.HermesCache.sessionsFile(app, profile)
                    // Official "N more sessions" semantics (tcpdump-captured 2026-11-12,
                    // exact-match derivation): the official paginates the profile
                    // (archived=include&limit=100&offset=N… → the server's LISTABLE set,
                    // 1,558 rows on default: subagent runs + compression continuations
                    // already excluded) and counts client-side as
                    // rows − {cron, oneshot, ntfy, webui} = 899; 899 − 5 visible =
                    // "894 more" — matches the official on-device exactly. telegram and
                    // tool rows COUNT (and are listed); unknown/rare sources drop out.
                    fun decodeList(el: kotlinx.serialization.json.JsonElement?) =
                        if (el == null) emptyList() else runCatching {
                            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                .decodeFromJsonElement(
                                    kotlinx.serialization.builtins.ListSerializer(JsonRpcClient.SessionInfo.serializer()), el)
                        }.getOrDefault(emptyList())
                    fun chatTotal(list: List<JsonRpcClient.SessionInfo>): Int = list.count {
                        it.source !in setOf("cron", "oneshot", "ntfy", "webui")
                    }
                    // Displayed rows additionally need a preview (a blank-title row was
                    // the v0.2.12 bug); the COUNT above intentionally includes them.
                    fun visible(list: List<JsonRpcClient.SessionInfo>) =
                        list.filter { !it.preview.isNullOrBlank() }
                    var offset = 0
                    val all = ArrayList<JsonRpcClient.SessionInfo>()
                    var failed = false
                    while (true) {
                        val r = com.gravol.hermesclone.core.network.DashboardApiClient.apiGet(
                            "/api/sessions?archived=include&limit=100&offset=$offset&profile=$profile")
                        if (r !is com.gravol.hermesclone.core.network.NetworkResult.Success) { failed = true; break }
                        val obj = r.data.jsonObject
                        val page = decodeList(obj["sessions"])
                        all += page
                        if (page.isEmpty()) break
                        offset += 100
                        // Official semantics: keep paging until an EMPTY page (its
                        // include_pinned backfill can make the list longer than
                        // `total`, so stopping at offset>=total loses rows — the
                        // ±1 profile-count misses in the first G6 cut).
                        if (offset >= 4000) break
                    }
                    if (failed) {
                        // Caching ▸ Chat List (real read path): when the network is down,
                        // render the last snapshot so the list is there before the network
                        // answers — the official's documented behaviour for this category.
                        if (cacheOn && cacheFile.exists()) {
                            // New cache = JSON array; older caches were the raw
                            // {sessions,total} page object — accept both.
                            val el = runCatching {
                                kotlinx.serialization.json.Json.parseToJsonElement(cacheFile.readText())
                            }.getOrNull() ?: return emptyList<JsonRpcClient.SessionInfo>() to null
                            val list = decodeList(
                                if (el is kotlinx.serialization.json.JsonArray) el
                                else (el as? kotlinx.serialization.json.JsonObject)?.get("sessions"))
                            return visible(list) to chatTotal(list)
                        }
                        return emptyList<JsonRpcClient.SessionInfo>() to null
                    }
                    // Pinned backfill can repeat a row across pages — dedupe by id.
                    val uniq = all.distinctBy { it.id }
                    if (cacheOn) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            cacheFile.writeText(
                                kotlinx.serialization.json.Json.encodeToString(
                                    kotlinx.serialization.builtins.ListSerializer(JsonRpcClient.SessionInfo.serializer()),
                                    uniq))
                        }
                    }
                    return visible(uniq) to chatTotal(uniq)
                }
                val perBot = coroutineScope {
                    profiles.map { p ->
                        async { p.name to restBotSessions(p.name) }
                    }.awaitAll().toMap()
                }
                val groups = profiles.map { p ->
                    val (botSessions, botTotal) = perBot[p.name] ?: emptyList<JsonRpcClient.SessionInfo>() to null
                    val sessions = botSessions.sortedByDescending { it.last_activity_at ?: it.started_at ?: 0.0 }
                    BotGroup(
                        name = p.name,
                        displayName = when {
                            p.displayName.isNotBlank() -> p.displayName
                            p.isDefault -> "Hermes"
                            else -> p.name
                        },
                        description = p.description,
                        hasAvatar = p.hasAvatar,
                        lastSessionTs = sessions.firstOrNull()?.let { it.last_activity_at ?: it.started_at },
                        sessions = sessions,
                        totalSessions = botTotal,
                    )
                }.sortedWith(
                    compareByDescending<BotGroup> { g -> g.lastSessionTs ?: g.sessions.maxOfOrNull { it.last_activity_at ?: it.started_at ?: 0.0 } ?: 0.0 }
                        .thenBy { it.name }
                )
                _bots.value = groups
                botsLoadedOnce = true
                DebugLog.log("INFO", "SessionsVM", "bots loaded: ${groups.size} profiles")
            }.onFailure {
                DebugLog.log("ERROR", "SessionsVM", "bots load failed: ${it.message}")
            }
        }
    }

    // ── v0.2.10: official home Filters (Show / Include / Agents) ─────────────
    data class HomeFilters(
        val showMode: String = "All",                  // All | Unread | Working | Pinned
        val includeRoutineRuns: Boolean = false,       // Include ▸ Routine runs (source cron/oneshot)
        val includeKanban: Boolean = false,            // Include ▸ Kanban workers (source kanban)
        val includeArchived: Boolean = false,          // Include ▸ Archived
        val selectedBots: Set<String>? = null,         // null = all agents
    ) {
        val includeSub: String
            get() {
                val on = listOf(includeRoutineRuns, includeKanban, includeArchived).count { it }
                return when {
                    on == 0 -> "None"
                    on == 1 && includeRoutineRuns -> "+ Routine runs"
                    on == 1 && includeKanban -> "+ Kanban workers"
                    on == 1 && includeArchived -> "+ Archived"
                    else -> "+ $on kinds"
                }
            }
        val agentsSub: String
            get() = when {
                selectedBots == null -> "All"
                selectedBots.isEmpty() -> "None"
                else -> "${selectedBots.size} selected"
            }
    }

    private val _filters = MutableStateFlow(HomeFilters())
    val filters: StateFlow<HomeFilters> = _filters.asStateFlow()

    val readTimes: StateFlow<Map<String, Double>> = settingsRepo.sessionReadTimes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap<String, Double>())

    fun markSessionRead(sessionId: String) {
        viewModelScope.launch {
            settingsRepo.markSessionRead(sessionId)
            // Official parity: unread is a SERVER watermark (PATCH /api/sessions/{id}
            // {"unread": false} → set_session_read). Without this the dot only clears locally.
            runCatching {
                com.gravol.hermesclone.core.network.DashboardApiClient.apiSend(
                    "/api/sessions/${java.net.URLEncoder.encode(sessionId, "UTF-8")}",
                    "PATCH", "{\"unread\": false}",
                )
            }
        }
    }

    fun setFilters(f: HomeFilters) {
        DebugLog.log("INFO", "SessionsVM", "setFilters routine=${f.includeRoutineRuns} archived=${f.includeArchived} show=${f.showMode} bots=${f.selectedBots}")
        val old = _filters.value
        _filters.value = f
        // (Re)fetch the Include extras when a toggle first turns on.
        if (f.includeRoutineRuns && !old.includeRoutineRuns) loadExtras(routine = true, archived = f.includeArchived)
        else if (f.includeArchived && !old.includeArchived) loadExtras(routine = f.includeRoutineRuns, archived = true)
    }

    fun resetFilters() {
        DebugLog.log("INFO", "SessionsVM", "resetFilters")
        _filters.value = HomeFilters()
    }

    /** Official Include-submenu subtitles: per-kind session totals ("N sessions"). Null = unknown yet. */
    data class IncludeCounts(val routines: Int? = null, val kanban: Int? = null, val archived: Int? = null)
    private val _includeCounts = MutableStateFlow(IncludeCounts())
    val includeCounts: StateFlow<IncludeCounts> = _includeCounts.asStateFlow()

    /** Sum the gateway `total` per profile for one Include kind (limit=1 — we only need the count). */
    fun refreshIncludeCounts() {
        viewModelScope.launch {
            val bots = _bots.value
            suspend fun total(query: String): Int? = coroutineScope {
                val perBot = bots.map { g ->
                    async {
                        val r = com.gravol.hermesclone.core.network.DashboardApiClient.apiGet(
                            "/api/sessions?limit=1&offset=0&profile=${g.name}&$query")
                        ((r as? com.gravol.hermesclone.core.network.NetworkResult.Success)
                            ?.data?.jsonObject?.get("total") as? kotlinx.serialization.json.JsonPrimitive)
                            ?.content?.toIntOrNull()
                    }
                }.awaitAll()
                if (perBot.all { it == null } && perBot.isNotEmpty()) null
                else perBot.sumOf { it ?: 0 }
            }
            // Official Include▸Routine runs counts cron ONLY (715 on-device; oneshot's 96
            // stay visible by default — verified 2026-11-12 against official 715 vs cron-811).
            val routines = total("sources=cron")
            val kanban = total("sources=kanban")
            val archived = total("archived=only")
            _includeCounts.value = IncludeCounts(routines, kanban, archived)
            DebugLog.log("INFO", "SessionsVM", "includeCounts routines=$routines kanban=$kanban archived=$archived")
        }
    }

    /** REST-fetched rows for the Include toggles, per bot name. */
    private val _routineExtras = MutableStateFlow<Map<String, List<JsonRpcClient.SessionInfo>>>(emptyMap())
    private val _archivedExtras = MutableStateFlow<Map<String, List<JsonRpcClient.SessionInfo>>>(emptyMap())
    val routineExtras: StateFlow<Map<String, List<JsonRpcClient.SessionInfo>>> = _routineExtras.asStateFlow()
    val archivedExtras: StateFlow<Map<String, List<JsonRpcClient.SessionInfo>>> = _archivedExtras.asStateFlow()

    private fun loadExtras(routine: Boolean, archived: Boolean) {
        DebugLog.log("INFO", "SessionsVM", "loadExtras routine=$routine archived=$archived")
        viewModelScope.launch {
            val bots = _bots.value
            suspend fun fetch(map: MutableMap<String, List<JsonRpcClient.SessionInfo>>, query: String) {
                coroutineScope {
                bots.map { g ->
                    async {
                        val r = com.gravol.hermesclone.core.network.DashboardApiClient.apiGet(
                            "/api/sessions?limit=50&order=recent&profile=${g.name}&$query")
                        if (r is com.gravol.hermesclone.core.network.NetworkResult.Success) {
                            val el = r.data.jsonObject["sessions"]
                            val list = if (el == null) emptyList() else runCatching {
                                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                    .decodeFromJsonElement(
                                        kotlinx.serialization.builtins.ListSerializer(JsonRpcClient.SessionInfo.serializer()), el)
                            }.getOrDefault(emptyList())
                            if (list.isNotEmpty()) map[g.name] = list
                            DebugLog.log("INFO", "SessionsVM",
                                "extras ${g.name} $query → ${list.size} rows (${r::class.simpleName})")
                        }
                    }
                }.awaitAll()
                }
            }
            if (routine) {
                val m = HashMap(_routineExtras.value)
                fetch(m, "sources=cron%2Coneshot")
                _routineExtras.value = m
            }
            if (archived) {
                val m = HashMap(_archivedExtras.value)
                fetch(m, "archived=only")
                _archivedExtras.value = m
            }
        }
    }

    /** Raw bot groups + filters → groups exactly as the home tree should render. */
    fun applyFilters(
        groups: List<BotGroup>, f: HomeFilters, working: Set<String>,
        pinned: Set<String>, read: Map<String, Double>,
    ): List<BotGroup> {
        val extras = HashMap<String, MutableList<JsonRpcClient.SessionInfo>>()
        if (f.includeRoutineRuns) _routineExtras.value.forEach { (k, v) -> extras.getOrPut(k) { mutableListOf() } += v }
        if (f.includeArchived) _archivedExtras.value.forEach { (k, v) -> extras.getOrPut(k) { mutableListOf() } += v }
        val botOk = f.selectedBots?.let { sel -> groups.filter { it.name in sel } } ?: groups
        return botOk.map { g ->
            // Official parity: routine runs (source cron/oneshot) and archived rows are NOT in
            // the base list — they appear only when the matching Include toggle is on. Proven
            // on-device: official default Include=None shows zero schedule rows; toggling on
            // shows them (REST extras below).
            // v0.2.25 (G6): telegram rows are NOT hidden — the official counts and lists
            // them (they simply weren't recent enough to appear in its 5-row peek).
            // subagent rows no longer reach here at all: the server's listable set
            // (exclude_children) already omits sub-agent runs.
            val base = g.sessions.filter { s ->
                (f.includeRoutineRuns || (s.source != "cron" && s.source != "oneshot")) &&
                    (f.includeKanban || s.source != "kanban") &&
                    (f.includeArchived || s.archived != true)
            }
            val merged = (base + (extras[g.name] ?: emptyList()))
                .distinctBy { it.id }
                .sortedByDescending { it.last_activity_at ?: it.started_at ?: 0.0 }
            val filtered = merged.filter { s ->
                when (f.showMode) {
                    "Working" -> s.id in working
                    "Pinned" -> s.id in pinned
                    // Official parity: the unread dot is CLIENT-side per-device state — proven on
                    // device: gateway last_read_at is NULL for every row here (server `unread`=false)
                    // yet the official dots them all. Never opened on this device = unread;
                    // otherwise active-since-last-open.
                    "Unread" -> read[s.id]?.let { rt -> (s.last_activity_at ?: s.started_at ?: 0.0) > rt } ?: true
                    else -> true
                }
            }
            g.copy(sessions = filtered)
        }
    }

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            loadDashboardSessions()
        }
    }

    fun togglePin(sessionId: String) {
        viewModelScope.launch {
            settingsRepo.togglePinned(sessionId)
        }
    }

    /** Create a new session server-side, then hand the live session id to [onDone]. */
    fun createSession(profile: String? = null, onDone: (String?) -> Unit) {
        if (_uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                val rpc = ensureShared() ?: throw Exception("connection unavailable")
                // v0.1.88: apply the user's model/effort pick (sticky, like the
                // desktop composer) to the new session.
                val modelPick = settingsRepo.modelPick.first().ifBlank { null }
                val reasoningPick = settingsRepo.reasoningPick.first().ifBlank { null }
                val (liveSid, durableKey) = rpc.createSessionWithKey(
                    model = modelPick, reasoningEffort = reasoningPick, profile = profile,
                )
                // v0.1.901: open the new chat by its DURABLE key. The live sid is a
                // routing key the gateway reaps ~20 s after this socket goes away, and
                // the row exists in state.db from the moment it is created — so the
                // drawer list already shows it, and every notification / deep link /
                // Reply built from this id resolves. (Mike's blackie9 fix; same bug
                // class we had already eliminated everywhere else.)
                val openKey = durableKey ?: liveSid
                DebugLog.log("INFO", "SessionsVM",
                    "session.create → live=$liveSid durable=$openKey" +
                    if (durableKey == null) " (NO durable key — falling back to live)" else "" +
                    " (model=$modelPick effort=$reasoningPick)")
                onDone(openKey)
            } catch (e: Exception) {
                Log.e("Hermex", "SessionsViewModel: createSession failed", e)
                DebugLog.log("ERROR", "SessionsVM", "session.create failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to create session",
                )
                onDone(null)
            } finally {
                _uiState.value = _uiState.value.copy(isCreating = false)
            }
        }
    }

    /** Delete a session server-side, then reload the session list. */
    fun deleteSession(sessionId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deleting = true, error = null)
            try {
                val rpc = ensureShared() ?: throw Exception("connection unavailable")
                rpc.sessionDelete(sessionId)
                // A deleted session's live sids must stop being accepted by any chat
                // screen: without this the chat ViewModel's SessionSidRegistry keeps
                // mappings that outlive the session they describe.
                com.gravol.hermesclone.feature.chat.SessionSidRegistry.forget(sessionId)
                DebugLog.log("INFO", "SessionsVM", "session.delete → $sessionId")
                _uiState.value = _uiState.value.copy(deleting = false)
                loadSessions()
                onDone()
            } catch (e: Exception) {
                Log.e("Hermex", "SessionsViewModel: deleteSession failed", e)
                DebugLog.log("ERROR", "SessionsVM", "session.delete failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    deleting = false,
                    error = e.message ?: "Failed to delete session",
                )
                onDone()
            }
        }
    }

    /**
     * Load the session list from the dashboard via JSON-RPC `session.list`.
     *
     * v0.1.151: reuse the persistent observer connection (established in [init])
     * when it's already live, instead of opening a fresh WS + login on every
     * reload. `sessions.changed` fires constantly (cron runs, other clients, turn
     * completion); each reload spinning up a new WS previously triggered a re-login
     * that tripped the server's brute-force throttle and churned reconnects. The
     * observer holds one long-lived connection for all reloads. Falls back to a
     * fresh connection only if the observer isn't available (e.g. it errored out).
     */
    /**
     * Cold-start bug (v0.1.157): the fallback path opened a throwaway WS
     * connection and `return`ed WITHOUT ever calling sessionList(), so on a
     * first launch — when the observer connection isn't live yet — the list
     * stayed stuck on the loading spinner until an unrelated `sessions.changed`
     * broadcast happened to fire minutes later. Now the fresh connection actually
     * fetches before it disconnects, and both paths share one apply function.
     */
    /**
     * v0.1.172: stop opening a throwaway socket per reload. The old fallback path
     * opened a fresh WS + ticket on every `sessions.changed` when the observer
     * wasn't instantly live, then disconnected — so frequent broadcasts spun up
     * one socket per reload, racing the reap window and thrashing the brute-force
     * throttle (the exact "observer unavailable — opening fresh WS connection"
     * churn in the crash loop). Now we REUSE the persistent observer socket and
     * only reconnect it IN PLACE if it died. No throwaway sockets, no per-reload
     * ticket fetch.
     */
    private suspend fun loadDashboardSessions() {
        // v0.1.886 — kill the duplicate-request storm. `sessions.changed` fires
        // constantly (cron runs, other clients, turn completion) and the screen
        // also calls loadSessions() on entry; field logs show session.list
        // arriving in IDENTICAL PAIRS every 2-10s. One reload at a time.
        if (reloadInFlight) {
            DebugLog.log("INFO", "SessionsVM", "session.list deferred — reload in flight")
            return
        }
        reloadInFlight = true
        try {
            loadDashboardSessionsInner()
            loadBots(force = true)
        } finally {
            reloadInFlight = false
        }
    }

    private var reloadInFlight = false

    private suspend fun loadDashboardSessionsInner() {
        DebugLog.log("INFO", "SessionsVM", "loadSessions via DASHBOARD JsonRpcClient.sessionList()")
        Log.d("Hermex", "SessionsViewModel: loading dashboard sessions")

        // v0.1.83: the old fallback built a SECOND JsonRpcClient over the
        // observer socket and never start()ed it — an unstarted client has no
        // frame-consumer loop, so session.list sent, no response was ever
        // dispatched, and it timed out at 30s (every "observer reconnected"
        // line in field logs was followed by exactly that). ensureObserver()
        // already installs a started observerClient — use it, don't clone.
        val liveClient = sharedRpc.takeIf { sharedWs.isConnected } ?: ensureShared()
        if (liveClient == null) {
            DebugLog.log("ERROR", "SessionsVM", "no observer socket — session.list deferred")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Dashboard: observer connection unavailable",
            )
            return
        }

        try {
            applySessionList(liveClient)
        } catch (e: Exception) {
            Log.e("Hermex", "SessionsViewModel: session.list failed", e)
            DebugLog.log("ERROR", "SessionsVM", "session.list failed: ${e.message}")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Dashboard: ${e.message ?: "Session load failed"}",
            )
        }
    }

    /**
     * Fetch session.list and fold the result into [uiState]. Shared by the
     * live-observer path and the cold-start fresh-connection fallback so both
     * actually populate the list (the old fresh path silently returned without
     * fetching — see loadDashboardSessions).
     */
    private suspend fun applySessionList(client: JsonRpcClient) {
        try {
            val asked = listLimit
            val rpcSessions = client.sessionList(limit = asked)
            // A short page means the server has nothing older — that is proof, so the
            // footer can hide itself. A full page means there is more to ask for.
            val atEnd = rpcSessions.size < asked
            DebugLog.log("INFO", "SessionsVM",
                "session.list(limit=$asked) → ${rpcSessions.size} sessions" +
                if (atEnd) " (short page: no older rows exist)" else " (page full: older rows remain)")

            val mapped = rpcSessions.map { it.toSessionSummary() }
            val filtered = mapped.filter { it.messageCount > 0 }
            val filteredCount = mapped.size - filtered.size
            if (filteredCount > 0) {
                DebugLog.log("INFO", "SessionsVM", "filtered out $filteredCount empty sessions")
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                sessions = filtered,
                fullListLoaded = atEnd,
                error = null,
            )
        } catch (e: Exception) {
            Log.e("Hermex", "SessionsViewModel: dashboard session load failed", e)
            DebugLog.log("ERROR", "SessionsVM", "dashboard session.list failed: ${e.message}")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Dashboard: ${e.message ?: "Session load failed"}",
            )
        }
    }

    // ── Mapping: JsonRpcClient.SessionInfo → SessionSummary ──

    private fun JsonRpcClient.SessionInfo.toSessionSummary(): SessionSummary {
        return SessionSummary(
            id = id,
            title = title,
            source = source,
            model = model,
            // Server sends started_at/ended_at as epoch seconds (Double), not ISO strings.
            startedAt = started_at,
            endedAt = ended_at,
            messageCount = message_count ?: 0,
            preview = preview,
            inputTokens = input_tokens ?: -1,
            outputTokens = output_tokens ?: -1,
            lastActivityAt = last_activity_at,
            isActive = is_active,
            unread = unread,
            sessionSource = source,
        )
    }

    /**
     * Server-side search (official parity, G5): GET /api/sessions/search
     * matches message CONTENT including tool activity — invisible to the
     * client-side title/preview filter (that's why "alive" missed "We
     * alive?" while fuzzy-matching an unrelated "active" title).
     */
    suspend fun serverSearch(query: String): List<ServerSearchHit> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 2) return@withContext emptyList()
            val r = com.gravol.hermesclone.core.network.DashboardApiClient.apiGet(
                "/api/sessions/search?q=" + java.net.URLEncoder.encode(q, "UTF-8") + "&limit=50")
            if (r !is com.gravol.hermesclone.core.network.NetworkResult.Success)
                return@withContext emptyList()
            val arr = (r.data as? kotlinx.serialization.json.JsonObject)
                ?.get("results") as? kotlinx.serialization.json.JsonArray
                ?: return@withContext emptyList()
            fun kotlinx.serialization.json.JsonObject.str(vararg keys: String): String? {
                for (k in keys) {
                    val p = this[k] as? kotlinx.serialization.json.JsonPrimitive ?: continue
                    val c = p.content
                    if (!c.isNullOrBlank() && c != "null") return c
                }
                return null
            }
            fun kotlinx.serialization.json.JsonObject.dbl(vararg keys: String): Double? {
                for (k in keys) {
                    val p = this[k] as? kotlinx.serialization.json.JsonPrimitive ?: continue
                    p.content?.toDoubleOrNull()?.let { return it }
                }
                return null
            }
            arr.mapNotNull { el ->
                val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val sid = o.str("session_id", "id") ?: return@mapNotNull null
                ServerSearchHit(
                    sessionId = sid,
                    title = o.str("title"),
                    // The gateway marks matches with >>> <<<; the official
                    // strips the markers and highlights the term instead —
                    // strip here (highlighting may follow).
                    snippet = o.str("snippet")?.replace(">>>", "")?.replace("<<<", ""),
                    role = o.str("role"),
                    profile = o.str("profile"),
                    source = o.str("source"),
                    lastActive = o.dbl("last_active", "started_at"),
                )
            }
        }

    companion object {
        private const val LIST_TIER_START = 25
        /** Reuse a fresh Insights snapshot on re-open instead of refetching hundreds of KB. */
        private const val INSIGHTS_TTL_MS = 120_000L
    }
}

/** One /api/sessions/search hit (server-side content search). */
data class ServerSearchHit(
    val sessionId: String,
    val title: String?,
    val snippet: String?,
    val role: String?,
    val profile: String?,
    val source: String?,
    val lastActive: Double?,
)
