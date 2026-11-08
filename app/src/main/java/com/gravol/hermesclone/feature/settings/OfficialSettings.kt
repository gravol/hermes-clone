package com.gravol.hermesclone.feature.settings

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gravol.hermesclone.core.data.auth.KeychainStore
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.NetworkResult
import com.gravol.hermesclone.ui.theme.HermesAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Official "App Settings" tree — rebuilt for visual parity against
 * ai.nousresearch.hermes.mobile 1.0.0 measured on the Pixel 8 (356 dpi override,
 * dp = px / 2.225). Root + Instances / Security / Caching / Look & Feel / Chat /
 * Notifications / Contact.
 *
 * Palette, geometry and copy below are pixel-sampled / uiautomator-measured from
 * the official app (evidence: work/serial-parity-run/06-evidence/off-*.png|xml).
 *
 * Everything that had real behavior before still does: caching switches + limit
 * + usage + reset (HermesCache), screen-lock switch, accent + appearance + text
 * scale, notification permission/categories. Clone-only additions Jeff relies on
 * (Debug Log, App Info) stay, at the bottom, in the same visual language.
 */

// ─── official palette (sampled from the installed official app) ──────────────

private val SPage = Color(0xFF000022)          // page canvas
private val SCard = Color(0xFF12123A)          // grouped card
private val SCardBorder = Color(0xFF393A3E)    // 1dp card hairline
private val SText = Color(0xFFF4F1EB)          // warm white titles
private val SSub = Color(0xFFABA9A5)           // subtitles / section headers
private val SFoot = Color(0xFF6E6C7A)          // footnotes
private val SField = Color(0xFF151533)         // dropdown field fill
private val SPill = Color(0xFF1D2761)          // full-width pill buttons
private val SPillBright = Color(0xFF1E3A7B)    // Contact "Open Support Thread"
private val SAccent = Color(0xFF447CFF)
private val SDivider = Color(0xFF38383A)
private val SRed = Color(0xFFFF453A)
private val SRadio = Color(0xFF85868B)
private val SIcon = Color(0xFFC7C4C8)          // row icon tint
private val SBanner = Color(0xFF00002E)
private val SGreen = Color(0xFF30D158)
private val SSwitchOff = Color(0xFF5D5D75)
private val SHeaderCircle = Color(0xFF101A3C)
private val SHeaderCircleBorder = Color(0xFF2A3560)
private val STileBg = Color(0xFF000022)        // Chat style tile interior
private val STrack = Color(0xFF242447)         // segmented track
private val STrackSel = Color(0xFF1C1F45)      // segmented selected segment

// ─── keys ────────────────────────────────────────────────────────────────────

object SettingsKeys {
    // appearance_mode: 0=System 1=Light 2=Dark
    const val APPEARANCE_MODE = "appearance_mode"
    const val FONT_CHOICE = "font_choice"          // nunito|manrope|serif|lora|monospace|system
    const val TEXT_SIZE_NAME = "text_size_name"    // Tiny..Extra Large
    const val LIGHT_BG = "light_bg"                // white|paper
    const val DARK_BG = "dark_bg"                  // midnight|black|gray
    const val CHAT_STYLE = "chat_style"            // bubbles|classic
    const val DISMISS_KEYBOARD = "dismiss_keyboard"
    const val SHOW_DEVICE_ACTIONS = "show_device_actions"
    const val REQUIRE_SCREEN_LOCK = "require_screen_lock"
    const val CACHE_CHAT_LIST = "cache_chat_list"
    const val CACHE_MESSAGES = "cache_messages"
    const val CACHE_FILES = "cache_files_links"
    const val CACHE_LIMIT = "cache_limit"          // chats kept, 0 = unlimited
    const val NOTIFY_SHOW_SOURCE = "notify_show_source"
    const val NOTIFY_WORKING_AGENTS = "notify_working_agents"
    const val REDUCE_MOTION = "reduce_motion"      // keep bot characters still
    const val HAPTICS = "haptics"                  // subtle tactile feedback
}

@Composable
private fun rememberKeyInt(key: String, default: Int, onSet: (Int) -> Unit = {}): Pair<Int, (Int) -> Unit> {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { com.gravol.hermesclone.di.AppModule.provideDataStoreManager(ctx) }
    val value by store.getInt(key).collectAsState(initial = null)
    return (value ?: default) to { v -> scope.launch { store.setInt(key, v) }; onSet(v) }
}

@Composable
private fun rememberKeyBool(key: String, default: Boolean): Pair<Boolean, (Boolean) -> Unit> {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { com.gravol.hermesclone.di.AppModule.provideDataStoreManager(ctx) }
    val value by store.getBoolean(key).collectAsState(initial = null)
    return (value ?: default) to { v -> scope.launch { store.setBoolean(key, v) } }
}

@Composable
private fun rememberKeyString(key: String, default: String): Pair<String, (String) -> Unit> {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { com.gravol.hermesclone.di.AppModule.provideDataStoreManager(ctx) }
    val value by store.getString(key).collectAsState(initial = null)
    return ((value ?: default)) to { v -> scope.launch { store.setString(key, v) } }
}

/**
 * Tactile feedback for selections when "Haptics" is on (Look & Feel ▸ Interaction).
 * Logged so the gate itself can be exercised from device QA.
 */
@Composable
private fun rememberTick(): () -> Unit {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val store = remember { com.gravol.hermesclone.di.AppModule.provideDataStoreManager(ctx) }
    val enabled by store.getBoolean(SettingsKeys.HAPTICS).collectAsState(initial = null)
    val on = enabled != false
    return {
        if (on) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            com.gravol.hermesclone.core.network.DebugLog.log("DEBUG", "settings", "haptic: selection tick")
        }
    }
}

// ─── cache engine ────────────────────────────────────────────────────────────

