package com.gravol.hermesclone.feature.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.NetworkResult
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/**
 * v0.2.26 — Default Model + Model Settings screens rebuilt to official parity
 * (device-captured 2026-11-12 against ai.nousresearch.hermes.mobile).
 *
 * Official facts driving this file:
 *  - Default Model screen = header card (humanized model + instance chip),
 *    Favourites | All models tabs; All models = search + user-defined instance
 *    chips + friendly-named rows of ONE instance at a time (default: current),
 *    each row with a star button (a11y "Add to favourites: <model> (<provider>)").
 *    Favourites are CLIENT-side (server has no favourites API — verified by
 *    grepping the gateway source).
 *  - Advanced = "Model Settings": Main Model / Defaults & Fallbacks /
 *    Auxiliary Tasks / Subagents / Mixture of Agents / Use Recommended Default,
 *    then an inline Usage section (30D, MODELS USED, TOTAL SESSIONS, Recent Models).
 *  - All strings below are verbatim from on-device dumps.
 */
private data class ModelOption(val provider: String, val name: String, val providerName: String)

private data class ModelOptionsPayload(
    val options: List<ModelOption>,
    val currentSlug: String,
    val userDefined: List<Pair<String, String>>, // slug to display name, in payload order
)

private fun parseModelOptions(data: JsonElement?): ModelOptionsPayload {
    val obj = data as? JsonObject ?: return ModelOptionsPayload(emptyList(), "", emptyList())
    var current = ""
    val ud = mutableListOf<Pair<String, String>>()
    val opts = obj.arr("providers").flatMap { p ->
        val slug = p.str("slug") ?: return@flatMap emptyList()
        if (p.bool("is_current") == true) current = slug
        if (p.bool("is_user_defined") == true) ud.add(slug to (p.str("name") ?: slug))
        p.arr("models").mapNotNull { m ->
            val n = m.jsonPrimitive.contentOrNull ?: return@mapNotNull null
            ModelOption(slug, n, p.str("name") ?: slug)
        }
    }
    return ModelOptionsPayload(opts, current, ud)
}

private const val PREFS = "hermex"
private const val PREFS_FAVS = "model_favourites"

private fun loadFavourites(ctx: android.content.Context): MutableSet<String> {
    val raw = ctx.getSharedPreferences(PREFS, 0).getString(PREFS_FAVS, "[]") ?: "[]"
    return runCatching {
        Json.parseToJsonElement(raw).jsonArray.map { it.jsonPrimitive.content }.toMutableSet()
    }.getOrDefault(mutableSetOf())
}

