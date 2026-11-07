package com.gravol.hermesclone.feature.hub

import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.NetworkResult
import com.gravol.hermesclone.di.AppModule
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

// ─────────────────────────────────────────────────────────────
// Plugins — GET /api/dashboard/plugins, enable/disable by name
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubPluginsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var plugins by remember { mutableStateOf<List<JsonObject>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val r = DashboardApiClient.apiGet("/api/dashboard/plugins")
        if (r is NetworkResult.Success) plugins = r.data.itemsArray.map { it as JsonObject }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plugins · ${plugins?.size ?: "…"}") },
                navigationIcon = { IconButton(onBack.let { { it() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(plugins ?: emptyList(), key = { it.str("name") ?: "?" }) { p ->
                val name = p.str("name") ?: return@items
                HubCard {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.str("label") ?: name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                p.str("description") ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(Modifier.widthIn(max = 150.dp), horizontalAlignment = Alignment.End) {
                            TextButton(onClick = {
                                scope.launch {
                                    msg = apiCall("/api/dashboard/plugins/$name/visibility", "POST",
                                        buildJsonObject { put("visible", true) }.toString())
                                        ?.let { null } ?: "Updated"
                                }
                            }) { Text("Visible") }
                        }
                    }
                }
            }
        }
    }
    msg?.let { }
}

// ─────────────────────────────────────────────────────────────
// Connections — WS connectors.catalog / connectors.list
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubConnectionsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var services by remember { mutableStateOf<List<JsonObject>?>(null) }
    var accounts by remember { mutableStateOf<List<JsonObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val rpc = AppModule.provideRpcClient()
            val cat = rpc.requestRaw("connectors.catalog")
            services = when (cat) {
                is JsonArray -> cat.map { it as JsonObject }
                is JsonObject -> (cat["services"] as? JsonArray ?: cat["catalog"] as? JsonArray)?.map { it as JsonObject }
                else -> null
            } ?: emptyList()
            try {
                val acc = rpc.requestRaw("connectors.accounts")
                accounts = when (acc) {
                    is JsonArray -> acc.map { it as JsonObject }
                    is JsonObject -> (acc["accounts"] as? JsonArray)?.map { it as JsonObject } ?: emptyList()
                    else -> emptyList()
                }
            } catch (_: Exception) { accounts = emptyList() }
        } catch (e: Exception) {
            error = e.message
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connections") },
                navigationIcon = { IconButton(onBack.let { { it() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        if (error != null) {
            Text("Connectors unavailable: ${error}", Modifier.padding(20.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            val connected = accounts?.mapNotNull { it.str("service") ?: it.str("id") }?.toSet() ?: emptySet()
            items(services ?: emptyList(), key = { it.str("id") ?: it.str("name") ?: "?" }) { s ->
                val id = s.str("id") ?: s.str("name") ?: "?"
                val on = connected.contains(id)
                HubRow(
                    if (on) Icons.Filled.Link else Icons.Filled.LinkOff,
                    s.str("label") ?: s.str("name") ?: id,
                    if (on) "Connected" else null,
                    sub = s.str("description"),
                    onClick = {
                        scope.launch {
                            try {
                                AppModule.provideRpcClient().requestRaw("connectors.connect", mapOf("service" to id))
                                error = null
                            } catch (e: Exception) { error = e.message }
                        }
                    },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Toolsets — GET /api/tools/toolsets + PUT enable
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubToolsetsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sets by remember { mutableStateOf<List<JsonObject>?>(null) }

    fun load() {
        scope.launch {
            val r = DashboardApiClient.apiGet("/api/tools/toolsets")
            if (r is NetworkResult.Success) sets = r.data.itemsArray.map { it as JsonObject }
        }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Toolsets") },
                navigationIcon = { IconButton(onBack.let { { it() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(sets ?: emptyList(), key = { it.str("name") ?: "?" }) { t ->
                val name = t.str("name") ?: return@items
                var on by remember(t) { mutableStateOf(t.bool("enabled") == true) }
                HubCard {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.str("label") ?: name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                t.str("description") ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(on, { want ->
                            on = want
                            scope.launch {
                                val err = apiCall("/api/tools/toolsets/$name", "PUT",
                                    buildJsonObject { put("enabled", want) }.toString())
                                if (err != null) on = !want
                            }
                        })
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Webhooks — GET/POST /api/webhooks, DELETE /api/webhooks/{name}
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubWebhooksScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var subs by remember { mutableStateOf<List<JsonObject>?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            val r = DashboardApiClient.apiGet("/api/webhooks")
            if (r is NetworkResult.Success) subs = r.data.arr("subscriptions").map { it as JsonObject }
        }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Webhooks") },
                navigationIcon = { IconButton(onBack.let { { it() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "New Webhook") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(subs ?: emptyList(), key = { it.str("name") ?: "?" }) { s ->
                val name = s.str("name") ?: return@items
                HubRow(
                    Icons.Filled.Webhook, name,
                    if (s.bool("enabled") == false) "disabled" else null,
                    sub = (s.arr("events").joinToString { it.jsonPrimitive.contentOrNull ?: "" }),
                    onClick = {
                        scope.launch {
                            msg = apiCall("/api/webhooks/$name", "DELETE") ?: "Deleted"
                            load()
                        }
                    },
                )
            }
        }
        msg?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary) }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var events by remember { mutableStateOf("session.completed") }
        var prompt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New Webhook") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(events, { events = it }, label = { Text("Events (comma separated)") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(prompt, { prompt = it }, label = { Text("Prompt") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        msg = apiCall(
                            "/api/webhooks", "POST",
                            buildJsonObject {
                                put("name", name)
                                putJsonArray("events") { events.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) } }
                                put("prompt", prompt)
                            }.toString(),
                        ) ?: "Created"
                        showAdd = false
                        load()
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Messaging — GET /api/messaging/platforms (status only)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubMessagingScreen(onBack: () -> Unit) {
    var platforms by remember { mutableStateOf<List<JsonObject>?>(null) }
    LaunchedEffect(Unit) {
        val r = DashboardApiClient.apiGet("/api/messaging/platforms")
        if (r is NetworkResult.Success) platforms = r.data.arr("platforms").map { it as JsonObject }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messaging") },
                navigationIcon = { IconButton(onBack.let { { it() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(platforms ?: emptyList(), key = { it.str("id") ?: "?" }) { p ->
                val state = p.str("state") ?: p.str("status")
                HubRow(
                    Icons.Filled.Forum,
                    p.str("name") ?: p.str("id") ?: "?",
                    state,
                    sub = p.str("description"),
                    onClick = { },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Voice — GET /api/audio/voice-config
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubVoiceScreen(onBack: () -> Unit) {
    var cfg by remember { mutableStateOf<JsonObject?>(null) }
    LaunchedEffect(Unit) {
        val r = DashboardApiClient.apiGet("/api/audio/voice-config")
        if (r is NetworkResult.Success) cfg = r.data as? JsonObject
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice") },
                navigationIcon = { IconButton(onBack.let { { it() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Speech Engine", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (cfg == null) {
                        Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        (cfg as JsonObject).forEach { (k, v) ->
                            if (v !is JsonObject && v !is JsonArray && v !is JsonNull) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(v.jsonPrimitive.content, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Live Mode and voice calls are configured on the gateway (config.yaml → voice).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Keys — GET /api/env, PUT /api/env {key,value}
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubKeysScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var env by remember { mutableStateOf<JsonObject?>(null) }
    var filter by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Pair<String, JsonObject>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val r = DashboardApiClient.apiGet("/api/env")
        if (r is NetworkResult.Success) env = r.data as? JsonObject
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keys") },
                navigationIcon = { IconButton(onBack.let { { it() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                filter, { filter = it },
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                placeholder = { Text("Search keys") }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
            )
            msg?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary) }
            val keys = (env?.toMap() ?: emptyMap())
                .filter { (k, _) -> filter.isBlank() || k.contains(filter, true) }
                .toList()
                .sortedBy { (k, _) -> k }
            LazyColumn(Modifier.fillMaxSize()) {
                items(keys, key = { it.first }) { (k, v) ->
                    val o = v as? JsonObject
                    HubRow(
                        if (o?.bool("is_set") == true) Icons.Filled.CheckCircle else Icons.Filled.Radio,
                        k,
                        if (o?.bool("is_set") == true) "set" else null,
                        sub = o?.str("description"),
                        onClick = { editing = k to (o ?: JsonObject(emptyMap())) },
                    )
                }
            }
        }
    }

    editing?.let { (key, meta) ->
        var value by remember(key) { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(key) },
            text = {
                Column {
                    meta.str("description")?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value, { value = it },
                        label = { Text("New value") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (value.isBlank()) { editing = null; return@TextButton }
                    busy = true
                    scope.launch {
                        msg = apiCall("/api/env", "PUT",
                            buildJsonObject { put("key", key); put("value", value) }.toString())
                            ?: "$key saved"
                        busy = false
                        editing = null
                    }
                }) { Text(if (busy) "Saving…" else "Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Instances — GET /api/pairing, approve / revoke
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubInstancesScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<JsonObject>?>(null) }
    var approved by remember { mutableStateOf<List<JsonObject>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            val r = DashboardApiClient.apiGet("/api/pairing")
            if (r is NetworkResult.Success) {
                pending = r.data.arr("pending").map { it as JsonObject }
                approved = r.data.arr("approved").map { it as JsonObject }
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instances") },
                navigationIcon = { IconButton(onBack.let { { it() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            if (!pending.isNullOrEmpty()) item { HubSectionHeader("Pending") }
            items(pending ?: emptyList(), key = { "p" + (it.str("id") ?: it.str("name") ?: "?") }) { p ->
                HubRow(Icons.Filled.PersonAdd, p.str("name") ?: p.str("id") ?: "?", "pending",
                    onClick = {
                        scope.launch {
                            msg = apiCall("/api/pairing/approve", "POST",
                                buildJsonObject { p["id"]?.let { put("id", it) } ?: put("name", p.str("name") ?: "") }.toString())
                                ?: "Approved"
                            load()
                        }
                    })
            }
            item { HubSectionHeader("Approved") }
            items(approved ?: emptyList(), key = { "a" + (it.str("id") ?: it.str("name") ?: "?") }) { p ->
                HubRow(Icons.Filled.PhoneAndroid, p.str("name") ?: p.str("id") ?: "?",
                    onClick = {
                        scope.launch {
                            msg = apiCall("/api/pairing/revoke", "POST",
                                buildJsonObject { p["id"]?.let { put("id", it) } ?: put("name", p.str("name") ?: "") }.toString())
                                ?: "Revoked"
                            load()
                        }
                    })
            }
            msg?.let { item { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary) } }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Logs — export the in-app debug ring and share it
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubLogsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = { IconButton(onBack.let { { it() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            Text(
                "The clone keeps a rolling debug ring (RPC, socket, events). Export it to share with support or debug on the desktop.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        try {
                            val path = com.gravol.hermesclone.core.network.DebugLog.exportShort(
                                appVersion = "clone",
                                serverUrl = DashboardApiClient.baseUrl(),
                            )
                            val f = File(path)
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Hermex logs"))
                            msg = null
                        } catch (e: Exception) {
                            msg = "Export failed: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export & Share Debug Log") }
            msg?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
        }
    }
}