object HermesCache {
    fun dir(ctx: Any): File = File(ctx.let { it as android.content.Context }.filesDir, "cache/hermes").apply { mkdirs() }
    fun sessionsFile(ctx: android.content.Context, profile: String) = File(dir(ctx), "sessions_$profile.json")
    fun messagesDir(ctx: android.content.Context) = File(dir(ctx), "messages").apply { mkdirs() }
    fun filesDir(ctx: android.content.Context) = File(dir(ctx), "files").apply { mkdirs() }
    fun totalBytes(ctx: android.content.Context): Long =
        dir(ctx).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun deleteCategory(ctx: android.content.Context, kind: String) {
        when (kind) {
            "chat_list" -> dir(ctx).listFiles { f -> f.name.startsWith("sessions_") }?.forEach { it.delete() }
            "messages" -> messagesDir(ctx).deleteRecursively()
            "files" -> filesDir(ctx).deleteRecursively()
        }
    }
    fun resetAll(ctx: android.content.Context) { dir(ctx).deleteRecursively(); dir(ctx).mkdirs() }
}

private fun humanBytes(b: Long): String = when {
    b >= 1_048_576 -> String.format(Locale.US, "%.1f MB", b / 1_048_576.0)
    b >= 1_024 -> String.format(Locale.US, "%.0f KB", b / 1_024.0)
    else -> "$b B"
}

// ─── shared chrome ───────────────────────────────────────────────────────────

/** Official header: 47dp bordered circle + left-aligned 20sp title, no app bar surface. */
@Composable
private fun SettingsHeader(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val tick = rememberTick()
    Row(
        Modifier.fillMaxWidth().statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(47.dp).clip(CircleShape).background(SHeaderCircle)
                .border(1.dp, SHeaderCircleBorder, CircleShape)
                .clickable { tick(); onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SText, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(13.dp))
        Text(title, color = SText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun SettingsFrame(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(SPage)) {
        SettingsHeader(title, onBack, actions)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(top = 30.dp, bottom = 40.dp),
        ) { content() }
    }
}

/** Grouped card: 24dp side margins, 20dp radius, #12123A fill, 1dp hairline. */
@Composable
private fun SCard(
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(20.dp))
            .background(SCard).border(1.dp, SCardBorder, RoundedCornerShape(20.dp)),
    ) {
        if (topInset > 0.dp) Spacer(Modifier.height(topInset))
        content()
        if (bottomInset > 0.dp) Spacer(Modifier.height(bottomInset))
    }
}

@Composable
private fun SCardDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 22.dp), thickness = 1.dp, color = SDivider)
}

/** Card header with a small leading glyph ("Cached On This Device", "Usage", …). */
@Composable
private fun SCardHeader(icon: ImageVector?, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = SSub, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(text, color = SSub, fontSize = 15.sp)
    }
}

/** Label that sits outside a card ("Light Background", "Default Chat Visibility"). */
@Composable
private fun SLabel(text: String, top: Dp = 20.dp) {
    Text(
        text, color = SSub, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = top, bottom = 8.dp),
    )
}

@Composable
private fun SFoot(text: String, top: Dp = 4.dp, insideCard: Boolean = true) {
    Text(
        text, color = SFoot, fontSize = 12.sp, lineHeight = 16.sp,
        modifier = Modifier.fillMaxWidth()
            .padding(start = if (insideCard) 22.dp else 24.dp, end = if (insideCard) 22.dp else 24.dp,
                top = top, bottom = 16.dp),
    )
}

@Composable
private fun SSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)? = null) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color(0xFFD8D8E0),
            uncheckedTrackColor = SSwitchOff,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/**
 * Official row: leading glyph, title 17sp, optional 15sp subtitle, trailing
 * chevron / value / custom control. `onClick == null` renders non-interactive.
 */
@Composable
private fun SRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = true,
    titleColor: Color = SText,
    onClick: (() -> Unit)? = null,
) {
    val tick = rememberTick()
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { tick(); onClick() } else Modifier)
            .heightIn(min = 68.dp)
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = SIcon, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(15.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 17.sp, lineHeight = 22.sp)
            if (subtitle != null) Text(subtitle, color = SSub, fontSize = 14.sp, lineHeight = 18.sp)
        }
        if (value != null) Text(value, color = SSub, fontSize = 15.sp)
        if (chevron) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SSub,
                modifier = Modifier.size(22.dp))
        }
    }
}

/** Row whose trailing control is a switch instead of a chevron. */
@Composable
private fun SSwitchRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val tick = rememberTick()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp)
            .padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = SIcon, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(15.dp))
        }
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = SText, fontSize = 17.sp, lineHeight = 23.sp)
            if (subtitle != null) Text(subtitle, color = SSub, fontSize = 15.sp, lineHeight = 21.sp)
        }
        SSwitch(checked) { v -> tick(); onChange(v) }
    }
}

