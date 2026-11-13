package com.gravol.hermesclone.feature.hub

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme as M3Theme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gravol.hermesclone.BuildConfig
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.NetworkResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private const val KB = "/api/plugins/kanban"

/** Official lane spec (decompiled id8.java): key, display label, description. */
private data class KbLane(val key: String, val label: String, val desc: String)
private val KB_LANES = listOf(
    KbLane("triage", "Triage", "Ideas waiting for a clear specification"),
    KbLane("todo", "Todo", "Planned work waiting on dependencies or ownership"),
    KbLane("scheduled", "Scheduled", "Work held until a known time"),
    KbLane("ready", "Ready", "Prepared work ready for the dispatcher"),
    KbLane("running", "In Progress", "Claimed by a profile and actively running"),
    KbLane("blocked", "Blocked", "Work that needs attention before it can continue"),
    KbLane("review", "Review", "Finished work waiting for a decision"),
    KbLane("done", "Done", "Completed work kept for reference"),
    KbLane("archived", "Archived", "Work removed from the active board"),
)
private fun kbLane(key: String) = KB_LANES.firstOrNull { it.key == key } ?: KbLane(key, key.replaceFirstChar { it.uppercase() }, "")

/** Stage picker in New Task = official id8.q list. */
private val KB_STAGES = listOf("triage", "todo", "ready", "blocked", "done")

private data class KbBoard(val slug: String, val name: String, val description: String, val total: Int, val isCurrent: Boolean)

private data class KbTask(
    val id: String,
    val title: String,
    val body: String,
    val assignee: String,
    val status: String,
    val priority: Int,
    val tenant: String,
    val commentCount: Int,
    val latestSummary: String,
    val skills: List<String>,
    val goalMode: Boolean,
    val workspaceKind: String,
    val projectLabel: String,
)

private data class KbBoardData(
    val columns: Map<String, List<KbTask>>,
    val tenants: List<String>,
    val assignees: List<String>,
)

private fun kbStr(o: JsonObject, k: String) = o[k]?.jsonPrimitive?.contentOrNull ?: ""

private fun kotlinx.serialization.json.JsonElement?.asObj(): JsonObject? = this as? JsonObject
private fun kotlinx.serialization.json.JsonElement?.asArr(): kotlinx.serialization.json.JsonArray? = this as? kotlinx.serialization.json.JsonArray

private fun kbTask(t: JsonObject): KbTask {
    val skills = t["skills"].asArr()?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
    return KbTask(
        id = kbStr(t, "id"),
        title = kbStr(t, "title"),
        body = kbStr(t, "body"),
        assignee = kbStr(t, "assignee"),
        status = kbStr(t, "status"),
        priority = t["priority"]?.jsonPrimitive?.intOrNull ?: 0,
        tenant = kbStr(t, "tenant"),
        commentCount = t["comment_count"]?.jsonPrimitive?.intOrNull ?: 0,
        latestSummary = kbStr(t, "latest_summary"),
        skills = skills,
        goalMode = t["goal_mode"]?.jsonPrimitive?.booleanOrNull == true,
        workspaceKind = kbStr(t, "workspace_kind"),
        projectLabel = kbStr(t, "project_id"),
    )
}

