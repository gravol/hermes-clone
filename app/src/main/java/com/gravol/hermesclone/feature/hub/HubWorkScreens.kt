package com.gravol.hermesclone.feature.hub

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.NetworkResult
import com.gravol.hermesclone.di.AppModule
import com.gravol.hermesclone.ui.orb.BlobAvatar
import com.gravol.hermesclone.ui.orb.recipeFor
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date

// ─────────────────────────────────────────────────────────────
// Projects — official data source is WS `projects.tree`, NOT `projects.list`.
// `projects.list` only answers USER-CREATED projects (projects.db), which is
// empty on a stock install → that is why this screen used to render blank.
// `projects.tree` is the grouping the official app (and desktop sidebar) use:
// one node per workspace (auto repo/project) plus the `__no_project__` "Home"
// bucket, each carrying sessionCount / lastActive / previewSessions.
// ─────────────────────────────────────────────────────────────

/** Sampled from the official Projects screen (card #12123A on #000022). */
private val ProjectCard = Color(0xFF12123A)
private val ProjectMuted = Color(0xFF6E6C7A)
private val ProjectTitle = Color(0xFFF4F1EB)
private val ProjectPreview = Color(0xFFB9B7C4)
private val ProjectDivider = Color(0xFF1A2450)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubProjectsScreen(
    onBack: () -> Unit,
    onOpenSession: (id: String, title: String) -> Unit,
    onOpenProject: (id: String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val rpc = remember { AppModule.provideRpcClient() }
    var projects by remember { mutableStateOf<List<JsonObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        try {
            projects = fetchProjectTree(rpc, previewLimit = 3)
            error = null
        } catch (e: Exception) {
            error = e.message ?: e.toString()
            if (projects == null) projects = emptyList()
        }
    }

    Scaffold(
        containerColor = Color(0xFF000022),
        topBar = {
            ProjectsTopBar(
                title = "Projects",
                onBack = onBack,
                actionIcon = Icons.Filled.Add,
                actionDesc = "New Project",
                onAction = { showNew = true },
            )
        },
    ) { pad ->
        val list = projects
        when {
            list == null -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ProjectTitle, strokeWidth = 2.dp)
            }

            error != null && list.isEmpty() -> Box(
                Modifier.padding(pad).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not load projects", color = ProjectTitle, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(error.orEmpty(), color = ProjectMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { reloadTick++ }) { Text("Retry") }
                }
            }

            list.isEmpty() -> Box(
                Modifier.padding(pad).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ViewInAr, null, tint = ProjectMuted, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No conversations yet", color = ProjectTitle, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Started conversations group here by their workspace.",
                        color = ProjectMuted, fontSize = 13.sp,
                    )
                }
            }

            else -> LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
            ) {
                items(list, key = { it.str("id") ?: it.str("label") ?: "?" }) { p ->
                    ProjectTreeRow(p) { onOpenProject(p.str("id").orEmpty()) }
                }
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
            containerColor = Color(0xFF12123A),
            title = { Text("New Project", color = ProjectTitle) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(desc, { desc = it }, label = { Text("What is it for?") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(path, { path = it }, label = { Text("Remote workspace") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                // Gateway contract (projects.create): name/description/primary_path/folders —
                                // the old `cwd` key was ignored, which created folder-less projects.
                                val folders = buildJsonArray { if (path.isNotBlank()) add(JsonPrimitive(path.trim())) }
                                AppModule.provideRpcClient().requestRaw(
                                    "projects.create",
                                    mapOf(
                                        "name" to name.trim(),
                                        "description" to desc,
                                        "primary_path" to path.trim().ifBlank { null },
                                        "folders" to folders,
                                    ),
                                )
                                showNew = false
                                reloadTick++
                            } catch (e: Exception) {
                                error = e.message
                                showNew = false
                            }
                            busy = false
                        }
                    },
                ) { Text(if (busy) "Creating…" else "Create Project") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } },
        )
    }
}

/**
 * `projects.tree` → project nodes, ordered the way the official screen orders them:
 * conversation count descending. (The gateway's array order is grouping order — Home, then
 * session-derived workspaces, then discovered ones — and it does NOT match the official list:
 * verified on-device, the same gateway returned [tmp 11, hermes-bridge 35, …] to the clone while
 * the official app showed [hermes-bridge 35, tmp 11, …] = count descending. Ties keep the
 * gateway's order.) Throws when the RPC itself fails.
 */