/** Full-width pill action inside a card ("Open Security Settings"). */
@Composable
private fun SPill(
    text: String,
    bg: Color = SPill,
    textColor: Color = SText,
    height: Dp = 40.dp,
    horizontalPadding: Dp = 22.dp,
    onClick: () -> Unit,
) {
    val tick = rememberTick()
    Box(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp)
            .height(height).clip(RoundedCornerShape(height / 2))
            .background(bg).clickable { tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}

/** Dropdown-looking field (official "Nunito" / "Keep Up To: 100 chats" controls). */
@Composable
private fun SField(value: String, onClick: () -> Unit) {
    val tick = rememberTick()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(48.dp)
            .clip(RoundedCornerShape(12.dp)).background(SField)
            .border(1.dp, SCardBorder, RoundedCornerShape(12.dp))
            .clickable { tick(); onClick() }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, Modifier.weight(1f), color = SText, fontSize = 15.sp)
        Icon(Icons.Filled.UnfoldMore, null, tint = SSub, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SRadio(selected: Boolean) {
    Box(
        Modifier.size(22.dp).clip(CircleShape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
                  else Modifier.border(1.5.dp, SRadio, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

/** Thin 5-stop slider matching the official Text Size control. */
@Composable
private fun SDiscreteSlider(value: Float, onChange: (Float) -> Unit, onFinished: (Float) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    var widthPx by remember { mutableFloatStateOf(1f) }
    val snap: (Float) -> Float = { v -> kotlin.math.round(v).coerceIn(0f, 4f) }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp).height(36.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val v = snap(off.x / widthPx * 4f)
                    onChange(v); onFinished(v)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onFinished(value) },
                ) { change, _ ->
                    onChange(snap(change.position.x / widthPx * 4f))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2
            val inset = 10.dp.toPx()
            val usable = (size.width - inset * 2).coerceAtLeast(1f)
            drawLine(Color(0xFF2A2A4C), Offset(inset, cy), Offset(inset + usable, cy),
                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            val x = inset + usable * (value / 4f)
            drawLine(accent, Offset(inset, cy), Offset(x, cy), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            for (i in 0..4) {
                drawCircle(
                    color = if (i <= value) Color(0xFFF4F1EB) else SFoot,
                    radius = 1.8.dp.toPx(),
                    center = Offset(inset + usable * (i / 4f), cy),
                )
            }
            drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(x, cy))
        }
    }
}

/** Official usage row: caps label …dotted leader… right-aligned value. */
@Composable
private fun SLeaderRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = SSub, fontSize = 13.sp, letterSpacing = 0.6.sp)
        Canvas(Modifier.weight(1f).padding(horizontal = 10.dp).height(10.dp)) {
            drawLine(
                color = SSub.copy(alpha = 0.45f),
                start = Offset(0f, size.height * 0.78f),
                end = Offset(size.width, size.height * 0.78f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 3.dp.toPx())),
            )
        }
        Text(value, color = SText, fontSize = 17.sp)
    }
}

/** Segmented control (official Appearance: System / Light / Dark). */
@Composable
private fun SSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val tick = rememberTick()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(44.dp)
            .clip(RoundedCornerShape(11.dp)).background(STrack),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).padding(4.dp).clip(RoundedCornerShape(8.dp))
                    .then(if (on) Modifier.background(STrackSel).border(1.dp, SCardBorder, RoundedCornerShape(8.dp)) else Modifier)
                    .clickable { tick(); onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 15.sp, color = if (on) SText else SSub)
            }
        }
    }
}

private fun parseColorHex(hex: String): Long {
    val h = hex.removePrefix("#")
    return try { (0xFF000000L or h.toLong(16)) } catch (_: Exception) { 0xFF447CFFL }
}

// ─── root ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialSettingsRoot(onBack: () -> Unit, onOpenDebug: () -> Unit) {
    var screen by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val (appearanceMode, setAppearanceMode) = rememberKeyInt(SettingsKeys.APPEARANCE_MODE, 0)
    val (requireLock, setRequireLock) = rememberKeyBool(SettingsKeys.REQUIRE_SCREEN_LOCK, false)
    val (cacheChatList) = rememberKeyBool(SettingsKeys.CACHE_CHAT_LIST, true)
    val (cacheMessages) = rememberKeyBool(SettingsKeys.CACHE_MESSAGES, true)
    val (cacheFiles) = rememberKeyBool(SettingsKeys.CACHE_FILES, true)
    val (chatStyle) = rememberKeyString(SettingsKeys.CHAT_STYLE, "bubbles")
    val tick = rememberTick()

    fun openUrl(url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "No browser available", Toast.LENGTH_SHORT).show()
        }
    }

    // Android system Back inside a nested Settings screen returns to the App Settings
    // root (official parity, verified on-device 2026-11-08: Instances/Security/… +
    // Add Instance → Back lands on the parent screen, never out of Settings).
    BackHandler(enabled = screen != null) { screen = null }

    when (screen) {
        null -> Unit
        "instances" -> { InstancesScreen(onBack = { screen = null }); return }
        "security" -> { SecurityScreen(requireLock, setRequireLock, onBack = { screen = null }); return }
        "caching" -> { CachingScreen(onBack = { screen = null }); return }
        "lookfeel" -> { LookFeelScreen(onBack = { screen = null }); return }
        "chat" -> { ChatSettingsScreen(onBack = { screen = null }); return }
        "notifications" -> { NotificationsSettingsScreen(onBack = { screen = null }); return }
        "contact" -> { ContactScreen(onBack = { screen = null }) { u -> openUrl(u) }; return }
    }

    SettingsFrame("App Settings", onBack) {
        // ── Continue with Nous banner (official: white halftone art → navy body) ──
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp).height(46.dp)
                .clip(RoundedCornerShape(10.dp)).background(SBanner)
                .border(1.dp, SCardBorder, RoundedCornerShape(10.dp))
                .clickable { tick(); openUrl("https://portal.nousresearch.com") },
        ) {
            Image(
                painter = painterResource(com.gravol.hermesclone.R.drawable.hermes_nous_banner_left),
                contentDescription = null,
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.fillMaxHeight().width(88.dp).align(Alignment.CenterStart),
            )
            Text(
                "CONTINUE WITH NOUS",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White, fontSize = 15.sp, letterSpacing = 5.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SText,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(20.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        SCard(topInset = 8.dp, bottomInset = 4.dp) {
            SRow(Icons.Outlined.Storage, "Instances", subtitle = "1") { screen = "instances" }
            SCardDivider()
            SRow(Icons.Outlined.Lock, "Security", subtitle = if (requireLock) "On" else "Off") {
                screen = "security"
            }
            SCardDivider()
            SRow(Icons.Outlined.List, "Caching",
                subtitle = if (cacheChatList || cacheMessages || cacheFiles) "On" else "Off") {
                screen = "caching"
            }
        }

        Spacer(Modifier.height(16.dp))

        SCard(topInset = 8.dp, bottomInset = 4.dp) {
            SRow(Icons.Outlined.Palette, "Look & Feel",
                subtitle = listOf("System", "Light", "Dark")[appearanceMode.coerceIn(0, 2)]) {
                screen = "lookfeel"
            }
            SCardDivider()
            SRow(Icons.Outlined.ChatBubbleOutline, "Chat",
                subtitle = if (chatStyle == "classic") "Classic" else "Bubbles") { screen = "chat" }
        }

        Spacer(Modifier.height(16.dp))

        SCard(topInset = 8.dp, bottomInset = 4.dp) {
            SRow(Icons.Outlined.NotificationsNone, "Notifications") { screen = "notifications" }
            SCardDivider()
            SRow(Icons.Outlined.MailOutline, "Contact") { screen = "contact" }
        }

        Spacer(Modifier.height(16.dp))

        SCard(topInset = 8.dp, bottomInset = 4.dp) {
            SRow(Icons.Outlined.MenuBook, "Documentation") {
                openUrl("https://hermes-agent.nousresearch.com/docs")
            }
        }

        // ── clone-only, documented deviations (field support) ──
        Spacer(Modifier.height(16.dp))
        SCard {
            SRow(Icons.Outlined.BugReport, "Debug Log", chevron = false) { onOpenDebug() }
            SCardDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
                Text("App Info", color = SSub, fontSize = 15.sp)
                Text("Version: ${com.gravol.hermesclone.BuildConfig.VERSION_NAME}",
                    color = SFoot, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                Text("Server: ${DashboardApiClient.baseUrl()}", color = SFoot, fontSize = 12.sp)
            }
        }
    }
}

// ─── Instances ───────────────────────────────────────────────────────────────

@Composable
private fun InstancesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    val tick = rememberTick()
    // System Back from Add Instance returns to the Instances list (official parity).
    BackHandler(enabled = adding) { adding = false }
    if (adding) { InstanceFormScreen(onBack = { adding = false }); return }

    val url = remember { KeychainStore.getDashboardUrl(ctx) ?: DashboardApiClient.baseUrl() }
    val user = remember { KeychainStore.getDashboardUsername(ctx) ?: "" }
    val host = remember(url) { url.substringAfter("//").substringBefore("/").substringBefore(":") }

    SettingsFrame(
        title = "Instances",
        onBack = onBack,
        actions = {
            Box(
                Modifier.size(47.dp).clip(CircleShape).background(SHeaderCircle)
                    .border(1.dp, SHeaderCircleBorder, CircleShape)
                    .clickable { tick(); adding = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, "Add instance", tint = SText, modifier = Modifier.size(22.dp)) }
        },
    ) {
        SCard {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(SGreen))
                        Spacer(Modifier.width(9.dp))
                        Text(host.ifBlank { url }, color = SText, fontSize = 17.sp)
                    }
                    Text("Signed in as $user", color = SSub, fontSize = 15.sp,
                        modifier = Modifier.padding(top = 2.dp))
                }
                // Which instance the app is talking to. One instance is configured, so
                // this is an indicator (the official app switches the active one here).
                SSwitch(checked = true, onChange = null)
            }
        }
    }
}

