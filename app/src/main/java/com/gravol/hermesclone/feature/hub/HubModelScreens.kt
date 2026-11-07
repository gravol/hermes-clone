package com.gravol.hermesclone.feature.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.NetworkResult
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/**
 * v0.2.7 — Model hub screens (official parity, minus stars which the gateway
 * does not persist): Default Model picker, Models (advanced) with Auxiliary
 * Tasks and Mixture of Agents editors.
 */
private data class ModelOption(val provider: String, val name: String)

private fun parseModelOptions(data: JsonElement?): Pair<List<ModelOption>, String> {
    val obj = data as? JsonObject ?: return emptyList<ModelOption>() to ""
    var current = ""
    val opts = obj.arr("providers").flatMap { p ->
        val slug = p.str("slug") ?: return@flatMap emptyList()
        if (p.bool("is_current") == true) current = slug
        p.arr("models").mapNotNull { m ->
            val n = m.jsonPrimitive.contentOrNull ?: return@mapNotNull null
            ModelOption(slug, n)
        }
    }
    return opts to current
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubDefaultModelScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showAdvanced by remember { mutableStateOf(false) }
    if (showAdvanced) {
        HubModelsAdvancedScreen(onBack = { showAdvanced = false })
        return
    }

    var info by remember { mutableStateOf<JsonObject?>(null) }
    var options by remember { mutableStateOf<List<ModelOption>?>(null) }
    var currentSlug by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            DashboardApiClient.apiGet("/api/model/info").let {
                if (it is NetworkResult.Success) info = it.data as? JsonObject
            }
            val r = DashboardApiClient.apiGet("/api/model/options")
            if (r is NetworkResult.Success) {
                val (opts, cur) = parseModelOptions(r.data)
                options = opts; currentSlug = cur
            }
        }
    }
    LaunchedEffect(Unit) { reload() }

    val model = info?.str("model") ?: "—"
    val provider = info?.str("provider") ?: ""

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
            Surface(color = MaterialTheme.colorScheme.surface, shape = HubCardShape,
                modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(model, style = MaterialTheme.typography.titleMedium)
                    Text(provider, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                filter, { filter = it },
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                placeholder = { Text("Search all models") }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
            )
            msg?.let {
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp))
            val shown = remember(options, filter) {
                options?.filter {
                    filter.isBlank() || it.name.contains(filter, true) || it.provider.contains(filter, true)
                } ?: emptyList()
            }
            LazyColumn(Modifier.fillMaxSize()) {
                shown.groupBy { it.provider }.forEach { (prov, models) ->
                    item {
                        Text(
                            prov + " · ${models.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp),
                        )
                    }
                    items(models, key = { it.provider + "/" + it.name }) { m ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
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
                                        msg = err ?: "Applied — new chats use ${m.name}"
                                        if (err == null) reload()
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(m.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (m.provider == currentSlug && m.name == model) {
                                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubModelsAdvancedScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showAux by remember { mutableStateOf(false) }
    var showMoa by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    if (showAux) { HubAuxScreen(onBack = { showAux = false }); return }
    if (showMoa) { HubMoaScreen(onBack = { showMoa = false }); return }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Models") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            HubSectionHeader("Model Settings")
            HubRow(Icons.Filled.Tune, "Auxiliary Tasks", null,
                sub = "Vision, compression, titles, review…",
                onClick = { showAux = true })
            HubRow(Icons.Filled.Groups, "Mixture of Agents", null,
                sub = "Reference models + aggregator",
                onClick = { showMoa = true })
            HubRow(Icons.Filled.AutoFixHigh, "Use Recommended Default", null,
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
        }
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
