package com.gravol.hermesclone.feature.hub

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.DebugLog
import com.gravol.hermesclone.core.network.NetworkResult
import com.gravol.hermesclone.di.AppModule
import com.gravol.hermesclone.ui.theme.HermesBackground
import com.gravol.hermesclone.ui.theme.HermesSurface
import com.gravol.hermesclone.ui.theme.HermesSurfaceRaised
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
// Connections — official parity. WS connectors.catalog / connectors.accounts / connectors.connect.
//
// Live shapes verified 2026-11-08 against tui_gateway/contracts/connectors.py and the running
// gateway (54 catalog rows on this instance):
//   connectors.catalog   → {"connectors":[{slug,name,description,category,logo_url}]}
//   connectors.accounts  → {"accounts":[{connection_id,connector,status,label,alias,active,…}]}
//   connectors.connect   → {owner:{type:"account"}, connectors:[slug]} → operation view whose
//                          targets[] carry {name,state,connect_url}; the URL is the portal sign-in
//                          page the official app opens in a browser tab.
// The previous version parsed `services` / `catalog`; the real key is `connectors`, so the list
// was always empty (blank screen). Rows are also keyed by `slug`, not `id`.
// ─────────────────────────────────────────────────────────────

private val ConnWarmWhite = Color(0xFFF4F1EB)     // official text (sampled #F4F1EB)
private val ConnMuted = Color(0xFFABA9A5)         // official descriptions (sampled #ABA9A5)
private val ConnChipSelected = Color(0xFF142857)  // selected category chip (sampled #142857)
private val ConnSearchFill = Color(0xFF1D1D39)    // search field (sampled #1D1D39)
private val ConnCard = Color(0xFF12123A)          // pending sign-in card (sampled #12123A)
private val ConnAmber = Color(0xFFFF9F0A)         // "N connected" dot (sampled #FF9F0A)

private const val ConnectionsFooter =
    "Connect services through the Nous account signed in on this Hermes instance. " +
        "Your agents can use them while Hermes Agent is closed."