@Composable
private fun InstanceFormScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }   // 0 Direct, 1 SSH
    var address by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(KeychainStore.getDashboardUsername(ctx) ?: "") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SettingsFrame("Add Instance", onBack) {
        SSegmented(listOf("Direct", "SSH"), tab) { tab = it }
        Spacer(Modifier.height(14.dp))
        if (tab == 1) {
            Text(
                "SSH tunneling is not available in this build yet. Use Direct with the gateway's address " +
                    "(Tailscale address works), or the SSH tab in a future release.",
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                color = SSub, fontSize = 15.sp, lineHeight = 21.sp,
            )
            return@SettingsFrame
        }
        Text("Server", Modifier.padding(start = 24.dp, top = 4.dp), color = SSub, fontSize = 15.sp)
        OutlinedTextField(
            address, { address = it },
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            label = { Text("Address") }, placeholder = { Text("https://hermes.example.com") },
            singleLine = true,
        )
        OutlinedTextField(
            name, { name = it },
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            label = { Text("Name · optional") }, placeholder = { Text("Home, Studio…") },
            singleLine = true,
        )
        Text(
            "Your dashboard's full address, with its port. With Tailscale you can use the 100.x address.",
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            color = SFoot, fontSize = 12.sp, lineHeight = 16.sp,
        )
        Text("Sign In", Modifier.padding(start = 24.dp, top = 12.dp), color = SSub, fontSize = 15.sp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Method", Modifier.weight(1f), color = SText, fontSize = 17.sp)
            Text("Password", color = SSub, fontSize = 15.sp)
        }
        OutlinedTextField(
            username, { username = it },
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            label = { Text("Username") }, placeholder = { Text("Your dashboard username") },
            singleLine = true,
        )
        OutlinedTextField(
            password, { password = it },
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            label = { Text("Password") }, singleLine = true,
            visualTransformation = if (showPass) androidx.compose.ui.text.input.VisualTransformation.None
                                   else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        "Show password")
                }
            },
        )
        error?.let {
            Text(it, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        SPill(
            text = if (busy) "Connecting…" else "Connect",
            onClick = {
                if (busy) return@SPill
                busy = true; error = null
                scope.launch {
                    try {
                        when (val probe = DashboardApiClient.status(address.trim())) {
                            is NetworkResult.Success -> {
                                KeychainStore.saveDashboardCredentials(ctx, address.trim(), password, username)
                                Toast.makeText(ctx, "✓ Connected — reconnecting", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                            else -> error = "Could not reach $address — check the address and port."
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Connection failed"
                    } finally { busy = false }
                }
            },
            horizontalPadding = 24.dp,
            height = 48.dp,
        )
    }
}

// ─── Security ────────────────────────────────────────────────────────────────

@Composable
private fun SecurityScreen(locked: Boolean, setLocked: (Boolean) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val keyguard = remember { ctx.getSystemService(android.content.Context.KEYGUARD_SERVICE) as KeyguardManager }
    val deviceSecure = remember { keyguard.isDeviceSecure }

    SettingsFrame("Security", onBack) {
        SCard {
            SSwitchRow(Icons.Outlined.Fingerprint, "Require Screen Lock",
                "Asked for when the app opens.", locked, setLocked)
            SPill("Open Security Settings") {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) { }
            }
            if (!deviceSecure) {
                SFoot(
                    "This device has no screen lock or biometrics set up. Add one in Android Settings " +
                        "to use the lock.", top = 6.dp,
                )
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─── Caching ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CachingScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val (chatList, setChatList) = rememberKeyBool(SettingsKeys.CACHE_CHAT_LIST, true)
    val (messages, setMessages) = rememberKeyBool(SettingsKeys.CACHE_MESSAGES, true)
    val (filesLinks, setFilesLinks) = rememberKeyBool(SettingsKeys.CACHE_FILES, true)
    val (limit, setLimit) = rememberKeyInt(SettingsKeys.CACHE_LIMIT, 100)
    var limitSheet by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var usageTick by remember { mutableStateOf(0) }
    val usage = remember(usageTick, chatList, messages, filesLinks) {
        withContextLikeContext(ctx) { ctx2 ->
            val total = HermesCache.totalBytes(ctx2)
            val bots = HermesCache.dir(ctx2).listFiles { f -> f.name.startsWith("sessions_") }?.size ?: 0
            val chatRows = HermesCache.dir(ctx2).listFiles { f -> f.name.startsWith("sessions_") }
                ?.sumOf { f -> runCatching { org.json.JSONArray(f.readText()).length() }.getOrDefault(0) } ?: 0
            val chats = HermesCache.messagesDir(ctx2).listFiles()?.size ?: 0
            val files = HermesCache.filesDir(ctx2).listFiles()?.size ?: 0
            Usage(total, bots, chatRows, chats, files)
        }
    }
    LaunchedEffect(Unit) { usageTick++ }

    SettingsFrame("Caching", onBack) {
        SCard {
            SCardHeader(Icons.Outlined.List, "Cached On This Device")
            SSwitchRow(Icons.Outlined.ChatBubbleOutline, "Chat List",
                "Titles, previews and dates, so the list is there before the server answers.",
                chatList) { v -> setChatList(v); if (!v) HermesCache.deleteCategory(ctx, "chat_list") }
            SSwitchRow(Icons.Outlined.ChatBubbleOutline, "Messages",
                "Whole conversations, so a chat opens instantly and can be read offline.",
                messages) { v -> setMessages(v); if (!v) HermesCache.deleteCategory(ctx, "messages") }
            SSwitchRow(Icons.Outlined.AttachFile, "Files & Links",
                "The files, images and links mined from recent chats.",
                filesLinks) { v -> setFilesLinks(v); if (!v) HermesCache.deleteCategory(ctx, "files") }
            SFoot("Turning one off deletes what was already kept for it. Nothing is removed from the server.",
                top = 8.dp)
        }

        Spacer(Modifier.height(16.dp))

        SCard {
            SCardHeader(Icons.Outlined.Storage, "Limit")
            SField("Keep Up To: ${if (limit == 0) "everything" else "$limit chats"}") { limitSheet = true }
            SFoot(
                when (limit) {
                    100 -> "A good balance for daily use."
                    0 -> "Nothing is dropped."
                    else -> "Older items are dropped first once the limit is reached."
                },
                top = 10.dp,
            )
        }

        Spacer(Modifier.height(16.dp))

        SCard {
            SCardHeader(Icons.Outlined.BarChart, "Usage")
            SLeaderRow("ON THIS DEVICE", humanBytes(usage.total))
            SLeaderRow("BOTS", "${usage.bots}")
            SLeaderRow("CHAT LIST", "${usage.chatRows}")
            SLeaderRow("MESSAGES", "${usage.chats} chats")
            SLeaderRow("FILES & LINKS", "${usage.files}")
            SFoot("Older items are dropped first once the limit is reached.", top = 10.dp)
        }

        Spacer(Modifier.height(16.dp))

        SCard {
            SCardHeader(Icons.Outlined.Restore, "Reset Cache")
            Text(
                "Reset Cache", color = SRed, fontSize = 17.sp,
                modifier = Modifier.fillMaxWidth()
                    .clickable { confirmReset = true }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            )
            SFoot("Empties everything above. The app reads it all again as you use it.", top = 4.dp)
        }
    }

    if (limitSheet) {
        ModalBottomSheet(onDismissRequest = { limitSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text("Keep up to", color = SSub, fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 6.dp))
                listOf(25, 50, 100, 250, 500, 0).forEach { n ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            setLimit(n); limitSheet = false; usageTick++
                        }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (n == 0) "Keep everything" else "$n chats", Modifier.weight(1f),
                            color = SText, fontSize = 17.sp)
                        if (limit == n) Icon(Icons.Filled.Check, "Selected", tint = SAccent)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset Cache?") },
            text = { Text("This empties all cached chat lists, messages and files on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    HermesCache.resetAll(ctx); confirmReset = false; usageTick++
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

private data class Usage(val total: Long, val bots: Int, val chatRows: Int, val chats: Int, val files: Int)

private fun <T> withContextLikeContext(
    ctx: android.content.Context,
    block: (android.content.Context) -> T,
): T = kotlinx.coroutines.runBlocking(Dispatchers.IO) { block(ctx) }

// ─── Look & Feel ─────────────────────────────────────────────────────────────

private val ACCENTS = listOf(
    "Hermes" to null,
    "Blue" to "#0A84FF", "Indigo" to "#7C82F7", "Teal" to "#36CBD0",
    "Green" to "#62D982", "Yellow" to "#FFD60A", "Orange" to "#FF9F0A",
    "Red" to "#FF453A", "Pink" to "#FF375F",
)

private val TEXT_SIZES = listOf("Tiny" to 85, "Small" to 92, "Medium" to 100, "Large" to 115, "Extra Large" to 130)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LookFeelScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    val tick = rememberTick()
    val (mode, setMode) = rememberKeyInt(SettingsKeys.APPEARANCE_MODE, 0)
    val accentHex by repo.accentColorHex.collectAsState(initial = null)
    val (fontChoice, setFontChoice) = rememberKeyString(SettingsKeys.FONT_CHOICE, "nunito")
    val (textSize, setTextSize) = rememberKeyString(SettingsKeys.TEXT_SIZE_NAME, "Medium")
    val (lightBg, setLightBg) = rememberKeyString(SettingsKeys.LIGHT_BG, "White")
    val (darkBg, setDarkBg) = rememberKeyString(SettingsKeys.DARK_BG, "Midnight")
    val (reduceMotion, setReduceMotion) = rememberKeyBool(SettingsKeys.REDUCE_MOTION, false)
    val (haptics, setHaptics) = rememberKeyBool(SettingsKeys.HAPTICS, true)
    val textPercent by repo.textScalePercent.collectAsState(initial = 100)
    var fontSheet by remember { mutableStateOf(false) }
    var sizeSheet by remember { mutableStateOf(false) }
    var accentPicker by remember { mutableStateOf(false) }

    val sliderIndex = TEXT_SIZES.indexOfFirst { it.first == textSize }.coerceAtLeast(2)
    var sliderPos by remember(textSize) { mutableFloatStateOf(sliderIndex.toFloat()) }

    SettingsFrame("Look & Feel", onBack) {
        SCard {
            SCardHeader(null, "Appearance")
            SSegmented(listOf("System", "Light", "Dark"), mode) { setMode(it) }
            SCardHeader(null, "Accent")
            val cols = 5
            val rows = (ACCENTS.size + 1 + cols - 1) / cols
            repeat(rows) { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    repeat(cols) { c ->
                        val idx = r * cols + c
                        Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                            if (idx < ACCENTS.size) {
                                val (name, hex) = ACCENTS[idx]
                                val selected = (accentHex.isNullOrEmpty() && hex == null) || accentHex == hex
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape)
                                        .background(if (hex == null) HermesAccent else Color(parseColorHex(hex)))
                                        .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                                        .semantics {
                                            contentDescription = name
                                            if (selected) stateDescription = "selected"
                                        }
                                        .clickable {
                                            tick(); scope.launch { repo.setAccentColorHex(hex ?: "") }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) Icon(Icons.Filled.Check, null,
                                        tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            } else if (idx == ACCENTS.size) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape)
                                        .semantics { contentDescription = "Custom accent" }
                                        .clickable {
                                            tick(); accentPicker = true
                                        }.background(
                                        Brush.sweepGradient(
                                            listOf(Color(0xFFFF3B30), Color(0xFFFFD60A), Color(0xFF30D158),
                                                Color(0xFF40C8E0), Color(0xFF447CFF), Color(0xFFBF5AF2),
                                                Color(0xFFFF3B30)),
                                        ),
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Tune, null, tint = Color.White,
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        SLabel("Light Background")
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            BackgroundTile("White", Color(0xFFFFFFFF), Color(0xFFF2F2F7),
                lightBg == "White", { setLightBg("White") }, 1f)
            Spacer(Modifier.width(12.dp))
            BackgroundTile("Paper White", Color(0xFFFDFAF3), Color(0xFFF4EFE3),
                lightBg == "Paper White", { setLightBg("Paper White") }, 1f)
        }

        SLabel("Dark Background")
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            BackgroundTile("Midnight", Color(0xFF12123A), Color(0xFF1C1C46),
                darkBg == "Midnight", { setDarkBg("Midnight") }, 1f)
            Spacer(Modifier.width(10.dp))
            BackgroundTile("Black", Color(0xFF1C1C1E), Color(0xFF26262A),
                darkBg == "Black", { setDarkBg("Black") }, 1f)
            Spacer(Modifier.width(10.dp))
            BackgroundTile("Gray", Color(0xFF2C2C2E), Color(0xFF36363A),
                darkBg == "Gray", { setDarkBg("Gray") }, 1f)
        }

        SLabel("Font")
        SField(fontChoice.replaceFirstChar { it.uppercase() }) { fontSheet = true }
        Text("A clear view of your conversations.", color = SText, fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 10.dp))

        SLabel("Text Size")
        SField(TEXT_SIZES.getOrNull(sliderPos.toInt())?.first ?: textSize) { sizeSheet = true }
        SDiscreteSlider(
            value = sliderPos,
            onChange = { sliderPos = it },
            onFinished = {
                val idx = it.toInt().coerceIn(0, TEXT_SIZES.size - 1)
                val (nm, pct) = TEXT_SIZES[idx]
                sliderPos = idx.toFloat()
                setTextSize(nm)
                scope.launch { repo.setTextScalePercent(pct) }
            },
        )

        Spacer(Modifier.height(8.dp))
        SCard {
            SSwitchRow(null, "Reduce motion", null, reduceMotion) { setReduceMotion(it) }
            SFoot("Bot characters breathe while idle and move about while an agent is working. " +
                "Turn this off to keep them still.", top = 0.dp)
        }

        Spacer(Modifier.height(16.dp))
        SCard {
            SCardHeader(Icons.Outlined.Vibration, "Interaction")
            SSwitchRow(null, "Haptics", null, haptics) { setHaptics(it) }
            SFoot("Plays subtle tactile feedback for selections, confirmations, and errors.", top = 0.dp)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "App Icon", color = SSub, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 8.dp),
        )
        SFoot("Icon alternatives ship with the app bundle; this build uses the Hermes launcher icon.",
            top = 2.dp, insideCard = false)
    }

    if (fontSheet) {
        ModalBottomSheet(onDismissRequest = { fontSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text("Typeface", color = SSub, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
                listOf(
                    "nunito" to "Rounded and friendly. The Hermes default.",
                    "manrope" to "Modern grotesque with open shapes.",
                    "serif" to "Classic book-like letterforms.",
                    "lora" to "Warm serif with high x-height.",
                    "monospace" to "Fixed-width, code-like.",
                ).forEach { (id, desc) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            tick(); setFontChoice(id); fontSheet = false
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(id.replaceFirstChar { it.uppercase() }, color = SText, fontSize = 17.sp)
                            Text(desc, fontSize = 13.sp, color = SFoot)
                        }
                        if (fontChoice == id) Icon(Icons.Filled.Check, "Selected", tint = SAccent)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (sizeSheet) {
        ModalBottomSheet(onDismissRequest = { sizeSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text("Choose text size", color = SSub, fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 6.dp))
                TEXT_SIZES.forEachIndexed { idx, (name, pct) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            tick(); sliderPos = idx.toFloat(); setTextSize(name)
                            scope.launch { repo.setTextScalePercent(pct) }
                            sizeSheet = false
                        }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, Modifier.weight(1f), color = SText, fontSize = 17.sp)
                        if (textSize == name) Icon(Icons.Filled.Check, "Selected", tint = SAccent)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (accentPicker) {
        var hex by remember { mutableStateOf(accentHex?.takeIf { it.isNotEmpty() } ?: "#447CFF") }
        AlertDialog(
            onDismissRequest = { accentPicker = false },
            title = { Text("Custom accent") },
            text = {
                Column {
                    OutlinedTextField(hex, { hex = it.take(7) }, label = { Text("Hex") }, singleLine = true)
                    Row(Modifier.padding(top = 8.dp)) {
                        listOf("#447CFF", "#7C4DFF", "#00BFA5", "#FF6E40", "#EC407A").forEach { c ->
                            Box(Modifier.size(30.dp).padding(4.dp).clip(CircleShape)
                                .background(Color(parseColorHex(c)))
                                .clickable { hex = c })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.setAccentColorHex(hex) }
                    accentPicker = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { accentPicker = false }) { Text("Cancel") } },
        )
    }
}

/** Background preset tile: swatch with an inner card silhouette, label + radio. */
@Composable
private fun RowScope.BackgroundTile(
    label: String,
    swatch: Color,
    inner: Color,
    selected: Boolean,
    onClick: () -> Unit,
    weightF: Float,
) {
    val tick = rememberTick()
    Column(
        Modifier.weight(weightF).clip(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                  else Modifier.border(1.dp, SCardBorder, RoundedCornerShape(14.dp)))
            .clickable { tick(); onClick() },
    ) {
        Box(Modifier.fillMaxWidth().height(52.dp).background(swatch), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(0.58f).height(24.dp).clip(RoundedCornerShape(7.dp)).background(inner))
        }
        Row(
            Modifier.fillMaxWidth().background(SCard).padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), color = SText, fontSize = 15.sp)
            SRadio(selected)
        }
    }
}

// ─── Chat settings ───────────────────────────────────────────────────────────

@Composable
private fun ChatSettingsScreen(onBack: () -> Unit) {
    val (style, setStyle) = rememberKeyString(SettingsKeys.CHAT_STYLE, "bubbles")
    val (dismissKb, setDismissKb) = rememberKeyBool(SettingsKeys.DISMISS_KEYBOARD, false)
    val (showToolCalls, setShowToolCalls) = rememberKeyBool("show_tool_calls", true)
    val (showDeviceActions, setShowDeviceActions) = rememberKeyBool(SettingsKeys.SHOW_DEVICE_ACTIONS, true)
    val (showThinking, setShowThinking) = rememberKeyBool("show_thinking", true)
    val (foldTurns, setFoldTurns) = rememberKeyBool("fold_finished_turns", true)
    val tick = rememberTick()

    SettingsFrame("Chat", onBack) {
        SCard {
            SCardHeader(null, "Chat Style")
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp)) {
                StyleTile("Bubbles", selected = style != "classic", weightF = 1f, onClick = {
                    tick(); setStyle("bubbles")
                }) { BubblesPreview() }
                Spacer(Modifier.width(10.dp))
                StyleTile("Classic", selected = style == "classic", weightF = 1f, onClick = {
                    tick(); setStyle("classic")
                }) { ClassicPreview() }
            }
            SFoot("Choose how messages appear in all your conversations.", top = 10.dp)
        }

        Spacer(Modifier.height(16.dp))

        SCard {
            SCardHeader(null, "Keyboard")
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Dismiss keyboard after sending", color = SText, fontSize = 17.sp, lineHeight = 23.sp)
                    Text("Off keeps the keyboard ready for your next message.", color = SSub,
                        fontSize = 15.sp, lineHeight = 21.sp)
                }
                SSwitch(dismissKb) { v -> tick(); setDismissKb(v) }
            }
        }

        SLabel("Default Chat Visibility")
        SCard {
            SSwitchRow(Icons.Outlined.Build, "Tool calls",
                "Show general tool activity between messages.", showToolCalls) { setShowToolCalls(it) }
            SCardDivider()
            SSwitchRow(Icons.Outlined.Smartphone, "Device actions",
                "Show calendar, health, location and other device requests between messages.",
                showDeviceActions) { setShowDeviceActions(it) }
            SCardDivider()
            SSwitchRow(Icons.Outlined.Psychology, "Thinking",
                "Show what Hermes reasons through before it answers, when the model shares it.",
                showThinking) { setShowThinking(it) }
            SCardDivider()
            SSwitchRow(Icons.Outlined.UnfoldLess, "Fold finished turns",
                "Tuck tool calls and thinking behind one row once Hermes has answered.",
                foldTurns) { setFoldTurns(it) }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "Defaults for new conversations. Chats you already customized keep their own choice.",
            color = SFoot, fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 10.dp),
        )
    }
}

@Composable
private fun RowScope.StyleTile(
    label: String,
    selected: Boolean,
    weightF: Float,
    onClick: () -> Unit,
    preview: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.weight(weightF).height(196.dp).clip(RoundedCornerShape(14.dp)).background(STileBg)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                  else Modifier.border(1.dp, SCardBorder, RoundedCornerShape(14.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            preview()
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = SText, fontSize = 17.sp)
            SRadio(selected)
        }
    }
}

@Composable
private fun ColumnScope.BubblesPreview() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF272740))
            .padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text("Your day is all planned.", color = Color(0xFFA8A6C0), fontSize = 11.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF1F3B7D))
            .padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text("Sounds good!", color = Color(0xFFD8DCF0), fontSize = 11.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF272740))
            .padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text("I’ll take care of it.", color = Color(0xFFA8A6C0), fontSize = 11.sp)
        }
    }
}