private suspend fun fetchProjectTree(
    rpc: com.gravol.hermesclone.core.network.JsonRpcClient,
    previewLimit: Int,
): List<JsonObject> {
    val r = rpc.requestRaw("projects.tree", mapOf("preview_limit" to JsonPrimitive(previewLimit)))
    val arr: JsonArray = when (r) {
        is JsonArray -> r
        is JsonObject -> r.arr("projects")
        else -> JsonArray(emptyList())
    }
    return arr.mapNotNull { it as? JsonObject }
        .sortedByDescending { it.num("sessionCount") ?: 0.0 }
}

/** Official row: glyph · name / "Active …" · conversation count · chevron. */
@Composable
private fun ProjectTreeRow(p: JsonObject, onClick: () -> Unit) {
    val isHome = p.bool("isNoProject") == true
    val label = p.str("label") ?: p.str("id") ?: "?"
    val count = p.num("sessionCount")?.toInt() ?: 0
    val lastActive = p.num("lastActive")?.takeIf { it > 0.0 }
    val sub = when {
        lastActive != null -> "Active " + relativeTime(lastActive)
        isHome -> "Conversations without a workspace"
        else -> "No conversations yet"
    }
    Surface(
        color = ProjectCard,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(start = 20.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isHome) Icons.Outlined.Home else Icons.Outlined.ViewInAr,
                null, tint = ProjectMuted, modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label, color = ProjectTitle, fontSize = 16.sp, lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    sub, color = ProjectMuted, fontSize = 13.sp, lineHeight = 17.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(count.toString(), color = ProjectMuted, fontSize = 13.sp, lineHeight = 17.sp)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = ProjectMuted, modifier = Modifier.size(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectsTopBar(
    title: String,
    onBack: () -> Unit,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    actionDesc: String? = null,
    onAction: (() -> Unit)? = null,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF000022)),
        title = {
            Text(
                title, color = ProjectTitle, fontSize = 19.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp),
            )
        },
        navigationIcon = {
            Box(Modifier.padding(start = 16.dp).size(48.dp).clip(CircleShape)
                .background(Color(0xFF0C1A3C)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ProjectTitle, modifier = Modifier.size(22.dp))
            }
        },
        actions = {
            if (actionIcon != null && onAction != null) {
                Box(Modifier.padding(end = 16.dp).size(48.dp).clip(CircleShape)
                    .background(Color(0xFF0C1A3C)).clickable(onClick = onAction), contentAlignment = Alignment.Center) {
                    Icon(actionIcon, actionDesc, tint = ProjectTitle, modifier = Modifier.size(22.dp))
                }
            }
        },
    )
}

/**
 * One project's screen — a real nav destination (route `hub-project/{id}`) so that opening a
 * conversation from here and pressing Back returns HERE, exactly like the official app
 * (verified on-device: official chat → back lands on the project detail, not the list).
 */
