// Official Hermes app home screen ("Agents" tree), reconstructed from the
// reference screenshots + live UI dumps of the real app on the test device.
//
// Layout spec (1080×2400 reference, density 2.625):
//  - canvas #000022, agents_backdrop pinned to the bottom
//  - top-left pill [chat(active), cluster] at (10, 55)dp; top-right pill
//    [filters, more] mirrored at the right edge
//  - "Agents" section header at 17dp left, chevron far right
//  - agent rows: orb avatar 29dp at x19, name 17sp semibold at x61,
//    relative time right, chevron; description line 14sp secondary
//  - child sessions: tree rail at x40dp with elbow into a 7dp accent dot
//    at x61, title 17sp at x73, time 13sp tertiary, chevron
//  - "N more sessions ›" tertiary 14sp
//  - bottom: search pill (h48, r24, #0C1A3C) + circular compose FAB #1D2761
package com.gravol.hermesclone.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Window
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import com.gravol.hermesclone.R
import com.gravol.hermesclone.core.network.SessionSummary
import com.gravol.hermesclone.feature.sessions.SessionsViewModel
import com.gravol.hermesclone.ui.orb.BlobAvatar
import com.gravol.hermesclone.ui.orb.MascotAvatar
import com.gravol.hermesclone.ui.orb.recipeFor
import com.gravol.hermesclone.ui.theme.*
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

// ─── data helpers (mirrored from the V4 screen so this file is self-contained) ───

private fun SessionSummary.lastActivityTs(): Double? =
    lastActivityAt ?: lastActive ?: startedAt

private val dateFormat: DateFormat get() = DateFormat.getDateInstance(DateFormat.MEDIUM)

private fun relativeTime(epochSeconds: Double): String {
    val now = System.currentTimeMillis()
    val ts = (epochSeconds * 1000).toLong()
    val minutes = (now - ts) / 60_000
    val days = minutes / (60 * 24)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        days < 7 -> "${days}d ago"
        days < 30 -> "${days / 7} weeks ago"
        days < 365 -> "${days / 30} months ago"
        else -> "${days / 365}y ago"
    }
}

private fun agentKeyFor(s: SessionSummary): String =
    (s.source?.takeIf { it.isNotBlank() } ?: "other").lowercase()

private fun agentDisplayName(key: String): String =
    key.replaceFirstChar { it.uppercaseChar() }

private data class HomeAgentGroup(
    val key: String,
    val name: String,
    val description: String,
    val lastTs: Double?,
    val sessions: List<SessionSummary>,
    // Server-side profile total (REST `total`). The official "N more sessions" number is
    // serverTotal − visible rows (verified: 1331−15 → "1316 more sessions").
    val total: Int? = null,
)

private const val PEEK = 5

