package com.gravol.hermesclone.feature.system

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.NetworkResult
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put

// ─────────────────────────────────────────────────────────────
// Cron jobs
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var jobs by remember { mutableStateOf<List<DashboardApiClient.CronJob>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // v0.1.80: editor state — null = list, else editing (or creating when id empty)
    var editorJob by remember { mutableStateOf<DashboardApiClient.CronJob?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            when (val r = DashboardApiClient.cronJobs()) {
                is NetworkResult.Success -> { jobs = r.data; error = null }
                is NetworkResult.HttpError -> error = "Server error (${r.code})"
                is NetworkResult.Error -> error = r.exception.message
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    val editorOpen = editorJob != null || showCreate

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editorOpen) (if (showCreate) "New Routine" else "Edit Routine") else "Routines") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (editorOpen) { editorJob = null; showCreate = false }
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!editorOpen) {
                        // v0.2.26l official parity: plus only, no Refresh
                        IconButton(onClick = { showCreate = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Routine")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                editorOpen -> {
                    CronEditScreen(
                        job = editorJob,
                        onDone = {
                            editorJob = null
                            showCreate = false
                            load()
                        },
                    )
                }
                error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { load() }) { Text("Retry") }
                    }
                }
                jobs == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                jobs!!.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No cron jobs — tap + to create one", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(jobs!!, key = { it.id }) { job ->
                            CronJobRow(
                                job = job,
                                onAction = { action ->
                                    scope.launch {
                                        when (val r = DashboardApiClient.cronAction(job.id, action)) {
                                            is NetworkResult.Success -> {
                                                Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
                                                load()
                                            }
                                            is NetworkResult.HttpError ->
                                                Toast.makeText(context, "Server error (${r.code})", Toast.LENGTH_SHORT).show()
                                            is NetworkResult.Error ->
                                                Toast.makeText(context, "Failed: ${r.exception.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onEdit = { editorJob = job },
                                onDelete = {
                                    scope.launch {
                                        when (val r = DashboardApiClient.cronDelete(job.id)) {
                                            is NetworkResult.Success -> {
                                                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                                load()
                                            }
                                            is NetworkResult.HttpError ->
                                                Toast.makeText(context, "Server error (${r.code})", Toast.LENGTH_SHORT).show()
                                            is NetworkResult.Error ->
                                                Toast.makeText(context, "Failed: ${r.exception.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// Official humanizes the cron expr client-side (device-verified): "Every 15 minutes",
// "Daily at 07:00", "Weekly on Mon at 13:00", "Monthly on the 1st at 15:00"; anything
// fancier stays raw ("0 7,16 * * *").
private fun humanizeCron(expr: String?): String? {
    val parts = expr?.trim()?.split(Regex("\\s+")) ?: return null
    if (parts.size != 5) return null
    val (m, h, dom, mon, dow) = parts
    val ord = mapOf("1" to "1st", "2" to "2nd", "3" to "3rd", "4" to "4th")
    val days = mapOf("0" to "Sun", "7" to "Sun", "1" to "Mon", "2" to "Tue", "3" to "Wed",
        "4" to "Thu", "5" to "Fri", "6" to "Sat")
    val at = if (h.matches(Regex("\\d+"))) "%02d:%02d".format(h.toInt(), m.toInt().coerceAtMost(59)) else null
    return when {
        m == "*/N" -> null
        m.startsWith("*/") && h == "*" && dom == "*" && mon == "*" && dow == "*" ->
            "Every ${m.removePrefix("*/")} minutes"
        m == "0" && h == "*" && dom == "*" && mon == "*" && dow == "*" -> "Hourly"
        m == "0" && at != null && dom == "*" && mon == "*" && dow == "*" -> "Daily at $at"
        m == "0" && at != null && dom == "*" && mon == "*" && dow in days ->
            "Weekly on ${days[dow]} at $at"
        m == "0" && at != null && dom in ord && mon == "*" && dow == "*" ->
            "Monthly on the ${ord[dom]} at $at"
        else -> null
    }
}

@Composable
private fun CronJobRow(
    job: DashboardApiClient.CronJob,
    onAction: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // Official Routines row (device-verified 2026-11-12): status icon + name (red when the
    // last run failed, "Script exited with code 1…" shown as its line) + description line
    // (script filename or prompt) + schedule line (schedule_display + " · next in X") + chevron.
    val failed = job.lastStatus?.lowercase()?.contains("fail") == true ||
        job.lastStatus?.lowercase()?.contains("error") == true
    val paused = job.enabled == false || !job.pausedAt.isNullOrBlank()
    val desc = job.script?.takeIf { it.isNotBlank() }
        ?: job.prompt?.takeIf { it.isNotBlank() }?.lineSequence()?.first()?.take(160)
    val sch = job.schedule
    val schedLine = when {
        sch != null && sch.kind?.contains("interval") == true && sch.minutes != null ->
            if (sch.minutes == 1) "Every minute" else "Every " + sch.minutes + " minutes"
        else -> humanizeCron(sch?.expr) ?: job.scheduleDisplay ?: sch?.expr ?: ""
    }
    val sched = buildString {
        append(schedLine)
        val next = job.nextRunAt?.let { runCatching { java.time.Instant.parse(it).epochSecond }.getOrNull() }
        if (next != null && !paused) {
            val d = next - System.currentTimeMillis() / 1000
            if (d > 0) {
                append(" · next in ")
                append(when {
                    d < 60 -> "$d seconds"
                    d < 3600 -> "${d / 60} minutes"
                    d < 86400 -> "${d / 3600} hours"
                    d < 604800 -> "${d / 86400} days"
                    else -> "${d / 604800} weeks"
                })
            }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // status icon
            when {
                failed -> Icon(Icons.Filled.Error, "Failed",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                paused -> Icon(Icons.Filled.PauseCircle, "Paused",
                    tint = androidx.compose.ui.graphics.Color(0xFFE8A33D),
                    modifier = Modifier.size(22.dp))
                else -> Icon(Icons.Filled.CheckCircle, "Scheduled",
                    tint = androidx.compose.ui.graphics.Color(0xFF2FBF71),
                    modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (failed && !job.lastError.isNullOrBlank()) job.lastError!! else job.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (desc != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(desc, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                if (sched.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(sched, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@Composable
private fun CronEditScreen(
    job: DashboardApiClient.CronJob?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isCreate = job == null

    var name by remember { mutableStateOf(job?.name ?: "") }
    var prompt by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf(job?.scheduleDisplay ?: "") }
    var deliver by remember { mutableStateOf("local") }
    var saving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var targets by remember { mutableStateOf<List<DashboardApiClient.CronDeliveryTarget>>(emptyList()) }
    var showDeliverMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val r = DashboardApiClient.cronDeliveryTargets()) {
            is NetworkResult.Success -> targets = r.data.targets
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; formError = null },
            label = { Text("Name") },
            placeholder = { Text("e.g. Morning weather check") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = schedule,
            onValueChange = { schedule = it; formError = null },
            label = { Text("Schedule") },
            placeholder = { Text("0 16 * * 1-5  or  every 90m") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text(
                    "Cron expression (min hour dom month dow) or interval shorthand.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt") },
            placeholder = { Text("What should the job do? (leave empty to reuse the name)") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )

        // Deliver target dropdown
        Box {
            OutlinedButton(
                onClick = { showDeliverMenu = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Deliver to: ${targets.firstOrNull { it.id == deliver }?.name ?: deliver}")
            }
            DropdownMenu(expanded = showDeliverMenu, onDismissRequest = { showDeliverMenu = false }) {
                targets.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.name ?: t.id) },
                        onClick = { deliver = t.id; showDeliverMenu = false },
                    )
                }
            }
        }

        formError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = {
                scope.launch {
                    saving = true
                    formError = null
                    try {
                        val sched = schedule.trim()
                        if (sched.isBlank()) {
                            formError = "Schedule is required"
                            saving = false
                            return@launch
                        }
                        val result = if (isCreate) {
                            DashboardApiClient.cronCreate(prompt, sched, name, deliver)
                        } else {
                            val updates = buildMap {
                                put("schedule", sched)
                                if (name.isNotBlank()) put("name", name)
                                put("deliver", deliver)
                                if (prompt.isNotBlank()) put("prompt", prompt)
                            }
                            DashboardApiClient.cronUpdate(job!!.id, updates)
                        }
                        when (result) {
                            is NetworkResult.Success -> {
                                Toast.makeText(context, if (isCreate) "Created" else "Saved", Toast.LENGTH_SHORT).show()
                                onDone()
                            }
                            is NetworkResult.HttpError ->
                                formError = "Server error (${result.code}) — check the schedule format"
                            is NetworkResult.Error ->
                                formError = result.exception.message ?: "Save failed"
                        }
                    } catch (e: Exception) {
                        formError = e.message ?: "Save failed"
                    }
                    saving = false
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(if (isCreate) "Create job" else "Save changes")
            }
        }

        if (!isCreate) {
            Text(
                "Run history: pause/resume/run from the list. Deleting is done from the list (with confirmation).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Skills
// ─────────────────────────────────────────────────────────────

/** Official category display: split '-' and first-cap each token; MLOps is the one special case. */
private fun humanizeCategory(c: String): String = when (c.lowercase()) {
    "mlops" -> "MLOps"
    else -> c.split('-', '_').joinToString(" ") { t ->
        t.split(' ').joinToString(" ") { w -> w.replaceFirstChar { ch -> ch.uppercase() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    onBack: () -> Unit,
    onOpenSkill: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var skills by remember { mutableStateOf<List<DashboardApiClient.SkillInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // v0.2.7 official parity: show-mode + category filter + New Skill sheet
    var showMode by remember { mutableStateOf("All") }        // All | Enabled | Disabled
    var category by remember { mutableStateOf<String?>(null) } // null = all categories
    var sortBy by remember { mutableStateOf("Name") }          // Name | Category | Most used
    var showFilter by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showBulk by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            when (val r = DashboardApiClient.skillsList()) {
                is NetworkResult.Success -> { skills = r.data; error = null }
                is NetworkResult.HttpError -> error = "Server error (${r.code})"
                is NetworkResult.Error -> error = r.exception.message
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skills") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilter = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter and Sort")
                    }
                    IconButton(onClick = { showNew = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Skill")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { load() }) { Text("Retry") }
                    }
                }
                skills == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    val all = skills!!
                    val filtered = all.filter { sk ->
                        (showMode == "All" ||
                            (showMode == "Enabled" && sk.enabled) ||
                            (showMode == "Disabled" && !sk.enabled)) &&
                        (category == null || sk.category.equals(category, ignoreCase = true))
                    }.let { list ->
                        when (sortBy) {
                            "Category" -> list.sortedWith(
                                compareBy({ it.category ?: "~" }, { it.name.lowercase() }))
                            "Most used" -> list.sortedByDescending { it.usage ?: 0 }
                            else -> list.sortedBy { it.name.lowercase() }
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        // Official header card: "Installed | <n> | <n> on ⇅" (⇅ = Enable/Disable All).
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Installed", style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f))
                                    Text("${all.size}", style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(16.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { showBulk = true },
                                    ) {
                                        Text("${all.count { it.enabled }} on",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Default.SwapVert, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        items(filtered, key = { it.name }) { skill ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable { onOpenSkill(skill.name) }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = skill.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        )
                                        skill.description?.let { d ->
                                            if (d.isNotBlank()) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = d,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 4,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Switch(
                                        checked = skill.enabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch {
                                                when (val r = DashboardApiClient.toggleSkill(skill.name, enabled)) {
                                                    is NetworkResult.Success -> load()
                                                    is NetworkResult.HttpError ->
                                                        Toast.makeText(context, "Server error (${r.code})", Toast.LENGTH_SHORT).show()
                                                    is NetworkResult.Error ->
                                                        Toast.makeText(context, "Failed: ${r.exception.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Official Filter & Sort bottom sheet: Show (+counts), Categories (+counts, humanized),
    // Sort By (Name | Category | Most used), Reset in the header.
    if (showFilter) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFilter = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            val all = skills ?: emptyList()
            val cats = all.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }
                .groupingBy { it }.eachCount()
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Filter & Sort", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        showMode = "All"; category = null; sortBy = "Name"
                    }) { Text("Reset") }
                }
                Text("Show", Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("All" to all.size, "Enabled" to all.count { it.enabled },
                    "Disabled" to all.count { !it.enabled }).forEach { (mode, n) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { showMode = mode }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(mode, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("$n", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (showMode == mode) {
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text("Categories", Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().clickable { category = null }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("All categories", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (category == null) Icon(Icons.Default.Check, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                cats.entries.sortedBy { humanizeCategory(it.key).lowercase() }.forEach { (c, n) ->
                    val label = humanizeCategory(c)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { category = if (category == c) null else c }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("$n", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (category == c) {
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text("Sort By", Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("Name", "Category", "Most used").forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { sortBy = s }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (sortBy == s) Icon(Icons.Default.Check, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    // Official "N on ⇅" menu: Enable All / Disable All (server has no bulk route — official
    // loops the same toggle; we do the same).
    if (showBulk) {
        val all = skills ?: emptyList()
        AlertDialog(
            onDismissRequest = { showBulk = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = null,
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showBulk = false
                            scope.launch {
                                all.filter { !it.enabled }.forEach {
                                    DashboardApiClient.toggleSkill(it.name, true)
                                }
                                load()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Enable All", color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(
                        onClick = {
                            showBulk = false
                            scope.launch {
                                all.filter { it.enabled }.forEach {
                                    DashboardApiClient.toggleSkill(it.name, false)
                                }
                                load()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Disable All", color = MaterialTheme.colorScheme.onSurface) }
                }
            },
            confirmButton = {},
        )
    }

    // v0.2.7: New Skill (official Add Skill → New Skill)
    if (showNew) {
        var name by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("---\nname: \ndescription: \n---\n\n") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New Skill") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(name, { name = it }, label = { Text("name (kebab-case)") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        content, { content = it },
                        label = { Text("SKILL.md") },
                        minLines = 6,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isBlank()) return@TextButton
                    busy = true
                    scope.launch {
                        val r = DashboardApiClient.apiSend(
                            "/api/skills", "POST",
                            kotlinx.serialization.json.buildJsonObject {
                                put("name", name)
                                put("content", content)
                            }.toString(),
                        )
                        busy = false
                        showNew = false
                        when (r) {
                            is NetworkResult.Success -> load()
                            is NetworkResult.HttpError -> Toast.makeText(context, "Server error (${r.code})", Toast.LENGTH_SHORT).show()
                            is NetworkResult.Error -> Toast.makeText(context, "Failed: ${r.exception.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(if (busy) "Creating…" else "Create") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillDetailScreen(skillName: String, onBack: () -> Unit) {
    var content by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun load() {
        scope.launch {
            when (val r = DashboardApiClient.skillContent(skillName)) {
                is NetworkResult.Success -> { content = r.data.content; error = null }
                is NetworkResult.HttpError -> error = "Server error (${r.code})"
                is NetworkResult.Error -> error = r.exception.message
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(skillName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { load() }) { Text("Retry") }
                    }
                }
                content == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    var draft by remember(content) { mutableStateOf(content!!) }
                    var saving by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    ) {
                        OutlinedTextField(
                            draft, { draft = it },
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp,
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                saving = true
                                scope.launch {
                                    val r = DashboardApiClient.apiSend(
                                        "/api/skills/content", "PUT",
                                        kotlinx.serialization.json.buildJsonObject {
                                            put("name", skillName)
                                            put("content", draft)
                                        }.toString(),
                                    )
                                    saving = false
                                    when (r) {
                                        is NetworkResult.Success -> {
                                            content = draft
                                            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                        }
                                        is NetworkResult.HttpError -> Toast.makeText(context, "Server error (${r.code})", Toast.LENGTH_SHORT).show()
                                        is NetworkResult.Error -> Toast.makeText(context, "Failed: ${r.exception.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = draft != content && !saving,
                        ) { Text(if (saving) "Saving…" else "Save Changes") }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Config (core files / soul)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            when (val r = DashboardApiClient.configRaw()) {
                is NetworkResult.Success -> {
                    loaded = r.data.yaml
                    draft = r.data.yaml.orEmpty()
                    error = null
                }
                is NetworkResult.HttpError -> error = "Server error (${r.code})"
                is NetworkResult.Error -> error = r.exception.message
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun save() {
        scope.launch {
            saving = true
            when (val r = DashboardApiClient.saveConfigRaw(draft)) {
                is NetworkResult.Success -> {
                    loaded = r.data.yaml ?: draft
                    Toast.makeText(context, "Config saved", Toast.LENGTH_SHORT).show()
                    error = null
                }
                is NetworkResult.HttpError ->
                    Toast.makeText(context, "Save failed (${r.code}): ${r.message.take(120)}", Toast.LENGTH_LONG).show()
                is NetworkResult.Error ->
                    Toast.makeText(context, "Save failed: ${r.exception.message}", Toast.LENGTH_LONG).show()
            }
            saving = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Config (core / soul)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(10.dp)) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "This is the live config.yaml — personality (soul), providers, tools. Bad YAML or values can break Hermes. Save writes immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                error != null && loaded == null -> {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { load() }) { Text("Retry") }
                    }
                }
                loaded == null -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { save() },
                        enabled = !saving && draft != loaded,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (saving) "Saving…" else "Save config")
                    }
                }
            }
        }
    }
}