@Composable
fun HubProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onOpenSession: (id: String, title: String) -> Unit,
) {
    val rpc = remember { AppModule.provideRpcClient() }
    var node by remember(projectId) { mutableStateOf<JsonObject?>(null) }      // hydrated drill-in
    var listNode by remember(projectId) { mutableStateOf<JsonObject?>(null) }  // tree row (label/path)
    var loaded by remember(projectId) { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        node = try {
            val r = rpc.requestRaw(
                "projects.project_sessions",
                mapOf("project_id" to projectId, "session_limit" to JsonPrimitive(2000)),
            )
            (r as? JsonObject)?.get("project") as? JsonObject
        } catch (e: Exception) {
            null
        }
        // A workspace the drill-in does not hydrate (the gateway skips its zero-session
        // discovery tier) still has a list row: the official app keeps showing that row's
        // name/path with an empty conversation list, so fetch the row for label + path only.
        if (node == null) {
            listNode = try {
                fetchProjectTree(rpc, previewLimit = 0).firstOrNull { it.str("id") == projectId }
            } catch (e: Exception) {
                null
            }
        }
        loaded = true
    }

    val data = node ?: listNode ?: JsonObject(emptyMap())
    val label = data.str("label") ?: if (projectId == "__no_project__") "Home" else "Project"
    val path = data.str("path")
    // Only hydrated lanes count as conversations here (matches the official empty state).
    val sessions = remember(node) { node?.let { projectSessionRows(it) } ?: emptyList() }

    Scaffold(
        containerColor = Color(0xFF000022),
        topBar = { ProjectsTopBar(title = label, onBack = onBack) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            if (!path.isNullOrBlank()) {
                item("path") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.ViewInAr, null, tint = ProjectMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(
                            path, color = ProjectMuted, fontSize = 14.sp, lineHeight = 18.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (!loaded) {
                item("loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ProjectTitle, strokeWidth = 2.dp)
                    }
                }
            } else if (sessions.isEmpty()) {
                item("empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 44.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Outlined.ViewInAr, null, tint = ProjectMuted, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No conversations yet", color = ProjectTitle, fontSize = 16.sp, lineHeight = 20.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Conversations started in this project's folder will appear here.",
                            color = ProjectMuted, fontSize = 14.sp, lineHeight = 18.sp,
                        )
                    }
                }
            } else {
                item("header") {
                    Text(
                        "Conversations", color = ProjectMuted, fontSize = 13.sp, lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                items(sessions, key = { it.str("id") ?: "?" }) { s ->
                    ProjectSessionRow(s, onOpenSession)
                }
            }
        }
    }
}

/** Flatten a hydrated project node (repo → lane → sessions), newest first. */
private fun projectSessionRows(node: JsonObject): List<JsonObject> {
    val out = LinkedHashMap<String, JsonObject>()
    for (repo in node.arr("repos")) {
        for (group in ((repo as? JsonObject)?.arr("groups") ?: JsonArray(emptyList()))) {
            for (s in ((group as? JsonObject)?.arr("sessions") ?: JsonArray(emptyList()))) {
                val row = s as? JsonObject ?: continue
                val id = row.str("id") ?: continue
                out.putIfAbsent(id, row)
            }
        }
    }
    if (out.isEmpty()) {
        for (s in node.arr("previewSessions")) {
            val row = s as? JsonObject ?: continue
            val id = row.str("id") ?: continue
            out.putIfAbsent(id, row)
        }
    }
    return out.values.sortedByDescending { it.num("last_active") ?: it.num("started_at") ?: 0.0 }
}

@Composable
private fun ProjectSessionRow(s: JsonObject, onOpen: (String, String) -> Unit) {
    val id = s.str("id") ?: return
    val title = s.str("title")?.takeIf { it.isNotBlank() } ?: "Untitled"
    val preview = s.str("preview")?.trim()?.takeIf { it.isNotEmpty() }
    val profile = s.str("profile").orEmpty()
    val bot = if (profile.isBlank() || profile == "default") "Hermes" else profile
    val msgs = s.num("message_count")?.toInt() ?: 0
    val whenTs = s.num("last_active") ?: s.num("started_at")
    Column(
        Modifier.fillMaxWidth()
            .clickable { onOpen(id, title) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            title, color = ProjectTitle, fontSize = 16.sp, lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (preview != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                preview, color = ProjectPreview, fontSize = 14.sp, lineHeight = 18.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlobAvatar(recipe = recipeFor(bot.lowercase()), size = 24.dp)
            Spacer(Modifier.width(7.dp))
            Text(bot, color = ProjectTitle, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "$msgs " + (if (msgs == 1) "message" else "messages") +
                    (whenTs?.let { " · " + relativeTime(it) } ?: ""),
                color = ProjectMuted, fontSize = 12.sp, maxLines = 1,
            )
        }
    }
    HorizontalDivider(color = ProjectDivider)
}

/**
 * Official wording = ICU RelativeDateTimeFormatter (`a27.f` in the official dex): "4 months ago",
 * "1 minute ago", "now" under a minute. DateUtils.getRelativeTimeSpanString falls back to an
 * absolute DATE past ~a week ("Jun 17, 2026"), which the official app never shows here — so the
 * thresholds below mirror the official ladder: seconds→now, minute, hour, day, week, month, year.
 */
private fun relativeTime(epochSeconds: Double): String {
    val now = System.currentTimeMillis()
    var seconds = (now - (epochSeconds * 1000.0).toLong()) / 1000.0
    if (seconds < 0) seconds = 0.0
    if (seconds < 60) return "now"
    val value: Int
    val unit: String
    when {
        seconds < 3_600 -> { value = (seconds / 60).toInt(); unit = "minute" }
        seconds < 86_400 -> { value = (seconds / 3_600).toInt(); unit = "hour" }
        seconds < 604_800 -> { value = (seconds / 86_400).toInt(); unit = "day" }
        seconds < 2_592_000 -> { value = (seconds / 604_800).toInt(); unit = "week" }
        seconds < 31_536_000 -> { value = (seconds / 2_592_000).toInt(); unit = "month" }
        else -> { value = (seconds / 31_536_000).toInt(); unit = "year" }
    }
    return "$value $unit" + if (value == 1) " ago" else "s ago"
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