/** Profile display name: default profile shows as "Hermes" (official convention). */
private fun kbProfileLabel(name: String) = if (name == "default") "Hermes" else name

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubKanbanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var boards by remember { mutableStateOf<List<KbBoard>?>(null) }
    var board by remember { mutableStateOf<String?>(null) }          // slug
    var data by remember { mutableStateOf<KbBoardData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var lane by remember { mutableStateOf("triage") }
    var search by remember { mutableStateOf("") }

    // Filters (official popup): tenant, assignee ("Profile"), show archived, group by profile.
    var fTenant by remember { mutableStateOf<String?>(null) }
    var fProfile by remember { mutableStateOf<String?>(null) }
    var fArchived by remember { mutableStateOf(false) }
    var fGroup by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showBoardActions by remember { mutableStateOf(false) }
    var showBoards by remember { mutableStateOf(false) }
    var kanbanVersion by remember { mutableStateOf("") }
    var gatewayVersion by remember { mutableStateOf("") }

    // Sheets
    var newTask by remember { mutableStateOf(false) }
    var newBoard by remember { mutableStateOf(false) }
    var orchestration by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<KbTask?>(null) }
    var archiveConfirm by remember { mutableStateOf(false) }

    // Select Tasks mode (official: bottom bar count · Move · Archive · Delete · Done)
    var selectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showMoveSheet by remember { mutableStateOf(false) }

    // Hardware back closes custom overlays before popping the screen.
    BackHandler(enabled = showFilter || showBoardActions || selectMode) {
        when {
            showFilter -> showFilter = false
            showBoardActions -> showBoardActions = false
            selectMode -> { selectMode = false; selectedIds = emptySet() }
        }
    }

    val anyFilter = fTenant != null || fProfile != null || fArchived
    fun boardQ() = board?.let { "?board=$it" } ?: ""
    fun boardAmp() = if (board != null) "&board=$board" else ""

    fun reloadBoards(selectCurrent: Boolean = false) {
        scope.launch {
            when (val r = DashboardApiClient.apiGet("$KB/boards")) {
                is NetworkResult.Success -> {
                    val o = r.data.jsonObject
                    val list = o["boards"].asArr()?.mapNotNull { it.asObj() }.orEmpty().map { b ->
                        KbBoard(
                            slug = kbStr(b, "slug"), name = kbStr(b, "name"),
                            description = kbStr(b, "description"),
                            total = b["total"]?.jsonPrimitive?.intOrNull ?: 0,
                            isCurrent = b["is_current"]?.jsonPrimitive?.booleanOrNull == true,
                        )
                    }
                    boards = list
                    if (board == null || selectCurrent) {
                        board = o["current"]?.jsonPrimitive?.contentOrNull
                            ?: list.firstOrNull { it.isCurrent }?.slug ?: list.firstOrNull()?.slug
                    }
                }
                is NetworkResult.HttpError -> error = "Server error (${r.code})"
                is NetworkResult.Error -> error = r.exception.message
            }
        }
    }

    var loadSeq by remember { mutableIntStateOf(0) }
    fun load() {
        val seq = ++loadSeq
        scope.launch {
            error = null
            val q = "board=" + (board ?: "") +
                (fTenant?.let { "&tenant=$it" } ?: "") +
                (if (fArchived) "&include_archived=true" else "")
            when (val r = DashboardApiClient.apiGet("$KB/board?$q")) {
                is NetworkResult.Success -> if (seq != loadSeq) {
                    // Stale response (a newer load is in flight) — drop it.
                } else {
                    val o = r.data.jsonObject
                    val cols = o["columns"].asArr()?.mapNotNull { it.asObj() }?.associate { c ->
                        kbStr(c, "name") to (c["tasks"].asArr() ?: emptyList()).mapNotNull { it.asObj() }.map { kbTask(it) }
                    }.orEmpty()
                    com.gravol.hermesclone.core.network.DebugLog.log(
                        "KANBAN", "board", "cols=" + cols.entries.joinToString(",") { "${it.key}:${it.value.size}" },
                    )
                    data = KbBoardData(
                        columns = cols,
                        tenants = o["tenants"].asArr()?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                        assignees = o["assignees"].asArr()?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                    )
                }
                is NetworkResult.HttpError -> if (seq == loadSeq) error = "Server error (${r.code})"
                is NetworkResult.Error -> if (seq == loadSeq) error = r.exception.message
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadBoards(selectCurrent = true)
        when (val r = DashboardApiClient.apiGet("/api/dashboard/plugins")) {
            is NetworkResult.Success -> {
                val arr = (r.data as? kotlinx.serialization.json.JsonArray)
                    ?: r.data.asObj()?.get("plugins").asArr()
                arr?.firstNotNullOfOrNull { e ->
                    val o = e.asObj() ?: return@firstNotNullOfOrNull null
                    if (kbStr(o, "name") == "kanban") kbStr(o, "version").ifBlank { null } else null
                }?.let { kanbanVersion = it }
            }
            else -> {}
        }
        when (val r = DashboardApiClient.apiGet("/api/status")) {
            is NetworkResult.Success -> gatewayVersion = r.data.asObj()?.let { kbStr(it, "version") } ?: ""
            else -> {}
        }
    }
    LaunchedEffect(board, fTenant, fArchived) { load() }

    val allTasks = data?.columns?.values?.flatten().orEmpty()
    val boardEmpty = data != null && allTasks.isEmpty() && !anyFilter
    val laneTasks = remember(data, lane, search, fProfile, fGroup) {
        (data?.columns?.get(lane) ?: emptyList()).let { ts ->
            val byProfile = if (fProfile != null) ts.filter { it.assignee == fProfile } else ts
            if (search.isBlank()) byProfile
            else byProfile.filter {
                val q = search.lowercase()
                it.title.lowercase().contains(q) || it.body.lowercase().contains(q) ||
                    it.latestSummary.lowercase().contains(q) || it.id.lowercase().contains(q)
            }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(WindowInsets.systemBars.asPaddingValues()),
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Top bar (official q4i): circle Back · centered BoardName ▾ · pill[Filter ⋯] ──
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Box(
                    Modifier.size(56.dp).align(Alignment.CenterStart)
                        .clip(CircleShape).background(Color(0xFF0C1A3C))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                        tint = Color(0xFFF4F1EB))
                }
                val cur = boards?.firstOrNull { it.slug == board }
                Row(
                    Modifier.align(Alignment.Center)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { showBoards = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        cur?.name ?: "Kanban",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFE8EAF6),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 300.dp),
                    )
                    Text("  ⌄", color = Color(0xFF8A8AA6))
                }
                Row(
                    Modifier.align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(28.dp)).background(Color(0xFF0C1A3C)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showFilter = true }) {
                        Icon(
                            if (anyFilter) Icons.Outlined.FilterAlt else Icons.Outlined.FilterList,
                            contentDescription = if (anyFilter) "Filter tasks, filters applied" else "Filter tasks",
                            tint = Color(0xFFF4F1EB),
                        )
                    }
                    IconButton(onClick = { showBoardActions = true }) {
                        Icon(Icons.Filled.MoreHoriz, contentDescription = "Board actions", tint = Color(0xFFF4F1EB))
                    }
                }
            }

            // ── Search field (official #1D1D39 + subtle border) ──
            Surface(
                color = Color(0xFF1D1D39),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, contentDescription = null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = search, onValueChange = { search = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (search.isEmpty()) Text("Search tasks", color = Color(0xFF9FA8DA),
                                    style = MaterialTheme.typography.bodyLarge)
                                inner()
                            }
                        },
                    )
                }
            }

            // ── Active filter chips ──
            if (anyFilter) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    @Composable
                    fun chip(label: String, clear: () -> Unit) {
                        Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF1F3B7D),
                    ) {
                        Row(
                            Modifier.clip(RoundedCornerShape(18.dp)).clickable { clear() }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.Close, contentDescription = null, Modifier.size(14.dp))
                        }
                    }
                    }
                    fTenant?.let { chip(it) { fTenant = null } }
                    fProfile?.let { chip(kbProfileLabel(it)) { fProfile = null } }
                    if (fArchived) chip("Archived") { fArchived = false }
                }
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    error != null -> Text(error!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                    data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    boardEmpty -> {
                        // Official empty-board state: "A Clear Board"
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome, contentDescription = null,
                                Modifier.size(44.dp), tint = Color(0xFFB8C4E8),
                            )
                            Spacer(Modifier.height(20.dp))
                            Text("A Clear Board", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFE8EAF6))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Capture the next piece of work, then let Hermes help move it forward.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF9FA8DA),
                            )
                            Spacer(Modifier.height(28.dp))
                            Surface(
                                shape = RoundedCornerShape(28.dp), color = Color(0xFF1F2B5C),
                                modifier = Modifier.clickable { newTask = true },
                            ) {
                                Row(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add First Task", style = MaterialTheme.typography.titleSmall, color = Color(0xFFE8EAF6))
                                }
                            }
                        }
                    }
                    else -> {
                        val cols = data!!.columns
                        if (!cols.containsKey(lane)) {
                            LaunchedEffect(cols.keys) { lane = KB_LANES.map { it.key }.firstOrNull { cols.containsKey(it) } ?: "triage" }
                        }
                        val laneOrder = KB_LANES.map { it.key }.filter { cols.containsKey(it) }
                        Column(Modifier.fillMaxSize()) {
                            // ── Lane chip strip (official: active = blue fill #051B59 + #447CFF border) ──
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                laneOrder.forEach { key ->
                                    val l = kbLane(key)
                                    val n = cols[key]?.size ?: 0
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = if (lane == key) Color(0xFF051B59) else Color(0xFF151533),
                                        border = if (lane == key) BorderStroke(2.dp, Color(0xFF447CFF))
                                        else BorderStroke(1.dp, Color(0x2EFFFFFF)),
                                        modifier = Modifier.clickable { lane = key },
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(l.label, style = MaterialTheme.typography.titleSmall,
                                                color = Color(0xFFF4F1EB))
                                            Spacer(Modifier.width(10.dp))
                                            Text("$n", style = MaterialTheme.typography.titleSmall,
                                                color = Color(0xFF9FA8DA))
                                        }
                                    }
                                }
                            }
                            // ── Lane card (official: bordered card wraps header + description + lane content) ──
                            val l = kbLane(lane)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x0AFFFFFF)),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, Color(0x24FFFFFF)),
                                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp),
                            ) {
                                Column(Modifier.fillMaxSize()) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        KbDashedCircle(Modifier.size(24.dp), Color(0xFF676571))
                                        Spacer(Modifier.width(14.dp))
                                        Text(l.label, style = MaterialTheme.typography.headlineSmall,
                                            color = Color(0xFFE8EAF6),
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${laneTasks.size}", style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF9FA8DA))
                                        Spacer(Modifier.width(10.dp))
                                        Box(
                                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                                                .background(Color(0x14FFFFFF))
                                                .border(BorderStroke(1.dp, Color(0x2EFFFFFF)), RoundedCornerShape(16.dp))
                                                .clickable { newTask = true },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = "Add task to ${l.label}",
                                                tint = Color(0xFFF4F1EB))
                                        }
                                    }
                                    Text(
                                        l.desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9FA8DA),
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                    )
                                    LazyColumn(
                                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 120.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        items(laneTasks, key = { it.id }) { t ->
                                            KbTaskCard(t, selected = t.id in selectedIds) {
                                                if (selectMode) {
                                                    if (t.id in selectedIds) selectedIds = selectedIds - t.id
                                                    else selectedIds = selectedIds + t.id
                                                } else detail = t
                                            }
                                        }
                                        if (laneTasks.isEmpty()) {
                                            // Official ny2 case 13: 140dp card, 20dp radius, dashed icon 22dp @0.6 alpha
                                            item {
                                                Row(
                                                    Modifier.fillMaxWidth().height(140.dp)
                                                        .clip(RoundedCornerShape(20.dp))
                                                        .background(Color(0x0AFFFFFF))
                                                        .padding(horizontal = 24.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    KbDashedCircle(Modifier.size(22.dp),
                                                        Color(0xFFB8C4E8).copy(alpha = 0.6f))
                                                    Spacer(Modifier.width(10.dp))
                                                    Text("No tasks", color = Color(0xFFB8C4E8),
                                                        style = MaterialTheme.typography.bodyLarge)
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

        // ── FAB ──
        if (data != null && !selectMode) {
            FloatingActionButton(
                onClick = { newTask = true },
                shape = CircleShape,
                containerColor = Color(0xFF1D2761),
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New Task", tint = Color(0xFFE8EAF6))
            }
        }

        // ── Select-mode bottom bar (official: count pill · Move · Archive · Delete · Done) ──
        if (selectMode) {
            Surface(
                color = Color(0xFF0C1A3C),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 16.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(0x1FFFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${selectedIds.size}", style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFF4F1EB))
                    }
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        onClick = { if (selectedIds.isNotEmpty()) showMoveSheet = true },
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Move")
                    }
                    TextButton(
                        onClick = {
                            if (selectedIds.isEmpty()) return@TextButton
                            scope.launch {
                                val body = org.json.JSONObject()
                                    .put("ids", org.json.JSONArray(selectedIds.toList()))
                                    .put("archive", true).toString()
                                DashboardApiClient.apiSend("$KB/tasks/bulk${boardQ()}", "POST", body)
                                selectedIds = emptySet()
                                load(); reloadBoards()
                            }
                        },
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Inventory2, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Archive")
                    }
                    IconButton(
                        onClick = {
                            if (selectedIds.isEmpty()) return@IconButton
                            scope.launch {
                                selectedIds.forEach { id ->
                                    DashboardApiClient.apiSend("$KB/tasks/$id", "DELETE")
                                }
                                selectedIds = emptySet()
                                load(); reloadBoards()
                            }
                        },
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete selected",
                            tint = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { selectMode = false; selectedIds = emptySet() }) {
                        Text("Done", color = Color(0xFF447CFF), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        // ── Move-to-lane sheet (Select Tasks ▸ Move) ──
        if (showMoveSheet) {
            ModalBottomSheet(onDismissRequest = { showMoveSheet = false }, containerColor = Color(0xFF0C1A3C)) {
                Column(Modifier.padding(bottom = 24.dp)) {
                    Text("Move to", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8EAF6),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    KB_LANES.filter { it.key != "archived" }.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showMoveSheet = false
                                    scope.launch {
                                        val body = org.json.JSONObject()
                                            .put("ids", org.json.JSONArray(selectedIds.toList()))
                                            .put("status", l.key).toString()
                                        DashboardApiClient.apiSend("$KB/tasks/bulk${boardQ()}", "POST", body)
                                        selectedIds = emptySet()
                                        lane = l.key
                                        load(); reloadBoards()
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KbDashedCircle(Modifier.size(20.dp), Color(0xFF676571))
                            Spacer(Modifier.width(14.dp))
                            Text(l.label, style = MaterialTheme.typography.titleSmall, color = Color(0xFFE8EAF6))
                        }
                    }
                }
            }
        }

        // ── Filter popup ──
        if (showFilter) {
            KbFilterPopup(
                onDismiss = { showFilter = false },
                tenants = data?.tenants.orEmpty(),
                assignees = data?.assignees.orEmpty(),
                fTenant = fTenant, fProfile = fProfile, fArchived = fArchived, fGroup = fGroup,
                onTenant = { fTenant = it; load() },
                onProfile = { fProfile = it },
                onArchived = { fArchived = it },
                onGroup = { fGroup = it },
                onReset = { fTenant = null; fProfile = null; fArchived = false; fGroup = false; load() },
            )
        }

        // ── Board actions menu ──
        if (showBoardActions) {
            Box(Modifier.fillMaxSize()) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { showBoardActions = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 130.dp, end = 8.dp)
                        .widthIn(min = 300.dp).background(Color(0xFF0C1A3C)),
                ) {
                    @Composable fun sep() = HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0xFF29294C))
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = { showBoardActions = false; reloadBoards(); load() },
                    )
                    DropdownMenuItem(
                        text = { Text("Orchestration") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        onClick = { showBoardActions = false; orchestration = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Select Tasks") },
                        leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                        onClick = { showBoardActions = false; selectMode = true; selectedIds = emptySet() },
                    )
                    DropdownMenuItem(
                        text = { Text("Nudge Dispatcher") },
                        leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null) },
                        onClick = {
                            showBoardActions = false
                            scope.launch {
                                when (val r = DashboardApiClient.apiSend("$KB/dispatch${boardQ()}", "POST")) {
                                    is NetworkResult.Success -> Toast.makeText(context, "Dispatcher nudged", Toast.LENGTH_SHORT).show()
                                    is NetworkResult.HttpError -> Toast.makeText(context, "Dispatch failed (${r.code})", Toast.LENGTH_SHORT).show()
                                    is NetworkResult.Error -> Toast.makeText(context, "Dispatch failed", Toast.LENGTH_SHORT).show()
                                }
                                load()
                            }
                        },
                    )
                    sep()
                    DropdownMenuItem(
                        text = { Text("Archive Board", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Inventory2, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error) },
                        onClick = { showBoardActions = false; archiveConfirm = true },
                    )
                    sep()
                    Text("Compatibility", style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF9FA8DA),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    DropdownMenuItem(
                        enabled = false,
                        text = { Text("Kanban ${kanbanVersion.ifBlank { "…" }}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingIcon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = {},
                    )
                    DropdownMenuItem(
                        enabled = false,
                        text = { Text("Hermes ${gatewayVersion.ifBlank { "…" }}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = {},
                    )
                }
            }
        }

        // ── Archive board confirm (official: "Archive <Board>?", navy surface) ──
        if (archiveConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { archiveConfirm = false },
                containerColor = Color(0xFF141438),
                title = { Text("Archive ${boards?.firstOrNull { it.slug == board }?.name ?: "Board"}?",
                    color = Color(0xFFF4F1EB)) },
                text = { Text("Tasks stay in Hermes, but this board disappears from the active board list.",
                    color = Color(0xFFB8C4E8)) },
                confirmButton = {
                    TextButton(onClick = {
                        archiveConfirm = false
                        scope.launch {
                            val slug = board ?: return@launch
                            when (val r = DashboardApiClient.apiSend("$KB/boards/$slug", "DELETE")) {
                                is NetworkResult.Success -> {
                                    val next = r.data.asObj()?.get("current")?.jsonPrimitive?.contentOrNull
                                    board = next
                                    reloadBoards(selectCurrent = true)
                                    load()
                                }
                                else -> Toast.makeText(context, "Could not archive board", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("Archive Board", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall) }
                },
                dismissButton = { TextButton(onClick = { archiveConfirm = false }) {
                    Text("Cancel", color = Color(0xFF447CFF)) } },
            )
        }

        // ── Boards sheet ──
        if (showBoards) {
            ModalBottomSheet(onDismissRequest = { showBoards = false }, containerColor = Color(0xFF0C1A3C)) {
                Column(Modifier.padding(bottom = 24.dp)) {
                    Text("Boards", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8EAF6),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF14224A)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column {
                            boards?.forEachIndexed { i, b ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color(0xFF29294C))
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            board = b.slug
                                            showBoards = false
                                            scope.launch { DashboardApiClient.apiSend("$KB/boards/${b.slug}/switch", "POST") }
                                            load()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (b.slug == board) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null,
                                            Modifier.size(22.dp), tint = Color(0xFF447CFF))
                                        Spacer(Modifier.width(12.dp))
                                    } else {
                                        Spacer(Modifier.width(34.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(b.name, style = MaterialTheme.typography.titleSmall, color = Color(0xFFE8EAF6))
                                        if (b.description.isNotBlank()) {
                                            Text(b.description, style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF9FA8DA),
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Text("${b.total}", style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFF9FA8DA))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF14224A)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .clickable { showBoards = false; newBoard = true },
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color(0xFF447CFF))
                            Spacer(Modifier.width(14.dp))
                            Text("New Board", style = MaterialTheme.typography.titleSmall, color = Color(0xFFE8EAF6))
                        }
                    }
                }
            }
        }

        // ── New board sheet ──
        if (newBoard) {
            KbNewBoardSheet(onDismiss = { newBoard = false }, onCreated = { slug ->
                newBoard = false
                board = slug
                reloadBoards()
                load()
            })
        }

        // ── New task sheet ──
        if (newTask) {
            KbNewTaskSheet(
                boardSlug = board ?: "",
                profiles = data?.assignees.orEmpty(),
                tasks = allTasks,
                defaultLane = lane,
                onDismiss = { newTask = false },
                onCreated = { status ->
                    newTask = false
                    lane = status
                    load()
                    reloadBoards()
                },
            )
        }

        // ── Orchestration sheet ──
        if (orchestration) {
            KbOrchestrationSheet(profiles = data?.assignees.orEmpty(), onDismiss = { orchestration = false })
        }

        // ── Task detail sheet ──
        detail?.let { t ->
            KbTaskDetailSheet(
                task = t,
                boardSlug = board ?: "",
                profiles = data?.assignees.orEmpty(),
                onDismiss = { detail = null; load(); reloadBoards() },
            )
        }
    }
}

/** Official lane header / empty-card glyph: dashed circle outline. */
@Composable
private fun KbDashedCircle(modifier: Modifier = Modifier, color: Color) {
    val stroke = Stroke(
        width = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f), 0f),
    )
    Canvas(modifier) {
        val pad = 2f
        drawArc(
            color = color, startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(pad, pad),
            size = Size(size.width - 2 * pad, size.height - 2 * pad),
            style = stroke,
        )
    }
}

@Composable
private fun KbTaskCard(t: KbTask, selected: Boolean = false, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF1F3B7D) else Color(0xFF14224A)),
        shape = RoundedCornerShape(18.dp),
        border = if (selected) BorderStroke(2.dp, Color(0xFF447CFF)) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(t.title, style = MaterialTheme.typography.titleSmall, color = Color(0xFFE8EAF6), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (t.latestSummary.isNotBlank() || t.body.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    t.latestSummary.ifBlank { t.body },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9FA8DA),
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            if (t.assignee.isNotBlank() || t.commentCount > 0) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (t.assignee.isNotBlank()) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF1D5945)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(t.assignee.take(2).uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(t.assignee, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    if (t.commentCount > 0) {
                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "${t.commentCount} comment",
                            Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text("${t.commentCount}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KbFilterPopup(
    onDismiss: () -> Unit,
    tenants: List<String>,
    assignees: List<String>,
    fTenant: String?, fProfile: String?, fArchived: Boolean, fGroup: Boolean,
    onTenant: (String?) -> Unit,
    onProfile: (String?) -> Unit,
    onArchived: (Boolean) -> Unit,
    onGroup: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    var tenantExpanded by remember { mutableStateOf(false) }
    var profileExpanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        // scrim
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0C1A3C),
            tonalElevation = 6.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 130.dp, end = 8.dp).width(390.dp),
        ) {
            Column(Modifier.padding(vertical = 10.dp)) {
                @Composable fun row(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, trailing: @Composable () -> Unit = {}) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = Color(0xFFB8C4E8))
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        trailing()
                    }
                }
                @Composable fun checkRow(label: String, selected: Boolean, pick: () -> Unit) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { pick() }.padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", Modifier.size(18.dp))
                    }
                }
                // Tenant
                Modifier.run {}
                Surface(Modifier.clickable { tenantExpanded = !tenantExpanded }) {
                    row("Tenant · ${fTenant ?: "All"}", Icons.Filled.FolderOpen) {
                        Text(if (tenantExpanded) "⌃" else "⌄", color = Color(0xFF447CFF))
                    }
                }
                if (tenantExpanded) {
                    checkRow("All tenants", fTenant == null) { onTenant(null); tenantExpanded = false }
                    tenants.forEach { checkRow(it, fTenant == it) { onTenant(it); tenantExpanded = false } }
                }
                // Profile
                Surface(Modifier.clickable { profileExpanded = !profileExpanded }) {
                    row("Profile · ${fProfile?.let { kbProfileLabel(it) } ?: "All"}", Icons.Filled.Person) {
                        Text(if (profileExpanded) "⌃" else "⌄", color = Color(0xFF447CFF))
                    }
                }
                if (profileExpanded) {
                    checkRow("All profiles", fProfile == null) { onProfile(null); profileExpanded = false }
                    assignees.forEach { checkRow(kbProfileLabel(it), fProfile == it) { onProfile(it); profileExpanded = false } }
                }
                // Show archived
                row("Show Archived", Icons.AutoMirrored.Filled.Article) {
                    Switch(checked = fArchived, onCheckedChange = onArchived,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color(0xFF447CFF),
                            checkedThumbColor = Color.White,
                        ))
                }
                // Group by profile
                row("Group by Profile", Icons.Filled.SupervisorAccount) {
                    Switch(checked = fGroup, onCheckedChange = onGroup,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color(0xFF447CFF),
                            checkedThumbColor = Color.White,
                        ))
                }
                if (fTenant != null || fProfile != null || fArchived || fGroup) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0xFF29294C))
                    Surface(Modifier.clickable { onReset() }) {
                        row("Reset Filters", Icons.Filled.Refresh)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KbNewBoardSheet(onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF0C1A3C)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("New Board", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text("Name", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                placeholder = { Text("Board name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text("The URL-safe identifier is generated automatically.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Surface(
                shape = RoundedCornerShape(24.dp), color = Color(0xFF1F2B5C),
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .clickable(enabled = name.isNotBlank()) {
                        val n = name.trim()
                        val slug = n.lowercase().map { if (it.isLetterOrDigit()) it else '-' }
                            .joinToString("").trim('-').replace(Regex("-+"), "-")
                        scope.launch {
                            val body = buildJsonObject { put("name", n); put("slug", slug); put("switch", true) }
                            when (val r = DashboardApiClient.apiSend("$KB/boards", "POST", body.toString())) {
                                is NetworkResult.Success -> {
                                    val s = r.data.asObj()?.get("board").asObj()?.let { kbStr(it, "slug") }
                                    if (!s.isNullOrBlank()) onCreated(s) else Toast.makeText(context, "Board not created", Toast.LENGTH_SHORT).show()
                                }
                                else -> Toast.makeText(context, "Could not create board", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
            ) {
                Text("Create Board", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KbNewTaskSheet(
    boardSlug: String,
    profiles: List<String>,
    tasks: List<KbTask>,
    defaultLane: String,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var assignee by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var brief by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(if (defaultLane in KB_STAGES) defaultLane else "triage") }
    var priority by remember { mutableFloatStateOf(0f) }
    var advanced by remember { mutableStateOf(false) }
    var project by remember { mutableStateOf<String?>(null) }
    var projects by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // id to name
    var persistent by remember { mutableStateOf(false) }
    var parent by remember { mutableStateOf<KbTask?>(null) }
    var skills by remember { mutableStateOf("") }
    var goalMode by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var showParentPicker by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val r = DashboardApiClient.apiGet("/api/plugins/kanban/projects")) {
            is NetworkResult.Success -> projects = r.data.asObj()?.get("projects").asArr()
                ?.mapNotNull { it.asObj() }?.map { kbStr(it, "id") to kbStr(it, "name") }.orEmpty()
            else -> {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0C1A3C),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 0.dp)) {
            Column(
                Modifier.verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Text("New Task", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                // Assignee profile chips
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    profiles.forEach { p ->
                        val sel = assignee == p
                        Box(
                            Modifier.padding(horizontal = 8.dp)
                                .size(if (sel) 64.dp else 44.dp)
                                .clip(CircleShape)
                                .background(if (p == "default") Color(0xFF8B5CF6) else Color(0xFF1D5945))
                                .clickable { assignee = if (sel) null else p },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(kbProfileLabel(p).take(2).uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = if (sel) 16.sp else 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clickable { assignee = null }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = assignee == null, onCheckedChange = { assignee = null })
                    Column {
                        Text("Let the dispatcher choose", style = MaterialTheme.typography.titleSmall)
                        Text("Leaves the task unassigned for the dispatcher to route.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("What should happen?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    placeholder = { Text("Ship the onboarding redesign") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("A clear outcome works better than a category.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Text("Brief", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = brief, onValueChange = { brief = it },
                    placeholder = { Text("Context, constraints, and what done looks like (optional)") },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Text("Stage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                KB_STAGES.chunked(3).forEach { rowStages ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowStages.forEach { s ->
                            FilterChip(
                                selected = stage == s,
                                onClick = { stage = s },
                                label = { Text(kbLane(s).label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowStages.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(kbLane(stage).desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Text("Priority", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Slider(value = priority, onValueChange = { priority = it }, valueRange = 0f..10f, steps = 9)
                Text("Higher values rise in dispatcher ordering.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                // Advanced
                Surface(
                    shape = RoundedCornerShape(16.dp), color = Color(0xFF14224A),
                    modifier = Modifier.fillMaxWidth().clickable { advanced = !advanced },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Settings, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Advanced", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(if (advanced) "⌃" else "⌄", color = Color(0xFF447CFF))
                    }
                }
                if (advanced) {
                    Spacer(Modifier.height(16.dp))
                    Text("Project", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Surface(
                        shape = RoundedCornerShape(14.dp), color = Color(0xFF14224A),
                        modifier = Modifier.fillMaxWidth().clickable { showProjectPicker = true },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(project?.let { pid -> projects.firstOrNull { it.first == pid }?.second } ?: "Optional",
                                modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.Settings, contentDescription = null, Modifier.size(16.dp))
                        }
                    }
                    Text("An optional tenant or workstream label.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("Workspace", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(selected = !persistent, onClick = { persistent = false },
                            label = { Text("Temporary") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = persistent, onClick = { persistent = true },
                            label = { Text("Persistent") }, modifier = Modifier.weight(1f))
                    }
                    Text("A temporary workspace is cleaned up after completion.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("Parent Task", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Surface(
                        shape = RoundedCornerShape(14.dp), color = Color(0xFF14224A),
                        modifier = Modifier.fillMaxWidth().clickable { showParentPicker = true },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(parent?.title ?: "No Parent", modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text("This task waits for its parent to complete.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("Skills", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = skills, onValueChange = { skills = it },
                        placeholder = { Text("research, swiftui-specialist") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Comma-separated skill names loaded before work begins.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Goal Mode", style = MaterialTheme.typography.titleSmall)
                            Text("Continue until achieved or genuinely blocked.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = goalMode, onCheckedChange = { goalMode = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF447CFF)))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            // Pin Add Task button
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = Color(0xFF0C1A3C),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp), color = Color(0xFF1F2B5C),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp)
                        .clickable(enabled = title.isNotBlank() && !submitting) {
                            submitting = true
                            scope.launch {
                                val q = if (boardSlug.isNotBlank()) "?board=$boardSlug" else ""
                                val body = buildJsonObject {
                                    put("title", title.trim())
                                    if (brief.isNotBlank()) put("body", brief.trim())
                                    assignee?.let { put("assignee", it) }
                                    put("priority", priority.toInt())
                                    put("triage", stage == "triage")
                                    parent?.let { putJsonArray("parents") { add(kotlinx.serialization.json.JsonPrimitive(it.id)) } }
                                    if (skills.isNotBlank()) putJsonArray("skills") {
                                        skills.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                                            .forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                                    }
                                    if (goalMode) put("goal_mode", true)
                                    project?.let { put("project_id", it) }
                                }
                                when (val r = DashboardApiClient.apiSend("$KB/tasks$q", "POST", body.toString())) {
                                    is NetworkResult.Success -> {
                                        // Route to the chosen stage: create lands in triage/todo, then PATCH status.
                                        val created = r.data.jsonObject["task"]?.jsonObject
                                        val newId = created?.let { kbStr(it, "id") } ?: ""
                                        val landed = created?.let { kbStr(it, "status") } ?: ""
                                        if (newId.isNotBlank() && stage != landed) {
                                            DashboardApiClient.apiSend("$KB/tasks/$newId$q", "PATCH",
                                                buildJsonObject { put("status", stage) }.toString())
                                        }
                                        submitting = false
                                        onCreated(stage)
                                    }
                                    else -> {
                                        submitting = false
                                        Toast.makeText(context, "Could not create task", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                ) {
                    Text("Add Task", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
                }
            }
        }
    }

    if (showParentPicker) {
        ModalBottomSheet(onDismissRequest = { showParentPicker = false }, containerColor = Color(0xFF0C1A3C)) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth().clickable { parent = null; showParentPicker = false }
                    .padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("No Parent", modifier = Modifier.weight(1f))
                    if (parent == null) Icon(Icons.Filled.Check, contentDescription = "Selected")
                }
                tasks.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { parent = t; showParentPicker = false }
                        .padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (parent?.id == t.id) Icon(Icons.Filled.Check, contentDescription = "Selected")
                    }
                }
            }
        }
    }
    if (showProjectPicker) {
        ModalBottomSheet(onDismissRequest = { showProjectPicker = false }, containerColor = Color(0xFF0C1A3C)) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth().clickable { project = null; showProjectPicker = false }
                    .padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("None", modifier = Modifier.weight(1f))
                    if (project == null) Icon(Icons.Filled.Check, contentDescription = "Selected")
                }
                projects.forEach { (id, name) ->
                    Row(Modifier.fillMaxWidth().clickable { project = id; showProjectPicker = false }
                        .padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f))
                        if (project == id) Icon(Icons.Filled.Check, contentDescription = "Selected")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KbOrchestrationSheet(profiles: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var orchestrator by remember { mutableStateOf("") }
    var assignee by remember { mutableStateOf("") }
    var resolvedOrch by remember { mutableStateOf("") }
    var resolvedAssignee by remember { mutableStateOf("") }
    var autoDecompose by remember { mutableStateOf(true) }
    var autoPromote by remember { mutableStateOf(true) }
    var pickerFor by remember { mutableStateOf<String?>(null) } // "orch" | "assignee"

    LaunchedEffect(Unit) {
        when (val r = DashboardApiClient.apiGet("$KB/orchestration")) {
            is NetworkResult.Success -> {
                val o = r.data.jsonObject
                orchestrator = kbStr(o, "orchestrator_profile")
                assignee = kbStr(o, "default_assignee")
                resolvedOrch = kbStr(o, "resolved_orchestrator_profile")
                resolvedAssignee = kbStr(o, "resolved_default_assignee")
                autoDecompose = o["auto_decompose"]?.jsonPrimitive?.booleanOrNull ?: true
                autoPromote = o["auto_promote_children"]?.jsonPrimitive?.booleanOrNull ?: true
                loaded = true
            }
            else -> loaded = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = Color(0xFF0C1A3C),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text("Orchestration", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    scope.launch {
                        val body = buildJsonObject {
                            put("orchestrator_profile", orchestrator)
                            put("default_assignee", assignee)
                            put("auto_decompose", autoDecompose)
                            put("auto_promote_children", autoPromote)
                        }
                        when (DashboardApiClient.apiSend("$KB/orchestration", "PUT", body.toString())) {
                            is NetworkResult.Success -> { Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show(); onDismiss() }
                            else -> Toast.makeText(context, "Could not save", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Save", color = Color(0xFF447CFF)) }
            }
            Text("Dispatch routing", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Text("Choose who shapes ambiguous work and who receives work without an explicit owner.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            @Composable fun profileRow(label: String, hint: String, resolved: String, open: () -> Unit) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF14224A),
                    modifier = Modifier.fillMaxWidth().clickable { open() }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${if (loaded) "Active" else "…"} · ${if (hint.isNotBlank()) hint else "default"}",
                            modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(resolved, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }
            profileRow("Orchestrator", if (orchestrator.isBlank()) "default" else orchestrator, "Specs and decomposes triage work.") { pickerFor = "orch" }
            profileRow("Default Assignee", if (assignee.isBlank()) "default" else assignee, "Receives work when routing has no better match.") { pickerFor = "assignee" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-decompose", style = MaterialTheme.typography.titleSmall)
                    Text("Break suitable triage tasks into a child graph automatically.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoDecompose, onCheckedChange = { autoDecompose = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF447CFF)))
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Promote children", style = MaterialTheme.typography.titleSmall)
                    Text("Move unblocked child tasks forward when dependencies finish.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoPromote, onCheckedChange = { autoPromote = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF447CFF)))
            }
        }
    }

    pickerFor?.let { which ->
        ModalBottomSheet(onDismissRequest = { pickerFor = null }, containerColor = Color(0xFF0C1A3C)) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth().clickable {
                    if (which == "orch") orchestrator = "" else assignee = ""
                    pickerFor = null
                }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Default", modifier = Modifier.weight(1f))
                    val cur = if (which == "orch") orchestrator else assignee
                    if (cur.isBlank()) Icon(Icons.Filled.Check, contentDescription = "Selected")
                }
                profiles.forEach { p ->
                    Row(Modifier.fillMaxWidth().clickable {
                        if (which == "orch") orchestrator = p else assignee = p
                        pickerFor = null
                    }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(kbProfileLabel(p), modifier = Modifier.weight(1f))
                        val cur = if (which == "orch") orchestrator else assignee
                        if (cur == p) Icon(Icons.Filled.Check, contentDescription = "Selected")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KbTaskDetailSheet(
    task: KbTask,
    boardSlug: String,
    profiles: List<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val q = if (boardSlug.isNotBlank()) "?board=$boardSlug" else ""
    var full by remember { mutableStateOf<JsonObject?>(null) }
    var brief by remember { mutableStateOf(task.body) }
    var title by remember { mutableStateOf(task.title) }
    var edit by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }
    var moveMenu by remember { mutableStateOf(false) }
    var assignMenu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(task.status) }
    var assignee by remember { mutableStateOf(task.assignee) }

    fun reload() {
        scope.launch {
            when (val r = DashboardApiClient.apiGet("$KB/tasks/${task.id}$q")) {
                is NetworkResult.Success -> {
                    full = r.data.asObj()
                    val t = full?.get("task").asObj()
                    if (t != null) {
                        val s = kbStr(t, "latest_summary")
                        if (s.isNotBlank()) brief = s
                        val st = kbStr(t, "status"); if (st.isNotBlank()) status = st
                    }
                }
                else -> {}
            }
        }
    }
    LaunchedEffect(Unit) { reload() }

    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = Color(0xFF0C1A3C),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { edit = !edit }) { Text(if (edit) "Done" else "Edit") }
                Spacer(Modifier.weight(1f))
                Text("Task", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
            }
            Column(
                Modifier.verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF14224A),
                    modifier = Modifier.fillMaxWidth()) {
                    Text(
                        kbLane(status).label,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (edit) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (assignee.isNotBlank()) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF1D5945)), contentAlignment = Alignment.Center) {
                            Text(assignee.take(2).uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(assignee, style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Unassigned", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    TextButton(onClick = { moveMenu = true }) { Text("Move") }
                    TextButton(onClick = { assignMenu = true }) { Text("Assign") }
                    if (edit) {
                        TextButton(onClick = {
                            scope.launch {
                                val body = buildJsonObject { put("title", title.trim()); put("body", brief) }
                                when (DashboardApiClient.apiSend("$KB/tasks/${task.id}$q", "PATCH", body.toString())) {
                                    is NetworkResult.Success -> { Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show(); edit = false }
                                    else -> Toast.makeText(context, "Could not save", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) { Text("Save", color = Color(0xFF447CFF)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Brief", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                if (edit) {
                    OutlinedTextField(value = brief, onValueChange = { brief = it }, minLines = 6,
                        modifier = Modifier.fillMaxWidth())
                } else {
                    Text(
                        brief.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(20.dp))
                // Comments (from full reload)
                val comments = full?.get("comments").asArr()?.mapNotNull { it.asObj() }.orEmpty()
                if (comments.isNotEmpty()) {
                    Text("Comments", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    comments.forEach { c ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14224A)),
                            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                if (kbStr(c, "author").isNotBlank()) {
                                    Text(kbStr(c, "author"), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(kbStr(c, "body"), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            // Comment composer
            Surface(color = Color(0xFF0C1A3C), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF14224A), modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = comment, onValueChange = { comment = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    Box {
                                        if (comment.isEmpty()) Text("Add a comment", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        inner()
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    IconButton(onClick = {
                        if (comment.isNotBlank()) {
                            scope.launch {
                                val body = buildJsonObject { put("body", comment.trim()); put("author", "mobile") }
                                when (DashboardApiClient.apiSend("$KB/tasks/${task.id}/comments$q", "POST", body.toString())) {
                                    is NetworkResult.Success -> { comment = ""; reload() }
                                    else -> Toast.makeText(context, "Could not post comment", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Send, contentDescription = "Send comment")
                    }
                }
            }
        }
    }

    if (moveMenu) {
        ModalBottomSheet(onDismissRequest = { moveMenu = false }, containerColor = Color(0xFF0C1A3C)) {
            Column(Modifier.padding(bottom = 24.dp)) {
                (KB_STAGES + listOf("archived")).filter { it != status }.forEach { s ->
                    Row(Modifier.fillMaxWidth().clickable {
                        moveMenu = false
                        scope.launch {
                            when (DashboardApiClient.apiSend("$KB/tasks/${task.id}$q", "PATCH",
                                buildJsonObject { put("status", s) }.toString())) {
                                is NetworkResult.Success -> { status = s; Toast.makeText(context, "Moved to ${kbLane(s).label}", Toast.LENGTH_SHORT).show() }
                                else -> Toast.makeText(context, "Move rejected by server", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.padding(horizontal = 24.dp, vertical = 14.dp)) {
                        Text(kbLane(s).label)
                    }
                }
            }
        }
    }
    if (assignMenu) {
        ModalBottomSheet(onDismissRequest = { assignMenu = false }, containerColor = Color(0xFF0C1A3C)) {
            Column(Modifier.padding(bottom = 24.dp)) {
                @Composable fun aRow(label: String, value: String) {
                    Row(Modifier.fillMaxWidth().clickable {
                        assignMenu = false
                        scope.launch {
                            when (DashboardApiClient.apiSend("$KB/tasks/${task.id}$q", "PATCH",
                                buildJsonObject { put("assignee", value) }.toString())) {
                                is NetworkResult.Success -> { assignee = value }
                                else -> Toast.makeText(context, "Could not assign", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.weight(1f))
                        if (assignee == value) Icon(Icons.Filled.Check, contentDescription = "Selected")
                    }
                }
                aRow("Unassigned", "")
                profiles.forEach { aRow(kbProfileLabel(it), it) }
            }
        }
    }
}