@Composable
private fun ColumnScope.ClassicPreview() {
    listOf("Assistant" to "Your day is all planned.",
           "You" to "Sounds good!",
           "Assistant" to "I’ll take care of it.").forEach { (who, line) ->
        Text(who, color = Color(0xFF8E8CA6), fontSize = 10.sp)
        Text(line, color = Color(0xFFD9D7CE), fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 7.dp))
    }
}

// ─── Notifications ───────────────────────────────────────────────────────────

@Composable
private fun NotificationsSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val nm = remember {
        ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    }
    var permTick by remember { mutableStateOf(0) }
    val permissionGranted = remember(permTick) {
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    val (showSource, setShowSource) = rememberKeyBool(SettingsKeys.NOTIFY_SHOW_SOURCE, true)
    val (workingAgents, setWorkingAgents) = rememberKeyBool(SettingsKeys.NOTIFY_WORKING_AGENTS, true)
    val host = remember { (KeychainStore.getDashboardUrl(ctx) ?: DashboardApiClient.baseUrl())
        .substringAfter("//").substringBefore("/").substringBefore(":") }

    SettingsFrame("Notifications", onBack) {
        SCard {
            SCardHeader(null, "On This Device")
            Text(
                if (permissionGranted) "Notifications are on" else "Notifications are off",
                color = SText, fontSize = 17.sp,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Text(
                "Allow Hermes Agent to show messages and requests from paired instances.",
                color = SText, fontSize = 15.sp, lineHeight = 21.sp,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp),
            )
            if (!permissionGranted) {
                Spacer(Modifier.height(8.dp))
                SPill("Allow notifications", onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                })
            }
            Text(
                "Open Android settings", color = SAccent, fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().clickable {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) { }
                }.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 18.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        SCard {
            SCardHeader(Icons.Outlined.List, "Categories on This Device")
            listOf(
                "Agent Replies" to com.gravol.hermesclone.notify.NotificationHelper.CHANNEL_REPLY,
                "Approvals & Questions" to com.gravol.hermesclone.notify.NotificationHelper.CHANNEL_ALERTS,
                "Automations & Deliveries" to com.gravol.hermesclone.notify.NotificationHelper.CHANNEL_CRON,
                "Gateway Status" to com.gravol.hermesclone.notify.NotificationHelper.CHANNEL_TURNS,
            ).forEach { (label, channelId) ->
                val state = nm.getNotificationChannel(channelId)
                val on = permissionGranted && state != null &&
                    nm.importance >= android.app.NotificationManager.IMPORTANCE_LOW &&
                    (state.importance >= android.app.NotificationManager.IMPORTANCE_LOW)
                SRow(null, label, value = if (on) "On" else "Off", chevron = false) {
                    try {
                        ctx.startActivity(Intent("android.settings.CHANNEL_NOTIFICATION_SETTINGS")
                            .putExtra("android.provider.extra.CHANNEL_ID", channelId)
                            .putExtra("android.provider.extra.APP_PACKAGE", ctx.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) { }
                }
            }
            SFoot("Choose sounds, vibration and how notifications appear on this device.", top = 6.dp)
        }

        Spacer(Modifier.height(16.dp))

        SCard {
            SCardHeader(Icons.Outlined.List, "Appearance")
            SSwitchRow(Icons.Outlined.Label, "Show Source",
                "Name where a message came from, such as Telegram or WhatsApp. Applies to new " +
                    "notifications; event details and routine names stay visible.",
                showSource) { setShowSource(it) }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        SCard {
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Working Agents", color = SText, fontSize = 17.sp)
                    Text(
                        "Show which agents are working, for how long, what they are doing and any " +
                            "question waiting for you, in an ongoing notification while Hermes Agent " +
                            "is in the background. Turning this off removes it from this device.",
                        color = SText, fontSize = 15.sp, lineHeight = 21.sp,
                    )
                }
                SSwitch(workingAgents) { v -> setWorkingAgents(v) }
            }
        }

        Spacer(Modifier.height(16.dp))

        SCard {
            SCardHeader(Icons.Outlined.Storage, "Instances")
            SRow(Icons.Outlined.ViewAgenda, host, subtitle = "Not set up") { }
            SFoot("Notifications aren't available in this build.", top = 6.dp)
        }

        Spacer(Modifier.height(16.dp))

        SCard {
            SCardHeader(null, "Privacy")
            Text(
                "Choose “push” in routines or webhooks to send notifications to this device. " +
                    "Previews are encrypted on your agent and decrypted here; the relay and Google " +
                    "can’t read them. Hermes Agent provides a generic preview for the lock screen; " +
                    "Android's settings control what is shown.",
                color = SText, fontSize = 15.sp, lineHeight = 21.sp,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 18.dp),
            )
        }
    }
}

// ─── Contact ─────────────────────────────────────────────────────────────────

@Composable
private fun ContactScreen(onBack: () -> Unit, openUrl: (String) -> Unit) {
    SettingsFrame("Contact", onBack) {
        SCard {
            SCardHeader(null, "Discord")
            SPill("Open Support Thread", bg = SPillBright, height = 48.dp, horizontalPadding = 22.dp) {
                openUrl("https://discord.gg/nousresearch")
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