private fun saveFavourites(ctx: android.content.Context, favs: Set<String>) {
    val arr = buildJsonArray { favs.forEach { add(it) } }
    ctx.getSharedPreferences(PREFS, 0).edit().putString(PREFS_FAVS, arr.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubDefaultModelScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var showAdvanced by remember { mutableStateOf(false) }
    if (showAdvanced) {
        HubModelSettingsScreen(onBack = { showAdvanced = false })
        return
    }

    var info by remember { mutableStateOf<JsonObject?>(null) }
    var payload by remember { mutableStateOf(ModelOptionsPayload(emptyList(), "", emptyList())) }
    var filter by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) } // 0 Favourites, 1 All models
    var selectedSlug by remember { mutableStateOf<String?>(null) }
    var favourites: Set<String> by remember { mutableStateOf(loadFavourites(ctx)) }

    fun reload() {
        scope.launch {
            DashboardApiClient.apiGet("/api/model/info").let {
                if (it is NetworkResult.Success) info = it.data as? JsonObject
            }
            val r = DashboardApiClient.apiGet("/api/model/options")
            if (r is NetworkResult.Success) {
                payload = parseModelOptions(r.data)
                if (selectedSlug == null) selectedSlug = payload.currentSlug
            }
        }
    }
    LaunchedEffect(Unit) { reload() }

    val model = info?.str("model") ?: "—"
    val provider = info?.str("provider") ?: ""
    val currentProviderName = payload.options.firstOrNull { it.provider == payload.currentSlug }?.providerName
        ?: provider

    fun setModel(m: ModelOption) {
        busy = true; msg = null
        scope.launch {
            val err = apiCall(
                "/api/model/set", "POST",
                buildJsonObject {
                    put("scope", "main")
                    put("provider", m.provider)
                    put("model", m.name)
                }.toString(),
            )
            busy = false
            msg = err ?: "Applied — new chats use ${humanizeModel(m.name)}"
            if (err == null) reload()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Default Model") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { showAdvanced = true }) { Text("Advanced") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Header card: humanized model + instance chip (official).
            Surface(color = MaterialTheme.colorScheme.surface, shape = HubCardShape,
                modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(humanizeModel(model), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(currentProviderName,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("Favourites") })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("All models") })
            }
            msg?.let {
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp))

            if (tab == 0) {
                if (favourites.isEmpty()) {
                    // Official empty-state string, verbatim.
                    Text("Star a model in All models to keep it here.",
                        Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        val favOpts = favourites.mapNotNull { key ->
                            payload.options.firstOrNull { "${it.provider}|${it.name}" == key }
                        }
                        items(favOpts, key = { "fav-" + it.provider + "|" + it.name }) { m ->
                            ModelFavRow(m, model, payload.currentSlug, favourites,
                                onPick = { setModel(m) },
                                onStar = {
                                    if (favourites.contains(keyOf(m))) {
                                        favourites = favourites - keyOf(m)
                                    } else favourites = favourites + keyOf(m)
                                    saveFavourites(ctx, favourites)
                                })
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    filter, { filter = it },
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    placeholder = { Text("Search all models") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                )
                // Instance chips: user-defined providers only (official scope), name + count.
                LazyRow(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(payload.userDefined, key = { it.first }) { (slug, name) ->
                        val count = payload.options.count { it.provider == slug }
                        val sel = selectedSlug == slug
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (sel) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { selectedSlug = slug },
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(6.dp))
                                Text("$count", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                val sel = selectedSlug ?: payload.currentSlug
                val shown = remember(payload, filter, sel) {
                    payload.options.filter {
                        it.provider == sel &&
                            (filter.isBlank() || it.name.contains(filter, true) ||
                                humanizeModel(it.name).contains(filter, true))
                    }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.provider + "|" + it.name }) { m ->
                        ModelFavRow(m, model, payload.currentSlug, favourites,
                            onPick = { setModel(m) },
                            onStar = {
                                if (favourites.contains(keyOf(m))) favourites = favourites - keyOf(m)
                                else favourites = favourites + keyOf(m)
                                saveFavourites(ctx, favourites)
                            })
                    }
                }
            }
        }
    }
}

private fun keyOf(m: ModelOption) = "${m.provider}|${m.name}"