@Composable
fun HomeScreen(
    onSessionTap: (SessionSummary) -> Unit = {},
    onSettings: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    onOpenAgents: () -> Unit = {},
    activeSessions: StateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap()),
    viewModel: SessionsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bots by viewModel.bots.collectAsStateWithLifecycle()
    val activeMap by activeSessions.collectAsStateWithLifecycle()
    val workingIds by viewModel.working.collectAsStateWithLifecycle()
    val homeFilters by viewModel.filters.collectAsStateWithLifecycle()
    val pinnedSet by viewModel.pinnedIds.collectAsStateWithLifecycle()
    val readMap by viewModel.readTimes.collectAsStateWithLifecycle()
    val routineExtras by viewModel.routineExtras.collectAsStateWithLifecycle()
    val archivedExtras by viewModel.archivedExtras.collectAsStateWithLifecycle()
    val includeCounts by viewModel.includeCounts.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadBots() }
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshWorking()
            kotlinx.coroutines.delay(4_000)
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var filterSub by rememberSaveable { mutableStateOf<String?>(null) }
    // Official has no layout/sort/window controls — fixed defaults (clean-copy pass).
    val layoutMode = 0
    val showHidden = false
    val windowDays = 0
    val sortByName = false
    val expand = remember { mutableStateMapOf<String, Int>() }   // key → visible children (-1 = all)
    var agentsCollapsed by rememberSaveable { mutableStateOf(false) }
    var seeded by rememberSaveable { mutableStateOf(false) }
    var menuSession by remember { mutableStateOf<SessionSummary?>(null) }

    val q = query.trim()
    // v0.2.25 (G5): server-side content search — the gateway matches message
    // CONTENT including tool activity, which the client-side title/preview
    // filter can never see (official shows snippet + "Matched in tool
    // activity" rows for these). Debounced so typing doesn't spam the API.
    var serverHits by remember {
        mutableStateOf<List<com.gravol.hermesclone.feature.sessions.ServerSearchHit>>(emptyList())
    }
    LaunchedEffect(q) {
        if (q.length < 2) { serverHits = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(350)
        serverHits = viewModel.serverSearch(q)
    }
    // Official nests search matches UNDER their session row inside the agent
    // tree (snippet child rows; tool matches render as a "Matched in tool
    // activity" label row without the raw output). v0.2.25.
    val hitsById: Map<String, List<com.gravol.hermesclone.feature.sessions.ServerSearchHit>> =
        remember(serverHits) { serverHits.groupBy { it.sessionId } }
    val hitsByProfile: Map<String, List<com.gravol.hermesclone.feature.sessions.ServerSearchHit>> =
        remember(serverHits) { serverHits.groupBy { it.profile ?: "default" } }
    val windowStartSec = remember(windowDays) {
        (System.currentTimeMillis() / 1000.0) - windowDays.coerceAtLeast(1) * 86_400.0
    }
    val homeVisibleLegacy = remember(state.sessions, q, windowStartSec) {
        state.sessions.filter { s ->
            val ts = s.lastActivityTs()
            (ts == null || windowDays == 0 || ts * 1000.0 >= windowStartSec * 1000.0) &&
                (q.isEmpty() ||
                    (s.title ?: "").contains(q, ignoreCase = true) ||
                    (s.preview ?: "").contains(q, ignoreCase = true) ||
                    agentDisplayName(agentKeyFor(s)).contains(q, ignoreCase = true))
        }
    }
    // ── Bot model (official): groups come from the profiles roster; each bot
    // shows its own sessions. Fallback: group loaded sessions by source.
    val windowOk: (Double?) -> Boolean = { ts ->
        windowDays == 0 || ts == null || ts * 1000.0 >= windowStartSec * 1000.0
    }
    val filteredBots = remember(bots, homeFilters, workingIds, pinnedSet, readMap, routineExtras, archivedExtras) {
        viewModel.applyFilters(bots, homeFilters, workingIds, pinnedSet, readMap)
    }
    val displayGroups: List<HomeAgentGroup> = if (bots.isNotEmpty()) {
        filteredBots.mapNotNull { b ->
            val sessions = b.sessions
                .filter { windowOk(it.last_activity_at ?: it.started_at) }
                .filter { s ->
                    q.isEmpty() ||
                        (s.title ?: "").contains(q, ignoreCase = true) ||
                        (s.preview ?: "").contains(q, ignoreCase = true) ||
                        b.displayName.contains(q, ignoreCase = true)
                }
                .map { info ->
                    // Client-side unread (official parity): never opened on this device, or
                    // active since the last open. The gateway's `unread` watermark stays NULL
                    // for sessions no client ever marked read, yet the official dots them.
                    val readAt = readMap[info.id]
                    val actTs = info.last_activity_at ?: info.started_at
                    val unreadNow = readAt?.let { (actTs ?: 0.0) > it } ?: true
                    SessionSummary(
                        id = info.id, title = info.title, source = b.name, model = info.model,
                        startedAt = info.started_at, lastActivityAt = info.last_activity_at,
                        lastActive = info.last_activity_at, preview = info.preview,
                        messageCount = info.message_count ?: 0,
                        unread = unreadNow,
                        sessionSource = info.source,
                    )
                }
                // v0.2.25: merge server content-search hits for this profile
                // into the tree (official: matched sessions appear nested
                // under their agent, even when the title never matched).
                .let { base ->
                    if (q.isEmpty()) base
                    else {
                        val have = base.map { it.id }.toSet()
                        val extra = (hitsByProfile[b.name] ?: emptyList())
                            .filter { it.sessionId !in have }
                            .distinctBy { it.sessionId }
                            .map { hit ->
                                val readAt = readMap[hit.sessionId]
                                val unreadNow = readAt?.let { (hit.lastActive ?: 0.0) > it } ?: true
                                SessionSummary(
                                    id = hit.sessionId,
                                    title = hit.title ?: hit.sessionId,
                                    source = b.name,
                                    lastActive = hit.lastActive,
                                    lastActivityAt = hit.lastActive,
                                    preview = hit.snippet,
                                    unread = unreadNow,
                                )
                            }
                        base + extra
                    }
                }
            // Bots with no matching sessions hide while searching; visible when idle ("say hi").
            if (sessions.isEmpty() && (q.isNotEmpty() || windowDays != 0 && b.lastSessionTs == null)) return@mapNotNull null
            HomeAgentGroup(
                key = b.displayName.lowercase(),
                name = b.displayName,
                description = b.description.ifBlank { "No conversations yet — say hi" },
                lastTs = b.sessions
                    .mapNotNull { it.started_at }.minOrNull()
                    ?.takeIf {
                        System.currentTimeMillis() / 1000.0 - it < 30 * 86_400.0
                    },
                sessions = sessions.sortedByDescending { it.lastActivityTs() ?: 0.0 },
                total = b.totalSessions,
            )
        }
            .let { g ->
                if (sortByName) g.sortedBy { it.name.lowercase() }
                else g.sortedWith(
                    compareByDescending<HomeAgentGroup> { it.sessions.maxOfOrNull { s -> s.lastActivityTs() ?: 0.0 } ?: it.lastTs ?: 0.0 }
                )
            }
    } else {
        homeVisibleLegacy
            .groupBy { agentKeyFor(it) }
            .map { (key, list) ->
                val sorted = list.sortedByDescending { it.lastActivityTs() ?: 0.0 }
                HomeAgentGroup(
                    key = key, name = agentDisplayName(key),
                    description = "No conversations yet — say hi",
                    lastTs = sorted.maxOfOrNull { it.lastActivityTs() ?: 0.0 },
                    sessions = sorted,
                )
            }
            .let { g ->
                if (sortByName) g.sortedBy { it.name.lowercase() }
                else g.sortedByDescending { it.lastTs ?: 0.0 }
            }
    }

    // Official seeds the most recent group expanded on first load.
    LaunchedEffect(displayGroups.map { it.key }) {
        val first = displayGroups.firstOrNull()?.key ?: return@LaunchedEffect
        if ((seeded || expand.values.none { it > 0 }) && expand[first] == null) {
            expand[first] = PEEK
            seeded = true
        }
    }

    Box(Modifier.fillMaxSize().background(HermesBackground)) {
        // Bottom backdrop art, faded into the canvas.
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.agents_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .graphicsLayer { alpha = 0.75f },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(HermesBackground.copy(alpha = 0.9f), Color.Transparent),
                    )
                )
        )

        val density = LocalDensity.current

        // ── content tree ──
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 112.dp, bottom = 96.dp),
        ) {
            // Official hides the "Agents" heading while a search returns nothing:
            // mb_fu's first content is the mascot at 135dp, no header row.
            val searchingEmpty = q.isNotEmpty() && displayGroups.isEmpty() &&
                serverHits.isEmpty() && !state.isLoading
            if (!searchingEmpty) item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { agentsCollapsed = !agentsCollapsed }
                        .padding(start = 17.dp, end = 17.dp, top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Agents", color = HermesTextPrimary, fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (agentsCollapsed)
                            Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null, tint = HermesTextSecondary,
                    )
                }
            }
            if (!agentsCollapsed) {
                if (displayGroups.isEmpty() && !state.isLoading) {
                    item { NoMatchingAgents(searching = q.isNotEmpty()) }
                }
                val flatSessions: List<SessionSummary>? =
                    if (layoutMode == 1) displayGroups.flatMap { it.sessions } else null
                if (flatSessions != null) {
                    itemsIndexed(flatSessions, key = { _, ses -> "flat-${ses.id}" }) { _, session ->
                        ChildRow(
                            session = session,
                            first = false, last = false,
                            working = activeMap[session.id] == true || workingIds.contains(session.id),
                            onClick = { onSessionTap(session) },
                            onLongClick = { menuSession = session },
                        )
                    }
                }
                if (layoutMode != 1) displayGroups.forEach { group ->
                    item(key = "agent-${group.key}") {
                        AgentRow(
                            group = group,
                            expanded = (expand[group.key] ?: 0) > 0,
                            onClick = {
                                // Official parity (G3, verified on-device 2026-11-12): tapping
                                // an agent row (anywhere on it, chevron included) starts a new
                                // chat bound to THAT agent — it does NOT collapse the group.
                                if (!state.isCreating) viewModel.createSession(profile = group.name) { sid ->
                                    if (sid != null) onSessionTap(
                                        SessionSummary(id = sid, title = "New session", source = group.name),
                                    )
                                }
                            },
                        )
                    }
                    val shown = if (layoutMode == 2) 0 else (expand[group.key]
                        ?: if (q.isNotEmpty()) -1 else 0)
                    if (shown == 0 && group.total != null && group.total > 0 &&
                        layoutMode != 2 && q.isEmpty()) {
                        // Official shows "N more sessions ›" under COLLAPSED groups too
                        // (verified: link-curator "101 more sessions", clerk "9 more").
                        item(key = "morerow-${group.key}") {
                            MoreSessionsRow(hidden = group.total, onClick = { expand[group.key] = PEEK })
                        }
                    }
                    if (shown != 0) {
                        val visibleKids = if (shown < 0) group.sessions
                        else group.sessions.take(shown)
                        itemsIndexed(visibleKids, key = { _, s -> "s-${s.id}" }) { index, session ->
                            // Official collapses many hits into AT MOST two
                            // rows per session: the BEST message snippet
                            // (user message preferred, else any non-tool) and
                            // a single "Matched in tool activity" label when
                            // any tool match exists. v0.2.25 G7.
                            val hits = hitsById[session.id].orEmpty()
                            val display = ArrayList<com.gravol.hermesclone.feature.sessions.ServerSearchHit>(2)
                            (hits.firstOrNull { it.role == "user" && !it.snippet.isNullOrBlank() }
                                ?: hits.firstOrNull { it.role != "tool" && !it.snippet.isNullOrBlank() })
                                ?.let { display.add(it) }
                            hits.firstOrNull { it.role == "tool" }?.let { display.add(it) }
                            Column {
                                ChildRow(
                                    session = session,
                                    first = index == 0,
                                    last = index == visibleKids.lastIndex && display.isEmpty(),
                                    working = activeMap[session.id] == true || workingIds.contains(session.id),
                                    onClick = { onSessionTap(session) },
                                    onLongClick = { menuSession = session },
                                )
                                display.forEachIndexed { mi, hit ->
                                    MatchRow(
                                        hit = hit,
                                        last = index == visibleKids.lastIndex && mi == display.lastIndex,
                                        onClick = { onSessionTap(session) },
                                    )
                                }
                            }
                        }
                        if (group.sessions.size > shown && shown > 0) {
                            item(key = "more-${group.key}") {
                                // Official counts "more" from the SERVER total, not the loaded page
                                // (verified: 1316 more for a 15-row page over 1,331 sessions).
                                val hidden = (if (homeFilters.showMode == "All")
                                    group.total?.let { it - shown } else null)
                                    ?: (group.sessions.size - shown)
                                MoreSessionsRow(
                                    hidden = hidden.coerceAtLeast(0),
                                    onClick = { expand[group.key] = -1 },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── floating top pills ──
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 10.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PillGroup {
                PillButton(onClick = onOpenAgents) {
                    Icon(Icons.Filled.Chat, "Agents",
                        tint = HermesTextPrimary, modifier = Modifier.size(21.dp))
                }
                // Official home left pill second icon is the bot-cluster glyph (a11y "More")
                // and opens the Bot Screen hub — was a phone-inventory "Devices" glyph before.
                PillButton(onClick = onOpenDevices) {
                    Icon(Icons.Filled.DeviceHub, "More",
                        tint = HermesTextPrimary, modifier = Modifier.size(21.dp))
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 10.dp, top = 12.dp),
        ) {
            PillGroup {
                Box {
                    PillButton(onClick = { showFilters = true }) {
                        // Official tints the Filters icon accent-blue while filters differ from defaults.
                        Icon(Icons.Outlined.FilterList, "Filters",
                            tint = if (homeFilters != SessionsViewModel.HomeFilters()) HermesAccent
                                   else HermesTextPrimary,
                            modifier = Modifier.size(21.dp))
                    }
                }
                // Official parity: "More options" (⋮) opens App Settings directly —
                // verified on-device. The old Layout/Sort/Recent-sessions popup was
                // Hermex legacy backbone, removed in the clean-copy pass (v0.2.26).
                PillButton(onClick = onSettings) {
                    Icon(Icons.Outlined.MoreVert, "More options",
                        tint = HermesTextPrimary, modifier = Modifier.size(21.dp))
                }
            }
        }

        // ── bottom search + compose ──
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SearchPill(modifier = Modifier.weight(1f), query = query, onQuery = { query = it })
            ComposeFab(
                busy = state.isCreating,
                onClick = {
                    if (!state.isCreating) {
                        viewModel.createSession { sid ->
                            if (sid != null) onSessionTap(SessionSummary(id = sid, title = "New session"))
                        }
                    }
                },
            )
        }

        // FiltersMenu declared at ROOT scope: Popups anchor to their declaring
        // composable; the official filter popup is window-centered (~x370..735).
        if (showFilters) FiltersMenu(
            filters = homeFilters,
            onOpenSub = { filterSub = it },
            onReset = { viewModel.resetFilters() },
            onDismiss = { showFilters = false },
        )
        filterSub?.let { which ->
            FilterSubmenu(
                which = which,
                filters = homeFilters,
                counts = includeCounts,
                botNames = bots.map { it.name to it.displayName },
                onFilters = { viewModel.setFilters(it) },
                onRefreshCounts = { viewModel.refreshIncludeCounts() },
                onDismiss = { filterSub = null },
            )
        }
        menuSession?.let { s ->
            SessionContextMenu(session = s, onDismiss = { menuSession = null })
        }
    }
}

// ─── top pills ───

@Composable
private fun PillGroup(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(HermesSurface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

@Composable
private fun PillButton(active: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) HermesSurfaceActive else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun FiltersMenu(
    filters: SessionsViewModel.HomeFilters,
    onOpenSub: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    // v0.2.10: official Filters main popup. Submenus open as a SEPARATE
    // HomeScreen-hosted popup (FilterSubmenu) — two overlapping popups made
    // every submenu row but the first eat touches in the window below.
    // v0.2.12: official parity — "Reset filters" row appears only when filters differ
    // from defaults (verified on-device: absent at All/None/All, present at "+ 2 kinds").
    // Official popup centers under the top bar (bounds x370..735 → center ≈ screen center).
    Popup(alignment = Alignment.TopCenter, properties = PopupProperties(focusable = true), onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                // Measured onto official bounds (text x497 y283): top 64dp, nudge +9dp right.
                .padding(top = 64.dp)
                .offset(x = 9.dp)
                // Official popup is compact: ~360px wide (137dp), rows ~98px pitch (37dp).
                .width(140.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(HermesSurfaceRaised)
                .padding(vertical = 8.dp),
        ) {
            MenuRow("Show", filters.showMode, Icons.Outlined.Window) { onOpenSub("show"); onDismiss() }
            MenuRow("Include", filters.includeSub, Icons.Filled.Layers) { onOpenSub("include"); onDismiss() }
            MenuRow("Agents", filters.agentsSub, Icons.Filled.People) { onOpenSub("agents"); onDismiss() }
            if (filters != SessionsViewModel.HomeFilters()) {
                HorizontalDivider(
                    color = HermesRail,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onReset(); onDismiss() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.History, null, tint = HermesTextPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Reset filters", color = HermesTextPrimary, fontSize = 15.sp, maxLines = 2)
                }
            }
        }
    }
}

/** Switch row inside the filter submenus (official Include/Agents popups use switches). */
@Composable
private fun MenuSwitchRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    subtitle: String?,
    checked: Boolean,
    leading: (@Composable () -> Unit)? = null,
    labelColor: Color = HermesTextPrimary,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChecked(!checked) }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading(); Spacer(Modifier.width(14.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = HermesTextPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = labelColor, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = HermesTextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedTrackColor = HermesAccent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF5D5D75),
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun FilterSubmenu(
    which: String,
    filters: SessionsViewModel.HomeFilters,
    counts: SessionsViewModel.IncludeCounts,
    botNames: List<Pair<String, String>>,
    onFilters: (SessionsViewModel.HomeFilters) -> Unit,
    onRefreshCounts: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Official submenu popups carry their own header (Back arrow + title) and open
    // lazily-fetched "N sessions" counts for the Include rows.
    LaunchedEffect(Unit) { if (which == "include") onRefreshCounts() }
    Popup(alignment = Alignment.TopEnd, properties = PopupProperties(focusable = true), onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                // Official submenu right edge x≈925 → end padding ≈59dp.
                .padding(top = 95.dp, end = 59.dp)
                // Official submenu popup ~535px wide (204dp).
                .width(205.dp)
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(18.dp))
                .background(HermesSurfaceRaised)
                .padding(vertical = 8.dp, horizontal = 0.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = HermesTextPrimary)
                }
                Text(
                    when (which) { "show" -> "Show"; "include" -> "Include"; else -> "Agents" },
                    color = HermesTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            when (which) {
                "show" -> {
                    val icons: Map<String, androidx.compose.ui.graphics.vector.ImageVector> = mapOf(
                        "All" to Icons.Outlined.ChatBubbleOutline,
                        "Unread" to Icons.Filled.MarkChatUnread,
                        "Working" to Icons.Outlined.Bolt,
                        "Pinned" to Icons.Outlined.PushPin,
                    )
                    listOf("All", "Unread", "Working", "Pinned").forEach { m ->
                        MenuCheck(m, icons.getValue(m), filters.showMode == m) {
                            onFilters(filters.copy(showMode = m)); onDismiss()
                        }
                    }
                }
                "include" -> {
                    MenuSwitchRow(
                        "Routine runs", Icons.Outlined.Schedule,
                        counts.routines?.let { "$it sessions" }, filters.includeRoutineRuns,
                    ) { on -> onFilters(filters.copy(includeRoutineRuns = on)) }
                    MenuSwitchRow(
                        "Kanban workers", Icons.Outlined.ViewKanban,
                        counts.kanban?.let { "$it sessions" }, filters.includeKanban,
                    ) { on -> onFilters(filters.copy(includeKanban = on)) }
                    MenuSwitchRow(
                        "Archived", Icons.Outlined.Inventory,
                        counts.archived?.let { "$it sessions" }, filters.includeArchived,
                    ) { on -> onFilters(filters.copy(includeArchived = on)) }
                }
                "agents" -> {
                    MenuSwitchRow("All agents", Icons.Filled.People, null, filters.selectedBots == null) { on ->
                        onFilters(filters.copy(selectedBots = if (on) null else emptySet()))
                    }
                    botNames.forEach { (name, display) ->
                        val on = filters.selectedBots?.contains(name) ?: true
                        MenuSwitchRow(
                            display, null, null, on,
                            leading = { BlobAvatar(recipe = recipeFor(name.lowercase()), size = 28.dp) },
                            // Official colors each bot's name with its blob palette color.
                            labelColor = recipeFor(name.lowercase()).body,
                        ) {
                            val cur = filters.selectedBots ?: botNames.map { (n, _) -> n }.toSet()
                            onFilters(filters.copy(selectedBots = if (on) cur - name else cur + name))
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun MenuRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            // Official main-popup rows are 37dp pitch (label+value stacked, no padding).
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = HermesTextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = HermesTextPrimary, fontSize = 17.sp, lineHeight = 20.sp)
            Text(subtitle, color = HermesTextSecondary, fontSize = 13.sp, lineHeight = 15.sp)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = HermesTextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MenuSection(title: String) {
    Text(title, color = HermesTextTertiary, fontSize = 12.sp,
        modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 10.dp, bottom = 4.dp))
}

@Composable
private fun MenuCheck(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = HermesTextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = HermesTextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (checked) Icon(Icons.Filled.Check, null, tint = HermesTextPrimary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MenuAction(label: String, onClick: () -> Unit) {
    Text(
        label, color = HermesTextPrimary, fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

// ─── agent + session rows ───

@Composable
private fun AgentRow(group: HomeAgentGroup, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 19.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BlobAvatar(recipe = recipeFor(group.key), size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(group.name, color = HermesTextPrimary, fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(group.description, color = HermesTextSecondary, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        group.lastTs?.let {
            Text(relativeTime(it), color = HermesTextTertiary, fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = HermesTextTertiary, modifier = Modifier.size(18.dp))
    }
}

/**
 * v0.2.25: one search-match child row nested under its session in the agent
 * tree. Message matches show the snippet (markers already stripped); tool
 * matches render the official "Matched in tool activity" label with a tool
 * glyph and NO raw output (verified against the official app).
 */
@Composable
private fun MatchRow(
    hit: com.gravol.hermesclone.feature.sessions.ServerSearchHit,
    last: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val railX = with(LocalDensity.current) { 40.dp.toPx() }
        val dotX = with(LocalDensity.current) { 61.dp.toPx() }
        Canvas(Modifier.width(73.dp).height(34.dp)) {
            val mid = size.height / 2
            val stroke = 1.dp.toPx()
            val color = HermesRail
            drawLine(color, Offset(railX, 0f), Offset(railX, if (last) mid else size.height), stroke)
            drawLine(color, Offset(railX, mid), Offset(dotX - 2.dp.toPx(), mid), stroke)
        }
        Spacer(Modifier.width(16.dp))
        if (hit.role == "tool") {
            Icon(
                Icons.Filled.Build, null,
                modifier = Modifier.size(14.dp), tint = HermesTextSecondary,
            )
            Spacer(Modifier.width(8.dp))
            Text("Matched in tool activity", color = HermesTextSecondary, fontSize = 15.sp)
        } else {
            Text(
                hit.snippet ?: "",
                color = HermesTextSecondary, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChildRow(
    session: SessionSummary,
    first: Boolean,
    last: Boolean,
    working: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tree rail: vertical at x40dp + elbow into the row centre.
        val railX = with(LocalDensity.current) { 40.dp.toPx() }
        val dotX = with(LocalDensity.current) { 61.dp.toPx() }
        Canvas(Modifier.width(73.dp).height(37.dp)) {
            val mid = size.height / 2
            val stroke = 1.dp.toPx()
            val color = HermesRail
            val top = if (first) mid else 0f
            val bottom = if (last) mid else size.height
            drawLine(color, Offset(railX, top), Offset(railX, bottom), stroke)
            if (!last || first) drawLine(color, Offset(railX, mid), Offset(dotX - 2.dp.toPx(), mid), stroke)
        }
        // Official parity: the blue dot marks SERVER-unread sessions only (not every row),
        // and routine-started rows carry an amber circle-of-dots chip between the dot and
        // the title (content-desc "Started by a schedule", sampled #FF9F0A).
        if (!working && session.unread == true) {
            Canvas(Modifier.size(16.dp)) {
                drawCircle(HermesAccent, radius = 3.5.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        val isRoutine = session.sessionSource == "cron" || session.sessionSource == "oneshot"
        if (isRoutine) {
            Icon(
                Icons.Filled.Pending, "Started by a schedule",
                tint = Color(0xFFFF9F0A), modifier = Modifier.size(15.dp).padding(end = 2.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            session.title ?: session.id,
            color = HermesTextPrimary, fontSize = 17.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (working) {
            CircularProgressIndicator(color = HermesAccent, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
            Text("Working", color = HermesAccent, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
        } else {
            session.lastActivityTs()?.let {
                Text(relativeTime(it), color = HermesTextTertiary, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
            }
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = HermesTextTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MoreSessionsRow(hidden: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = 65.dp, top = 4.dp, bottom = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$hidden more session${if (hidden == 1) "" else "s"}",
            color = HermesMuted, fontSize = 14.sp,
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = HermesTextTertiary, modifier = Modifier.size(15.dp))
    }
}

// ─── empty search state: fur blob + copy ───

@Composable
private fun NoMatchingAgents(searching: Boolean) {
    Column(
        // Official search-empty state (mb_fu, 13 frames): mascot ink top 135.2dp
        // from the screen top, i.e. 23dp below this LazyColumn's 112dp content
        // padding, and NO "Agents" header above it. Blob->title ink gap 26.3dp,
        // title->subtitle ink gap 14.1dp (clone Roboto ink runs ~5dp looser).
        modifier = Modifier.fillMaxWidth().padding(top = if (searching) 23.dp else 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MascotAvatar(size = 48.dp)
        Spacer(Modifier.height(18.5.dp))
        Text(if (searching) "No matching agents" else "No agents yet",
            color = HermesTextPrimary, fontSize = 17.sp)
        Spacer(Modifier.height(if (searching) 3.dp else 5.dp))
        Text(if (searching) "Try another name, or words from a session."
             else "Say hi to your agent below.",
            color = HermesTextSecondary, fontSize = 12.sp)
    }
}


// ─── bottom bar pieces ───

@Composable
private fun SearchPill(modifier: Modifier = Modifier, query: String, onQuery: (String) -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(HermesSurface),
        contentAlignment = Alignment.CenterStart,
    ) {
        Icon(Icons.Outlined.Search, null, tint = HermesTextSecondary,
            modifier = Modifier.padding(start = 16.dp).size(20.dp))
        if (query.isEmpty()) {
            Text("Search agents and sessions", color = HermesTextTertiary, fontSize = 16.sp,
                modifier = Modifier.padding(start = 48.dp))
        }
        androidx.compose.foundation.text.BasicTextField(
            value = query, onValueChange = onQuery,
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(HermesTextPrimary),
            textStyle = LocalTextStyle.current.copy(color = HermesTextPrimary, fontSize = 16.sp),
            modifier = Modifier.padding(start = 48.dp, end = 44.dp).fillMaxWidth(),
            decorationBox = { inner -> inner() },
        )
        if (query.isNotEmpty()) {
            Icon(Icons.Filled.Clear, "Clear search", tint = HermesTextSecondary,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(20.dp)
                    .clickable { onQuery("") })
        }
    }
}

@Composable
private fun ComposeFab(busy: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(HermesFab)
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(color = HermesTextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        else Icon(Icons.Outlined.Edit, "New message", tint = HermesTextPrimary, modifier = Modifier.size(21.dp))
    }
}

// ─── long-press menu (keep until the official one is captured) ───

@Composable
private fun SessionContextMenu(
    session: SessionSummary,
    onDismiss: () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(session.title ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val rpc = com.gravol.hermesclone.di.AppModule.provideRpcClient()

    if (renaming) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = HermesSurfaceRaised,
            title = { Text("Rename session", color = HermesTextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        draft, { draft = it },
                        label = { Text("Title") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = HermesTextPrimary,
                            unfocusedTextColor = HermesTextPrimary,
                        ),
                    )
                    error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    busy = true
                    scope.launch {
                        runCatching { rpc.sessionTitle(session.id, draft) }
                            .onSuccess { onDismiss() }
                            .onFailure { error = it.message }
                        busy = false
                    }
                }) { Text(if (busy) "Saving…" else "Save", color = HermesAccent) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = HermesTextSecondary) } },
        )
        return
    }

    // Official parity (v0.2.26, verified on-device): long-press menu is
    // Pin / Archive / Rename / Delete — no title, no Close button (tap-outside).
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HermesSurfaceRaised,
        title = null,
        text = {
            Column {
                TextButton(onClick = {
                    scope.launch {
                        runCatching {
                            val repo = com.gravol.hermesclone.feature.settings.SettingsRepository(
                                ctx.applicationContext)
                            repo.togglePinned(session.id)
                        }
                        onDismiss()
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Pin", color = HermesTextPrimary)
                }
                TextButton(onClick = {
                    scope.launch { runCatching { rpc.sessionArchive(session.id, true) }; onDismiss() }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Archive", color = HermesTextPrimary)
                }
                TextButton(onClick = { renaming = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Rename", color = HermesTextPrimary)
                }
                TextButton(onClick = {
                    scope.launch {
                        runCatching { rpc.sessionDelete(session.id) }
                        onDismiss()
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
    )
}
