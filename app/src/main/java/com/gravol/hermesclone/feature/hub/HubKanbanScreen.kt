package com.gravol.hermesclone.feature.hub

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.NetworkResult
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Kanban board parity with the official app (v0.2.9). Backed by the kanban
 * dashboard-plugin REST API: GET /api/plugins/kanban/boards, GET …/board,
 * POST …/tasks, PATCH …/tasks/{id}, DELETE …/tasks/{id}.
 */
private const val KB = "/api/plugins/kanban"

private data class KbTask(
    val id: String,
    val title: String,
    val body: String,
    val assignee: String,
    val status: String,
    val priority: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubKanbanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var boards by remember { mutableStateOf<List<JsonObject>?>(null) }
    var board by remember { mutableStateOf<String?>(null) }          // slug
    var columns by remember { mutableStateOf<List<Pair<String, List<KbTask>>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<KbTask?>(null) }
    var showNew by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            error = null
            when (val r = DashboardApiClient.apiGet("$KB/board" + (board?.let { "?board=$it" } ?: ""))) {
                is NetworkResult.Success -> columns = r.data.jsonObject["columns"]?.jsonArray
                    ?.map { c ->
                        val o = c.jsonObject
                        (o["name"]?.jsonPrimitive?.contentOrNull ?: "?") to
                            (o["tasks"]?.jsonArray ?: emptyList()).map { t ->
                                val to = t.jsonObject
                                KbTask(
                                    id = to["id"]?.jsonPrimitive?.contentOrNull ?: "",
                                    title = to["title"]?.jsonPrimitive?.contentOrNull ?: "",
                                    body = to["body"]?.jsonPrimitive?.contentOrNull ?: "",
                                    assignee = to["assignee"]?.jsonPrimitive?.contentOrNull ?: "",
                                    status = to["status"]?.jsonPrimitive?.contentOrNull ?: "",
                                    priority = to["priority"]?.jsonPrimitive?.intOrNull ?: 0,
                                )
                            }
                    }
                is NetworkResult.HttpError -> error = "Server error (${r.code})"
                is NetworkResult.Error -> error = r.exception.message
            }
        }
    }

    LaunchedEffect(Unit) {
        when (val r = DashboardApiClient.apiGet("$KB/boards")) {
            is NetworkResult.Success -> {
                val list = r.data.jsonObject["boards"]?.jsonArray?.map { it.jsonObject }.orEmpty()
                boards = list
                board = list.firstOrNull { it["is_current"]?.jsonPrimitive?.contentOrNull == "true" }
                    ?.get("slug")?.jsonPrimitive?.contentOrNull
                    ?: list.firstOrNull()?.get("slug")?.jsonPrimitive?.contentOrNull
            }
            is NetworkResult.HttpError -> error = "Server error (${r.code})"
            is NetworkResult.Error -> error = r.exception.message
        }
    }
    LaunchedEffect(board) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val n = columns?.sumOf { it.second.size }
                    Text(if (n == null) "Kanban" else "Kanban · $n tasks")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showNew = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "New Task")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Board switcher
            boards?.let { list ->
                if (list.size > 1) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        list.forEach { b ->
                            val slug = b["slug"]?.jsonPrimitive?.contentOrNull
                            FilterChip(
                                selected = board == slug,
                                onClick = { board = slug },
                                label = { Text(b["name"]?.jsonPrimitive?.contentOrNull ?: slug ?: "?") },
                            )
                        }
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    error != null -> Text(
                        error!!, Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                    columns == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> Row(
                        Modifier.fillMaxSize()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        columns!!.forEach { (name, tasks) ->
                            Column(
                                Modifier.width(280.dp).fillMaxSize()
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(
                                        name.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "  ${tasks.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                LazyColumn(
                                    contentPadding = PaddingValues(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    items(tasks, key = { it.id }) { t ->
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            ),
                                            onClick = { open = t },
                                        ) {
                                            Column(Modifier.padding(10.dp)) {
                                                Text(
                                                    t.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (t.assignee.isNotBlank() || t.priority != 0) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        if (t.assignee.isNotBlank()) Surface(
                                                            shape = MaterialTheme.shapes.small,
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                        ) {
                                                            Text(
                                                                t.assignee,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            )
                                                        }
                                                        if (t.priority != 0) Text(
                                                            "P${t.priority}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.tertiary,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Task detail: move between columns (PATCH status), delete ──
    open?.let { t ->
        val allStatuses = columns?.map { it.first } ?: emptyList()
        var body by remember(t) { mutableStateOf(t.body) }
        var dirty by remember(t) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { open = null; load() },
            title = { Text(t.title, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "${t.id}  ·  ${t.status}" + if (t.assignee.isNotBlank()) "  ·  @${t.assignee}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(body, { body = it; dirty = true }, minLines = 4)
                    Spacer(Modifier.height(8.dp))
                    Text("Move to", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        allStatuses.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { s ->
                                    FilterChip(
                                        selected = s == t.status,
                                        enabled = s != t.status,
                                        onClick = {
                                            scope.launch {
                                                DashboardApiClient.apiSend(
                                                    "$KB/tasks/${t.id}", "PATCH",
                                                    buildJsonObject { put("status", s) }.toString(),
                                                )
                                                open = null
                                                load()
                                            }
                                        },
                                        label = { Text(s, maxLines = 1) },
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        enabled = dirty,
                        onClick = {
                            scope.launch {
                                DashboardApiClient.apiSend(
                                    "$KB/tasks/${t.id}", "PATCH",
                                    buildJsonObject { put("body", body) }.toString(),
                                )
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                dirty = false
                            }
                        },
                    ) { Text("Save") }
                    TextButton(onClick = { open = null }) { Text("Close") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        DashboardApiClient.apiSend("$KB/tasks/${t.id}", "DELETE")
                        open = null
                        load()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    // ── New task (POST /tasks) ──
    if (showNew) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var assignee by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New Task") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(body, { body = it }, label = { Text("Body") }, minLines = 3)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(assignee, { assignee = it }, label = { Text("Assignee (profile, optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        scope.launch {
                            DashboardApiClient.apiSend(
                                "$KB/tasks", "POST",
                                buildJsonObject {
                                    put("title", title.trim())
                                    if (body.isNotBlank()) put("body", body)
                                    if (assignee.isNotBlank()) put("assignee", assignee.trim())
                                }.toString(),
                            )
                            showNew = false
                            load()
                        }
                    },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } },
        )
    }
}
