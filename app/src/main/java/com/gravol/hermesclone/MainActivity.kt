package com.gravol.hermesclone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.gravol.hermesclone.feature.chat.ChatScreen
import com.gravol.hermesclone.feature.chat.ChatVmsHolder
import com.gravol.hermesclone.feature.botscreen.BotScreenScreen
import com.gravol.hermesclone.feature.agents.AgentsScreen
import com.gravol.hermesclone.feature.mcp.McpScreen
import com.gravol.hermesclone.feature.onboarding.DashboardSetupScreen
import com.gravol.hermesclone.feature.sessions.SessionsScreen
import com.gravol.hermesclone.feature.sessions.SessionsViewModel
import com.gravol.hermesclone.feature.settings.SettingsRepository
import com.gravol.hermesclone.feature.settings.SettingsScreen
import com.gravol.hermesclone.feature.soul.SoulScreen
import com.gravol.hermesclone.feature.system.ConfigScreen
import com.gravol.hermesclone.feature.system.CronScreen
import com.gravol.hermesclone.feature.system.SkillDetailScreen
import com.gravol.hermesclone.feature.system.SkillsScreen
import com.gravol.hermesclone.notify.NotificationHelper
import com.gravol.hermesclone.feature.hub.BotHubScreen
import com.gravol.hermesclone.feature.hub.HubDefaultModelScreen
import com.gravol.hermesclone.feature.hub.HubProjectDetailScreen
import com.gravol.hermesclone.feature.hub.HubProjectsScreen
import com.gravol.hermesclone.feature.hub.HubArtifactsScreen
import com.gravol.hermesclone.feature.hub.HubMemoryScreen
import com.gravol.hermesclone.feature.hub.HubUsageScreen
import com.gravol.hermesclone.feature.hub.HubPluginsScreen
import com.gravol.hermesclone.feature.hub.HubConnectionsScreen
import com.gravol.hermesclone.feature.hub.HubToolsetsScreen
import com.gravol.hermesclone.feature.hub.HubWebhooksScreen
import com.gravol.hermesclone.feature.hub.HubMessagingScreen
import com.gravol.hermesclone.feature.hub.HubVoiceScreen
import com.gravol.hermesclone.feature.hub.HubKeysScreen
import com.gravol.hermesclone.feature.hub.HubInstancesScreen
import com.gravol.hermesclone.feature.hub.HubKanbanScreen
import com.gravol.hermesclone.feature.hub.HubLogsScreen
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.DebugLog
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.gravol.hermesclone.ui.theme.HermexTheme
import com.gravol.hermesclone.feature.home.HomeScreen
import com.gravol.hermesclone.ui.theme.UiColorOverrides
import com.gravol.hermesclone.ui.theme.hexToColor
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Notification channels (turn-finished/cron/approval/reply)
        NotificationHelper.ensureChannels(this)
        // v0.1.87: native ntfy subscription (push delivery rail) — follows the
        // setting in Settings; starts on every launch, idempotent (startForegroundService).
        kotlinx.coroutines.MainScope().launch {
            val settings = SettingsRepository(applicationContext)
            if (settings.ntfyEnabled.first() != false) {
                com.gravol.hermesclone.notify.NtfySubscriptionService.start(applicationContext)
            }
        }
        // Android 13+ needs a runtime grant for notifications
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4101)
        }

        setContent {
            // Display preferences: UI zoom (dp) + text scale (sp), applied as a
            // density override so every screen picks them up with no per-screen
            // changes. Text scales by zoom × textScale. Accent color (null =
            // follow system/wallpaper) overrides the theme primary.
            val appContext = LocalContext.current.applicationContext
            val settingsRepo = remember { SettingsRepository(appContext) }
            val zoomPercent by settingsRepo.uiZoomPercent.collectAsState(initial = 100)
            val textPercent by settingsRepo.textScalePercent.collectAsState(initial = 100)
            val accentHex by settingsRepo.accentColorHex.collectAsState(initial = null)
            val accentColor = remember(accentHex) {
                accentHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() }
            }
            // Per-part appearance overrides (v0.1.49+) — null = derive from accent
            val uiBgHex by settingsRepo.uiBackgroundHex.collectAsState(initial = null)
            val uiUserBubbleHex by settingsRepo.uiUserBubbleHex.collectAsState(initial = null)
            val uiAssistantBubbleHex by settingsRepo.uiAssistantBubbleHex.collectAsState(initial = null)
            val uiTextHex by settingsRepo.uiTextHex.collectAsState(initial = null)
            // v0.1.95: extra chat surfaces (code/thinking/tool/gauge) — null = derive from scheme
            val uiCodeBlockHex by settingsRepo.uiCodeBlockHex.collectAsState(initial = null)
            val uiThinkingHex by settingsRepo.uiThinkingHex.collectAsState(initial = null)
            val uiToolCardHex by settingsRepo.uiToolCardHex.collectAsState(initial = null)
            val uiGaugeHex by settingsRepo.uiGaugeHex.collectAsState(initial = null)
            val uiMonospace by settingsRepo.uiMonospace.collectAsState(initial = false)
            val uiOverrides = remember(
                uiBgHex, uiUserBubbleHex, uiAssistantBubbleHex, uiTextHex,
                uiCodeBlockHex, uiThinkingHex, uiToolCardHex, uiGaugeHex,
            ) {
                UiColorOverrides(
                    background = uiBgHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() },
                    userBubble = uiUserBubbleHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() },
                    assistantBubble = uiAssistantBubbleHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() },
                    text = uiTextHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() },
                    codeBlock = uiCodeBlockHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() },
                    thinkingBox = uiThinkingHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() },
                    toolCard = uiToolCardHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() },
                    gaugeTrack = uiGaugeHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() },
                )
            }
            // v0.2.13 Look & Feel: appearance mode (0=System 1=Light 2=Dark).
            val appearanceStore = remember { com.gravol.hermesclone.di.AppModule.provideDataStoreManager(appContext) }
            val appearanceMode by appearanceStore.getInt(com.gravol.hermesclone.feature.settings.SettingsKeys.APPEARANCE_MODE)
                .collectAsState(initial = null)
            val dark = when (appearanceMode ?: 0) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            HermexTheme(darkTheme = dark, dynamicColor = false) {
                val baseDensity = LocalDensity.current
                val scaledDensity = Density(
                    density = baseDensity.density * (zoomPercent / 100f),
                    fontScale = baseDensity.fontScale * (textPercent / 100f),
                )
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    // Activity-scoped: chat ViewModels outlive navigation so turns
                    // keep running when you leave a chat (see ChatVmsHolder).
                    val chatVmsHolder: ChatVmsHolder = viewModel()
                    HermexNavGraph(chatVmsHolder = chatVmsHolder)
                }
            }
        }
    }

    /** Notification taps re-deliver the intent while the activity is alive. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // v0.1.872: setIntent() alone does not recompose, so a tap while the app
        // is already foreground never re-fired the deep-link effect (cold starts
        // worked, warm taps did nothing). Bumping snapshot state makes the nav
        // effect key re-fire on every tap.
        intentSeq++
    }

    // v0.1.75: track app foreground/background so turn-finished notifications
    // fire when the app is backgrounded (screenVisible alone misses that case).
    override fun onStart() {
        super.onStart()
        AppState.isBackgrounded = false
    }

    override fun onStop() {
        super.onStop()
        AppState.isBackgrounded = true
    }
}

/** Nav routes a notification deep link may target (must match composable() routes). */
private val DEEP_LINK_ROUTES = setOf("home", "cron", "skills", "config", "settings", "soul")

