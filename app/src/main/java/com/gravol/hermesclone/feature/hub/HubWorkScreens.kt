package com.gravol.hermesclone.feature.hub

import android.net.Uri
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.NetworkResult
import com.gravol.hermesclone.di.AppModule
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date

// ─────────────────────────────────────────────────────────────
// Projects (WS projects.*)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubProjectsScreen(
    onBack: () -> Unit,
    onOpenSession: (id: String, title: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<JsonObject>?>(null) }
    var open by remember { mutableStateOf<JsonObject?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            try {
                val r = AppModule.provideRpcClient().requestRaw("projects.list")
                projects = when (r) {
                    is JsonArray -> r.map { it as JsonObject }
                    is JsonObject -> (r["projects"] as? JsonArray)?.map { it as JsonObject } ?: emptyList()
                    else -> emptyList()
                }
                error = null
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                navigationIcon = { IconButton(onBack.let { { it() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showNew = true }) { Icon(Icons.Filled.Add, "New Project") }
                },
            )
        },
    ) { pad ->
        open?.let { proj ->
            ProjectConversations(proj, onBack = { open = null }, onOpenSession = onOpenSession)
            return@Scaffold
        }
        if (error != null) {
            Text("Couldn't load projects: ${error}", Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(projects ?: emptyList(), key = { it.str("path") ?: it.str("name") ?: "?" }) { p ->
                HubRow(
                    Icons.Filled.FolderOpen,
                    p.str("name") ?: p.str("path") ?: "?",
                    p.num("session_count")?.toInt()?.toString(),
                    sub = p.str("path"),
                    onClick = { open = p },
                )
            }
        }
    }

    if (showNew) {
        var name by remember { mutableStateOf("") }
        var path by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New Project") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(desc, { desc = it }, label = { Text("What is it for?") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(path, { path = it }, label = { Text("Remote workspace (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    busy = true
                    scope.launch {
                        try {
                            AppModule.provideRpcClient().requestRaw(
                                "projects.create",
                                mapOf(
                                    "name" to name,
                                    "description" to desc,
                                    "cwd" to path.ifBlank { name },
                                ),
                            )
                            showNew = false
                            load()
                        } catch (e: Exception) {
                            error = e.message
                            showNew = false
                        }
                        busy = false
                    }
                }) { Text(if (busy) "Creating…" else "Create Project") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectConversations(
    project: JsonObject,
    onBack: () -> Unit,
    onOpenSession: (id: String, title: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<JsonObject>?>(null) }
    LaunchedEffect(project) {
        try {
            val cwd = project.str("path") ?: project.str("cwd") ?: ""
            val r = AppModule.provideRpcClient().requestRaw("projects.project_sessions", mapOf("cwd" to cwd))
            sessions = when (r) {
                is JsonArray -> r.map { it as JsonObject }
                is JsonObject -> (r["sessions"] as? JsonArray)?.map { it as JsonObject } ?: emptyList()
                else -> emptyList()
            }
        } catch (e: Exception) {
            sessions = emptyList()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project.str("name") ?: "Project") },
                navigationIcon = { IconButton(onBack.let { { it() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(sessions ?: emptyList(), key = { it.str("id") ?: "?" }) { s ->
                val id = s.str("id") ?: return@items
                HubRow(
                    Icons.Filled.Chat,
                    s.str("title")?.takeIf { it.isNotBlank() } ?: "Untitled",
                    s.num("message_count")?.toInt()?.let { "$it msgs" },
                    onClick = { onOpenSession(id, s.str("title") ?: "Untitled") },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Artifacts — gateway file browser (GET /api/files)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubArtifactsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf(System.getProperty("user.home") ?: "/") }
    var entries by remember { mutableStateOf<List<JsonObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }

    fun load(p: String) {
        scope.launch {
            entries = null
            val r = DashboardApiClient.apiGet("/api/files?path=" + URLEncoder.encode(p, "UTF-8"))
            if (r is NetworkResult.Success) {
                path = r.data.str("path") ?: p
                entries = r.data.arr("entries").map { it as JsonObject }
                error = null
            } else error = "Could not list $p"
        }
    }
    LaunchedEffect(Unit) { load(path) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (error == null && path.count { it == '/' } > 1) load(path.substringBeforeLast('/').ifBlank { "/" })
                        else onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                filter, { filter = it },
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                placeholder = { Text(path) }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
            )
            error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            val shown = (entries ?: emptyList()).filter {
                filter.isBlank() || (it.str("name") ?: "").contains(filter, true)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.str("path") ?: "?" }) { e ->
                    val dir = e.bool("is_directory") == true
                    val size = e.num("size")?.toLong()
                    HubRow(
                        if (dir) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                        e.str("name") ?: "?",
                        if (!dir && size != null) humanSize(size) else null,
                        onClick = { if (dir) load(e.str("path") ?: return@HubRow) },
                    )
                }
            }
        }
    }
}

private fun humanSize(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1f GB".format(n / 1e9)
    n >= 1_000_000 -> "%.1f MB".format(n / 1e6)
    n >= 1_000 -> "%.0f KB".format(n / 1e3)
    else -> "$n B"
}

// ─────────────────────────────────────────────────────────────
// Memory & Learning: learning graph list + curator + memory providers
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubMemoryScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nodes by remember { mutableStateOf<List<JsonObject>?>(null) }
    var curator by remember { mutableStateOf<JsonObject?>(null) }
    var showReset by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        DashboardApiClient.apiGet("/api/learning/graph").let {
            if (it is NetworkResult.Success) nodes = it.data.arr("nodes").map { n -> n as JsonObject }
        }
        DashboardApiClient.apiGet("/api/curator").let {
            if (it is NetworkResult.Success) curator = it.data as? JsonObject
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory & Learning") },
                navigationIcon = { IconButton(onBack.let { { it() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            HubSectionHeader("Your Constellation · ${nodes?.size ?: "…"}")
            val top = (nodes ?: emptyList())
                .sortedByDescending { it.num("useCount") ?: 0.0 }
                .take(40)
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    top.forEach { n ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(n.str("label") ?: "?", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${(n.num("useCount") ?: 0.0).toInt()} uses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            HubSectionHeader("Skill Curator")
            val paused = curator?.bool("paused") ?: false
            HubCard {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Curator", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (paused) "Paused" else "Active · every ${curator?.num("interval_hours")?.toInt() ?: 168} h",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(paused, { want ->
                        scope.launch {
                            apiCall("/api/curator/paused", "PUT", buildJsonObject { put("paused", want) }.toString())
                            DashboardApiClient.apiGet("/api/curator").let {
                                if (it is NetworkResult.Success) curator = it.data as? JsonObject
                            }
                        }
                    })
                }
                HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f))
                HubInnerRow(Icons.Filled.PlayArrow, "Run now", onClick = {
                    scope.launch { msg = apiCall("/api/curator/run", "POST", "{}") ?: "Curator run started" }
                })
                HubInnerRow(Icons.Filled.RestartAlt, "Reset Memory", onClick = { showReset = true })
            }
            msg?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset memory?") },
            text = { Text("Clears the agent's stored memory on the gateway. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { msg = apiCall("/api/memory/reset", "POST", "{}") ?: "Memory reset" }
                    showReset = false
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancel") } },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Usage: GET /api/analytics/usage (+ per-model rollup)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubUsageScreen(onBack: () -> Unit) {
    var usage by remember { mutableStateOf<JsonObject?>(null) }
    var models by remember { mutableStateOf<List<JsonObject>?>(null) }
    var days by remember { mutableStateOf(30) }

    LaunchedEffect(days) {
        val r = DashboardApiClient.apiGet("/api/analytics/usage?days=$days")
        if (r is NetworkResult.Success) usage = r.data as? JsonObject
        val m = DashboardApiClient.apiGet("/api/analytics/models?days=$days")
        if (m is NetworkResult.Success) models = m.data.arr("models").map { it as JsonObject }
    }

    val daily = usage?.arr("daily") ?: JsonArray(emptyList())
    val spend = daily.sumOf { it.num("estimated_cost") ?: 0.0 }
    val sessions = daily.sumOf { (it.num("sessions") ?: 0.0).toLong() }
    val calls = daily.sumOf { (it.num("api_calls") ?: 0.0).toLong() }
    val tokens = daily.sumOf {
        (it.num("input_tokens") ?: 0.0) + (it.num("output_tokens") ?: 0.0) + (it.num("cache_read_tokens") ?: 0.0)
    }
    val peak = daily.maxOfOrNull { it.num("estimated_cost") ?: 0.0 } ?: 0.0
    val avg = if (daily.size > 0) spend / daily.size else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usage") },
                navigationIcon = { IconButton(onBack.let { { it() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = { days = if (days == 30) 7 else 30 }) { Text(if (days == 30) "30D" else "7D") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            HubSectionHeader("Overview")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("SPEND", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$%.2f".format(spend), style = MaterialTheme.typography.headlineMedium)
                    Text("Last $days days · list-price estimate", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Stat("AVG / DAY", "$%.2f".format(avg), Modifier.weight(1f))
                        Stat("PEAK", "$%.2f".format(peak), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Stat("SESSIONS", sessions.toString(), Modifier.weight(1f))
                        Stat("API CALLS", calls.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Stat("TOKENS", humanTokens(tokens), Modifier.weight(1f))
                        Stat("PER SESSION", if (sessions > 0) "$%.2f".format(spend / sessions) else "—", Modifier.weight(1f))
                    }
                }
            }
            HubSectionHeader("Daily spend")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    daily.takeLast(14).forEach { d ->
                        val c = d.num("estimated_cost") ?: 0.0
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(d.str("day")?.takeLast(5) ?: "", style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(44.dp))
                            LinearProgressIndicator(
                                progress = { if (peak > 0) (c / peak).toFloat() else 0f },
                                modifier = Modifier.weight(1f).height(8.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("$%.2f".format(c), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            HubSectionHeader("Models used · ${models?.size ?: "…"}")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    (models ?: emptyList()).sortedByDescending { (it.num("input_tokens") ?: 0.0) + (it.num("output_tokens") ?: 0.0) }
                        .take(15).forEach { m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.str("model") ?: "?", style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${m.str("provider") ?: ""} · ${(m.num("sessions") ?: 0.0).toInt()} sessions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    humanTokens((m.num("input_tokens") ?: 0.0) + (m.num("output_tokens") ?: 0.0)),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun humanTokens(d: Double): String = when {
    d >= 1e9 -> "%.1fB".format(d / 1e9)
    d >= 1e6 -> "%.1fM".format(d / 1e6)
    d >= 1e3 -> "%.0fK".format(d / 1e3)
    else -> d.toInt().toString()
}