private fun openExternal(context: android.content.Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        // No browser installed — the sign-in URL stays available in the pending card.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubConnectionsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var catalog by remember { mutableStateOf<List<JsonObject>?>(null) }
    var accounts by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var signInUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var bannerDismissed by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf<String?>(null) }
    var activeSlug by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    suspend fun readAccounts(): List<JsonObject> = try {
        val acc = AppModule.provideRpcClient().requestRaw("connectors.accounts")
        ((acc as? JsonObject)?.get("accounts") as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    LaunchedEffect(reloadTick) {
        error = null
        try {
            val cat = AppModule.provideRpcClient().requestRaw("connectors.catalog")
            val rows = ((cat as? JsonObject)?.get("connectors") as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
            catalog = rows ?: emptyList()
            if (rows == null) error = "The gateway returned an unexpected connector catalog."
            else if (rows.isEmpty()) error = "The gateway returned an empty connector catalog."
            accounts = readAccounts()
            DebugLog.log(
                "INFO", "Connections",
                "catalog=${rows?.size ?: -1} accounts=${accounts.size} " +
                    "pending=${accounts.filter { it.str("status") == "pending" }.mapNotNull { it.str("connector") }}",
            )
        } catch (e: Exception) {
            error = e.message ?: "Connectors unavailable."
            catalog = catalog ?: emptyList()
        }
    }

    // The sign-in happens in the browser; refresh account state when the app comes back.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumedOnce by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (resumedOnce) reloadTick++ else resumedOnce = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val rows = catalog.orEmpty()
    val categories = remember(rows) {
        rows.mapNotNull { it.str("category") }.distinct().sorted()
    }
    val pendingAccounts = accounts.filter { it.str("status") == "pending" }
    val pendingSlugs = pendingAccounts.mapNotNull { it.str("connector") }.toSet()
    // Official drops EVERY connector that already has an account (pending or expired alike — both
    // verified on-device against the same gateway state); it only surfaces them via the count badge.
    val accountSlugs = accounts.mapNotNull { it.str("connector") }.toSet()
    // Official hides the connector it is waiting on from its category list (verified on-device) and
    // shows the sign-in card only for an operation THIS app started (a stale pending account from a
    // previous session shows in the count and keeps its row hidden, but renders no card — verified
    // against the official app on the same gateway state).
    val pendingSlug = activeSlug?.takeIf { it in pendingSlugs }
    val pendingLabel = pendingSlug?.let { slug ->
        rows.firstOrNull { it.str("slug") == slug }?.str("name") ?: slug
    }
    val showPendingCard = pendingSlug != null && !bannerDismissed

    val text = query.trim().lowercase()
    val visible = remember(rows, accounts, query, category) {
        rows.filter { r ->
            val cat = r.str("category").orEmpty()
            val name = r.str("name").orEmpty()
            val desc = r.str("description").orEmpty()
            val slug = r.str("slug").orEmpty()
            (category == "All" || cat == category) &&
                (text.isEmpty() || name.lowercase().contains(text) || desc.lowercase().contains(text)) &&
                slug !in accountSlugs
        }
    }
    val sections = remember(visible) { visible.groupBy { it.str("category").orEmpty() }.toSortedMap() }

    // A LazyColumn keeps its viewport anchored to the first visible key, so an item that is
    // PREPENDED (the pending sign-in card appearing after a Connect) lands above the viewport and
    // looks missing. Scroll to the card when it appears (official shows it at the top).
    val listState = rememberLazyListState()
    LaunchedEffect(showPendingCard) {
        if (showPendingCard) listState.scrollToItem(0)
    }

    // Catalog logos are SVG (logos.composio.dev) — the app's default Coil loader has no SVG decoder.
    val logoLoader = remember(ctx) {
        coil.ImageLoader.Builder(ctx)
            .components { add(coil.decode.SvgDecoder.Factory()) }
            .build()
    }

    fun connect(slug: String) {
        scope.launch {
            connecting = slug
            actionError = null
            try {
                val result = AppModule.provideRpcClient().requestRaw(
                    "connectors.connect",
                    mapOf(
                        "owner" to buildJsonObject { put("type", JsonPrimitive("account")) },
                        "connectors" to JsonArray(listOf(JsonPrimitive(slug))),
                    ),
                )
                val target = ((result as? JsonObject)?.get("targets") as? JsonArray)
                    ?.mapNotNull { it as? JsonObject }?.firstOrNull()
                val url = target?.str("connect_url")
                val state = target?.str("state")
                if (url != null) {
                    signInUrls = signInUrls + (slug to url)
                    activeSlug = slug
                    bannerDismissed = false
                } else if (state == "failed") {
                    actionError = target?.str("detail") ?: "Could not start sign-in."
                }
                accounts = readAccounts()
                if (url != null) openExternal(ctx, url)
            } catch (e: Exception) {
                actionError = e.message ?: "Connector request failed."
            } finally {
                connecting = null
            }
        }
    }

    Scaffold(
        containerColor = HermesBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Connections",
                        color = ConnWarmWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(HermesSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack, "Back",
                                tint = ConnWarmWhite, modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                },
                actions = {
                    if (accounts.isNotEmpty()) ConnectedBadge(accounts.size)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HermesBackground),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ConnectionsSearchPill(query) { query = it }
            ConnectionsCategoryChips(categories, category) { category = it }

            when {
                catalog == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ConnWarmWhite, strokeWidth = 2.dp)
                }

                rows.isEmpty() -> ConnectionsMessage(
                    title = "Connectors unavailable",
                    body = error ?: "The gateway returned no connector catalog.",
                    onRetry = { reloadTick++ },
                )

                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (showPendingCard) {
                        item("pending-card") {
                            PendingSignInCard(
                                label = pendingLabel.orEmpty(),
                                canOpen = pendingSlug?.let { signInUrls[it] }?.isNotBlank() == true,
                                onOpen = { pendingSlug?.let { signInUrls[it] }?.let { openExternal(ctx, it) } },
                                onDismiss = { bannerDismissed = true },
                            )
                        }
                    }
                    actionError?.let { message ->
                        item("action-error") {
                            Text(
                                message,
                                color = Color(0xFFFF6B6B),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                    sections.forEach { (sectionName, sectionRows) ->
                        item("header-$sectionName") { ConnectionsSectionHeader(sectionName) }
                        items(sectionRows, key = { it.str("slug") ?: it.str("name") ?: "?" }) { row ->
                            val slug = row.str("slug").orEmpty()
                            ConnectorRow(
                                name = row.str("name") ?: slug,
                                description = row.str("description").orEmpty(),
                                logoUrl = row.str("logo_url"),
                                busy = connecting == slug,
                                logoLoader = logoLoader,
                                onConnect = { connect(slug) },
                            )
                        }
                    }
                    if (visible.isEmpty()) {
                        item("empty") {
                            ConnectionsMessage(
                                title = "No matching services",
                                body = "Try another search or category.",
                                icon = true,
                            )
                        }
                    }
                    item("footer") {
                        Text(
                            ConnectionsFooter,
                            color = ConnMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedBadge(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 16.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(ConnAmber))
        Spacer(Modifier.width(7.dp))
        Text("$count connected", color = ConnWarmWhite, fontSize = 13.sp)
    }
}

@Composable
private fun ConnectionsSearchPill(query: String, onQuery: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(48.dp)
            .clip(CircleShape)
            .background(ConnSearchFill),
        contentAlignment = Alignment.CenterStart,
    ) {
        Icon(
            Icons.Filled.Search, null, tint = ConnMuted,
            modifier = Modifier.padding(start = 16.dp).size(19.dp),
        )
        if (query.isEmpty()) {
            Text(
                "Search services", color = ConnMuted, fontSize = 16.sp,
                modifier = Modifier.padding(start = 47.dp),
            )
        }
        BasicTextField(
            value = query, onValueChange = onQuery,
            singleLine = true,
            cursorBrush = SolidColor(ConnWarmWhite),
            textStyle = androidx.compose.ui.text.TextStyle(color = ConnWarmWhite, fontSize = 16.sp),
            modifier = Modifier.padding(start = 47.dp, end = 44.dp).fillMaxWidth(),
            decorationBox = { inner -> inner() },
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Clear, "Clear search", tint = ConnMuted,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(19.dp)
                    .clickable { onQuery("") },
            )
        }
    }
}

@Composable
private fun ConnectionsCategoryChips(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(listOf("All") + categories) { chip ->
            val on = chip == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (on) ConnChipSelected else HermesSurface)
                    .clickable { onSelect(chip) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    chip,
                    color = ConnWarmWhite,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ConnectionsSectionHeader(label: String) {
    Text(
        label,
        color = ConnWarmWhite,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun ConnectorRow(
    name: String,
    description: String,
    logoUrl: String?,
    busy: Boolean,
    logoLoader: coil.ImageLoader,
    onConnect: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(HermesSurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (!logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = logoUrl,
                    imageLoader = logoLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = ConnWarmWhite, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (description.isNotBlank()) {
                Text(
                    description, color = ConnMuted, fontSize = 13.sp, lineHeight = 17.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(HermesSurface)
                .clickable(enabled = !busy, onClick = onConnect)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = ConnWarmWhite, strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Text(
                    "Connect",
                    color = ConnWarmWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PendingSignInCard(
    label: String,
    canOpen: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = ConnCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = ConnAmber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    "Waiting for $label sign-in",
                    color = ConnWarmWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Finish signing in your browser, then return to Hermes Agent.",
                color = ConnMuted, fontSize = 13.sp, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                if (canOpen) {
                    ConnPillButton("Open Sign-In", onOpen, icon = Icons.Filled.OpenInNew)
                    Spacer(Modifier.width(10.dp))
                }
                ConnPillButton("Not Now", onDismiss)
            }
        }
    }
}

@Composable
private fun ConnPillButton(
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(HermesSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = ConnWarmWhite, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = ConnWarmWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ConnectionsMessage(
    title: String,
    body: String,
    icon: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon) {
            Icon(Icons.Filled.Link, null, tint = ConnMuted, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(14.dp))
        }
        Text(title, color = ConnWarmWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(body, color = ConnMuted, fontSize = 13.sp)
        if (onRetry != null) {
            Spacer(Modifier.height(18.dp))
            ConnPillButton("Retry", onRetry)
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