/** Friendly row + star; a11y label mirrors the official one verbatim. */
@Composable
private fun ModelFavRow(
    m: ModelOption,
    currentModel: String,
    currentSlug: String,
    favourites: Set<String>,
    onPick: () -> Unit,
    onStar: () -> Unit,
) {
    val isFav = favourites.contains(keyOf(m))
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(humanizeModel(m.name), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (m.provider == currentSlug && m.name == currentModel) {
            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onStar) {
            if (isFav) Icon(Icons.Filled.Star, "Remove from favourites: ${m.name} (${m.provider})",
                tint = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Outlined.StarBorder, "Add to favourites: ${m.name} (${m.provider})",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Model Settings (official "Advanced") ─────────────────────────

private val REASONING_LEVELS = listOf(
    "" to "Inherit", "none" to "None", "minimal" to "Minimal", "low" to "Low",
    "medium" to "Medium", "high" to "High", "xhigh" to "X-High", "max" to "Max", "ultra" to "Ultra",
)

@Composable
fun HubModelSettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<JsonObject?>(null) }
    var cfg by remember { mutableStateOf<JsonObject?>(null) }
    var aux by remember { mutableStateOf<JsonObject?>(null) }
    var moa by remember { mutableStateOf<JsonObject?>(null) }
    var usage by remember { mutableStateOf<JsonObject?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var showDefaults by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var showAux by remember { mutableStateOf(false) }
    var showMoa by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            DashboardApiClient.apiGet("/api/model/info").let {
                if (it is NetworkResult.Success) info = it.data as? JsonObject
            }
            DashboardApiClient.apiGet("/api/config").let {
                if (it is NetworkResult.Success) cfg = it.data as? JsonObject
            }
            DashboardApiClient.apiGet("/api/model/auxiliary").let {
                if (it is NetworkResult.Success) aux = it.data as? JsonObject
            }
            DashboardApiClient.apiGet("/api/model/moa").let {
                if (it is NetworkResult.Success) moa = it.data as? JsonObject
            }
            DashboardApiClient.apiGet("/api/analytics/models?days=30").let {
                if (it is NetworkResult.Success) usage = it.data as? JsonObject
            }
        }
    }
    LaunchedEffect(Unit) { reload() }

    if (showDefaults) { HubDefaultsFallbacksScreen(cfg, onBack = { showDefaults = false }, onChanged = { reload() }); return }
    if (showSubs) { HubSubagentsScreen(cfg, onBack = { showSubs = false }, onChanged = { reload() }); return }
    if (showAux) { HubAuxScreen(onBack = { showAux = false }); return }
    if (showMoa) { HubMoaScreen(onBack = { showMoa = false }); return }

    val model = info?.str("model") ?: "—"
    val provider = info?.str("provider") ?: ""
    val ctxLen = info?.num("effective_context_length")?.toInt() ?: 0

    val modelBlock = cfg?.get("model") as? JsonObject
    val deleg = cfg?.get("delegation") as? JsonObject
    val reasoningKey = modelBlock?.str("reasoning_effort")?.takeIf { it.isNotBlank() }
    val reasoningLabel = REASONING_LEVELS.firstOrNull { it.first == reasoningKey }?.second ?: "Medium"
    val fallbacks = cfg?.arr("fallback_providers") ?: buildJsonArray {}
    val fallbackText = if (fallbacks.isEmpty()) "No fallbacks" else "${fallbacks.size} fallbacks"

    val tasks = aux?.arr("tasks") ?: buildJsonArray {}
    val pinned = tasks.count {
        val p = it.str("provider") ?: "auto"; val mm = it.str("model") ?: ""
        p != "auto" || mm.isNotBlank()
    }

    val activePreset = (moa?.str("active_preset")?.takeIf { !it.isNullOrBlank() }
        ?: moa?.str("default_preset") ?: "default")
    val preset = ((moa?.get("presets") as? JsonObject)?.get(activePreset)) as? JsonObject
    val refs = preset?.arr("reference_models") ?: buildJsonArray {}
    val agg = (preset?.get("aggregator") as? JsonObject)?.let { "${it.str("provider")}/${it.str("model")}" } ?: ""
    val moaSub = buildString {
        append("${refs.size} reference")
        if (refs.size != 1) append("s")
        if (agg.isNotBlank()) append(" · $agg")
    }

    val subModel = deleg?.str("model") ?: ""
    val subSub = if (subModel.isBlank()) "Inherit" else humanizeModel(subModel)

    HubScaffold("Model Settings", onBack = onBack) { pad ->
        Column(pad.fillMaxSize().verticalScroll(rememberScrollState())) {
            HubRow(Icons.Filled.Memory, "Main Model",
                sub = "$provider · $model",
                sub2 = if (ctxLen > 0) "${fmtContext(ctxLen)} context" else null,
                onClick = { /* official row is informational; picker lives on the main screen */ })
            HubRow(Icons.Filled.Tune, "Defaults & Fallbacks",
                sub = "$reasoningLabel reasoning · $fallbackText",
                onClick = { showDefaults = true })
            HubRow(Icons.Filled.Description, "Auxiliary Tasks",
                sub = "${tasks.size} tasks · $pinned pinned",
                onClick = { showAux = true })
            HubRow(Icons.Filled.Groups, "Subagents", sub = subSub,
                onClick = { showSubs = true })
            HubRow(Icons.Filled.Layers, "Mixture of Agents", sub = moaSub,
                onClick = { showMoa = true })
            HubRow(Icons.Filled.AutoFixHigh, "Use Recommended Default",
                onClick = {
                    scope.launch {
                        val r = DashboardApiClient.apiGet("/api/model/recommended-default")
                        msg = if (r is NetworkResult.Success) {
                            val o = r.data as? JsonObject
                            "Gateway recommends: ${o?.str("detail") ?: o?.str("model") ?: "—"}"
                        } else "Not available"
                    }
                })
            msg?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary) }

            // Usage section (official inline section, 30D window).
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Usage", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("30D", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            val modelsArr = usage?.arr("models") ?: buildJsonArray {}
            val totals = usage?.get("totals") as? JsonObject
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("MODELS USED", "${modelsArr.size}", Modifier.weight(1f))
                StatBox("TOTAL SESSIONS",
                    (totals?.num("total_sessions")?.toInt() ?: modelsArr.sumOf { it.num("sessions")?.toInt() ?: 0 }).toString(),
                    Modifier.weight(1f))
            }
            if (modelsArr.isNotEmpty()) {
                Text("Recent Models", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall)
                modelsArr.take(5).forEach { m ->
                    HubInnerRow(Icons.Filled.Memory, humanizeModel(m.str("model") ?: "—"),
                        value = "${(m.num("sessions")?.toInt() ?: 0)} sessions",
                        onClick = {})
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = HubCardShape, color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/** PUT /api/config deep-merges, so partial root keys are safe writes (server-verified). */
private suspend fun putConfigRoot(key: String, value: JsonElement): String? =
    apiCall("/api/config", "PUT", buildJsonObject {
        putJsonObject("config") { put(key, value) }
    }.toString())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubDefaultsFallbacksScreen(
    cfg: JsonObject?,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val modelBlock = cfg?.get("model") as? JsonObject
    var reasoning by remember(cfg) {
        mutableStateOf(modelBlock?.str("reasoning_effort") ?: "")
    }
    var fastMode by remember(cfg) { mutableStateOf(modelBlock?.bool("fast_mode") == true) }
    var fallbacks: MutableList<String> by remember(cfg) {
        mutableStateOf(cfg?.arr("fallback_providers")?.mapNotNull {
            it.jsonPrimitive.contentOrNull
        }?.toMutableList() ?: mutableListOf<String>())
    }
    var pickReasoning by remember { mutableStateOf(false) }

    fun saveModelBlock(partial: JsonObject) {
        scope.launch {
            apiCall("/api/config", "PUT", buildJsonObject {
                putJsonObject("config") { put("model", partial) }
            }.toString())
            onChanged()
        }
    }

    HubScaffold("Defaults & Fallbacks", onBack = onBack) { pad ->
        Column(pad.fillMaxSize().verticalScroll(rememberScrollState())) {
            HubSectionHeader("New Conversations")
            HubCard {
                // Official shows the EFFECTIVE default: empty resolves to Medium, not "Inherit".
                HubInnerRow(Icons.Filled.Tune, "Reasoning",
                    value = REASONING_LEVELS.firstOrNull { it.first == reasoning.ifBlank { "medium" } }?.second ?: "Medium",
                    onClick = { pickReasoning = true })
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(16.dp))
                    Text("Fast Mode", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(fastMode, { on ->
                        fastMode = on
                        saveModelBlock(buildJsonObject { put("fast_mode", on) })
                    })
                }
            }
            // Official explanation, verbatim.
            Text(
                "The profile\u2019s defaults on this server, saved right away. Every new chat starts " +
                    "with them; the model picker in a chat changes only that chat.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HubSectionHeader("Fallback Models")
            HubCard {
                fallbacks.forEachIndexed { i, f ->
                    HubInnerRow(Icons.Filled.SwapHoriz, f, onClick = {
                        fallbacks.removeAt(i)
                        scope.launch {
                            putConfigRoot("fallback_providers",
                                buildJsonArray { fallbacks.forEach { add(it) } })
                            onChanged()
                        }
                    })
                }
                HubInnerRow(Icons.Filled.Add, "Add Fallback", onClick = {
                    scope.launch {
                        // Official opens a model picker; we cycle the hosted catalog lightly:
                        // add the gateway-recommended default if set, else nothing to add.
                        val r = DashboardApiClient.apiGet("/api/model/recommended-default")
                        if (r is NetworkResult.Success) {
                            val o = r.data as? JsonObject
                            val m = o?.str("model")
                            if (!m.isNullOrBlank() && fallbacks.none { it.contains(m) }) {
                                fallbacks.add("${o.str("provider") ?: "nous"}/$m")
                                putConfigRoot("fallback_providers",
                                    buildJsonArray { fallbacks.forEach { add(it) } })
                                onChanged()
                            }
                        }
                    }
                })
            }
            Text(
                "Tried in order when the main model is unavailable. Use each model\u2019s menu to change " +
                    "the order. Changes are saved right away.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickReasoning) {
        AlertDialog(
            onDismissRequest = { pickReasoning = false },
            title = { Text("Reasoning") },
            text = {
                Column {
                    REASONING_LEVELS.forEach { (v, label) ->
                        val active = (reasoning.ifBlank { "medium" }) == v
                        TextButton(
                            onClick = {
                                reasoning = v
                                pickReasoning = false
                                saveModelBlock(buildJsonObject { put("reasoning_effort", v) })
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label, color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface)
                            if (active) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.Check, null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubSubagentsScreen(
    cfg: JsonObject?,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val deleg = cfg?.get("delegation") as? JsonObject
    var subModel by remember(cfg) { mutableStateOf(deleg?.str("model") ?: "") }
    var subReasoning by remember(cfg) { mutableStateOf(deleg?.str("reasoning_effort") ?: "") }
    var pickReasoning by remember { mutableStateOf(false) }

    fun saveDelegation(key: String, value: String) {
        scope.launch {
            apiCall("/api/config", "PUT", buildJsonObject {
                putJsonObject("config") {
                    putJsonObject("delegation") { put(key, value) }
                }
            }.toString())
            if (key == "model") subModel = value else subReasoning = value
            onChanged()
        }
    }

    HubScaffold("Subagents", onBack = onBack) { pad ->
        Column(pad.fillMaxSize().verticalScroll(rememberScrollState())) {
            HubCard {
                HubInnerRow(Icons.Filled.Memory, "Model",
                    value = if (subModel.isBlank()) "—" else humanizeModel(subModel),
                    onClick = {
                        // Official opens the full instance picker (deep future work); for now
                        // cycle to the next distinct model among the user's custom instances.
                        scope.launch {
                            val r = DashboardApiClient.apiGet("/api/model/options")
                            if (r is NetworkResult.Success) {
                                val p = parseModelOptions(r.data)
                                val names = p.userDefined.mapNotNull { (slug, _) ->
                                    p.options.firstOrNull { it.provider == slug }?.name
                                }.distinct()
                                val idx = names.indexOf(subModel)
                                val next = names.getOrNull((idx + 1).coerceAtLeast(0).mod(names.size))
                                if (!next.isNullOrBlank()) saveDelegation("model", next)
                            }
                        }
                    })
                HubInnerRow(Icons.Filled.Tune, "Reasoning",
                    value = REASONING_LEVELS.firstOrNull { it.first == subReasoning }?.second ?: "Inherit",
                    onClick = { pickReasoning = true })
            }
            Text(
                "Delegated tasks use this model and reasoning level. Inherit uses the parent chat\u2019s " +
                    "settings. Changes are saved right away.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickReasoning) {
        AlertDialog(
            onDismissRequest = { pickReasoning = false },
            title = { Text("Reasoning") },
            text = {
                Column {
                    REASONING_LEVELS.forEach { (_, label) ->
                        TextButton(
                            onClick = {
                                pickReasoning = false
                                saveDelegation("reasoning_effort",
                                    if (label == "Inherit") "" else label.lowercase().replace("-", ""))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label, color = MaterialTheme.colorScheme.onSurface) }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

/** Auxiliary Tasks: rows from GET /api/model/auxiliary; writes via POST /api/model/set scope=auxiliary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubAuxScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf<List<JsonObject>?>(null) }
    var editing by remember { mutableStateOf<JsonObject?>(null) }

    LaunchedEffect(Unit) {
        val r = DashboardApiClient.apiGet("/api/model/auxiliary")
        if (r is NetworkResult.Success) tasks = r.data.arr("tasks").map { it as JsonObject }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auxiliary Tasks") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(tasks ?: emptyList(), key = { it.str("task") ?: "?" }) { t ->
                val task = t.str("task") ?: "?"
                val provider = t.str("provider") ?: "auto"
                val model = t.str("model") ?: ""
                val follows = provider == "auto" && model.isBlank()
                HubRow(
                    if (follows) Icons.Filled.AutoMode else Icons.Filled.Description,
                    task.replaceFirstChar { it.uppercase() },
                    if (follows) "Auto" else "$provider · $model",
                    onClick = { editing = t },
                )
            }
        }
    }

    editing?.let { t ->
        val task = t.str("task") ?: "?"
        var provider by remember(t) { mutableStateOf(t.str("provider") ?: "auto") }
        var model by remember(t) { mutableStateOf(t.str("model") ?: "") }
        var saving by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(task.replaceFirstChar { it.uppercase() }) },
            text = {
                Column {
                    OutlinedTextField(provider, { provider = it },
                        label = { Text("Provider (auto = follow main)") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    saving = true
                    scope.launch {
                        val err = apiCall(
                            "/api/model/set", "POST",
                            buildJsonObject {
                                put("scope", "auxiliary")
                                put("task", task)
                                put("provider", provider)
                                put("model", model)
                            }.toString(),
                        )
                        saving = false
                        editing = null
                        if (err == null) {
                            val r = DashboardApiClient.apiGet("/api/model/auxiliary")
                            if (r is NetworkResult.Success) tasks = r.data.arr("tasks").map { it as JsonObject }
                        }
                    }
                }) { Text(if (saving) "Saving…" else "Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

/** Mixture of Agents: GET/PUT /api/model/moa (edits the active/default preset). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubMoaScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var aggregator by remember { mutableStateOf("provider/model") }
    var refs by remember { mutableStateOf("") }
    var refTemp by remember { mutableStateOf("0.6") }
    var aggTemp by remember { mutableStateOf("0.4") }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val r = DashboardApiClient.apiGet("/api/model/moa")
        if (r is NetworkResult.Success) {
            val obj = r.data as? JsonObject
            val active = obj?.str("active_preset")?.takeIf { !it.isNullOrBlank() }
                ?: obj?.str("default_preset") ?: "default"
            val p = (obj?.get("presets") as? JsonObject)?.get(active) as? JsonObject
            enabled = p?.bool("enabled") ?: false
            (p?.get("aggregator") as? JsonObject)?.let { aggregator = "${it.str("provider")}/${it.str("model")}" }
            refs = p?.arr("reference_models")?.joinToString("\n") { "${it.str("provider")}/${it.str("model")}" } ?: ""
            p?.num("reference_temperature")?.let { refTemp = it.toString() }
            p?.num("aggregator_temperature")?.let { aggTemp = it.toString() }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mixture of Agents") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", Modifier.weight(1f))
                Switch(enabled, { enabled = it })
            }
            Spacer(Modifier.height(12.dp))
            Text("Aggregator (provider/model)")
            OutlinedTextField(aggregator, { aggregator = it }, Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(12.dp))
            Text("References, one provider/model per line")
            OutlinedTextField(refs, { refs = it }, Modifier.fillMaxWidth().heightIn(min = 80.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Reference temp")
                    OutlinedTextField(refTemp, { refTemp = it }, Modifier.fillMaxWidth(), singleLine = true)
                }
                Column(Modifier.weight(1f)) {
                    Text("Aggregator temp")
                    OutlinedTextField(aggTemp, { aggTemp = it }, Modifier.fillMaxWidth(), singleLine = true)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val parts = aggregator.split("/", limit = 2)
                    val body = buildJsonObject {
                        put("enabled", enabled)
                        putJsonObject("aggregator") {
                            put("provider", parts.getOrElse(0) { "" })
                            put("model", parts.getOrElse(1) { "" })
                        }
                        putJsonArray("reference_models") {
                            refs.lines().filter { it.contains('/') }.forEach { line ->
                                val ps = line.split("/", limit = 2)
                                addJsonObject {
                                    put("provider", ps[0]); put("model", ps[1]); put("enabled", true)
                                }
                            }
                        }
                        put("reference_temperature", refTemp.toDoubleOrNull() ?: 0.6)
                        put("aggregator_temperature", aggTemp.toDoubleOrNull() ?: 0.4)
                    }
                    scope.launch { msg = apiCall("/api/model/moa", "PUT", body.toString()) ?: "Saved" }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = loaded,
            ) { Text("Save Mixture of Agents") }
            msg?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary) }
        }
    }
}