/**
 * Bumped on every onNewIntent. The deep-link LaunchedEffect keys on this because
 * `activity.intent` is not observable state: setIntent() triggers no recomposition,
 * so the key is never re-evaluated and warm taps (app already foreground) silently
 * do nothing. Intent.equals also compares extras, so an identical repeat tap would
 * not change the key even if it were read.
 */
var intentSeq by androidx.compose.runtime.mutableIntStateOf(0)
    private set

@Composable
fun HermexNavGraph(chatVmsHolder: ChatVmsHolder) {
    val navController = rememberNavController()

    // v0.1.74: notification deep links — open_session extra → chat route.
    // v0.1.103: handle EVERY new intent (the old one-shot handledDeepLink flag
    // meant only the FIRST tap ever navigated — later taps did nothing). With
    // MainActivity now singleTop, notification taps arrive via onNewIntent →
    // setIntent, so this effect re-fires per tap; launchSingleTop makes re-taps
    // to the same chat a no-op instead of stacking duplicates.
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(intentSeq) {
        val intent = activity?.intent ?: return@LaunchedEffect
        val sessionKey = intent.getStringExtra(NotificationHelper.EXTRA_OPEN_SESSION)
        if (!sessionKey.isNullOrBlank() && DashboardApiClient.isConfigured) {
            val title = intent.getStringExtra(NotificationHelper.EXTRA_OPEN_TITLE) ?: sessionKey
            navController.navigate(NotificationHelper.chatRoute(sessionKey, title)) {
                launchSingleTop = true
            }
        } else {
            // v0.1.872: route deep links from link-less pushes (cron output,
            // infra alerts). Whitelisted — an unknown route must not throw.
            val route = intent.getStringExtra(NotificationHelper.EXTRA_OPEN_ROUTE)
            if (route != null && route in DEEP_LINK_ROUTES && DashboardApiClient.isConfigured) {
                navController.navigate(route) { launchSingleTop = true }
            }
        }
    }
    // Dashboard is the primary path. If not configured, user goes through
    // dashboard-setup flow. Legacy stack cleanup in progress.
    val startDest = when {
        DashboardApiClient.isConfigured -> {
            DebugLog.log("ROUTE", "MainActivity", "startup → home (dashboard configured)")
            "home"
        }
        else -> {
            DebugLog.log("ROUTE", "MainActivity", "startup → dashboard-setup")
            "dashboard-setup"
        }
    }

    // Official host uses directional push/pop transitions; retain the system
    // animator-off fallback rather than forcing motion for reduced-motion users.
    val animationsEnabled = Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled()

    // ── Chat-entry jank (task 04) ──
    // A chat destination cannot draw while it composes, and its first frame costs far more
    // than one vsync. Measured on the Pixel 8 (gfxinfo framestats + a compose-phase timer):
    // ChatScreen's entry composition is 105-140 ms — the largest item was the CLOSED
    // WebUI drawer composing its whole panel (62 ms), now gated off in WebuiMenuDrawer —
    // and the transcript's first layout adds another pass. The 300 ms push runs on frame
    // timestamps, so those blocked frames burned the animation: the clone drew 2 frames
    // of motion where the official app draws 14 over 283 ms.
    // The official's shape is a short stall, THEN the full move (measured: tap -> ~266-280 ms
    // -> 283 ms of moving frames). Holding the push at progress 0 for the settle window
    // reproduces that shape: the destination finishes its expensive first frame while it is
    // still off-screen, and the slide then plays at 60 fps. 300 ms covers the measured
    // tap -> settled-chat span (~250-300 ms cold, including the session.resume round trip)
    // and still lands inside the official's own tap -> motion window.
    val chatEntrySettleMs = 300L
    fun isChatRoute(route: String?): Boolean = route?.startsWith("chat/") == true
    fun chatEnter(): EnterTransition =
        if (animationsEnabled) {
            slideInHorizontally(tween(300, delayMillis = chatEntrySettleMs.toInt())) { it } +
                fadeIn(tween(300, delayMillis = chatEntrySettleMs.toInt()))
        } else {
            fadeIn(tween(150))
        }
    fun chatExit(): ExitTransition =
        if (animationsEnabled) {
            slideOutHorizontally(tween(300, delayMillis = chatEntrySettleMs.toInt())) { -it / 4 } +
                fadeOut(tween(300, delayMillis = chatEntrySettleMs.toInt()))
        } else {
            fadeOut(tween(150))
        }

    NavHost(
        navController = navController,
        startDestination = startDest,
        enterTransition = {
            if (isChatRoute(targetState.destination.route)) chatEnter()
            else if (animationsEnabled) slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))
            else fadeIn(tween(150))
        },
        exitTransition = {
            // The screen the chat covers must hold still for the same settle window,
            // otherwise it would slide away while the chat is still composing.
            if (isChatRoute(targetState.destination.route)) chatExit()
            else if (animationsEnabled) slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(300))
            else fadeOut(tween(150))
        },
        popEnterTransition = {
            if (animationsEnabled) slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300))
            else fadeIn(tween(150))
        },
        popExitTransition = {
            if (animationsEnabled) slideOutHorizontally(tween(300)) { it }
            else fadeOut(tween(150))
        },
    ) {
        composable("dashboard-setup") {
            DashboardSetupScreen(
                onDone = {
                    navController.navigate("home") {
                        popUpTo("dashboard-setup") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            // v0.5.4: assistant entry while on home — cold launch composes
            // this with voiceTick already >0 (session service ran first, same
            // process), warm re-launch bumps the tick. Create a session and
            // open it; the flag is NOT consumed here — ChatScreen consumes it
            // to arm the mic on entry.
            val sessionsVm: SessionsViewModel = viewModel()
            val voiceTick by AppState.voiceTick.collectAsState()
            LaunchedEffect(voiceTick) {
                if (voiceTick > 0 && AppState.pendingVoiceStart) {
                    sessionsVm.createSession { sid ->
                        if (sid != null) {
                            val enc = URLEncoder.encode("New session", "UTF-8")
                            navController.navigate("chat/$sid/$enc/default") { launchSingleTop = true }
                        } else {
                            // Creation failed — drop the request, don't let a
                            // later manual chat-open arm the mic out of turn.
                            AppState.consumePendingVoiceStart()
                        }
                    }
                }
            }
            HomeScreen(
                onSessionTap = { session ->
                    val encodedTitle = URLEncoder.encode(session.title ?: session.id, "UTF-8")
                    navController.navigate("chat/${session.id}/$encodedTitle/${URLEncoder.encode(session.source ?: "default", "UTF-8")}")
                },
                onSettings = {
                    navController.navigate("settings")
                },
                onOpenDevices = { navController.navigate("bothub") },
                onOpenAgents = { navController.navigate("agents") },
                activeSessions = chatVmsHolder.activeSessions,
                viewModel = sessionsVm,
            )
        }
        composable("settings") {
            com.gravol.hermesclone.feature.settings.OfficialSettingsRoot(
                onBack = { navController.popBackStack() },
                onOpenDebug = { navController.navigate("settings-legacy") },
            )
        }
        // Old flat settings kept only for its Debug Log tool (clone-only).
        composable("settings-legacy") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable("soul") {
            SoulScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable("cron") {
            CronScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable("botscreen") {
            BotScreenScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable("agents") {
            AgentsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable("mcp") {
            McpScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable("skills") {
            SkillsScreen(
                onBack = { navController.popBackStack() },
                onOpenSkill = { name ->
                    val encoded = URLEncoder.encode(name, "UTF-8")
                    navController.navigate("skills/$encoded")
                },
            )
        }
        composable(
            route = "skills/{name}",
            arguments = listOf(navArgument("name") { type = NavType.StringType }),
        ) { backStackEntry ->
            val name = URLDecoder.decode(backStackEntry.arguments?.getString("name") ?: "", "UTF-8")
            SkillDetailScreen(
                skillName = name,
                onBack = { navController.popBackStack() },
            )
        }
        composable("config") {
            ConfigScreen(
                onBack = { navController.popBackStack() },
            )
        }
        // ── v0.2.7 Bot Hub (official Bot Screen parity) ──
        composable("bothub") {
            BotHubScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable("newagent") {
            com.gravol.hermesclone.feature.agents.NewAgentScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable("hub-model") { HubDefaultModelScreen(onBack = { navController.popBackStack() }) }
        composable("hub-projects") {
            HubProjectsScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { id, title ->
                    val enc = URLEncoder.encode(title, "UTF-8")
                    navController.navigate("chat/$id/$enc/default") { launchSingleTop = true }
                },
                onOpenProject = { projectId ->
                    navController.navigate("hub-project/" + URLEncoder.encode(projectId, "UTF-8"))
                },
            )
        }
        // Project detail is its own destination (official: chat → back returns to the project).
        composable(
            route = "hub-project/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val projectId = URLDecoder.decode(backStackEntry.arguments?.getString("projectId") ?: "", "UTF-8")
            HubProjectDetailScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onOpenSession = { id, title ->
                    val enc = URLEncoder.encode(title, "UTF-8")
                    navController.navigate("chat/$id/$enc/default") { launchSingleTop = true }
                },
            )
        }
        composable("hub-artifacts") { HubArtifactsScreen(onBack = { navController.popBackStack() }) }
        composable("hub-memory") { HubMemoryScreen(onBack = { navController.popBackStack() }) }
        composable("hub-usage") { HubUsageScreen(onBack = { navController.popBackStack() }) }
        composable("hub-plugins") { HubPluginsScreen(onBack = { navController.popBackStack() }) }
        composable("hub-connections") { HubConnectionsScreen(onBack = { navController.popBackStack() }) }
        composable("hub-toolsets") { HubToolsetsScreen(onBack = { navController.popBackStack() }) }
        composable("hub-webhooks") { HubWebhooksScreen(onBack = { navController.popBackStack() }) }
        composable("hub-messaging") { HubMessagingScreen(onBack = { navController.popBackStack() }) }
        composable("hub-voice") { HubVoiceScreen(onBack = { navController.popBackStack() }) }
        composable("hub-keys") { HubKeysScreen(onBack = { navController.popBackStack() }) }
        composable("hub-instances") { HubInstancesScreen(onBack = { navController.popBackStack() }) }
        composable("hub-logs") { HubLogsScreen(onBack = { navController.popBackStack() }) }
        composable("hub-kanban") { HubKanbanScreen(onBack = { navController.popBackStack() }) }
        composable("hub-mcp") { McpScreen(onBack = { navController.popBackStack() }) }
        composable(
            route = "chat/{sessionId}/{title}/{bot}",
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("bot") { type = NavType.StringType; defaultValue = "default" },
            ),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
            val title = URLDecoder.decode(encodedTitle, "UTF-8")
            val bot = backStackEntry.arguments?.getString("bot").orEmpty().ifBlank { "default" }
            // Chat ViewModels are held at Activity scope (ChatVmsHolder) so the
            // WebSocket survives backing out of the chat — turns keep running
            // in the background instead of being torn down by the server's
            // orphan reaper. Reopening the same session returns the SAME VM
            // with its live state intact.
            val chatViewModel = chatVmsHolder.getOrCreate(sessionId)
            DebugLog.log("ROUTE", "MainActivity", "chat → DashboardChatViewModel (held)")
            ChatScreen(
                sessionId = sessionId,
                sessionTitle = title,
                botName = bot,
                onBack = { navController.popBackStack() },
                viewModel = chatViewModel,
                onNavigate = { route -> navController.navigate(route) },
                onOpenSession = { session ->
                    val encTitle = URLEncoder.encode(session.title ?: session.id, "UTF-8")
                    navController.navigate("chat/${session.id}/$encTitle/${URLEncoder.encode(session.source ?: "default", "UTF-8")}") { launchSingleTop = true }
                },
                // v0.2.18: drawer rail "Chat" → back to the chat/session lists. Pop whatever
                // chat entries sit above Home; if Home is not on the back stack (deep link /
                // notification entry) navigate to it instead of doing nothing.
                onOpenChats = {
                    if (!navController.popBackStack("home", inclusive = false)) {
                        navController.navigate("home") { launchSingleTop = true }
                    }
                },
            )
        }
    }
}
