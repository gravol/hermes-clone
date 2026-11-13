package com.gravol.hermesclone.feature.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale

/**
 * Artifacts — official parity (v0.2.27). Client-side indexer, no server route.
 *
 * Official screen anatomy (uiautomator ground truth of_art1/2.png):
 *   top bar: Back · "Artifacts" · [All Files]
 *   "Search artifacts" field (matches label, source conversation, AND file
 *   content read back from the gateway)
 *   "Images" section header + horizontal thumbnail carousel
 *     (card a11y: "file.png, <conversation> · <reltime>")
 *   "Documents" section header + rows:
 *     file icon · NAME (17sp) · "<conversation> · <reltime>" (13sp muted)
 *     · trailing "Open source conversation" icon button
 *   rows sorted by LAST occurrence time, newest first.
 */

private val ArtSection = Color(0xFF9B99A7)   // section header gray
private val ArtMuted = Color(0xFF6E6C7A)
private val ArtTitle = Color(0xFFF4F1EB)

private fun kindIcon(k: ArtifactKind): ImageVector = when (k) {
    ArtifactKind.Images -> Icons.Filled.Image
    ArtifactKind.Video -> Icons.Filled.Movie
    ArtifactKind.Audio -> Icons.Filled.GraphicEq
    ArtifactKind.Code -> Icons.Filled.Code
    ArtifactKind.Documents -> Icons.AutoMirrored.Filled.Article
    ArtifactKind.Data -> Icons.Filled.TableChart
    ArtifactKind.Archives -> Icons.Filled.FolderZip
    ArtifactKind.Links -> Icons.Filled.Link
    ArtifactKind.Other -> Icons.Filled.InsertDriveFile
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubArtifactsScreen(
    onBack: () -> Unit,
    onOpenChat: (sessionId: String, title: String, profile: String) -> Unit,
    onOpenFile: (Artifact) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ArtifactIndexer.ensureStarted(context) }
    val state by ArtifactIndexer.state.collectAsState()

    var query by remember { mutableStateOf("") }
    var filtered by remember { mutableStateOf<List<Artifact>?>(null) }  // null = query inactive
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artifacts") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { /* All Files — official file browser route (not yet cloned) */ }) {
                        Icon(Icons.Filled.FolderOpen, "All Files")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                query, { q ->
                    query = q
                    searchJob?.cancel()
                    if (q.isBlank()) { filtered = null } else {
                    searchJob = scope.launch {
                        delay(350)
                        filtered = searchArtifacts(ArtifactIndexer.state.value.artifacts, q)
                    }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("Search artifacts") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                leadingIcon = { Icon(Icons.Filled.Search, null) },
            )
            if (state.indexing && state.total > 0) {
                Text(
                    "Indexing ${NumberFormat.getIntegerInstance(Locale.US).format(state.scanned)}" +
                        " of ${NumberFormat.getIntegerInstance(Locale.US).format(state.total)} conversations",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = ArtMuted, fontSize = 13.sp,
                )
            }
            val shown = filtered ?: state.artifacts
            val images = shown.filter { it.kind == ArtifactKind.Images }
            val sections = ArtifactKind.entries
                .filter { it != ArtifactKind.Images }
                .map { k -> k to shown.filter { it.kind == k } }
                .filter { it.second.isNotEmpty() }
            LazyColumn(Modifier.fillMaxSize()) {
                if (images.isNotEmpty()) {
                    item { ArtSectionHeader("Images") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                            items(images, key = { "img-" + it.id }) { a ->
                                ImageCard(a, onClick = { onOpenFile(a) })
                            }
                        }
                    }
                }
                for ((kind, list) in sections) {
                    item { ArtSectionHeader(kind.sectionLabel) }
                    val kname = kind.name
                    items(list, key = { a -> kname + "-" + a.id }) { a ->
                        ArtifactRow(a,
                            onClick = { onOpenFile(a) },
                            onOpenConversation = { onOpenChat(a.sessionId, a.sessionTitle, a.profile) },
                        )
                    }
                }
                if (shown.isEmpty() && !state.indexing) {
                    item {
                        Text(
                            if (query.isNotBlank()) "No matching artifacts" else "No artifacts yet",
                            Modifier.padding(24.dp), color = ArtMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtSectionHeader(label: String) {
    Text(
        label,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
        color = ArtSection, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ImageCard(a: Artifact, onClick: () -> Unit) {
    Column(
        Modifier.padding(end = 10.dp).width(120.dp).clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(DashboardApiClient.restBaseUrl + "/api/files/download?path=" +
                    URLEncoder.encode(a.source, "UTF-8"))
                .crossfade(true)
                .build(),
            contentDescription = a.label + ", " + a.sessionTitle + " · " + relativeTime(a.createdAt),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(a.label, Modifier.padding(top = 6.dp), color = ArtTitle, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(a.sessionTitle + " · " + relativeTime(a.createdAt),
            color = ArtMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ArtifactRow(a: Artifact, onClick: () -> Unit, onOpenConversation: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kindIcon(a.kind), null, tint = ArtSection, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(a.label, color = ArtTitle, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.sessionTitle + " · " + relativeTime(a.createdAt),
                color = ArtMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onOpenConversation) {
            Icon(Icons.AutoMirrored.Filled.Article, "Open source conversation", tint = ArtSection)
        }
    }
}

/**
 * Official search semantics: matches label, source conversation title, and
 * the file's on-disk CONTENT (verified: searching 'token' hits a .txt whose
 * body contains "500-token"; 'restic-pass' — present only as a bare ls row,
 * never a path — correctly misses).
 */
private suspend fun searchArtifacts(all: List<Artifact>, q: String): List<Artifact> {
    val ql = q.lowercase(Locale.ROOT)
    val nameHits = all.filter {
        it.label.lowercase(Locale.ROOT).contains(ql) || it.sessionTitle.lowercase(Locale.ROOT).contains(ql)
    }.toMutableSet()
    val out = mutableListOf<Artifact>()
    for (a in all) {
        if (a in nameHits) { out.add(a); continue }
        if (a.kind == ArtifactKind.Links) continue
        if (out.size >= 200) break
        val txt = fetchFileText(a.source) ?: continue
        if (txt.lowercase(Locale.ROOT).contains(ql)) out.add(a)
    }
    return out
}

private val fileTextCache = java.util.Collections.synchronizedMap(
    object : java.util.LinkedHashMap<String, String?>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?) = size > 48
    })

internal suspend fun fetchFileText(path: String): String? {
    fileTextCache[path]?.let { return it }
    val r = DashboardApiClient.apiGet("/api/files/read?path=" + URLEncoder.encode(path, "UTF-8"))
    if (r !is NetworkResult.Success) return null
    val dataUrl = (r.data as? JsonObject)?.get("data_url")?.jsonPrimitive?.contentOrNull ?: return null
    val b64 = dataUrl.substringAfter("base64,", "")
    val text = runCatching { String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT)) }.getOrNull()
    if (text != null) fileTextCache[path] = text
    return text
}

/** Official artifact viewer (opened by tapping a row). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactViewerScreen(
    artifactId: String,
    onBack: () -> Unit,
    onOpenChat: (sessionId: String, title: String, profile: String) -> Unit,
) {
    val state by ArtifactIndexer.state.collectAsState()
    val artifact = remember(artifactId, state.artifacts) {
        state.artifacts.firstOrNull { it.id == artifactId }
    }
    var text by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var sourceMode by remember { mutableStateOf(false) }
    LaunchedEffect(artifact?.source) {
        text = artifact?.let { fetchFileText(it.source) }
        failed = text == null
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artifact?.label ?: "Artifact", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(onClick = { sourceMode = !sourceMode }) { Text("Source") }
                    IconButton(onClick = { artifact?.let { onOpenChat(it.sessionId, it.sessionTitle, it.profile) } }) {
                        Icon(Icons.AutoMirrored.Filled.Article, "Open Conversation")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            artifact?.let {
                Text(
                    it.kind.sectionLabel + " · " + it.source,
                    Modifier.padding(16.dp, 8.dp), color = ArtMuted, fontSize = 12.sp,
                )
            }
            if (failed) {
                Text("HTTP 404", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            }
            val t = text
            if (t != null) {
                if (sourceMode) {
                    Text(t, Modifier.padding(12.dp), fontSize = 12.sp)
                } else {
                    Text(t, Modifier.padding(16.dp, 4.dp), fontSize = 14.sp, lineHeight = 21.sp)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
