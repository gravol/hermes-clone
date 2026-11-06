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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
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
import kotlinx.coroutines.flow.StateFlow
import com.gravol.hermesclone.R
import com.gravol.hermesclone.core.network.SessionSummary
import com.gravol.hermesclone.feature.sessions.SessionsViewModel
import com.gravol.hermesclone.ui.orb.BlobAvatar
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
)

private const val PEEK = 5

@Composable
fun HomeScreen(
    onSessionTap: (SessionSummary) -> Unit = {},
    onSettings: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    activeSessions: StateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap()),
    viewModel: SessionsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bots by viewModel.bots.collectAsStateWithLifecycle()
    val activeMap by activeSessions.collectAsStateWithLifecycle()
    val workingIds by viewModel.working.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadBots() }
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshWorking()
            kotlinx.coroutines.delay(4_000)
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    var layoutMode by rememberSaveable { mutableStateOf(0) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var windowDays by rememberSaveable { mutableStateOf(3) }
    var sortByName by rememberSaveable { mutableStateOf(false) }
    val expand = remember { mutableStateMapOf<String, Int>() }   // key → visible children (-1 = all)
    var agentsCollapsed by rememberSaveable { mutableStateOf(false) }
    var seeded by rememberSaveable { mutableStateOf(false) }
    var menuSession by remember { mutableStateOf<SessionSummary?>(null) }

    val q = query.trim()
    val windowStartSec = remember(windowDays) {
        (System.currentTimeMillis() / 1000.0) - windowDays * 86_400.0
    }
    val homeVisibleLegacy = remember(state.sessions, q, windowStartSec) {
        state.sessions.filter { s ->
            val ts = s.lastActivityTs()
            (ts == null || ts * 1000.0 >= windowStartSec * 1000.0) &&
                (q.isEmpty() ||
                    (s.title ?: "").contains(q, ignoreCase = true) ||
                    (s.preview ?: "").contains(q, ignoreCase = true) ||
                    agentDisplayName(agentKeyFor(s)).contains(q, ignoreCase = true))
        }
    }
    // ── Bot model (official): groups come from the profiles roster; each bot
    // shows its own sessions. Fallback: group loaded sessions by source.
    val windowOk: (Double?) -> Boolean = { ts -> ts == null || ts * 1000.0 >= windowStartSec * 1000.0 }
    val displayGroups: List<HomeAgentGroup> = if (bots.isNotEmpty()) {
        bots.mapNotNull { b ->
            val sessions = b.sessions
                .filter { windowOk(it.last_activity_at ?: it.started_at) }
                .filter { s ->
                    q.isEmpty() ||
                        (s.title ?: "").contains(q, ignoreCase = true) ||
                        (s.preview ?: "").contains(q, ignoreCase = true) ||
                        b.displayName.contains(q, ignoreCase = true)
                }
                .map { info ->
                    SessionSummary(
                        id = info.id, title = info.title, source = b.name, model = info.model,
                        startedAt = info.started_at, lastActivityAt = info.last_activity_at,
                        lastActive = info.last_activity_at, preview = info.preview,
                        messageCount = info.message_count ?: 0,
                    )
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
            item {
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
                displayGroups.forEach { group ->
                    item(key = "agent-${group.key}") {
                        AgentRow(
                            group = group,
                            expanded = (expand[group.key] ?: 0) > 0,
                            onClick = {
                                expand[group.key] = when (expand[group.key]) {
                                    null, 0 -> PEEK
                                    -1 -> 0
                                    else -> -1
                                }
                            },
                        )
                    }
                    val shown = expand[group.key] ?: 0
                    if (shown != 0) {
                        val visibleKids = if (shown < 0) group.sessions
                            else group.sessions.take(shown)
                        itemsIndexed(visibleKids, key = { _, s -> "s-${s.id}" }) { index, session ->
                            ChildRow(
                                session = session,
                                first = index == 0,
                                last = index == visibleKids.lastIndex,
                                working = activeMap[session.id] == true || workingIds.contains(session.id),
                                onClick = { onSessionTap(session) },
                                onLongClick = { menuSession = session },
                            )
                        }
                        if (group.sessions.size > shown && shown > 0) {
                            item(key = "more-${group.key}") {
                                MoreSessionsRow(
                                    hidden = group.sessions.size - shown,
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
                PillButton(active = true, onClick = {}) {
                    Icon(Icons.Filled.Chat, "Chats",
                        tint = HermesTextPrimary, modifier = Modifier.size(21.dp))
                }
                PillButton(onClick = onOpenDevices) {
                    Icon(Icons.Filled.Devices, "Devices",
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
                        Icon(Icons.Outlined.FilterList, "Filters",
                            tint = HermesTextPrimary, modifier = Modifier.size(21.dp))
                    }
                    if (showFilters) FiltersMenu(onDismiss = { showFilters = false })
                }
                Box {
                    PillButton(onClick = { showMore = true }) {
                        Icon(Icons.Outlined.MoreVert, "More options",
                            tint = HermesTextPrimary, modifier = Modifier.size(21.dp))
                    }
                    if (showMore) HomeMoreMenu(
                        onDismiss = { showMore = false },
                        layout = layoutMode,
                        onLayout = { layoutMode = it; showMore = false },
                        sortByName = sortByName,
                        onSortByName = { sortByName = it; showMore = false },
                        windowDays = windowDays,
                        onWindowDays = { windowDays = it; showMore = false },
                        showHidden = showHidden,
                        onShowHidden = { showHidden = it },
                    )
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
private fun FiltersMenu(onDismiss: () -> Unit) {
    Popup(alignment = Alignment.TopEnd, properties = PopupProperties(focusable = true), onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(top = 96.dp, end = 14.dp)
                .width(230.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(HermesSurfaceRaised)
                .padding(vertical = 8.dp),
        ) {
            MenuRow("Show", "All", Icons.Filled.FolderOpen, onDismiss)
            MenuRow("Include", "None", Icons.Filled.Layers, onDismiss)
            MenuRow("Agents", "All", Icons.Filled.People, onDismiss)
        }
    }
}

@Composable
private fun HomeMoreMenu(
    onDismiss: () -> Unit,
    onLayout: (Int) -> Unit,     // 0 bots+sessions, 1 sessions only, 2 bots only
    layout: Int,
    onSortByName: (Boolean) -> Unit,
    sortByName: Boolean,
    onWindowDays: (Int) -> Unit,
    windowDays: Int,
    showHidden: Boolean,
    onShowHidden: (Boolean) -> Unit,
) {
    Popup(alignment = Alignment.TopEnd, properties = PopupProperties(focusable = true), onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(top = 96.dp, end = 14.dp)
                .width(272.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(HermesSurfaceRaised)
                .padding(vertical = 10.dp),
        ) {
            MenuSection("Layout")
            MenuCheck("Bots and sessions", Icons.Filled.ViewList, layout == 0) { onLayout(0) }
            MenuCheck("Sessions only", Icons.Filled.Chat, layout == 1) { onLayout(1) }
            MenuCheck("Bots only", Icons.Filled.People, layout == 2) { onLayout(2) }
            MenuSection("Sort bots")
            MenuCheck("Recent activity", Icons.Filled.AccessTime, !sortByName) { onSortByName(false) }
            MenuCheck("Name", Icons.Filled.SortByAlpha, sortByName) { onSortByName(true) }
            MenuSection("Recent sessions")
            MenuCheck("Last day", Icons.Filled.AccessTime, windowDays == 1) { onWindowDays(1) }
            MenuCheck("Last 3 days", Icons.Filled.AccessTime, windowDays == 3) { onWindowDays(3) }
            MenuCheck("Last week", Icons.Filled.AccessTime, windowDays == 7) { onWindowDays(7) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.VisibilityOff, null, tint = HermesTextPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text("Show hidden bots", color = HermesTextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = showHidden,
                    onCheckedChange = onShowHidden,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = HermesAccent,
                        uncheckedTrackColor = HermesSurfaceActive,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = HermesTextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = HermesTextPrimary, fontSize = 16.sp)
            Text(subtitle, color = HermesTextSecondary, fontSize = 12.sp)
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
        if (!working) {
            Canvas(Modifier.size(16.dp)) {
                drawCircle(HermesAccent, radius = 3.5.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
            }
        } else {
            Spacer(Modifier.width(16.dp))
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
        modifier = Modifier.fillMaxWidth().padding(top = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FurBlobPlaceholder(Modifier.size(96.dp))
        Spacer(Modifier.height(22.dp))
        Text(if (searching) "No matching agents" else "No agents yet",
            color = HermesTextPrimary, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text(if (searching) "Try another name, or words from a session."
             else "Say hi to your agent below.",
            color = HermesTextSecondary, fontSize = 15.sp)
    }
}

/** Placeholder for the character-fur mascot until the AGSL fur renderer lands. */
@Composable
private fun FurBlobPlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.22f, h * 0.55f)
            cubicTo(w * 0.10f, h * 0.20f, w * 0.35f, h * 0.05f, w * 0.55f, h * 0.10f)
            cubicTo(w * 0.85f, h * 0.12f, w * 0.95f, h * 0.45f, w * 0.82f, h * 0.72f)
            cubicTo(w * 0.72f, h * 0.95f, w * 0.30f, h * 0.95f, w * 0.22f, h * 0.55f)
            close()
        }
        drawPath(path, Color(0xFF8FC4EE))
        val eye = Color(0xFF182A4A)
        drawOval(eye, Offset(w * 0.42f, h * 0.42f), androidx.compose.ui.geometry.Size(w * 0.09f, h * 0.13f))
        drawOval(eye, Offset(w * 0.62f, h * 0.42f), androidx.compose.ui.geometry.Size(w * 0.09f, h * 0.13f))
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
private fun SessionContextMenu(session: SessionSummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HermesSurfaceRaised,
        title = { Text(session.title ?: session.id, color = HermesTextPrimary, fontSize = 17.sp) },
        text = { Text("Rename, pin and delete arrive with the official menu pass.", color = HermesTextSecondary) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = HermesAccent) } },
    )
}
