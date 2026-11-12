package com.gravol.hermesclone.feature.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import com.gravol.hermesclone.ui.orb.BlobAvatar
import com.gravol.hermesclone.ui.orb.MascotAvatar
import com.gravol.hermesclone.ui.orb.recipeFor
import com.gravol.hermesclone.R
import androidx.compose.ui.text.font.Font
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.South
import androidx.compose.material.icons.outlined.North
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Bolt
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.runtime.produceState
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.gravol.hermesclone.di.AppModule
import com.gravol.hermesclone.feature.settings.SettingsRepository
import com.gravol.hermesclone.AppState
import com.gravol.hermesclone.assistant.TtsPlayback
import com.gravol.hermesclone.feature.menu.WebuiMenuDrawer
import com.gravol.hermesclone.ui.theme.LocalUiSurfaces
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.DebugLog
import com.gravol.hermesclone.core.network.JsonRpcClient
import com.gravol.hermesclone.core.network.NetworkResult
import com.gravol.hermesclone.core.network.SessionSummary
import com.gravol.hermesclone.core.network.WsConnectionManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val ChatManrope = FontFamily(Font(R.font.manrope_chat_regular))

/** Timestamp header the official action card prints ("Nov 8, 01:23"). */
internal fun messageTimestampLabel(ms: Long): String =
    java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(ms))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    sessionId: String,
    sessionTitle: String?,
    botName: String = "default",
    onBack: () -> Unit,
    viewModel: ChatViewModelContract,
    onNavigate: (String) -> Unit = {},
    onOpenSession: (SessionSummary) -> Unit = {},
    // v0.2.18: the drawer rail's "Chat" = back to the chat/session lists (Host pops to Home).
    onOpenChats: () -> Unit = {},
) {
    LaunchedEffect(sessionId) {
        viewModel.init(sessionId, sessionTitle)

    }

    // Report screen visibility so the VM can flag turns that finish while the
    // user is away (background turns — v0.1.60).
    // v0.1.874: keyed on sessionId. With key=Unit this ran ONCE, on first
    // composition — i.e. BEFORE LaunchedEffect(sessionId) had populated
    // sessionId (it starts empty), and it never re-ran when the session
    // changed. AppState.visibleSessionId therefore stayed "" forever and the
    // quiet-channel guard never matched.
    DisposableEffect(sessionId) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }

    val state = viewModel.uiState
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    // /new /reset — navigate to the freshly created session (v0.1.130).
    LaunchedEffect(viewModel.resetTargetSession) {
        val sid = viewModel.resetTargetSession ?: return@LaunchedEffect
        viewModel.resetTargetSession = null
        onOpenSession(SessionSummary(id = sid, title = "New session"))
    }
    val listState = rememberLazyListState()
    var composerText by remember { mutableStateOf("") }
    // The official timeline hides extended diagnostic cards by default.
    var showExtendedActivity by remember { mutableStateOf(false) }
    // v0.2.5: composer bar height (px) — growth (multi-line draft, chips)
    // must re-pin the transcript so the stream stays visible above it.
    var composerBarH by remember { mutableStateOf(0) }
    // v0.2.20: fold probe. The field bug (keyboard open, newest line half
    // buried under the composer) is only distinguishable from a stale pin by
    // measuring whether the list's bottom edge actually sits above the
    // composer's top edge. fold = listBottomY - composerTopY; positive means
    // the list extends UNDER the composer (buried content), and at a correct
    // pin the newest line ends contentPadding above that bottom edge.
    var composerTopY by remember { mutableFloatStateOf(-1f) }
    var listBottomY by remember { mutableFloatStateOf(-1f) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Message actions (official long-press menu, v0.3.0) ──
    // The official app opens a floating action card on long-press; every row is
    // functional here (Copy/Select/Share client-side, the rest gateway RPCs).
    var actionTarget by remember { mutableStateOf<MessageActionTarget?>(null) }
    var selectSheetText by remember { mutableStateOf<String?>(null) }
    var reactionMessageId by remember { mutableStateOf<String?>(null) }
    var branchTarget by remember { mutableStateOf<Pair<UiMessage, Int>?>(null) }
    var undoConfirmOpen by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<UiMessage?>(null) }
    val onToggleReaction: (String, String) -> Unit = remember(viewModel) {
        { messageId, emoji -> viewModel.toggleReaction(messageId, emoji) }
    }
    val onAddReaction: (String) -> Unit = remember {
        { messageId -> reactionMessageId = messageId }
    }
    val onMessageLongPress: (UiMessage, androidx.compose.ui.geometry.Rect) -> Unit = remember(viewModel) {
        { message, bounds ->
            val lastUserId = viewModel.uiState.messages.lastOrNull { it.role == "user" }?.id
            actionTarget = MessageActionTarget(
                message = message,
                bounds = bounds,
                isLastUserTurn = message.role == "user" && message.id == lastUserId,
            )
        }
    }

    // v0.1.96: tool-call visibility toggle — persisted; flipped from the top
    // bar or Settings. Off hides tool-call boxes/rows (thinking + response stay).
    val settingsRepo = remember { SettingsRepository(context.applicationContext) }
    // v0.2.10: Filters ▸ Show ▸ Unread — opening a chat marks it read.
    // v0.2.12: also on the SERVER (official parity): the gateway stamps a last_read_at
    // watermark; the home dot comes from its `unread` flag, so the local map alone
    // leaves the dot on every other client.
    LaunchedEffect(sessionId) {
        if (sessionId.isNotBlank()) {
            runCatching { settingsRepo.markSessionRead(sessionId) }
            runCatching {
                com.gravol.hermesclone.core.network.DashboardApiClient.apiSend(
                    "/api/sessions/${java.net.URLEncoder.encode(sessionId, "UTF-8")}",
                    "PATCH", "{\"unread\": false}",
                )
            }
        }
    }
    val showToolCalls by settingsRepo.showToolCalls.collectAsState(initial = true)
    // v0.1.97: thinking visibility toggle — same pattern (tools + response stay).
    val showThinking by settingsRepo.showThinking.collectAsState(initial = true)
    // v0.5.4: spoken assistant replies toggle (Settings > Phone Assistant).
    val voiceRepliesOn by settingsRepo.voiceRepliesEnabled.collectAsState(initial = false)

    // v0.1.102: live tokens/sec readout while streaming. OpenAI-compatible APIs
    // don't stream per-delta token counts, so this estimates from text flow:
    // chars/sec ÷ ~4 chars/token. Works for cloud (DeepSeek) AND local models
    // (Ollama Qwen) — both arrive as plain message.delta text. The exact turn
    // average (real token counts, incl. Ollama eval stats) renders in the
    // message footer at completion.
    // v0.1.104: always visible while streaming — dimmed at 0 (e.g. during the
    // first-token / context-ingestion wait on a local model) so it's obviously
    // live instead of looking missing; EMA-smoothed for a steady readout.
    // v0.1.107: separate THINKING speed (from thinkingText deltas) shown beside
    // the "Live activity" label while reasoning flows.
    // v0.1.110: each tick re-reads viewModel.uiState (not the captured `state`)
    // so the meter sees growing content — the old `state` snapshot was stale
    // inside the LaunchedEffect coroutine, always returning the initial empty
    // message → len−lastLen = 0 → tok/s stuck at 0.
    var streamPhase by remember { mutableStateOf("thinking") } // "thinking" | "decode"
    var streamTokPerSec by remember { mutableStateOf(0f) }
    var streamThinkingTokPerSec by remember { mutableStateOf(0f) }
    // V4 official parity: finished turns collapse to a one-line summary row;
    // this tracks which collapsed rows the user tapped open (msg id → expanded).
    val expandedTurns = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(state.isStreaming) {
        if (!state.isStreaming) {
            streamTokPerSec = 0f
            streamThinkingTokPerSec = 0f
            streamPhase = "thinking"
            return@LaunchedEffect
        }
        streamPhase = "thinking"
        var lastLen = viewModel.uiState.messages.lastOrNull { it.isStreaming }?.content?.length ?: 0
        var lastThinkLen = viewModel.uiState.messages.lastOrNull { it.isStreaming }?.thinkingText?.length ?: 0
        var lastTime = System.currentTimeMillis()
        var firstSample = true
        var firstThinkSample = true
        while (true) {
            delay(1000)
            val currentUi = viewModel.uiState
            val streamingMsg = currentUi.messages.lastOrNull { it.isStreaming } ?: break
            val now = System.currentTimeMillis()
            val len = streamingMsg.content.length
            val thinkLen = streamingMsg.thinkingText?.length ?: 0
            val dtSec = (now - lastTime) / 1000f
            if (dtSec > 0f) {
                val sample = ((len - lastLen) / dtSec) / 4f
                streamTokPerSec = if (firstSample) {
                    sample
                } else {
                    streamTokPerSec * 0.6f + sample * 0.4f  // EMA smoothing
                }
                firstSample = false
                val thinkSample = ((thinkLen - lastThinkLen) / dtSec) / 4f
                streamThinkingTokPerSec = if (firstThinkSample) {
                    thinkSample
                } else {
                    streamThinkingTokPerSec * 0.6f + thinkSample * 0.4f
                }
                firstThinkSample = false
                streamPhase = when {
                    thinkSample > 0.3f -> "thinking"
                    sample > 0.3f -> "decode"
                    else -> streamPhase
                }
            }
            lastLen = len
            lastThinkLen = thinkLen
            lastTime = now
        }
    }

    // ── Photo attach state (v0.7.0: multi-image) ──
    // Render thumbnails from the already-decoded Bitmaps (not content URIs).
    // The picker-provided URI is ephemeral — the backing file can be replaced or its
    // read permission revoked between pickup and a Compose re-compose, and passing it
    // straight to Coil makes Coil re-read a now-dead URI on every recomposition -> crash.
    var pendingImages by remember { mutableStateOf<List<Pair<Bitmap, String>>>(emptyList()) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(8),
    ) { uris ->
        if (uris.isNotEmpty()) {
            val added = uris.mapNotNull { uri ->
                downscaleAndDecode(context, uri)?.let { it to bitmapToDataUrl(it) }
            }
            if (added.isNotEmpty()) {
                pendingImages = (pendingImages + added).take(8)
            } else {
                // v0.2.3: exotic formats / picker URIs the bitmap decoder
                // rejects (HEIC on some ROMs, GrapheneOS document URIs) —
                // ship the RAW bytes through the file channel instead; the
                // gateway resizes and decodes images server-side anyway.
                var sent = 0
                uris.forEach { uri ->
                    try {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null && bytes.isNotEmpty() && bytes.size <= 12_000_000) {
                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                            // Gateway sniffs the extension from the filename hint —
                            // keep it .jpg so exotic mimes (HEIC) pass the allowlist;
                            // the vision pipeline re-encodes server-side.
                            viewModel.sendMessageWithImages(
                                "",
                                listOf("data:$mime;base64,$b64"),
                            )
                            sent++
                        }
                    } catch (_: Exception) {
                    }
                }
                if (sent == 0) {
                    Toast.makeText(context, "Couldn't read image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Attach chooser (v0.7.0: official-app style menu) ──
    var showAttachSheet by remember { mutableStateOf(false) }
    var showDrawingDialog by remember { mutableStateOf(false) }
    // ── Background work (v0.7.0) + activity chips (v0.8.0) ──
    var showBgSheet by remember { mutableStateOf(false) }
    var showTodoSheet by remember { mutableStateOf(false) }
    val (bgProcs, runningChildren, bgRefresh) = rememberBgTasks(sessionId, enabled = true)
    val bgRunning = bgProcs.count { it.running }
    val openTodos = state.todos.count { !it.isDone }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        if (ok) {
            val uri = pendingCameraUri
            if (uri != null) {
                val bmp = downscaleAndDecode(context, uri)
                if (bmp != null) {
                    pendingImages = pendingImages + (bmp to bitmapToDataUrl(bmp))
                } else {
                    Toast.makeText(context, "Couldn't read photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
        pendingCameraUri = null
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        Toast.makeText(context, "Couldn't read file", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val name = queryFileName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    viewModel.sendMessageWithFile("", b64, mime, name)
                } catch (e: Exception) {
                    Toast.makeText(context, "File attach failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Voice message state ──
    var isRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    // v0.1.125: recorded voice staged for the user to Delete or Send instead of
    // auto-transcribing straight into the composer.
    var pendingVoiceB64 by remember { mutableStateOf<String?>(null) }
    var pendingVoiceDurSec by remember { mutableIntStateOf(0) }

    // v0.1.896 (audit Phase 5): release the microphone when this screen leaves.
    // The recorder was only ever released inside stopRecording(), so navigating away mid-recording —
    // back out of the chat, opening the drawer, a notification taking you elsewhere — left a dead
    // screen holding the mic: the system privacy indicator stayed on and other apps could not record.
    // rememberUpdatedState so onDispose sees the recorder that exists at teardown, not the one from
    // first composition (a plain capture would be null forever).
    val recorderAtTeardown by rememberUpdatedState(recorder)
    DisposableEffect(Unit) {
        onDispose {
            recorderAtTeardown?.let { rec ->
                runCatching { rec.stop() }   // stop() throws for a very short recording; release() is what matters
                rec.release()
            }
        }
    }

    fun startRecording() {
        try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.webm")
            val rec = MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.WEBM)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            recordingFile = file
            recordingSeconds = 0
            isRecording = true
        } catch (e: Exception) {
            Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    fun stopRecording() {
        val rec = recorder ?: return
        val file = recordingFile ?: return
        try {
            runCatching { rec.stop() }
        } catch (_: Exception) { /* short recording */ }
        rec.release()
        recorder = null
        recordingFile = null
        isRecording = false
        scope.launch {
            try {
                val bytes = file.readBytes()
                if (bytes.isEmpty()) {
                    Toast.makeText(context, "No audio recorded", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                // Stage the recording — the user picks Delete or Send.
                pendingVoiceB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                pendingVoiceDurSec = recordingSeconds
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to capture recording: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                file.delete()
            }
        }
    }

    fun sendPendingVoice() {
        val b64 = pendingVoiceB64 ?: return
        scope.launch {
            isTranscribing = true
            try {
                val dataUrl = "data:audio/webm;base64,$b64"
                when (val r = DashboardApiClient.transcribeAudio(dataUrl, "audio/webm")) {
                    is NetworkResult.Success -> {
                        val transcript = r.data.transcript.orEmpty().trim()
                        if (transcript.isBlank()) {
                            Toast.makeText(context, "No speech detected", Toast.LENGTH_SHORT).show()
                        } else {
                            pendingVoiceB64 = null
                            pendingVoiceDurSec = 0
                            viewModel.sendMessage(transcript)
                        }
                    }
                    is NetworkResult.HttpError -> {
                        Toast.makeText(
                            context,
                            "Transcription failed (${r.code})",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(
                            context,
                            "Transcription failed: ${r.exception.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Transcription failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isTranscribing = false
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, "Microphone permission needed for voice messages", Toast.LENGTH_LONG).show()
    }

    // Recording elapsed timer
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            recordingSeconds++
        }
    }

    // v0.5.4: phone-assistant launch — when the system assistant (VoiceInteraction
    // session) opens Hermex, arm the mic immediately. Keyed on AppState.voiceTick
    // so warm re-launches (screen already composed, activity merely reordered to
    // front) fire too; the tick is the only signal composition can observe.
    val voiceTick by AppState.voiceTick.collectAsState()
    LaunchedEffect(voiceTick) {
        if (voiceTick > 0 && AppState.consumePendingVoiceStart()) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startRecording()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // v0.5.4: spoken replies — with Settings > Phone Assistant > Speak replies on,
    // the final assistant text of a completed turn is synthesized server-side
    // (/api/audio/speak) and played here. Silence on failure: the text answer is
    // already on screen.
    var wasStreaming by remember { mutableStateOf(false) }
    var lastSpokenMsgId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.isStreaming) {
        Log.i("HermexTTS", "edge wasStreaming=$wasStreaming now=${state.isStreaming} " +
            "voiceReplies=$voiceRepliesOn msgs=${state.messages.size}")
        if (wasStreaming && !state.isStreaming && voiceRepliesOn) {
            val last = state.messages.lastOrNull { it.role == "assistant" && !it.isCommandAck }
            if (last != null && last.id != lastSpokenMsgId && last.content.isNotBlank()) {
                lastSpokenMsgId = last.id
                scope.launch {
                    try {
                        val r = DashboardApiClient.speakAudio(last.content)
                        if (r is NetworkResult.Success) {
                            Log.i("HermexTTS", "speak ok mime=${r.data.mime_type} len=${r.data.data_url?.length}")
                            r.data.data_url?.let { TtsPlayback.play(context, it) }
                        } else {
                            val detail = when (r) {
                                is NetworkResult.HttpError -> "HTTP ${r.code}: ${r.message.take(160)}"
                                is NetworkResult.Error -> "err: ${r.exception}"
                                else -> "$r"
                            }
                            Log.w("HermexTTS", "speak failed: $detail")
                        }
                    } catch (e: Exception) {
                        Log.w("HermexTTS", "speak threw: ${e.message}", e)
                    }
                }
            }
        }
        wasStreaming = state.isStreaming
    }

    fun sendComposer() {
        val text = composerText
        val imgs = pendingImages
        if (text.isBlank() && imgs.isEmpty()) return
        val editing = editingMessage
        if (editing != null) {
            // Official "Edit & resend": rewind to just before the edited message
            // and submit the new text as the next turn.
            if (text.isNotBlank()) viewModel.rewindAndResubmit(editing.id, text)
            editingMessage = null
            composerText = ""
            pendingImages = emptyList()
            return
        }
        if (imgs.isNotEmpty()) {
            viewModel.sendMessageWithImages(text, imgs.map { it.second })
        } else {
            viewModel.sendMessage(text)
        }
        composerText = ""
        pendingImages = emptyList()
    }

    // ── Slash-command completions (v0.1.65) ──
    var composerFocused by remember { mutableStateOf(false) }
    var slashItems by remember { mutableStateOf<List<JsonRpcClient.SlashItem>?>(null) }

    // ── Model picker (v0.1.88) ──
    var showModelPicker by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    var showSessionDetails by remember { mutableStateOf(false) }
    // v0.5.9: Children sheet — live delegated children of this session.
    var showChildren by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf<UiMessage?>(null) }
    // v0.2.6: STABLE per-screen callbacks. MessageBubble used to receive fresh
    // inline lambdas every recomposition, which broke Compose's skip: every
    // visible bubble (full Markdown subtrees) recomposed on every 9Hz stream
    // flush. Id-based hoisted lambdas keep params referentially equal, so only
    // the streaming bubble recomposes.
    val onReplyById: (String) -> Unit = remember {
        { id: String -> replyTarget = viewModel.uiState.messages.firstOrNull { it.id == id } }
    }

    // ── Context details panel (WebUI): tap the ring to open a details card
    //    with tokens in/out, cost, and a compress affordance. ──
    var showCtxPanel by remember { mutableStateOf(false) }
    val ctxUsage = state.messages.lastOrNull { it.usage != null }?.usage

    LaunchedEffect(composerText, composerFocused, state.isStreaming) {
        val text = composerText
        // v0.1.99: completions work mid-turn too — /stop, /steer, /queue etc.
        // are first-class while a turn streams (v0.1.92).
        if (composerFocused && text.startsWith("/")) {
            delay(150)  // debounce while typing
            slashItems = runCatching { viewModel.completeSlash(text) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
        } else {
            slashItems = null
        }
    }

    // Track whether user has manually scrolled away from the bottom
    var userScrolledUp by remember { mutableStateOf(false) }
    // v0.2.19: the last item's measured size on the previous layout emission.
    // A SHRINKING last item moves its own bottom up the screen with no scroll
    // involved — that is exactly what the streaming tail window does when an
    // answer crosses 2500 chars (the bubble re-renders as "…"+last 2000 chars
    // and gets ~390px shorter). Field 2026-11-08 (t3-away run): that shrink
    // walked the item bottom through the slack window on transition frames and
    // cleared userScrolledUp under a reader ~1800px up the message, so the
    // turn-end settle then pulled them to the newest text. Growth cannot cause
    // this (it only moves the bottom DOWN, away from the window), so only a
    // shrink blocks the clear; a real swipe back still clears on the first
    // frame where the bottom is in view and the size is steady or growing.
    var prevLastItemSize by remember { mutableStateOf(-1) }

    // Reset the "user is reading" flag once the transcript's bottom is back in
    // view. v0.2.18: judged from the LAST ITEM's bottom, not canScrollForward —
    // the 16dp bottom contentPadding keeps canScrollForward true even when the
    // newest line is already on screen, so the old check only cleared after the
    // user dragged into the padding (following could look stuck).
    LaunchedEffect(listState) {
        snapshotFlow {
            val li = listState.layoutInfo
            val last = li.visibleItemsInfo.lastOrNull()
            listOf(
                last?.index ?: -1,
                last?.let { it.offset + it.size } ?: -1,
                li.totalItemsCount,
                last?.size ?: -1,
            )
        }
            .distinctUntilChanged()
            .collect { v ->
                val lastIdx = v[0]
                val lastBottom = v[1]
                val total = v[2]
                val lastSize = v[3]
                val prevSize = prevLastItemSize
                prevLastItemSize = lastSize
                val shrank = prevSize >= 0 && lastSize in 1 until prevSize
                if (!userScrolledUp || lastIdx < 0 || total == 0) return@collect
                val viewport = listState.layoutInfo.viewportEndOffset
                // Two-sided window: the newest item's bottom must actually BE at
                // the viewport bottom. `lastBottom >= viewport - slack` alone is
                // true halfway up a long message (the item's bottom is far below
                // the fold), which cleared the flag on every drag frame.
                val inWindow = lastIdx == total - 1 && (
                    kotlin.math.abs(lastBottom - viewport) <= AT_BOTTOM_SLACK_PX ||
                        !listState.canScrollForward
                    )
                if (inWindow && !shrank) {
                    userScrolledUp = false
                    DebugLog.log("SCROLL", "DragDetect",
                        "userScrolledUp=false (bottom in view: item=$lastIdx " +
                        "bottom=$lastBottom viewport=$viewport)")
                } else if (inWindow && shrank) {
                    DebugLog.log("SCROLL", "DragDetect",
                        "kept userScrolledUp: last item shrank $prevSize->$lastSize " +
                        "(streaming tail window) bottom=$lastBottom viewport=$viewport")
                }
            }
    }

    // ── DIAG (scroll-jump investigation 2026-11-08) ──────────────────────────
    // Samples the viewport while streaming or while the reader is away;
    // unconditional per-frame journaling itself caused scroll jank.
    // A completed turn at the bottom is no longer traced every frame; use a
    // targeted diagnostic build if that transition needs frame-exact capture.
    LaunchedEffect(listState) {
        var lastTraceMs = 0L
        snapshotFlow {
            val li = listState.layoutInfo
            val first = li.visibleItemsInfo.firstOrNull()
            val last = li.visibleItemsInfo.lastOrNull()
            listOf(
                first?.index ?: -1,
                listState.firstVisibleItemScrollOffset,
                last?.index ?: -1,
                last?.let { it.offset + it.size } ?: -1,
                li.viewportSize.height,
                last?.size ?: -1,
            )
        }
            .distinctUntilChanged()
            .collect { v ->
                val s = viewModel.uiState
                // This diagnostic used to format/redact/journal every scroll frame,
                // including idle conversations. Keep a bounded trace during a
                // stream or reader-away investigation, not on every fling frame.
                if (!s.isStreaming && !userScrolledUp) return@collect
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastTraceMs < 250L) return@collect
                lastTraceMs = now
                DebugLog.log("TRACE", "ScrollPos",
                    "first=${v[0]} offset=${v[1]} last=${v[2]} lastBottom=${v[3]} " +
                    "viewport=${v[4]} lastSize=${v[5]} canFwd=${listState.canScrollForward} " +
                    "canBack=${listState.canScrollBackward} " +
                    "streaming=${s.isStreaming} userScrolledUp=$userScrolledUp " +
                    "msgs=${s.messages.size} lastLen=${s.messages.lastOrNull()?.content?.length ?: 0}")
            }
    }

    // ─── Debug: message count tracking ───
    var prevMessageCount by remember { mutableStateOf(0) }
    LaunchedEffect(state.messages.size) {
        val newCount = state.messages.size
        if (newCount != prevMessageCount) {
            DebugLog.log("UI", "MsgCount",
                "changed: $prevMessageCount → $newCount " +
                "(delta=${newCount - prevMessageCount}) " +
                "isStreaming=${state.isStreaming}")
            prevMessageCount = newCount
        }
    }

    // ─── One-shot: scroll to bottom on initial message load ───
    // Uses isNotEmpty() as key — fires exactly once when messages first arrive,
    // does NOT re-fire on subsequent sends (key stays true).
    LaunchedEffect(state.messages.isNotEmpty()) {
        if (state.messages.isNotEmpty()) {
            userScrolledUp = false
            autoScrollToBottom(
                listState = listState,
                targetIndex = state.messages.lastIndex,
                totalItems = state.messages.size,
                scrollGeneration = 0L,
                reason = "SessionOpen",
            )
            // v0.1.101: settle pass — the loaded conversation's final layout
            // (markdown parse, usage footer, bubble borders) lands a frame or two
            // AFTER the messages arrive, so the first scrollToItem clamps to the
            // pre-settle max scroll and the last message's bottom outline ends up
            // a few px behind the composer ("99%" visible). Wait a frame and
            // re-scroll to the true bottom until canScrollForward clears (same
            // idea as the v0.1.95 StreamEnd settle, looped for multi-frame
            // settles). No-op when the first scroll already landed exactly.
            repeat(3) { pass ->
                withFrameNanos { }
                if (!userScrolledUp && state.messages.isNotEmpty() && listState.canScrollForward) {
                    autoScrollToBottom(
                        listState = listState,
                        targetIndex = state.messages.lastIndex,
                        totalItems = state.messages.size,
                        scrollGeneration = 0L,
                        reason = "SessionOpenSettle$pass",
                    )
                }
            }
        }
    }

    // ── Streaming frame monitor ──
    // v0.1.82: Choreographer jank probe. While streaming, samples frame
    // skew every 2s; logs once per 5s window with the worst frame + count
    // of frames >100ms. THIS is the "phone slows down" proof — main-thread
    // saturation shows up as dropped frames, so an export after a slow run
    // says exactly when the UI thread choked (and whether it choked).
    LaunchedEffect(state.isStreaming) {
        if (!state.isStreaming) return@LaunchedEffect
        val choreo = android.view.Choreographer.getInstance()
        var frames = 0
        var worstMs = 0f
        var over100 = 0
        var windowStart = System.currentTimeMillis()
        var lastFrameNanos = 0L
        val cb = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                scrollDiagVsyncs.incrementAndGet()
                if (lastFrameNanos != 0L) {
                    val skewMs = (frameTimeNanos - lastFrameNanos) / 1_000_000f
                    frames++
                    if (skewMs > worstMs) worstMs = skewMs
                    // A frame with >100ms skew is visible jank (~6fps+ gap).
                    if (skewMs > 100f) over100++
                }
                lastFrameNanos = frameTimeNanos
                choreo.postFrameCallback(this)
            }
        }
        choreo.postFrameCallback(cb)
        // Unsubscribe when the effect dies: FrameCallback is one-shot, the
        // repost above keeps it alive while this coroutine owns it.
        try {
            while (true) {
                delay(2000)
                val elapsedS = (System.currentTimeMillis() - windowStart) / 1000f
                if (elapsedS >= 5f) {
                    if (frames > 0) {
                        DebugLog.log("SSE", "Perf",
                            "stream fps=${"%.1f".format(frames / elapsedS)} worst=${"%.0f".format(worstMs)}ms over100ms=$over100/$frames")
                    }
                    frames = 0; worstMs = 0f; over100 = 0; windowStart = System.currentTimeMillis()
                }
            }
        } finally {
            choreo.removeFrameCallback(cb)
        }
    }

    // ── Streaming auto-scroll: state-change driven (replaces 100ms poll) ───
    // Single source of truth for auto-scroll during streaming.
    // snapshotFlow + distinctUntilChanged fires ONLY when the last message's
    // visible content actually changes (text growth, thinking growth, new
    // message, tool card), instead of waking every 100ms — kills the no-op
    // wake-ups during thinking.
    // CRITICAL: read state via viewModel.uiState (the MutableState getter), NOT
    // the captured `state` val — a plain field read on the captured instance
    // registers no snapshot read, so snapshotFlow emits exactly once and never
    // re-fires (v0.1.44 regression: stream only scrolled at placeholder
    // creation, viewport never tracked the growing bubble).
    // The collect block runs to completion per emission in one coroutine; it is
    // NOT cancelled by rapid state writes (unlike the old LaunchedEffect keyed
    // on scrollGeneration), so the two-step scrollToItem+scrollBy compensation
    // still never gets interrupted mid-flight.
    // Respects manual scrolling: skips when userScrolledUp=true, resumes
    // automatically when user returns to bottom (userScrolledUp→false).
    // Gated on message presence (not isStreaming) so a session resumed while
    // the assistant is mid-response still tracks the stream.
    LaunchedEffect(state.isStreaming, state.messages.isNotEmpty()) {
        if (state.messages.isNotEmpty()) {
            DebugLog.log("SCROLL", "StreamLoop", "started (messages=${state.messages.size})")
            snapshotFlow {
                val s = viewModel.uiState
                val last = s.messages.last()
                // v0.8.0: thinking growth is now visible inline — the list must
                // follow it too (the docked panel used to own that scroll).
                listOf(
                    s.messages.size,
                    last.content.length,
                    last.toolCalls.size,
                    last.thinkingText?.length ?: 0,
                )
            }
                .distinctUntilChanged()
                .collect {
                    val s = viewModel.uiState
                    if (!userScrolledUp && s.messages.isNotEmpty()) {
                        // A user drag can cancel Compose's scroll mutation. Isolate
                        // that cancellation so it cannot kill the stream collector.
                        kotlinx.coroutines.coroutineScope {
                            launch {
                                autoScrollToBottom(
                                    listState = listState,
                                    targetIndex = s.messages.lastIndex,
                                    totalItems = s.messages.size,
                                    scrollGeneration = s.scrollGeneration,
                                    reason = "StreamLoop",
                                )
                            }.join()
                        }
                        // Turn completion (v0.2.19): the finished bubble swaps its
                        // 2000-char streaming tail for the FULL document here
                        // (MessageBubble shownContent), and that re-parse +
                        // re-measure lands over MANY frames — field journal
                        // 2026-11-08 (baseline-c, same APK): the last item's
                        // measured bottom grew 2639 -> 2717 -> 2810 -> 2915 ->
                        // ... -> 3452px in the ~500ms after the flip. The old
                        // single-frame settle measured the PRE-swap layout, saw
                        // the tail-window's "at bottom" and gave up, so the
                        // viewport kept the old offset while the swap had just
                        // re-anchored ~1900px of new text above it — the reader
                        // saw the transcript jump back up and nothing ever
                        // corrected it (the follow key is content length, which
                        // never changes again).
                        // Watch the finished layout until it stops moving.
                        if (!s.isStreaming) {
                            var lastSeenSize = -1
                            var calmFrames = 0
                            var settleLogged = false
                            for (settlePass in 0 until TURN_END_SETTLE_FRAMES) {
                                withFrameNanos { }
                                // Never follow a reader who scrolled back, and
                                // never fight a gesture that is in flight.
                                if (userScrolledUp || listState.isScrollInProgress) {
                                    DebugLog.log("SCROLL", "StreamEndSettle",
                                        "bailed after $settlePass frames " +
                                        "(userScrolledUp=$userScrolledUp " +
                                        "scrollInProgress=${listState.isScrollInProgress})")
                                    settleLogged = true
                                    break
                                }
                                val settleInfo = listState.layoutInfo
                                val settleLast = settleInfo.visibleItemsInfo.lastOrNull()
                                    ?: break
                                val settleSize = settleLast.size
                                val settleBottom = settleLast.offset + settleSize
                                val settleViewport = settleInfo.viewportEndOffset
                                // Flush means the item's bottom is AT the fold: no
                                // overflow (a positive overflow clips the last line
                                // behind the composer edge — field t1 run left 34px
                                // of "57 - The recipe called for thyme" cut off) and
                                // no gap wider than the slack window.
                                val flush = settleBottom <= settleViewport &&
                                    settleBottom >= settleViewport - AT_BOTTOM_SLACK_PX
                                calmFrames =
                                    if (settleSize == lastSeenSize && flush) calmFrames + 1 else 0
                                lastSeenSize = settleSize
                                if (calmFrames >= TURN_END_SETTLE_CALM_FRAMES) {
                                    DebugLog.log("SCROLL", "StreamEndSettle",
                                        "settled after $settlePass frames: " +
                                        "item[${settleLast.index}] size=$settleSize " +
                                        "bottom=$settleBottom viewport=$settleViewport")
                                    settleLogged = true
                                    break
                                }
                                // Only touch the list when it is actually off the
                                // bottom; a calm, flush layout logs nothing.
                                if (!flush) {
                                    autoScrollToBottom(
                                        listState = listState,
                                        targetIndex = s.messages.lastIndex,
                                        totalItems = s.messages.size,
                                        scrollGeneration = s.scrollGeneration,
                                        reason = "StreamEndSettle",
                                    )
                                }
                            }
                            if (!settleLogged) {
                                val expireInfo = listState.layoutInfo
                                val expireLast = expireInfo.visibleItemsInfo.lastOrNull()
                                DebugLog.log("SCROLL", "StreamEndSettle",
                                    "window expired after $TURN_END_SETTLE_FRAMES frames: " +
                                    "lastSize=$lastSeenSize " +
                                    "bottom=${expireLast?.let { it.offset + it.size } ?: -1} " +
                                    "viewport=${expireInfo.viewportSize.height}")
                            }
                        }
                    }
                }
            DebugLog.log("SCROLL", "StreamLoop", "ended (isStreaming=${state.isStreaming} messages=${state.messages.size})")
        }
    }

    // Detect keyboard open/close for scroll.
    // Read WindowInsets.ime BEFORE Scaffold.imePadding() consumes it.
    // v0.2.18: two gates, both from field evidence (2026-11-08, v0.2.17-test):
    //  - a REAL keyboard only. The IME reports 1-2px while it animates, and the
    //    old effect scrolled on every one of those steps (four auto-scrolls in
    //    250ms at 01:34:34.9–35.16);
    //  - never while the reader is scrolled back. That same session shows the
    //    effect pulling the transcript to the bottom under a reader who had
    //    scrolled up (userScrolledUp=true at 01:34:07, cleared without a gesture
    //    at 01:34:35.19). Scrolling away from what someone is reading is the bug.
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val sysBottom = WindowInsets.systemBars.getBottom(density)
    val sysTop = WindowInsets.systemBars.getTop(density)
    val keyboardOpen = imeBottom > 0
    var wasKeyboardOpen by remember { mutableStateOf<Boolean?>(null) }
    // Last IME height this effect acted on: the animation's 1-2px steps must not
    // each trigger a scroll; only a real open/resize (>= 40px) does.
    var handledImeHeight by remember { mutableStateOf(0) }
    LaunchedEffect(imeBottom) {
        val prevFirstVisible = listState.firstVisibleItemIndex
        val prevTotalItems = state.messages.size
        val prevViewportHeight = listState.layoutInfo.viewportSize.height
        val isOpen = imeBottom > 0
        val justOpened = isOpen && wasKeyboardOpen != true
        val justClosed = !isOpen && wasKeyboardOpen == true
        wasKeyboardOpen = isOpen

        // Log only on state transitions, not every intermediate frame
        if (justOpened || justClosed) {
            DebugLog.log("UI", "Keyboard",
                "event=${if (isOpen) "OPEN" else "CLOSE"} " +
                "imeHeight=${imeBottom}px messages=$prevTotalItems " +
                "firstVisibleBefore=$prevFirstVisible " +
                "viewportHeightBefore=$prevViewportHeight")
        }

        // Follow the keyboard only when it is a real keyboard (not the 1-2px
        // blips the IME reports while animating), it actually moved, and the
        // reader is at the bottom. Never while they have scrolled back.
        if (isOpen) {
            // DIAG (scroll-jump investigation): record any attempt to move a
            // reader who had scrolled away.
            if (userScrolledUp) {
                DebugLog.log("SCROLL", "Keyboard",
                    "SKIP: keyboard scroll suppressed while userScrolledUp=true " +
                    "(ime=${imeBottom}px justOpened=$justOpened)")
            }
        } else {
            handledImeHeight = 0
        }
        val realKeyboard = imeBottom >= MIN_REAL_KEYBOARD_PX
        val movedEnough = kotlin.math.abs(imeBottom - handledImeHeight) >= 40
        if (isOpen && realKeyboard && movedEnough && !userScrolledUp && state.messages.isNotEmpty()) {
            handledImeHeight = imeBottom
            // Log scroll-relevant metrics on first open frame (transition only)
            if (justOpened) {
                DebugLog.log("UI", "Keyboard",
                    "keyboard open details: ime=${imeBottom}px " +
                    "sysBottom=${sysBottom}px sysTop=${sysTop}px " +
                    "density=${density.density}")
            }

            // Wait for keyboard animation + layout to settle.
            // Uses frame-based waits (not a fixed delay) so we re-check
            // after Compose processes the IME-driven layout pass.
            var prevHeight = listState.layoutInfo.viewportSize.height
            repeat(3) { attempt ->
                withFrameNanos { }
                val currentHeight = listState.layoutInfo.viewportSize.height
                if (currentHeight != prevHeight) {
                    if (justOpened) {
                        DebugLog.log("UI", "Keyboard",
                            "frame $attempt: viewportHeight changed $prevHeight→$currentHeight (waiting for settle)")
                    }
                    prevHeight = currentHeight
                }
            }

            // Log viewport state after keyboard settles, before scroll
            val layoutInfo = listState.layoutInfo
            val firstVis = layoutInfo.visibleItemsInfo.firstOrNull()
            val lastVis = layoutInfo.visibleItemsInfo.lastOrNull()
            val targetIdx = state.messages.lastIndex
            val viewportHeightBefore = layoutInfo.viewportSize.height
            if (justOpened) {
                DebugLog.log("SCROLL", "Keyboard",
                    "reason=keyboard_open ime=${imeBottom}px " +
                    "target=$targetIdx totalItems=${state.messages.size} " +
                    "viewportBefore=[${firstVis?.index}..${lastVis?.index}] " +
                    "viewportHeight=$viewportHeightBefore " +
                    "totalViewportItems=${layoutInfo.visibleItemsInfo.size}")
            }

            autoScrollToBottom(
                listState = listState,
                targetIndex = targetIdx,
                totalItems = state.messages.size,
                scrollGeneration = state.scrollGeneration,
                reason = "Keyboard",
            )
            if (justOpened) {
                val viewportHeightAfter = listState.layoutInfo.viewportSize.height
                val firstVisAfter = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                val lastVisAfter = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                DebugLog.log("SCROLL", "Keyboard",
                    "scroll_result: target=$targetIdx " +
                    "firstVisible=${firstVisAfter?.index} lastVisible=${lastVisAfter?.index} " +
                    "viewportHeight=$viewportHeightAfter→$viewportHeightBefore")
            }
        }
    }

    // v0.2.20: keyboard follow under adjustResize (fixes v0.2.18-test
    // regression: opening the keyboard over an existing chat at the bottom
    // left the newest text hidden behind it).
    //
    // With manifest windowSoftInputMode=adjustResize the OS RESIZES the window
    // for the IME instead of dispatching IME insets, so WindowInsets.ime never
    // grows past 1-2px and the imeBottom effect above can never pass its
    // MIN_REAL_KEYBOARD_PX gate. Field journal (2026-11-08, Pixel 8, FUTO IME,
    // v0.2.18-test): every `Keyboard OPEN` event logged `imeHeight=1..2px`, the
    // viewport animated 1991→980px, and `reason=Keyboard` appears ZERO times in
    // the whole journal — the keyboard scroll never ran.
    //
    // The list viewport itself is the reliable signal: it loses >= 100px
    // exactly when the keyboard takes that space. Act only on a SHRINK (on
    // close the newest text is already visible; don't move the reader), wait
    // for the 1-2px animation steps to settle (debounce), then re-pin ONCE —
    // one follow per transition, which also kills the old v0.2.17 mid-anim
    // scroll spam. The userScrolledUp gate preserves a reader who scrolled
    // away, same as every other follow here.
    LaunchedEffect(listState) {
        var stableViewportH = -1
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .distinctUntilChanged()
            .collect { h ->
                if (stableViewportH < 0) {
                    stableViewportH = h
                    return@collect
                }
                if (h > stableViewportH) {
                    // Viewport grew (keyboard closed / panel undocked):
                    // newest text stayed visible — just track the new height.
                    stableViewportH = h
                    return@collect
                }
                val shrink = stableViewportH - h
                // v0.2.20: the old gate swallowed every shrink under
                // MIN_REAL_KEYBOARD_PX (100px) — but the field clip measures
                // ~one line, i.e. a SUB-keyboard shrink (IME toolbar/suggestion
                // strip, queue-chip row, Android 17 partial resize). Content
                // then sat below the fold until the next StreamLoop pin: the
                // "fixes itself when the next thinking box arrives" signature.
                // Only true animation noise (< 12px) stays filtered.
                if (shrink < MIN_SHRINK_REPIN_PX) return@collect
                // Big shrink in progress: debounce until the height stops
                // moving for a beat, then act once. While the IME animates,
                // each new emission re-arms this delay from the conflate.
                delay(350)
                val nowH = listState.layoutInfo.viewportSize.height
                if (nowH != h) return@collect  // still animating; a later emission re-arms
                val shrunkFrom = stableViewportH
                stableViewportH = nowH
                val s = viewModel.uiState
                val fold = (listBottomY - composerTopY).takeIf { composerTopY >= 0f && listBottomY >= 0f }?.toInt()
                if (!userScrolledUp && s.messages.isNotEmpty()) {
                    DebugLog.log("SCROLL", "ViewportShrink",
                        "viewport lost ${shrunkFrom - nowH}px (${shrunkFrom}→$nowH, keyboard resize) -> re-pin " +
                        "fold=${fold?.let { "${it}px" } ?: "?"}")
                    autoScrollToBottom(
                        listState = listState,
                        targetIndex = s.messages.lastIndex,
                        totalItems = s.messages.size,
                        scrollGeneration = s.scrollGeneration,
                        reason = "ViewportShrink",
                    )
                    // Post-pin fold: >0 after a full pin means the viewport
                    // bottom itself is under the composer (layout problem),
                    // not the pin (scroll problem).
                    val foldAfter = (listBottomY - composerTopY).takeIf { composerTopY >= 0f && listBottomY >= 0f }?.toInt()
                    DebugLog.log("SCROLL", "ViewportShrink", "post-pin fold=${foldAfter?.let { "${it}px" } ?: "?"}")
                } else {
                    DebugLog.log("SCROLL", "ViewportShrink",
                        "viewport lost ${shrunkFrom - nowH}px but skipped " +
                        "(userScrolledUp=$userScrolledUp messages=${s.messages.size}) " +
                        "fold=${fold?.let { "${it}px" } ?: "?"}")
                }
            }
    }

    // v0.2.5: when the composer grows (multi-line draft, activity chips) the
    // viewport shrinks — re-pin to the newest line so the streaming answer
    // stays above the chat box, exactly like the official app.
    LaunchedEffect(composerBarH) {
        if (composerBarH > 0 && !userScrolledUp && state.messages.isNotEmpty()) {
            withFrameNanos { }
            autoScrollToBottom(
                listState = listState,
                targetIndex = state.messages.lastIndex,
                totalItems = state.messages.size,
                scrollGeneration = state.scrollGeneration,
                reason = "ComposerGrow",
            )
        }
    }

    // Track composer focus separately (for other uses if needed)

    WebuiMenuDrawer(
        drawerState = drawerState,
        onNavigate = onNavigate,
        onOpenSession = onOpenSession,
        onOpenChats = onOpenChats,
    ) {
    if (showAttachSheet) {
        // v0.7.0: official-app style attach menu — Camera / Scan Document /
        // Photo Library / Files / Drawing / Voice Message.
        ModalBottomSheet(onDismissRequest = { showAttachSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                AttachOption(Icons.Outlined.PhotoCamera, "Camera") {
                    showAttachSheet = false
                    pendingCameraUri = createCameraUri(context)
                    pendingCameraUri?.let { cameraPicker.launch(it) }
                }
                AttachOption(Icons.Outlined.Description, "Scan Document") {
                    // v1 routes to the camera (ML Kit document scanner is a
                    // later upgrade); same capture path as Camera.
                    showAttachSheet = false
                    pendingCameraUri = createCameraUri(context)
                    pendingCameraUri?.let { cameraPicker.launch(it) }
                }
                AttachOption(Icons.Outlined.PhotoLibrary, "Photo Library") {
                    showAttachSheet = false
                    try {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    } catch (_: android.content.ActivityNotFoundException) {
                        // v0.2.3: no system photo picker (GrapheneOS et al.) —
                        // fall back to the Storage Access Framework document UI.
                        filePicker.launch(arrayOf("image/*"))
                    }
                }
                AttachOption(Icons.Outlined.Folder, "Files") {
                    showAttachSheet = false
                    filePicker.launch(arrayOf("*/*"))
                }
                AttachOption(Icons.Outlined.Edit, "Drawing") {
                    showAttachSheet = false
                    showDrawingDialog = true
                }
                AttachOption(Icons.Outlined.Mic, "Voice Message") {
                    showAttachSheet = false
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) startRecording()
                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
    if (showDrawingDialog) {
        DrawingDialog(
            onDismiss = { showDrawingDialog = false },
            onSave = { bmp ->
                showDrawingDialog = false
                pendingImages = pendingImages + (bmp to bitmapToDataUrl(bmp))
            },
        )
    }
    // v0.8.3: hoisted above the Scaffold so both the composer metrics strip
    // (bottomBar) and the floating status pill (content overlay) can read them.
    val displayTokPerSec = state.liveTokPerSec?.takeIf { it > 0f } ?: streamTokPerSec
    val isServerReported = state.liveTokPerSec != null && state.liveTokPerSec!! > 0f
    // Turn start for the status pill timer.
    var turnStartMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.isStreaming) {
        turnStartMs = if (state.isStreaming) System.currentTimeMillis() else null
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Color(0xFF000022),
        bottomBar = {
            Surface(
                shadowElevation = 0.dp,
                color = Color(0xFF000022),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                // v0.2.20: also record the composer's top edge in root coords
                // so scroll diagnostics can state whether the list bottom is
                // above or under it (the fold).
                Column(Modifier.onGloballyPositioned { c ->
                    composerBarH = c.size.height
                    composerTopY = c.positionInRoot().y
                }) {
                    // v0.7.0: animated chip while this session has background
                    // tasks running; tap opens the Background Work sheet.
                    if (bgRunning > 0) {
                        BackgroundTaskChip(count = bgRunning) {
                            bgRefresh()
                            showBgSheet = true
                        }
                    }
                    // v0.8.0: subagents + tasks ride the same pinned pattern.
                    if (showExtendedActivity && runningChildren > 0) {
                        PinnedActivityChip(
                            icon = Icons.Outlined.Groups,
                            text = "Running $runningChildren subagent${if (runningChildren == 1) "" else "s"}",
                            spin = true,
                            onClick = { showChildren = true },
                        )
                    }
                    if (showExtendedActivity && state.todos.isNotEmpty() && openTodos > 0) {
                        PinnedActivityChip(
                            icon = Icons.Outlined.Checklist,
                            text = "Tasks ${state.todos.count { it.isDone }}/${state.todos.size}",
                            spin = false,
                            tint = MaterialTheme.colorScheme.tertiary,
                            onClick = { showTodoSheet = true },
                        )
                    }
                    // v0.8.0: queued messages (official style) — ⚡ + text + ✕.
                    // Held client-side: ✕ is a true cancel, nothing was sent.
                    state.queuedMessages.forEachIndexed { idx, qtext ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = qtext,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel queued message",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.cancelQueued(idx) },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Pending image thumbnails (v0.7.0: multi, removable)
                    if (pendingImages.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 8.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                pendingImages.forEachIndexed { idx, (bmp, _) ->
                                    Box {
                                        AsyncImage(
                                            model = bmp,
                                            contentDescription = "Attached image ${idx + 1}",
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentScale = ContentScale.Crop,
                                        )
                                        // Per-thumbnail remove
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(Color.Black.copy(alpha = 0.65f))
                                                .clickable {
                                                    pendingImages = pendingImages.filterIndexed { i, _ -> i != idx }
                                                },
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove image ${idx + 1}",
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .padding(1.dp),
                                                tint = Color.White,
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "${pendingImages.size} attached",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(onClick = { pendingImages = emptyList() }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove all images")
                            }
                        }
                    }
                    // Recording indicator
                    if (isRecording || isTranscribing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isTranscribing) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color(0xFFFF3B30)
                                    }),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isTranscribing -> "Transcribing…"
                                    else -> "Recording %d:%02d".format(recordingSeconds / 60, recordingSeconds % 60)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isTranscribing) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color(0xFFFF3B30)
                                },
                            )
                        }
                    }
                    // Pending voice message — recorded, awaiting Delete or Send (v0.1.125)
                    val pendingVoice = pendingVoiceB64
                    if (pendingVoice != null) {
                        val voiceLabel = "%d:%02d".format(pendingVoiceDurSec / 60, pendingVoiceDurSec % 60)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 8.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Voice message ($voiceLabel)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (isTranscribing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = {
                                    pendingVoiceB64 = null
                                    pendingVoiceDurSec = 0
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete recording")
                                }
                                IconButton(onClick = { sendPendingVoice() }) {
                                    Icon(Icons.Default.Send, contentDescription = "Send voice message")
                                }
                            }
                        }
                    }
                    // Slash-command completions — pop up above the composer.
                    // Capture to a local: LazyColumn DEFERS its content lambda
                    // (runs later inside intervalContentState derivedStateOf),
                    // so a `!!` re-reading the mutable state there would NPE if
                    // the LaunchedEffect nulls slashItems (focus loss, text
                    // edit, suggestion tap) between guard and deferred exec.
                    val slashItemsNow = slashItems
                    if (slashItemsNow != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        ) {
                            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                itemsIndexed(slashItemsNow) { _, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Server completions omit the
                                                // leading "/" (already typed) —
                                                // restore it or the command
                                                // becomes plain text.
                                                composerText = if (item.text.startsWith("/")) {
                                                    item.text
                                                } else {
                                                    "/" + item.text
                                                }
                                                slashItems = null
                                                focusRequester.requestFocus()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = item.display ?: item.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                            ),
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (item.kind == "skill") {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (item.kind == "skill") {
                                            Text(
                                                text = "skill",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        item.meta?.let { meta ->
                                            if (meta.isNotBlank()) {
                                                Text(
                                                    text = meta,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(0.9f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // ── Inline clarify card (v0.1.134): renders the server's
                    // question ABOVE the composer, dsh-style, instead of a modal
                    // popup so you can always read what's being asked. ──
                    val pendingClarify = state.pendingClarify
                    if (pendingClarify != null) {
                        InlineClarifyCard(
                            pendingClarify = pendingClarify,
                            onAnswer = { answer -> viewModel.respondToClarify(answer) },
                        )
                    }
                    val canSend = (composerText.isNotBlank() || pendingImages.isNotEmpty()) && !isTranscribing
                    val activeComposer = composerFocused || composerText.isNotEmpty() || pendingImages.isNotEmpty()
                    val showStop = state.isStreaming && !canSend
                    val attach: @Composable () -> Unit = {
                        ChatCircle(Icons.Filled.Add, "Attach", Color(0xFF14224A)) { showAttachSheet = true }
                    }
                    val microphone: @Composable () -> Unit = {
                        ChatCircle(Icons.Outlined.Mic, "Microphone", Color(0xFF1D2761)) {
                            if (isRecording) stopRecording() else {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (granted) startRecording() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                    val sendOrStop: @Composable () -> Unit = {
                        ChatCircle(if (showStop) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                            if (showStop) "Stop" else "Send", Color(0xFF402B43)) {
                            if (showStop) viewModel.stopStreaming()
                            else if (state.isStreaming) { viewModel.queueMessage(composerText); composerText = "" }
                            else sendComposer()
                        }
                    }
                    val hasSend = canSend || showStop
                    val composerField = @Composable { alignMod: Modifier ->
                        BasicTextField(value = composerText, onValueChange = { composerText = it },
                            modifier = alignMod
                                .focusRequester(focusRequester).onFocusChanged { composerFocused = it.isFocused },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFF2F4FA), fontSize = 17.sp, fontFamily = ChatManrope),
                            cursorBrush = SolidColor(Color(0xFF447CFF)), maxLines = 4,
                            decorationBox = { inner -> Box {
                                if (composerText.isEmpty()) Text(if (state.isStreaming) "Queue a message..." else "Message Hermes...", color = Color(0xFF676571), fontSize = 17.sp, fontFamily = ChatManrope)
                                inner()
                            } })
                    }
                    val modelChip = @Composable {
                        Row(Modifier.clip(CircleShape).clickable { showModelPicker = true }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Psychology, null, tint = Color(0xFFA2A4B0), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(state.currentModel?.let { shortModelName(it) } ?: "Model", color = Color(0xFFA2A4B0), fontSize = 13.sp, maxLines = 1, fontFamily = ChatManrope)
                        }
                    }
                    // Official "EDITING EARLIER MESSAGE" bar above the composer,
                    // shown while a message is being edited (Edit & resend).
                    editingMessage?.let { em ->
                        EditingMessageBar(text = em.content) {
                            editingMessage = null
                            composerText = ""
                        }
                    }
                    // v0.2.5b: ONE stable layout for all composer states (the
                    // 0.2.5 if/else branch-swap destroyed the text-field node
                    // on focus → instant unfocus → keyboard could never open).
                    // Official parity: text row on top, +/model/mic/send row
                    // underneath, always present. Card grows with the draft;
                    // text can never overlap the button row; node identity is
                    // preserved so focus survives every state change.
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(24.dp)).background(Color(0xFF0C1A3C))
                            .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(24.dp)),
                    ) {
                        composerField(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            attach()
                            Spacer(Modifier.width(4.dp))
                            modelChip()
                            Spacer(Modifier.weight(1f))
                            microphone()
                            if (hasSend) {
                                Spacer(Modifier.width(6.dp))
                                sendOrStop()
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        // ── Message actions: floating card + the sheets/dialogs behind it ──
        // (official long-press menu parity; every row is wired to a real action)
        actionTarget?.let { target ->
            val msg = target.message
            MessageActionsPopup(
                target = target,
                timestamp = messageTimestampLabel(msg.timestamp),
                onDismiss = { actionTarget = null },
                onAction = { row ->
                    when (row) {
                        MessageActionRow.COPY -> {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Hermes message", msg.content))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                        MessageActionRow.SELECT -> selectSheetText = msg.content
                        MessageActionRow.SHARE -> {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, msg.content)
                            }
                            context.startActivity(Intent.createChooser(send, "Sharing text"))
                        }
                        MessageActionRow.REACT -> reactionMessageId = msg.id
                        MessageActionRow.BRANCH -> {
                            // The sheet's "Messages carried over" is the real
                            // stored count (branch count = raw-history length).
                            scope.launch {
                                val n = viewModel.branchMessageCount(msg.id)
                                branchTarget = msg to n
                            }
                        }
                        MessageActionRow.EDIT_RESEND -> {
                            editingMessage = msg
                            composerText = msg.content
                        }
                        MessageActionRow.RESTORE_RUN ->
                            viewModel.rewindAndResubmit(msg.id, msg.content)
                        MessageActionRow.UNDO -> undoConfirmOpen = true
                    }
                },
            )
        }
        selectSheetText?.let { text ->
            SelectMessageSheet(text = text) { selectSheetText = null }
        }
        reactionMessageId?.let { id ->
            AddReactionSheet(
                onPick = { emoji -> viewModel.toggleReaction(id, emoji) },
                onDismiss = { reactionMessageId = null },
            )
        }
        branchTarget?.let { (msg, count) ->
            BranchConfirmSheet(
                isUser = msg.role == "user",
                messageText = msg.content,
                carriedCount = count,
                onConfirm = {
                    branchTarget = null
                    viewModel.branchFromMessage(msg.id) { newId ->
                        onOpenSession(SessionSummary(id = newId, title = "Branch"))
                    }
                },
                onDismiss = { branchTarget = null },
            )
        }
        if (undoConfirmOpen) {
            UndoLastTurnDialog(
                onConfirm = {
                    undoConfirmOpen = false
                    viewModel.undoLastTurn()
                },
                onDismiss = { undoConfirmOpen = false },
            )
        }
    Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Error banner for send failures (wrap_content height)
            if (state.error != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.loadMessages() }, modifier = Modifier.size(24.dp)) {
                            Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Agent task list (todo tool state) — pinned above the messages
            if (state.todos.isNotEmpty()) {
                TasksCard(
                    todos = state.todos,
                    expanded = state.todosExpanded,
                    isStreaming = state.isStreaming,
                    onToggle = { viewModel.toggleTodosExpanded() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            // Completed-while-away banner (background turns, v0.1.60)
            if (state.completedWhileAway) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "✓ Turn finished while you were away",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            viewModel.clearCompletedWhileAway()
                            scope.launch {
                                if (state.messages.isNotEmpty()) {
                                    listState.scrollToItem(state.messages.lastIndex)
                                }
                            }
                        }) {
                            Text("View latest")
                        }
                    }
                }
            }
            // v0.8.0: inline live-turn status inputs (used by the streaming
            // message item's LiveTurnStatusLine). v0.1.110: prefer
            // server-reported liveTokPerSec, fall back to the char-count
            // estimate (streamTokPerSec).
            // (tok/s vals hoisted above the Scaffold — v0.8.3)
            when {
                state.isLoading && state.messages.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.messages.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = viewModel::loadMessages) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
            // Official turn grouping: consecutive assistant messages between
            // user messages are ONE turn; >1 tool call total -> single
            // collapsed row at the first tool message, rest suppressed.
            val turnGroupInfo: Map<Int, TurnGroupInfo> = remember(state.messages) {
                val out = HashMap<Int, TurnGroupInfo>()
                val anchorOf = HashMap<Int, Int>()
                val toolMap = HashMap<Int, MutableList<UiToolCall>>()
                var a = -1
                state.messages.forEachIndexed { i, m ->
                    when {
                        m.role == "user" -> a = -1
                        m.role == "assistant" && m.toolCalls.isNotEmpty() -> {
                            if (a == -1) a = i
                            anchorOf[i] = a
                            toolMap.getOrPut(a) { ArrayList() }.addAll(m.toolCalls)
                        }
                    }
                }
                toolMap.forEach { (anchorIdx, tools) ->
                    if (tools.isNotEmpty()) {
                        val streaming = state.messages.listIterator(anchorIdx)
                            .asSequence().takeWhile { it.role != "user" }.any { it.isStreaming }
                        out[anchorIdx] = TurnGroupInfo(true, state.messages[anchorIdx].id, tools.toList(), streaming)
                        anchorOf.filterValues { it == anchorIdx }.keys.filter { it != anchorIdx }
                            .forEach { absorbed -> out[absorbed] = TurnGroupInfo(false, "", emptyList(), false) }
                    }
                }
                out
            }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { c ->
                                listBottomY = c.positionInRoot().y + c.size.height
                            }
                            .nestedScroll(remember {
                                object : NestedScrollConnection {
                                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                        if (source == NestedScrollSource.UserInput && available.y > 0) {
                                            if (!userScrolledUp) {
                                                userScrolledUp = true
                                                DebugLog.log("SCROLL", "DragDetect",
                                                    "userScrolledUp=true (source=$source, " +
                                                    "available.y=${available.y}, " +
                                                    "isScrollInProgress=${listState.isScrollInProgress})")
                                            }
                                        }
                                        return Offset.Zero
                                    }
                                }
                            }),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 118.dp, bottom = 16.dp),
                    ) {
                        // Official chat empty state, measured on Pixel 8 against
                        // official v1.0.0 (2026-11-07) at density 2.625: 48dp
                        // mascot whose ink top sits 181.7dp from the screen top
                        // (median over 13 frames, breathing +-1dp), title 17sp,
                        // subtitle 12sp on a 17sp line advance (official line
                        // advance 17.14dp; the old 19sp was 1.9dp too loose).
                        if (state.messages.isEmpty() && !state.isStreaming) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    MascotAvatar(size = 48.dp)
                                    Spacer(Modifier.height(18.75.dp))
                                    Text(
                                        "What are we working on?",
                                        color = Color(0xFFF2F4FA), // HermesTextPrimary
                                        fontSize = 17.sp,
                                        fontFamily = ChatManrope,
                                    )
                                    Spacer(Modifier.height(7.dp))
                                    Text(
                                        "Ask a question, share an idea, or give your agent something to do.",
                                        color = Color(0xFF8A93B8), // HermesTextSecondary
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        fontFamily = ChatManrope,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 42.dp),
                                    )
                                }
                            }
                        }
                        itemsIndexed(
                            state.messages,
                            key = { _, msg -> msg.id },
                            // v0.2.22 PERF: let LazyColumn recycle only like-for-like
                            // slots; without it a plain bubble can land in a rich
                            // (thinking/tools) slot and pay full re-composition.
                            contentType = { _, msg ->
                                when {
                                    msg.role == "user" -> "user"
                                    msg.priorThinking.isNotEmpty() || msg.toolCalls.isNotEmpty() -> "rich"
                                    else -> "plain"
                                }
                            },
                        ) { index, msg ->
                            val sameSender = index > 0 && state.messages[index - 1].role == msg.role

                            // Live thinking block: shown ABOVE the assistant bubble
                            // while thinking is streaming and no real content has arrived yet.
                            // v0.1.163: the showLiveThinking local was removed — it fed a
                            // LiveThinkingTicker branch that required msg.isStreaming &&
                            // !state.isStreaming, but every Message's isStreaming is assigned
                            // = state.isStreaming (line 1144), so those two states can never
                            // diverge and the branch was provably unreachable. Kept here only as
                            // explanatory history; the inline thinking preview it guarded no
                            // longer exists in this list block.

                            // v0.1.162: removed — during streaming, thinking lives ONLY in
                            // the docked LiveActivityPanel (its own nested LazyColumn). The
                            // main list rendered a truncated inline preview line here too,
                            // so the same text scrolled in two independent places at
                            // different rates ("scroll in two places"). Main-list auto-scroll
                            // now keys on content + tool calls only; thinking scrolls inside
                            // the panel. Cleared once real content/tools arrive (turn ends →
                            // ThinkingScrollBox).

                            // v0.1.163: dead code removed. showLiveThinking requires
                            // msg.isStreaming == true, but line 1144 assigns every Message's
                            // isStreaming = state.isStreaming, so the global never lags the
                            // per-message flag. The condition below therefore required a message
                            // to be streaming while the global state was NOT — impossible by
                            // construction. LiveThinkingTicker only ever fired if those two states
                            // diverged, which they structurally cannot; removed rather than left
                            // as misleading dead code that looks like a real post-stream path.

                            // After the turn, thinking persists as a scrollable
                            // box above the tools+answer (during streaming it
                            // lives in the docked live panel instead). Hidden
                            // when the thinking toggle is off (v0.1.97).
                            // v0.8.0: live turn status (tok/s, thinking speed,
                            // elapsed) as a compact line at the head of the
                            // streaming item — the docked panel's readout,
                            // inline. Replaces the v0.1.64 bottom panel.
                            // v0.8.3: the inline status line moved to the floating
                            // top status pill + the composer metrics strip, matching
                            // the reference layout. Live thinking itself stays inline.
                            // v0.8.1: interleaved timeline — thinking segments
                            // and tool rows render in the order they happened
                            // (segments anchor to the tool that interrupted
                            // them). Rows respect the tool toggle; segments the
                            // thinking toggle.
                            if (msg.role == "assistant" &&
                                showThinking && msg.priorThinking.isNotEmpty()
                            ) {
                                // Thinking segments stay on the timeline; the tool
                                // calls collapse into the official turn-summary row.
                                ToolCallList(
                                    toolCalls = emptyList(),
                                    segments = msg.priorThinking,
                                )
                            }
                            if (msg.role == "assistant" &&
                                showToolCalls && msg.toolCalls.isNotEmpty()
                            ) {
                                // Official groups a WHOLE TURN's tool calls into
                                // one collapsed row ("Ran N commands and made M
                                // tool calls" live / "Worked for X min, Y sec ·
                                // N tools" done) anchored at the first tool
                                // message; later interim messages in the same
                                // turn suppress their own sections.
                                val gi = turnGroupInfo[index]
                                if (gi == null) {
                                    ToolCallList(toolCalls = msg.toolCalls)
                                } else if (gi.anchor) {
                                    val turnExpanded = expandedTurns[gi.key] == true
                                    // v0.2.6: remember-per-key so the row stays
                                    // skippable across stream flushes.
                                    val onToggleStable = remember(gi.key) {
                                        { expandedTurns[gi.key] = expandedTurns[gi.key] != true }
                                    }
                                    TurnSummaryRow(
                                        toolCalls = gi.tools,
                                        streaming = gi.streaming,
                                        expanded = turnExpanded,
                                        onToggle = onToggleStable,
                                    )
                                    if (turnExpanded) ToolCallList(toolCalls = gi.tools)
                                }
                            }
                            // v0.8.0: the CURRENT thinking renders INLINE in the
                            // transcript (expanded THINKING card while streaming,
                            // collapsed THOUGHT after). It sits after the
                            // timeline above because any thinking that PRECEDED
                            // a tool call was anchored there as a segment.
                            if (msg.role == "assistant" && showThinking &&
                                msg.thinkingText?.isNotBlank() == true
                            ) {
                                ThoughtCard(text = msg.thinkingText, streaming = msg.isStreaming)
                            }

                            // Skip the empty bubble for tool-only assistant rows
                            // from history (content blank, not streaming) — the
                            // thinking + tools boxes above already tell the story.
                            val suppressEmptyBubble = msg.isStreaming &&
                                msg.content.isBlank() &&
                                (msg.thinkingText?.isNotBlank() == true || msg.priorThinking.isNotEmpty())
                            if (msg.isCommandAck) {
                                CommandAckLine(text = msg.content)
                            } else if ((msg.content.isNotBlank() || msg.isStreaming) && !suppressEmptyBubble) {
                                MessageBubble(
                                    message = msg,
                                    sameSender = sameSender,
                                    onReply = onReplyById,
                                    onLongPress = onMessageLongPress,
                                    onToggleReaction = onToggleReaction,
                                    onAddReaction = onAddReaction,
                                )
                            }
                        }
                            // v0.2.3: the in-stream Stop pill is GONE — the composer's
                            // stop square is the only stop affordance (official parity).
                            // A stuck streaming state used to leave this pill on every
                            // message, which read as "Stop appears on every send".
                    }
                }
            }

            // v0.8.0: the docked live-activity panel (v0.1.64) is gone —
            // thinking, tool rows, and the tok/s readout now render INLINE in
            // the streaming message item above (see displayTokPerSec above).

        }
            // v0.2.3: official-style top scrim — transcript content fades out
            // under the floating header instead of sliding behind it.
            // Height tuned 2026-11-07: at 170dp the fade reached ~230dp from the
            // top (scrim starts at the edge-to-edge top, so it clears the status
            // bar too) and dimmed the empty-state mascot, which the official keeps
            // at 181dp full brightness. 120dp ends the fade just below the header.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                androidx.compose.ui.graphics.Color(0xFF000022.toInt()),
                                androidx.compose.ui.graphics.Color(0xF2000022.toInt()),
                                androidx.compose.ui.graphics.Color(0x00000022),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {}
            // Official holds the LAST tool caption through post-tool decode (off_toolrun.mp4:
            // "Using terminal… 0:58" while answer text streams), so the caption falls back to
            // the last (completed) tool call; the tok/s chip gate stays on the RUNNING tool.
            val toolMsg = state.messages.lastOrNull { it.isStreaming }
            val captionTool = toolMsg?.toolCalls?.let { tcs ->
                tcs.lastOrNull { !it.completed } ?: tcs.lastOrNull()
            }
            ChatHeader(title = state.sessionTitle, bot = botName, thinking = state.isStreaming,
                phase = streamPhase, tokPerSec = state.liveTokPerSec ?: streamTokPerSec,
                thinkingTokPerSec = streamThinkingTokPerSec,
                toolName = captionTool?.toolName,
                toolPreview = captionTool?.let { tc -> argsDisplay(tc.args) ?: tc.summary ?: tc.preview },
                toolRunning = toolMsg?.toolCalls?.any { !it.completed } == true,
                extraTools = toolMsg?.toolCalls?.count { it.toolName != captionTool?.toolName } ?: 0,
                onSettingsClick = { showChatSettings = true },
                onBack = onBack, onExecution = { showExtendedActivity = !showExtendedActivity },
                onMenuAction = { action ->
                    val sid = sessionId
                    scope.launch {
                        when (action) {
                            "Routines" -> onNavigate("cron")
                            "Artifacts" -> onNavigate("hub-artifacts")
                            "Bot Screen" -> onNavigate("bothub")
                            "Live Mode" -> onNavigate("hub-voice")
                            "Terminal" -> onNavigate("botscreen")
                            "Archive" -> {
                                try { com.gravol.hermesclone.di.AppModule.provideRpcClient().sessionArchive(sid, true); onBack() }
                                catch (e: Exception) { android.widget.Toast.makeText(context, "Archive failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                            "Delete" -> {
                                try { com.gravol.hermesclone.di.AppModule.provideRpcClient().sessionDelete(sid); onBack() }
                                catch (e: Exception) { android.widget.Toast.makeText(context, "Delete failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 6.dp))

            if (showChatSettings) {
                ChatSettingsSheet(
                    title = state.sessionTitle,
                    cwd = state.currentCwd,
                    model = state.currentModel,
                    onRename = { newTitle -> viewModel.renameSession(newTitle) },
                    onOpenModel = { showChatSettings = false; showModelPicker = true },
                    onOpenDetails = { showChatSettings = false; showSessionDetails = true },
                    onDismiss = { showChatSettings = false },
                )
            }

            if (showSessionDetails) {
                SessionDetailsSheet(
                    sessionDbKey = sessionId,
                    model = state.currentModel,
                    provider = state.currentProvider,
                    onDismiss = { showSessionDetails = false },
                )
            }

        }
    }

    // ── Model Picker Sheet (v0.1.88) ──
    if (showModelPicker) {
        ModelPickerSheet(
            viewModel = viewModel,
            onDismiss = { showModelPicker = false },
        )
    }

    // ── Children sheet (v0.5.9): live delegated children of this session ──
    if (showChildren) {
        ChildrenSheet(
            sessionDbKey = sessionId,
            onDismiss = { showChildren = false },
            onWatch = { childSid, goal ->
                val enc = java.net.URLEncoder.encode(goal.ifBlank { "Child" }, "UTF-8")
                onNavigate("chat/$childSid/$enc")
                showChildren = false
            },
        )
    }
    // ── Background work sheet (v0.7.0) ──
    if (showBgSheet) {
        BackgroundWorkSheet(
            procs = bgProcs,
            onKill = { procId ->
                scope.launch {
                    killBgTask(sessionId, procId)
                    bgRefresh()
                }
            },
            onDismiss = { showBgSheet = false },
        )
    }
    // ── Tasks sheet (v0.8.0) ──
    if (showTodoSheet) {
        TodoSheet(todos = state.todos, onDismiss = { showTodoSheet = false })
    }

    // ── Context Details Panel (WebUI): tap the ring to open details + compress ──
    if (showCtxPanel) {
        ContextDetailsPanel(
            used = state.contextUsed,
            max = state.contextMax,
            usage = ctxUsage,
            isStreaming = state.isStreaming,
            onCompress = { viewModel.sendMessage("/compress") },
            onDismiss = { showCtxPanel = false },
        )
    }

    // ── Reply / Copy dialog (v0.1.93) — long-press a message ──
    replyTarget?.let { target ->
        val replyText = target.content.ifBlank { target.thinkingText ?: "" }
        AlertDialog(
            onDismissRequest = { replyTarget = null },
            title = { Text("Message") },
            text = {
                Column {
                    Text(
                        text = replyText.take(300).ifBlank { "(tool activity)" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Copy just a URL out of the message (v0.1.875)
                    val urls = remember(replyText) {
                        Regex("https?://[^\\s)\"'<>]+").findAll(replyText)
                            .map { it.value }.toList().distinct()
                    }
                    if (urls.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        urls.forEach { u ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    u,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("link", u))
                                    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                                }) { Text("Copy link") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Quote the message into the composer so the user can ask
                    // "what did you mean by this".
                    val quote = replyText.take(500).lineSequence()
                        .joinToString("\n") { if (it.isBlank()) ">" else "> $it" }
                    composerText = "$quote\n\n"
                    replyTarget = null
                }) { Text("Reply") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (replyText.isNotBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Hermes message", replyText))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }
                    replyTarget = null
                }) { Text("Copy") }
            },
        )
    }

    // ── Tool Approval Dialog ──
    val pendingApproval = state.pendingApproval
    if (pendingApproval != null) {
        Dialog(onDismissRequest = { /* must approve or deny */ }) {
            Card(
                modifier = Modifier
                    .widthIn(min = 300.dp, max = 460.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .heightIn(max = 640.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // ── Title with tool name ──
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Approve: ${pendingApproval.toolName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // ── Command description (what will happen) — scrollable ──
                    if (pendingApproval.description.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Command",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = pendingApproval.description,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // ── Raw arguments (full detail) — scrollable ──
                    if (pendingApproval.toolArgs.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Arguments",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = pendingApproval.toolArgs,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Action buttons ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.denyCurrentTool() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Deny")
                        }
                        Button(
                            onClick = { viewModel.approveCurrentTool() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Approve")
                        }
                    }
                }
            }
        }
    }

    // ── Clarify is rendered INLINE above the composer (bottomBar), see
    // InlineClarifyCard below — not as a modal Dialog popup. ──
    }
}

// ── LIVE THINKING TICKER ──
// Shown above the assistant bubble while the model is thinking
// and no real content has arrived yet. Dimmed, italic, live-updating.

@Composable
private fun LiveThinkingTicker(text: String) {
    Text(text, color = Color.White, fontSize = 13.sp, fontFamily = ChatManrope,
        modifier = Modifier.padding(vertical = 4.dp).clip(CircleShape).background(Color(0xFF8B5CF6)).padding(horizontal = 12.dp, vertical = 5.dp))
}


// ── MESSAGE BUBBLE (Telegram-style) ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: UiMessage,
    sameSender: Boolean,
    onReply: (String) -> Unit,
    onLongPress: (UiMessage, Rect) -> Unit,
    onToggleReaction: (String, String) -> Unit,
    onAddReaction: (String) -> Unit,
) {
    // Root-space bounds of this bubble — the actions card is anchored to them,
    // exactly like the official menu that floats next to the pressed message.
    // v0.2.22 PERF: deliberately NOT snapshot state. onGloballyPositioned fires
    // on every position change — i.e. every scroll frame — and writing it to a
    // mutableStateOf recomposed every visible bubble every frame of a scroll
    // (the field 8.3% jank). A plain holder is read only on long-press.
    val bubbleBounds = remember { arrayOf(Rect.Zero) }
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    // v0.6.0: assistant messages render as PLAIN text on the background
    // (official-app style) — only user messages keep filled bubbles.
    val bubbleColor = Color(0xFF1F3B7D)
    val bubbleShape = RoundedCornerShape(if (isUser) 20.dp else 16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = 5.dp),
        horizontalAlignment = alignment,
    ) {
        // Assistant bubbles span the full width (edge to edge); user bubbles
        // stay capped at a chat-style max width, aligned right.
        val bubbleWidthModifier = if (isUser) {
            Modifier.widthIn(max = 288.dp)
        } else {
            Modifier.fillMaxWidth()
        }

        Box {
            Column(
                modifier = bubbleWidthModifier
                    .clip(bubbleShape)
                    .background(if (isUser) bubbleColor else Color(0xFF272740))
                    .onGloballyPositioned { bubbleBounds[0] = it.boundsInRoot() }
                    // v0.3.0: long-press opens the official message-actions card.
                    // A plain tap keeps the clone's existing quote-reply affordance.
                    .combinedClickable(
                        onClick = { onReply(message.id) },
                        onLongClick = { onLongPress(message, bubbleBounds[0]) },
                    )
                    .padding(horizontal = if (isUser) 14.dp else 16.dp, vertical = if (isUser) 10.dp else 12.dp),
            ) {
            // Thinking no longer renders in the message (v0.1.65): it streams
            // in the docked Live Activity panel while working — showing it here
            // too caused double-thinking. Tool cards above the answer remain.

            // Content
            if (message.content.isNotBlank()) {
                // Use immediate=true to avoid Loading→Success height oscillation
                // during streaming (fixes scroll-crazy feedback loop).
                // Override heading typography with reasonable sizes (not
                // displayLarge ~57sp which "balloons" text in the bubble).
                // Keep the document's beginning stable while streaming. Replacing
                // it with a sliding tail silently changes the text under a reader
                // who has scrolled away, even when no scroll command is issued.
                val shownContent = message.content
                // v0.2.22 PERF: looksLikeMath scans the whole text; memoize it
                // per content so per-frame recompositions never re-run it.
                val mathy = !isUser && remember(shownContent) {
                    MathTransform.looksLikeMath(shownContent)
                }
                val mdContent = if (mathy) MathTransform.toMarkdown(shownContent)
                else shownContent
                val mdState = rememberCachedMarkdownState(
                    content = mdContent,
                    streaming = message.isStreaming,
                )
                // v0.3.0: deliberately NO message-level SelectionContainer. Compose
                // grabs the long-press for text selection and draws the system
                // toolbar/handles on top of the actions card. The official app only
                // offers selection through the card's "Select" row (SelectMessageSheet),
                // so selection lives there instead of on the bubble.
                Markdown(
                        markdownState = mdState,
                        // v0.1.95: code block + inline code backgrounds are a
                        // themeable extra surface (library reads these via
                        // MarkdownTheme.colors inside highlightedCodeBlock).
                        colors = markdownColor(
                            codeBackground = LocalUiSurfaces.current.codeBlock,
                            inlineCodeBackground = LocalUiSurfaces.current.codeBlock,
                        ),
                        // v0.1.87: no width cap for assistant messages — text spans
                        // the full bubble edge to edge. User bubbles keep the cap
                        // (they're 320dp max anyway).
                        modifier = if (isUser) Modifier.widthIn(max = 400.dp) else Modifier.fillMaxWidth(),
                        typography = markdownTypography(
                            h1 = MaterialTheme.typography.titleLarge,
                            h2 = MaterialTheme.typography.titleMedium,
                            h3 = MaterialTheme.typography.titleSmall,
                            h4 = MaterialTheme.typography.bodyLarge,
                            h5 = MaterialTheme.typography.bodyMedium,
                            h6 = MaterialTheme.typography.bodyMedium,
                            text = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp, fontFamily = ChatManrope, fontWeight = FontWeight.Normal, color = Color(0xFFF2F4FA)),
                        ),
                        // v0.5.7: math pipeline v2 (see MathRendering). The parser natively
                        // makes math tokens the library silently drops — the annotator emits
                        // them as literal text (currency protection). Paragraph images never
                        // hit components.image; they go through ImageTransformer, whose library
                        // default is a NO-OP — ours draws latex: bitmaps.
                        annotator = remember { mathAnnotator() },
                        imageTransformer = mathImageTransformer(),
                        components = markdownComponents(
                            codeBlock = { CopyableCodeBlock(it, highlight = !message.isStreaming) },
                            codeFence = { CopyableCodeFence(it, highlight = !message.isStreaming) },
                        ),
                )
                if (message.isStreaming) {
                    Text(
                        text = " ▌",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (message.isStreaming && message.thinkingText == null) {
                // No content yet — show typing dots while waiting, cursor otherwise.
                // v0.1.120: add a small "waiting for model" label so the state is
                // obvious when reasoning is OFF and the model emits no thinking
                // deltas (e.g. Ornith with effort=off) — otherwise just dots can
                // look stuck during the long first-token wait on a local model.
                if (message.isWaitingForFirstEvent) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        TypingDots()
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "waiting for model…",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = FontStyle.Italic,
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                } else {
                    Text(
                        text = "▌",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (message.isStreaming && message.thinkingHasContent && message.content.isBlank()) {
                // Thinking done, content starting soon — show cursor
                Text(
                    text = "▌",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            }

        }

        // Reactions — official renders pills under the bubble plus a "+"
        // shortcut that reopens the picker.
        if (message.reactions.isNotEmpty()) {
            ReactionChips(
                reactions = message.reactions,
                onToggle = { emoji -> onToggleReaction(message.id, emoji) },
                onAdd = { onAddReaction(message.id) },
            )
        }
    }
}


// ── POST-TURN EVENT RENDERERS (v0.6.0, official-app style) ──
// Thinking and tool calls render as compact, standalone strips in the message
// flow — a collapsible THOUGHT card and one-line tool rows that open a detail
// bottom sheet. The old boxed "TOOLS · n" container is gone: the row itself is
// the affordance, matching the official client's density.

/** Past-tense verb per tool, shown bold in the row (official "Ran command"). */
internal fun toolVerb(toolName: String): String = when (toolName) {
    "terminal" -> "Ran command"
    "execute_code" -> "Ran code"
    "read_file" -> "Read"
    "write_file" -> "Wrote"
    "patch", "edit_file" -> "Edited"
    "search_files" -> "Searched"
    "web_search" -> "Searched web"
    "web_extract" -> "Fetched"
    "browser_exec" -> "Drove browser"
    "delegate_task" -> "Delegated"
    "vision_analyze" -> "Analyzed image"
    "session_search" -> "Searched sessions"
    "memory" -> "Memory"
    "skill_view", "skill_manage" -> "Skill"
    "text_to_speech" -> "Spoke"
    "cronjob_manage" -> "Cron"
    else -> "Ran $toolName"
}

@Composable
private fun toolBadgeIcon(toolName: String): androidx.compose.ui.graphics.vector.ImageVector =
    when (toolName) {
        "terminal", "execute_code" -> Icons.Outlined.Terminal
        "read_file" -> Icons.Outlined.Description
        "write_file", "patch", "edit_file" -> Icons.Outlined.Edit
        "search_files", "session_search" -> Icons.Outlined.Search
        "web_search", "web_extract", "browser_exec" -> Icons.Outlined.Language
        "delegate_task" -> Icons.Outlined.Hub
        "vision_analyze" -> Icons.Outlined.PhotoCamera
        "memory" -> Icons.Outlined.Psychology
        else -> Icons.Outlined.Handyman
    }

/**
 * Collapsed-by-default THOUGHT card (v0.6.0): small-caps label + chevron;
 * collapsed shows one italic line + ellipsis, expanded scrolls the full text.
 */
@Composable
private fun ThoughtCard(text: String, streaming: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    // v0.8.0: while the turn is live the card IS the thinking box — expanded,
    // uncapped, scrolling with the main list (no nested scroller). At turn end
    // `streaming` flips false and the card collapses to the normal one-line
    // THOUGHT preview, matching the official app.
    val effectiveExpanded = if (streaming) true else expanded
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded },
        color = LocalUiSurfaces.current.thinkingBox,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (streaming) "Thinking…" else "Thought",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = MaterialTheme.colorScheme.tertiary,
                )
                if (streaming) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (effectiveExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (effectiveExpanded) "Collapse thinking" else "Expand thinking",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
            if (!effectiveExpanded) {
                Text(
                    // v0.2.6: one-line preview must NOT lay out the whole
                    // thinking string (O(n) per recomposition). 160 chars is
                    // far past the visible width; ellipsis does the rest.
                    text = if (text.length > 160) text.take(160) else text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic,
                        lineHeight = 17.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
            AnimatedVisibility(
                visible = effectiveExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                if (streaming) {
                // v0.2.6: CONSTANT-COST live preview. The old box fed the whole
                // thinking string into a verticalScroll and called
                // scrollTo(maxValue) on every flush (~9/s) — every frame paid
                // O(total thinking) text layout: gfxinfo showed 20-30 ms
                // main-thread frames that grew worse as thinking lengthened
                // ("slow when you're thinking"). Now only the last ~700 chars
                // are laid out, bottom-anchored in a fixed 96dp frame. The
                // complete text lands in the card the moment the turn ends.
                Spacer(Modifier.height(4.dp))
                val liveTail = if (text.length > 700) "…" + text.substring(text.length - 700) else text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(
                        text = liveTail,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                            lineHeight = 17.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
                // v0.8.1: expanded THOUGHT caps at ~4 lines and scrolls within
                // itself (was 220dp).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 96.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                            lineHeight = 18.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
                }
            }
        }
    }
}

/**
 * Interleaved timeline (v0.8.1): thinking segments render at the position they
 * were interrupted (anchored by tool id; unanchored ones first), each tool row
 * follows its preceding segment. Rows are cards; a tap opens the detail sheet.
 */
/**
 * V4 official collapsed turn row: one line for a whole turn's activity —
 * "Ran N commands and made M tool calls" — with a green check when the turn
 * is finished and a blue spinner while it streams. Tap toggles the individual
 * tool rows. "Command" is a toolName heuristic (shell-like tools); the
 * gateway does not label commands differently from other tool calls.
 */
@Composable
private fun TurnSummaryRow(
    toolCalls: List<UiToolCall>,
    streaming: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val commandNames = listOf("bash", "shell", "terminal", "exec", "execute", "run_command", "subprocess")
    val commands = toolCalls.count { tc -> commandNames.any { tc.toolName.lowercase().contains(it) } }
    val tools = toolCalls.size - commands
    // Official collapsed-row wording: live = "Ran N commands and made M tool calls"
    // (blue, spinner); finished = "Worked for X min, Y sec · N tools" (grey, checklist).
    val liveText = when {
        commands > 0 && tools > 0 -> "Ran $commands command${if (commands == 1) "" else "s"} and made $tools tool call${if (tools == 1) "" else "s"}"
        commands > 0 -> "Ran $commands command${if (commands == 1) "" else "s"}"
        else -> "Made $tools tool call${if (tools == 1) "" else "s"}"
    }
    val starts = toolCalls.mapNotNull { it.startedAt }
    val ends = toolCalls.mapNotNull { it.completedAt }
    val secs = if (starts.isNotEmpty()) (((ends.maxOrNull() ?: System.currentTimeMillis()) - starts.min()) / 1000L) else 0L
    val dur = when {
        secs >= 3600 -> "%d hr, %d min".format(secs / 3600, (secs % 3600) / 60)
        secs >= 60 -> "%d min, %d sec".format(secs / 60, secs % 60)
        else -> "$secs sec"
    }
    val total = toolCalls.size
    val doneText = "Worked for $dur \u00b7 $total tool${if (total == 1) "" else "s"}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(CircleShape)
            .background(Color(0xFF2A2A4C))
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (streaming) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF447CFF))
        } else {
            Icon(Icons.Filled.Checklist, null, modifier = Modifier.size(16.dp), tint = Color(0xFF8A8AA6))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (streaming) liveText else doneText,
            fontFamily = ChatManrope,
            fontSize = 14.sp,
            color = if (streaming) Color(0xFF447CFF) else Color(0xFF8A8AA6),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse tool calls" else "Expand tool calls",
            modifier = Modifier.size(16.dp),
            tint = Color(0xFF8A8AA6),
        )
    }
}

@Composable
private fun ToolCallList(
    toolCalls: List<UiToolCall>,
    segments: List<ThinkingSeg> = emptyList(),
) {
    var detail by remember { mutableStateOf<UiToolCall?>(null) }
    val now by remember { mutableStateOf(System.currentTimeMillis()) }
    val isCmd: (UiToolCall) -> Boolean = { tc ->
        listOf("bash", "shell", "terminal", "exec", "execute", "run_command", "subprocess")
            .any { tc.toolName.lowercase().contains(it) }
    }
    // Official groups CONSECUTIVE same-kind calls: "Ran N commands" /
    // "Made N tool calls" (tap to expand); singles render one row each.
    val groups: List<Pair<Boolean, List<UiToolCall>>> = remember(toolCalls) {
        val out = mutableListOf<Pair<Boolean, MutableList<UiToolCall>>>()
        toolCalls.forEach { tc ->
            val c = isCmd(tc)
            val last = out.lastOrNull()
            if (last != null && last.first == c) last.second.add(tc) else out.add(c to mutableListOf(tc))
        }
        out.map { it.first to it.second.toList() }
    }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val rendered = mutableSetOf<ThinkingSeg>()
            segments.filter { it.beforeToolId == null && it.text.isNotBlank() }
                .forEach { rendered += it; ThoughtCard(text = it.text) }
            groups.forEach { (cmd, tools) ->
                segments.filter { it.beforeToolId == tools.first().id && it.text.isNotBlank() && it !in rendered }
                    .forEach { rendered += it; ThoughtCard(text = it.text) }
                if (tools.size == 1) {
                    ToolCallRow(tools.first(), now) { detail = tools.first() }
                } else {
                    GroupToolRow(tools, cmd, onOpen = { detail = it }) { tc -> ToolCallRow(tc, now) { detail = tc } }
                }
            }
            // v0.2.3: segments anchored to a tool that isn't rendered here
            // (the turn-summary path passes toolCalls = emptyList()) were
            // being silently dropped — that's why thinking boxes vanished.
            segments.filter { it !in rendered && it.text.isNotBlank() }
                .forEach { ThoughtCard(text = it.text) }
        }
    detail?.let { tc ->
        ToolDetailSheet(toolCall = tc, onDismiss = { detail = null })
    }
}

@Composable
private fun GroupToolRow(
    tools: List<UiToolCall>,
    command: Boolean,
    onOpen: (UiToolCall) -> Unit,
    render: @Composable (UiToolCall) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(CircleShape).background(Color(0xFF1D5945))
                .clickable { open = !open }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Checklist, null, tint = Color(0xFF30D158), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (command) "Ran ${tools.size} commands" else "Made ${tools.size} tool calls",
                color = Color(0xFFF2F4FA), fontFamily = ChatManrope, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                tint = Color(0xFF8A8AA6), modifier = Modifier.size(16.dp))
        }
        if (open) tools.forEach { render(it) }
    }
}

/**
 * Pull a human one-liner out of a tool call's args JSON: the most
 * command-like field first (what the official row shows), else raw text.
 */
private fun argsDisplay(args: String?): String? {
    if (args.isNullOrBlank()) return null
    return try {
        val el = kotlinx.serialization.json.Json.parseToJsonElement(args)
        if (el is kotlinx.serialization.json.JsonObject) {
            val key = listOf(
                "command", "code", "cmd", "file_path", "path", "pattern",
                "query", "url", "goal", "text", "content", "message", "prompt",
            ).firstOrNull { k -> el[k]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true }
                ?: el.keys.firstOrNull()
            key?.let { el[it]?.jsonPrimitive?.contentOrNull }?.takeIf { it.isNotBlank() }
                ?: args
        } else args
    } catch (_: Exception) {
        args
    }
}

/**
 * One-line tool row (v0.8.1): a clear card per call (fill + hairline border,
 * official style) with status-tinted icon badge + bold verb + truncated
 * monospace args. The whole card is the tap target for the detail sheet.
 */
@Composable
private fun ToolCallRow(toolCall: UiToolCall, now: Long, onTap: () -> Unit) {
    val running = !toolCall.completed
    val badgeColor = when {
        running -> MaterialTheme.colorScheme.primary
        else -> Color(0xFF43A047)
    }
    // v0.8.1: the row shows the INPUT (args), like the official app; output
    // lives in the detail sheet.
    val argsPreview = argsDisplay(toolCall.args) ?: toolCall.summary ?: toolCall.preview
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onTap),
        color = Color(0xFF1D5945),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = toolBadgeIcon(toolCall.toolName),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = Color(0xFF30D158),
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = toolVerb(toolCall.toolName),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF30D158),
            )
            if (!argsPreview.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = argsPreview.replace('\n', ' '),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(6.dp))
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color(0xFF447CFF))
            }
        }
    }
}

/**
 * Tool detail bottom sheet (v0.6.0): Input / Output / Call ID / Context
 * sections, each with its own Copy action — the official client's drill-down.
 */
/**
 * v0.8.2: process-wide cache of the gateway's tool definitions (name ->
 * toolset + one-line description) so a tapped tool row can explain what the
 * tool IS, like the official app. Fetched lazily on first sheet open; the
 * registry is static per gateway run, so one fetch per app process is enough.
 */
private object ToolDocs {
    data class Doc(val toolset: String, val description: String)
    // Snapshot state, not a plain field: a read during composition subscribes, so
    // a sheet opened while the fetch is in flight recomposes when it lands
    // (a plain @Volatile field only showed descriptions on the SECOND open).
    private val _state = androidx.compose.runtime.mutableStateOf<Map<String, Doc>?>(null)
    val map: Map<String, Doc>? get() = _state.value
    @Volatile private var loading = false

    fun loadAsync() {
        if (_state.value != null || loading) return
        loading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val r = AppModule.provideRpcClient().toolsShow()
                val m = HashMap<String, Doc>()
                r["sections"]?.jsonArray?.forEach { s ->
                    val o = s as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val toolset = o["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    o["tools"]?.jsonArray?.forEach { t ->
                        val to = t as? kotlinx.serialization.json.JsonObject ?: return@forEach
                        val n = to["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        val d = to["description"]?.jsonPrimitive?.contentOrNull ?: ""
                        m[n] = Doc(toolset, d)
                    }
                }
                if (m.isNotEmpty()) _state.value = m
            } catch (_: Exception) {
                // Sheet degrades to no-description; retry on next open.
            } finally {
                loading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolDetailSheet(toolCall: UiToolCall, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { ToolDocs.loadAsync() }
    val doc = ToolDocs.map?.get(toolCall.toolName)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // NO verticalScroll here: this Material3 ModalBottomSheet measures
                // its content slot with infinite maxHeight (the sheet scrolls itself
                // via the drag gesture), and any verticalScroll under it crashes the
                // app at measure time. Long output stays capped by the preview limits.
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = toolCall.toolName,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            // v0.8.2: what exactly this tool IS — the gateway's own one-line
            // description + its toolset, shown under the title.
            if (doc != null && doc.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = doc.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                if (doc.toolset.isNotBlank()) {
                    Text(
                        text = "toolset: ${doc.toolset}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // History rows sometimes carry the command only in preview (empty
            // args on the assistant row) — show it as Input, like the row does.
            val argsText = toolCall.args?.takeIf { it.isNotBlank() }
            val inputText = argsText ?: toolCall.preview?.takeIf { it.isNotBlank() }
            if (inputText != null) {
                SheetSection(label = "Input", value = inputText)
            }
            // preview counts as Output only when it wasn't consumed as Input.
            val output = toolCall.result ?: toolCall.summary
                ?: toolCall.preview?.takeIf { argsText != null }
            if (!output.isNullOrBlank()) {
                SheetSection(label = "Output", value = output)
            }
            SheetSection(label = "Call ID", value = toolCall.id)
            if (toolCall.startedAt != null) {
                SheetSection(
                    label = "Context",
                    value = "Started ${formatTimestamp(toolCall.startedAt)}",
                    mono = false,
                )
            }
            if (!toolCall.inlineDiff.isNullOrBlank()) {
                Text(
                    text = "Diff",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                DiffView(toolCall.inlineDiff)
            }
        }
    }
}

@Composable
private fun SheetSection(label: String, value: String, mono: Boolean = true) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    clipboard.setText(AnnotatedString(value))
                    copied = true
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (copied) "Copied" else "Copy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Surface(
        color = LocalUiSurfaces.current.codeBlock,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(max = 200.dp)
                .padding(10.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

/**
 * Desktop-style unified diff: monospace lines with red/green tinting for
 * removed/added lines, highlighted hunk headers and file lines. The server's
 * inline_diff carries ANSI color codes (terminal rendering) — we strip them
 * and re-classify by line prefix so colors follow the app theme.
 */
@Composable
private fun DiffView(diffText: String) {
    val lines = remember(diffText) { parseDiffLines(diffText) }
    Surface(
        color = Color(0xFF0D1117),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            lines.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(line.bgColor),
                ) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        ),
                        color = line.textColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private enum class DiffKind { FILE, HUNK, ADD, DEL, CONTEXT, OTHER }

private data class DiffLine(val text: String, val kind: DiffKind) {
    val textColor: Color
        get() = when (kind) {
            DiffKind.ADD -> Color(0xFFA5D6A7)
            DiffKind.DEL -> Color(0xFFEF9A9A)
            DiffKind.HUNK -> Color(0xFF82B1FF)
            DiffKind.FILE -> Color(0xFF80CBC4)
            else -> Color(0xFFB0BEC5)
        }
    val bgColor: Color
        get() = when (kind) {
            DiffKind.ADD -> Color(0x1F2E7D32)
            DiffKind.DEL -> Color(0x1FB71C1C)
            else -> Color.Transparent
        }
}

private val ansiRegex = Regex("\u001B\\[[0-9;]*[A-Za-z]")

private fun stripAnsi(text: String): String = ansiRegex.replace(text, "")

/** Parse ANSI-stripped inline_diff text into classified lines (desktop-style). */
private fun parseDiffLines(raw: String): List<DiffLine> {
    return raw.lineSequence().mapNotNull { rawLine ->
        val line = stripAnsi(rawLine).trimEnd('\r')
        if (line.isBlank() && !rawLine.startsWith(" ")) return@mapNotNull null
        val kind = when {
            line.startsWith("@@") -> DiffKind.HUNK
            line.startsWith("+") && !line.startsWith("+++") -> DiffKind.ADD
            line.startsWith("-") && !line.startsWith("---") -> DiffKind.DEL
            line.startsWith(" ") -> DiffKind.CONTEXT
            line.startsWith("a/") || line.startsWith("b/") || line.contains("→") -> DiffKind.FILE
            else -> DiffKind.OTHER
        }
        DiffLine(line, kind)
    }.toList()
}

/** Map tool name to an icon character. */
private fun toolIcon(name: String): String = when {
    name.contains("web_search", ignoreCase = true) -> "🔍"
    name.contains("web_fetch", ignoreCase = true) || name.contains("http", ignoreCase = true) -> "🌐"
    name.contains("read_file", ignoreCase = true) || name.contains("cat", ignoreCase = true) -> "📄"
    name.contains("write_file", ignoreCase = true) || name.contains("edit", ignoreCase = true) -> "✏️"
    name.contains("bash", ignoreCase = true) || name.contains("terminal", ignoreCase = true) ||
        name.contains("command", ignoreCase = true) || name.contains("shell", ignoreCase = true) -> "💻"
    name.contains("python", ignoreCase = true) || name.contains("code", ignoreCase = true) ||
        name.contains("run", ignoreCase = true) -> "▶️"
    name.contains("search", ignoreCase = true) -> "🔎"
    name.contains("list", ignoreCase = true) || name.contains("dir", ignoreCase = true) -> "📋"
    name.contains("think", ignoreCase = true) -> "🧠"
    else -> "⚙️"
}

/** Format elapsed time for a tool call. Shows live duration for running tools. */
private fun formatElapsed(startedAt: Long?, now: Long, completed: Boolean): String {
    val start = startedAt ?: return ""
    val elapsedMs = now - start
    val seconds = elapsedMs / 1000
    val millis = elapsedMs % 1000
    return if (completed || seconds >= 60) {
        // Show mm:ss for long or completed tools
        val m = seconds / 60
        val s = seconds % 60
        if (m > 0) "${m}m ${s}s" else "${seconds}.${millis / 100}s"
    } else {
        // Show X.Xs for short running tools
        "${seconds}.${millis / 100}s"
    }
}

// ── TIMESTAMP HELPERS ──

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
    .withZone(ZoneId.systemDefault())
private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    .withZone(ZoneId.systemDefault())

private fun formatTimestamp(epochMillis: Long): String {
    return try {
        dateFormatter.format(Instant.ofEpochMilli(epochMillis))
    } catch (_: Exception) {
        ""
    }
}

private fun formatTime(epochMillis: Long): String {
    return try {
        timeFormatter.format(Instant.ofEpochMilli(epochMillis))
    } catch (_: Exception) {
        ""
    }
}

// ── TYPING DOTS (bouncing animation, iMessage-style) ──

@Composable
private fun TypingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val delays = listOf(0, 150, 300)

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        delays.forEachIndexed { i, delayMs ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = delayMs),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    ),
            )
        }
    }
}

// ── AUTO-SCROLL HELPER ──
// Follows the BOTTOM of the transcript.
//
// v0.2.18 — root cause of "scrolling down while a response streams jumps up":
// the old implementation called `scrollToItem(targetIndex)` first, which places
// the target item's TOP at the viewport top, and only then read the layout to
// scroll the remainder. When that post-snap read landed on a stale layout (no
// measure pass yet — normal under load), `lastVisible.index >= targetIndex` was
// false, the correction was skipped, and the viewport stayed pinned at the START
// of the growing message: every following delta re-snapped the reader back up.
// Field log 2026-11-08 01:34:49–01:35:53 (device <test-phone-serial>, v0.2.17-test)
// shows exactly that: 60+ consecutive `StreamLoop ... done: firstVisible=5
// canScrollForward=true` with no compensating step.
//
// The follow is now a single `scrollBy(distanceToBottom)`: no top-snap, so no
// intermediate position to be left stranded in, and the reader is never moved
// further than the content actually grew. `scrollToItem` remains only for the
// case where the target item is not laid out at all, and that path waits for
// the next frame before measuring.
//
// DIAG: scrollDiagVsyncs counts vsyncs (incremented by the streaming frame
// monitor) so the snap path can prove it measured a fresh layout.
internal val scrollDiagVsyncs = java.util.concurrent.atomic.AtomicLong(0L)

/** The transcript's bottom counts as "in view" within this many px of the
 *  viewport bottom (≈1.5 text lines at 420dpi; the bottom contentPadding alone
 *  is ~36px, so `canScrollForward` alone would never report "at bottom"). */
private const val AT_BOTTOM_SLACK_PX = 72

/** An IME inset below this is the keyboard's open/close animation reporting
 *  1-2px steps, not an open keyboard — it must not trigger a scroll. */
private const val MIN_REAL_KEYBOARD_PX = 100
// v0.2.20: floor below which a viewport shrink is treated as IME animation
// noise; anything between this and MIN_REAL_KEYBOARD_PX used to be swallowed
// un-repinned (the field one-line-clip bug). See ViewportShrink.
private const val MIN_SHRINK_REPIN_PX = 12

/** Bounded window the end-of-turn settle watches the finished bubble re-measure.
 *  The tail->full swap's re-layout is spread over many frames (field journal:
 *  ~500ms of continuous growth), so one frame is a race, not a settle. */
private const val TURN_END_SETTLE_FRAMES = 90

/** Frames of unchanged measured size while flush that end the settle window
 *  early (the normal, short-answer case: nothing to follow, ~6 frames). */
private const val TURN_END_SETTLE_CALM_FRAMES = 6

private suspend fun autoScrollToBottom(
    listState: LazyListState,
    targetIndex: Int,
    totalItems: Int,
    scrollGeneration: Long,
    reason: String,
) {
    val beforeFirst = listState.firstVisibleItemIndex
    val beforeCanScroll = listState.canScrollForward
    val beforeViewportHeight = listState.layoutInfo.viewportSize.height

    DebugLog.log("SCROLL", reason,
        "follow start target=$targetIndex totalItems=$totalItems " +
        "gen=$scrollGeneration firstVisibleBefore=$beforeFirst " +
        "viewportHeight=$beforeViewportHeight canScrollForward=$beforeCanScroll")

    // Bounded: layout can change under us while a delta lands, so allow a
    // couple of passes, but never loop on a list that is genuinely at bottom.
    repeat(3) { pass ->
        val layoutInfo = listState.layoutInfo
        // LazyList item offsets are relative to the content origin, not the
        // viewport's outer top; viewportEndOffset accounts for top padding.
        val viewportHeight = layoutInfo.viewportEndOffset
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()

        if (lastVisible != null && lastVisible.index >= targetIndex) {
            val itemBottom = lastVisible.offset + lastVisible.size
            val beyondViewport = itemBottom - viewportHeight
            if (beyondViewport > 0) {
                val consumed = listState.scrollBy(beyondViewport.toFloat())
                DebugLog.log("SCROLL", reason,
                    "pass$pass scrollBy=${beyondViewport}px (consumed=${consumed.toInt()}) " +
                    "item[${lastVisible.index}] offset=${lastVisible.offset} " +
                    "size=${lastVisible.size} bottom=$itemBottom viewport=$viewportHeight")
                // LazyColumn consumes at most what it can lay out per pass; loop
                // (bounded) to take up the remainder rather than leaving the
                // newest line below the fold.
            } else if (
                lastVisible.index == listState.firstVisibleItemIndex &&
                lastVisible.size >= viewportHeight &&
                itemBottom < viewportHeight - AT_BOTTOM_SLACK_PX &&
                !listState.canScrollForward
            ) {
                // v0.2.19 stale-extent self-heal. The last item is TALLER than
                // the viewport, its measured bottom sits well ABOVE the viewport
                // bottom, and the list claims it cannot scroll forward: the
                // extent shrank under a fast fling while the streaming tail
                // window re-anchored the text, leaving the viewport parked past
                // the true maximum. Field 2026-11-08 (verify-b, 0.2.17-test):
                // offset=1792 with a valid max ~1529, item size constant 2473,
                // bottom=681 of a 980 viewport, canFwd=false — and every follow
                // pass after that logged "at-bottom" and did nothing, so the
                // turn-end tail->full swap re-anchored ~1900px of text under a
                // frozen viewport. scrollBy cannot fix this (there is nothing
                // forward to scroll), so re-anchor explicitly: put the item's
                // bottom flush against the viewport and let the next pass
                // measure the fresh layout.
                // Guards: a genuinely short last item (whole transcript fits)
                // keeps its at-bottom verdict; a flush layout with the 16dp
                // bottom padding is inside the slack window and never lands here.
                val wantOffset = (lastVisible.size - viewportHeight).coerceAtLeast(0)
                val currentOffset = listState.firstVisibleItemScrollOffset
                if (wantOffset != currentOffset) {
                    DebugLog.log("SCROLL", reason,
                        "pass$pass stale-extent re-anchor item[${lastVisible.index}] " +
                        "offset=$currentOffset -> $wantOffset size=${lastVisible.size} " +
                        "bottom=$itemBottom viewport=$viewportHeight")
                    listState.scrollToItem(lastVisible.index, wantOffset)
                    withFrameNanos { }
                } else {
                    // Offset already agrees with the measured size, yet the
                    // bottom is above the fold and forward scroll is refused:
                    // the drawn content is taller than the measured box. A
                    // scroll cannot fix that; report it instead of looping.
                    DebugLog.log("SCROLL", reason,
                        "pass$pass clamped-stale item[${lastVisible.index}] " +
                        "offset=$currentOffset size=${lastVisible.size} " +
                        "bottom=$itemBottom viewport=$viewportHeight")
                    return
                }
            } else {
                DebugLog.log("SCROLL", reason,
                    "pass$pass at-bottom item[${lastVisible.index}] bottom=$itemBottom " +
                    "viewport=$viewportHeight")
                return
            }
        } else {
            // Target item isn't in the laid-out set: bring it into view, then wait
            // for a real measure pass before measuring it for the remainder.
            val diagVsync0 = scrollDiagVsyncs.get()
            DebugLog.log("SCROLL", reason,
                "pass$pass snapToItem=$targetIndex (not laid out, visCount=${layoutInfo.visibleItemsInfo.size})")
            listState.scrollToItem(targetIndex)
            withFrameNanos { }
            DebugLog.log("SCROLL", reason,
                "pass$pass post-snap measured firstVisible=${listState.firstVisibleItemIndex} " +
                "canScrollForward=${listState.canScrollForward} " +
                "vsyncs_since_snap=${scrollDiagVsyncs.get() - diagVsync0}")
        }
    }

    DebugLog.log("SCROLL", reason,
        "gave up after 3 passes: firstVisible=${listState.firstVisibleItemIndex} " +
        "canScrollForward=${listState.canScrollForward}")
}

/** Compact token count formatting: 85123 → "85.1k", 1048576 → "1.0M". */
private fun formatTokens(tokens: Long): String {
    return when {
        tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000f)
        tokens >= 1_000 -> String.format("%.1fk", tokens / 1_000f)
        else -> tokens.toString()
    }
}

/**
 * v0.8.3 floating status pill (official-app style): pulsing dot + state word +
 * elapsed timer + tok/s, overlaid at the top of the transcript while a turn
 * runs. Replaces the inline LiveTurnStatusLine (its metrics moved to the
 * composer strip and here).
 */
@Composable
private fun TurnStatusPill(
    thinking: String,
    tokPerSec: Float,
    startMs: Long,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse = !pulse
            delay(900)
        }
    }
    val secs = ((now - startMs) / 1000).toInt().coerceAtLeast(0)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xE6101018),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (pulse) 1f else 0.35f),
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (thinking.isNotBlank()) "Thinking…" else "Writing…",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "%d:%02d".format(secs / 60, secs % 60),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
            if (tokPerSec > 0f) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "%.0f tok/s".format(tokPerSec),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/**
 * Downscale an image URI to a Bitmap (<=1600px). Returns null on failure. Runs on
 * the caller's thread — decodes once, no second read later (so Coil never re-reads a
 * now-dead content URI and crashes on recompose). The base64 payload is derived from
 * this same Bitmap via bitmapToDataUrl().
 */
private fun downscaleAndDecode(context: Context, uri: Uri): Bitmap? {
    return try {
        // Bounds probe. With inJustDecodeBounds the platform decoder ALWAYS returns null —
        // it allocates nothing and reports only outWidth/outHeight. Its return value must
        // never gate the flow (that made every attach fail before the real decode ran);
        // decodability is judged on the dimensions it filled in.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = decodeSampleSize(bounds.outWidth, bounds.outHeight, MAX_ATTACH_DIM)
        }
        val direct = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        if (direct != null) return direct
        // v0.2.3: HEIF/AVIF and other formats BitmapFactory won't touch —
        // ImageDecoder handles them on API 28+ (Pixel camera default is HEIC
        // when the HEIF format toggle is on).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                val src = android.graphics.ImageDecoder.createSource(
                    context.contentResolver, uri,
                )
                val sample = decodeSampleSize(bounds.outWidth, bounds.outHeight, MAX_ATTACH_DIM)
                return if (sample > 1) {
                    android.graphics.ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.setTargetSampleSize(sample)
                    }
                } else {
                    android.graphics.ImageDecoder.decodeBitmap(src)
                }
            } catch (_: Exception) {
            }
        }
        null
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

/**
 * Power-of-two inSampleSize that keeps both dimensions at or below [maxDim].
 * Pure so the sampling math is unit-testable without a device.
 */
internal fun decodeSampleSize(width: Int, height: Int, maxDim: Int): Int {
    var sample = 1
    while ((width / sample) > maxDim || (height / sample) > maxDim) {
        sample *= 2
    }
    return sample
}

private const val MAX_ATTACH_DIM = 1600

/** Encode a decoded Bitmap as a base64 JPEG data URL for sending to the server. */
private fun bitmapToDataUrl(bmp: Bitmap): String {
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
    val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    return "data:image/jpeg;base64,$b64"
}

/**
 * Collapsible agent task list card, pinned above the messages.
 * Collapsed: "Tasks 2/5" + active task + thin progress bar.
 * Expanded: full list with done / active (spinner) / pending states.
 */
@Composable
private fun TasksCard(
    todos: List<UiTodo>,
    expanded: Boolean,
    isStreaming: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val doneCount = todos.count { it.isDone }
    val active = todos.firstOrNull { it.isActive }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$doneCount/${todos.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (doneCount == todos.size && todos.isNotEmpty()) {
                            "All tasks done"
                        } else {
                            "Tasks"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isStreaming && active != null) {
                        Text(
                            text = active.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.ExpandLess
                    } else {
                        Icons.Filled.ExpandMore
                    },
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { if (todos.isEmpty()) 0f else doneCount.toFloat() / todos.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    todos.forEach { todo ->
                        TodoRow(todo)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoRow(todo: UiTodo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            todo.isDone -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Done",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            todo.isActive -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            else -> Icon(
                Icons.Outlined.RadioButtonUnchecked,
                contentDescription = "Pending",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = todo.content,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                todo.isDone -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                todo.isActive -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (todo.isActive) FontWeight.SemiBold else FontWeight.Normal,
            textDecoration = if (todo.status == "cancelled") TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Code block / fence wrapper with a copy button (v0.1.93).
 *
 * v0.1.900: the library hands every custom component override the FULL raw markdown
 * of the message as `model.content` (MarkdownExtension.kt: MarkdownComponentModel(content = content, ...)).
 * The built-in renderers only show the node's own slice because MarkdownCodeFence/
 * MarkdownCodeBlock re-slice via the AST node's child source offsets internally.
 * We now route through those same library splitters, so the copy button copies
 * exactly what the panel shows instead of the whole message. (Ported from brother's
 * blackie build, commit 0542d2f; our internals keep SafeCodeContent — off-composition
 * highlighting, v0.1.897 ANR fix — instead of his highlightedCode*.)
 */
@Composable
private fun CopyableCodeBlock(model: MarkdownComponentModel, highlight: Boolean) {
    MarkdownCodeBlock(model.content, model.node) { code, language, _ ->
        val langTag = language ?: (model.extra["language"] as? String)
        CodeBlockShell(code = code, language = langTag ?: "code") {
            SafeCodeContent(code, langTag, highlight)
        }
    }
}

@Composable
private fun CopyableCodeFence(model: MarkdownComponentModel, highlight: Boolean) {
    MarkdownCodeFence(model.content, model.node) { code, language, _ ->
        val langTag = language ?: (model.extra["language"] as? String)
        CodeBlockShell(code = code, language = langTag ?: "code") {
            SafeCodeContent(code, langTag, highlight)
        }
    }
}

// v0.1.897(e): the ANR loop (bug report 79d693d68801). The library's
// highlightedCodeFence builds snipme Highlights SYNCHRONOUSLY inside composition
// — the dump caught the main thread at 5.8s user-time in dev.snipme CodeAnalyzer
// while history-load composed many fences; input dispatch timed out twice in
// 30s (ANR → relaunch → same load → ANR). Also: language AUTO-DETECTION (any
// untagged fence) is the most expensive scan and buys nothing for LLM output.
// So: no highlighting during streaming, never auto-detect (untagged => plain),
// long fences stay plain, and everything that does highlight runs on
// Dispatchers.Default behind runCatching — colors land a frame late, the UI
// never blocks, and no snipme throw can crash the app again.
private const val HIGHLIGHT_MAX_CHARS = 8_000

/** Pure (off-main-thread safe): snipme pass -> AnnotatedString. Unknown tag or
 * any failure returns plain text. */
internal fun snipmeAnnotated(code: String, langName: String): AnnotatedString {
    val lang = SyntaxLanguage.getByName(langName) ?: return AnnotatedString(code)
    val highlights = runCatching {
        Highlights.Builder().language(lang).code(code).build().getHighlights()
    }.getOrNull() ?: return AnnotatedString(code)
    return AnnotatedString.Builder(code).apply {
        highlights.forEach { h ->
            val loc = h.location
            val start = loc.start.coerceAtLeast(0)
            val end = loc.end.coerceAtMost(code.length)
            if (start < end) when (h) {
                is ColorHighlight -> addStyle(SpanStyle(color = Color(h.rgb)), start, end)
                is BoldHighlight -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }
        }
    }.toAnnotatedString()
}

@Composable
private fun SafeCodeContent(code: String, langTag: String?, highlight: Boolean) {
    val style = LocalMarkdownTypography.current.code
    val eligible = highlight && langTag != null && code.length in 1..HIGHLIGHT_MAX_CHARS
    val plain = remember(code) { AnnotatedString(code) }
    val annotated by produceState(plain, code, langTag, eligible) {
        value = if (!eligible) plain else withContext(Dispatchers.Default) {
            runCatching { snipmeAnnotated(code, langTag!!) }.getOrDefault(plain)
        }
    }
    Text(
        text = annotated,
        style = style,
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalUiSurfaces.current.codeBlock)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun CodeBlockShell(code: String, language: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                // v0.1.95: code block header is a themeable extra surface
                .background(LocalUiSurfaces.current.codeBlock)
                .padding(start = 10.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                    Toast.makeText(context, "Copied code", Toast.LENGTH_SHORT).show()
                },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy code", modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copy", style = MaterialTheme.typography.labelSmall)
            }
        }
        content()
    }
}

/** Short display name for a model id (v0.1.88). */
private val QUANT_TOKENS = setOf("iq2","iq3","iq4","q2","q3","q4","q5","q6","q8","k","k_m","k_s","k_l","xs","xl","s","m","l","0","0_0","b","_0")

private fun shortModelName(model: String): String {
    // Official chip style: "gpt-5.6-terra" → "GPT-5.6 Terra"; quant suffixes dropped.
    val last = model.substringAfterLast('/').substringAfterLast(':').lowercase()
    val parts = last.split('-', '_').filter { it.isNotBlank() }
    // trim trailing quant chain
    val cut = parts.indexOfLast { !QUANT_TOKENS.contains(it) && !it.matches(Regex("q[2-8]")) }
    val kept = if (cut >= 0) parts.take(cut + 1) else parts
    val sb = StringBuilder()
    for ((i, tok) in kept.withIndex()) {
        val pretty = when {
            tok == "gpt" -> "GPT"
            tok == "ap" -> "AP"
            tok.matches(Regex("\\d[\\d.]*")) -> tok
            else -> tok.replaceFirstChar { it.uppercaseChar() }
        }
        when {
            sb.isEmpty() -> sb.append(pretty)
            tok.matches(Regex("\\d[\\d.]*")) -> sb.append('-').append(pretty)
            else -> sb.append(' ').append(pretty)
        }
    }
    return sb.toString().ifBlank { last }.take(24)
}

/** Reasoning effort label (v0.1.88; v0.1.116: off/minimal/xhigh added). */
private fun effortShort(effort: String): String = when (effort.lowercase()) {
    "off" -> "off"
    "minimal", "min" -> "min"
    "ultra" -> "ultra"
    "xhigh", "extreme" -> "xhigh"
    "high" -> "high"
    "medium", "med" -> "med"
    "low" -> "low"
    else -> effort.take(8)
}

/** All effort levels the picker offers, ordered quiet → loud. "off" disables thinking. */
private val EFFORT_OPTIONS = listOf("off", "minimal", "low", "medium", "high", "xhigh")

/** True when this effort string means "don't reason at all". */
internal fun effortIsOff(effort: String?): Boolean =
    effort?.lowercase() == "off"

/** Lowest non-off effort (used as the "thinking on" default). */
internal val DEFAULT_EFFORT = "low"

/**
 * Model + reasoning picker (v0.1.88). Reads model.options from the server,
 * shows providers grouped with their models; the selection persists and
 * applies to NEW sessions (desktop-composer contract — there is no
 * mid-conversation switch RPC yet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    viewModel: ChatViewModelContract,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf<JsonRpcClient.ModelOptionsResult?>(null) }
    var selectedModel by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf("") }
    // Start from the session's current effort so "Apply to this chat" doesn't
    // silently reset reasoning to medium when the user only meant to switch model.
    var selectedEffort by remember { mutableStateOf(viewModel.uiState.currentReasoning ?: DEFAULT_EFFORT) }
    // Thinking on/off is driven by whether effort is "off" (off) vs any real level (on).
    var selectedThinkingOn by remember { mutableStateOf(!effortIsOff(viewModel.uiState.currentReasoning)) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val opts = viewModel.loadModelOptions()
            options = opts
            selectedModel = opts.model ?: ""
            selectedProvider = opts.provider ?: ""
            selectedEffort = viewModel.uiState.currentReasoning ?: selectedEffort
            selectedThinkingOn = selectedEffort.isBlank() || !effortIsOff(selectedEffort)
        } catch (e: Exception) {
            error = e.message ?: "Failed to load models"
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Model & Reasoning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Apply to this chat, or save as the default for new chats.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            if (options == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                options!!.providers.forEach { provider ->
                    if (provider.models.isEmpty()) return@forEach
                    Text(
                        text = provider.name ?: provider.slug,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                    provider.models.forEach { model ->
                        val selected = model == selectedModel && provider.slug == selectedProvider
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                                .clickable { selectedModel = model; selectedProvider = provider.slug }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = shortModelName(model),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // v0.1.116: Thinking on/off toggle — effort "off" disables reasoning;
                // any real level turns it on. Effort selector is disabled while off.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Thinking",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = selectedThinkingOn,
                        onCheckedChange = { on ->
                            selectedThinkingOn = on
                            // Pick a sensible effort when flipping on; keep the current
                            // level when flipping off (we just mark it off).
                            if (on && selectedEffort.isBlank()) selectedEffort = DEFAULT_EFFORT
                        },
                    )
                }

                if (selectedThinkingOn) {
                    Spacer(Modifier.height(6.dp))
                    Text("Effort", style = MaterialTheme.typography.labelMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        EFFORT_OPTIONS.filterNot { it == "off" }.forEach { effort ->
                            FilterChip(
                                selected = selectedEffort == effort,
                                onClick = { if (!effortIsOff(effort)) selectedEffort = effort },
                                label = { Text(effort.replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Thinking is off — the model won't reason before answering.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }

                Spacer(Modifier.height(16.dp))
                // v0.1.89: switch THIS chat immediately (slash commands)…
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            viewModel.applyModelToSession(com.gravol.hermesclone.core.network.ModelPick(selectedModel, selectedProvider).switchValue(), selectedEffort, selectedThinkingOn)
                            saving = false
                            Toast.makeText(context, "Applied to this chat", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    enabled = !saving && selectedModel.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (saving) "Applying…" else "Apply to this chat")
                }
                Spacer(Modifier.height(8.dp))
                // …and persist the pick for future chats.
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            saving = true
                            viewModel.saveModelPick(com.gravol.hermesclone.core.network.ModelPick(selectedModel, selectedProvider).switchValue(), selectedEffort, selectedThinkingOn)
                            saving = false
                            Toast.makeText(context, "Saved — applies to new chats", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    enabled = !saving && selectedModel.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save for new chats")
                }
            }
        }
    }
}

// ── ATTACH CHOOSER HELPERS (v0.1.127) ──
@Composable
private fun AttachOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CommandAckLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun createCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "cam").apply { mkdirs() }
    val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun queryFileName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
    }
} catch (_: Exception) {
    null
}

// ── WEBUI-STYLE CONTEXT RING (v0.1.124) ──
/** Circular token-usage gauge matching the WebUI composer. */
@Composable
private fun ContextRing(
    used: Long?,
    max: Long?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val fraction = if (max != null && max > 0 && used != null) {
        (used.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val pct = (fraction * 100f).roundToInt()
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val arcColor = when {
        fraction > 0.75f -> Color(0xFFEF5350)
        fraction > 0.5f -> Color(0xFFFFA726)
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .size(34.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = arcSize,
            )
            if (fraction > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                )
            }
        }
        Text(
            text = if (max != null && max > 0) "$pct" else "·",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── YOLO BUTTON (WebUI-style pill, v0.1.4) ──
// Session-scoped approval-skip toggle. Mirrors the WebUI's .yolo-pill: amber
// when active, outlined when off. Toggles via viewModel.setYolo().
@Composable
private fun YoloButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val activeColor = Color(0xFFF59E0B)  // amber-500, matches WebUI yolo-pill
    val activeBg = activeColor.copy(alpha = 0.15f)
    val activeBorder = activeColor.copy(alpha = 0.35f)
    Surface(
        modifier = Modifier
            .height(30.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) activeBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, if (enabled) activeBorder else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = if (enabled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "YOLO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.04.sp,
                color = if (enabled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── CONTEXT DETAILS PANEL (WebUI) ──
// Tap the context ring → open a details card mirroring the WebUI ctx-indicator:
// % used, tokens in/out, estimated cost, and a compress affordance.
@Composable
private fun ContextDetailsPanel(
    used: Long?,
    max: Long?,
    usage: UiUsage?,
    isStreaming: Boolean,
    onCompress: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fraction = if (max != null && max > 0 && used != null) {
        (used.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    } else { 0f }
    val pct = (fraction * 100f).roundToInt()
    val promptTok = usage?.promptTokens ?: 0
    val completionTok = usage?.completionTokens ?: 0
    val cost = usage?.estimatedCostUsd
    val arcColor = when {
        fraction > 0.75f -> Color(0xFFEF5350)
        fraction > 0.5f -> Color(0xFFFFA726)
        else -> MaterialTheme.colorScheme.primary
    }
    val hasUsageData = promptTok > 0 || completionTok > 0 || cost != null
    val ringTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Context",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(16.dp))
                // Ring + percentage
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(64.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 4.dp.toPx()
                            val inset = stroke / 2f
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            drawArc(
                                color = ringTrackColor,
                                startAngle = -90f, sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                                topLeft = Offset(inset, inset), size = arcSize,
                            )
                            if (fraction > 0f) {
                                drawArc(
                                    color = arcColor,
                                    startAngle = -90f, sweepAngle = 360f * fraction,
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                                    topLeft = Offset(inset, inset), size = arcSize,
                                )
                            }
                        }
                        Box(modifier = Modifier.align(Alignment.Center)) {
                            Text(
                                text = if (fraction > 0f) "$pct" else "·",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (fraction > 0f)
                                "Used ${pct}% of context" else "Context window",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (max != null && max > 0) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${formatTokens(used ?: 0)} / ${formatTokens(max)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                // Token breakdown
                if (hasUsageData) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DetailStat(
                            label = "In",
                            value = formatTokens(promptTok.toLong()),
                            icon = Icons.Outlined.South,
                        )
                        Spacer(Modifier.weight(1f))
                        DetailStat(
                            label = "Out",
                            value = formatTokens(completionTok.toLong()),
                            icon = Icons.Outlined.North,
                        )
                        Spacer(Modifier.weight(1f))
                        DetailStat(
                            label = "Cost",
                            value = if (cost != null) "$${String.format("%.4f", cost)}" else "—",
                            icon = Icons.Outlined.AttachMoney,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }
                // Compress affordance — always visible so it's reachable even at low usage
                    Button(
                        onClick = onCompress,
                        enabled = !isStreaming,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.Compress,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Compress context")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            fraction >= 0.75f -> "Context is nearly full — compress to free space."
                            fraction >= 0.50f -> "Half of context used. Compress to stay efficient."
                            else -> "Compress earlier turns to free context for a focused task."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

// ── INLINE CLARIFY CARD ──
// v0.1.134: renders a pending clarification INLINE above the composer (dsh-style)
// instead of a modal Dialog popup, so the question is always readable and the
// answer chips are selectable. The server sends either a single question with
// `choices`, or nothing — in which case we fall back to free text (plus a
// dismiss/Cancel that sends an empty string to unblock the turn).
@Composable
private fun InlineClarifyCard(
    pendingClarify: PendingClarify,
    onAnswer: (String) -> Unit,
) {
    var customAnswer by remember(pendingClarify.requestId) { mutableStateOf("") }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Outlined.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Needs your input",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // The actual question — always readable, never truncated.
            if (pendingClarify.question.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = pendingClarify.question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Server-provided answer choices as selectable chips (if any).
            if (pendingClarify.choices.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                for (choice in pendingClarify.choices) {
                    AssistChip(
                        onClick = { onAnswer(choice) },
                        label = {
                            Text(
                                text = choice,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            // Free-text fallback + actions.
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = customAnswer,
                    onValueChange = { customAnswer = it },
                    placeholder = { Text("Type your answer…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    shape = RoundedCornerShape(10.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onAnswer("") }, // empty = dismiss/unblock the turn
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val answer = customAnswer.trim()
                        if (answer.isNotEmpty()) onAnswer(answer)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = customAnswer.isNotBlank(),
                ) {
                    Text("Send")
                }
            }
        }
    }
}


// Official activity pill (frame-verified vs off_toolrun.mp4 / off_think.mp4 @30fps):
// label = "Thinking\u2026" (💭) while reasoning, "Writing\u2026" (✏️) while answer text streams,
// "Using <tool>: <argsPreview> + N command(s)" (⚙️) while a tool runs. Emoji icons follow the
// official fw1 ChatActivityCaption map. The pill is otherwise STATIC (no icon/spinner animation)
// apart from the 1s timer tick, and the timer runs continuously across phases for the whole turn
// (0:41 thinking -> 0:45 tool, measured). After the tool completes the LAST tool caption is
// held through post-tool decode (matches official "Using terminal… 0:58" mid-stream behaviour).
@Composable
private fun LiveActivityPill(phase: String, toolName: String?, toolPreview: String?, extraTools: Int) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); elapsed++ } }
    val time = "%d:%02d".format(elapsed / 60, elapsed % 60)
    val usingLabel: String? = if (toolName != null) {
        val plus = if (extraTools > 0) " + $extraTools command${if (extraTools == 1) "" else "s"}" else ""
        val prev = toolPreview?.replace('\n', ' ')?.trim().orEmpty()
        ("Using $toolName: $prev$plus").trim().trimEnd(':', ' ')
    } else null
    val label: String = usingLabel ?: if (phase == "decode") "Writing\u2026" else "Thinking\u2026"
    val icon: String = when {
        usingLabel != null -> "\u2699\uFE0F"
        label == "Writing\u2026" -> "\u270F\uFE0F"
        else -> "\uD83D\uDCAD"
    }
    Row(
        Modifier.padding(top = 4.dp).clip(CircleShape).background(Color(0xFF0C1A3C))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color(0xFFB9BDD0), fontSize = 13.sp, fontFamily = ChatManrope,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 190.dp))
        Spacer(Modifier.width(6.dp))
        Text(time, color = Color(0xFF676571), fontSize = 13.sp, fontFamily = ChatManrope)
    }
}

@Composable
private fun ChatCircle(icon: ImageVector, description: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.size(if (description == "Back" || description == "More") 48.dp else 40.dp).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = Color(0xFFF2F4FA), modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSettingsSheet(
    title: String,
    cwd: String?,
    model: String?,
    onRename: suspend (String) -> Boolean,
    onOpenModel: () -> Unit,
    onOpenDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val repo = remember { com.gravol.hermesclone.feature.settings.SettingsRepository(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    val showToolCalls by repo.showToolCalls.collectAsState(initial = true)
    val showThinking by repo.showThinking.collectAsState(initial = true)
    val foldTurns by repo.foldFinishedTurns.collectAsState(initial = true)
    val linkPreviews by repo.linkPreviews.collectAsState(initial = true)
    val deviceActions by repo.deviceActions.collectAsState(initial = true)
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(title) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF12123A),
        scrimColor = Color(0xB307071E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF3A3A5C)) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Chat Settings", color = Color(0xFFF2F4FA), fontFamily = ChatManrope,
                fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)

            // Top card: Title + Folder
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF181846))) {
                SheetRow(Icons.Outlined.Edit, "Title", title.ifBlank { "Conversation" },
                    onClick = { draft = title; editing = true })
                HorizontalDivider(color = Color.White.copy(alpha = .06f), modifier = Modifier.padding(horizontal = 16.dp))
                SheetRow(Icons.Outlined.FolderOpen, "Folder", cwd ?: "~", onClick = {})
            }

            Text("In this chat", color = Color(0xFF8A8AA6), fontFamily = ChatManrope,
                fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF181846))) {
                SheetToggle(Icons.Filled.Build, "Tool calls", "Show general tool activity between messages.",
                    showToolCalls) { scope.launch { repo.setShowToolCalls(it) } }
                SheetDivider()
                SheetToggle(Icons.Outlined.PhoneAndroid, "Device actions", "Show calendar, health, location and other device requests between messages.",
                    deviceActions) { scope.launch { repo.setDeviceActions(it) } }
                SheetDivider()
                SheetToggle(Icons.Outlined.Lightbulb, "Thinking", "Show what Hermes reasons through before it answers, when the model shares it.",
                    showThinking) { scope.launch { repo.setShowThinking(it) } }
                SheetDivider()
                SheetToggle(Icons.Outlined.UnfoldMore, "Fold finished turns", "Tuck tool calls and thinking behind one row once Hermes has answered.",
                    foldTurns) { scope.launch { repo.setFoldFinishedTurns(it) } }
                SheetDivider()
                SheetToggle(Icons.Outlined.Link, "Link previews", "Show a card under a reply for each web page it links to.",
                    linkPreviews) { scope.launch { repo.setLinkPreviews(it) } }
            }

            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF181846))) {
                // Official behavior: this row OPENS the Session Details sheet (not an inline expand).
                SheetRow(Icons.Filled.BarChart, "Session details", null, onClick = onOpenDetails)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onOpenModel, modifier = Modifier.fillMaxWidth()) {
                Text("Change model & thinking", color = Color(0xFF447CFF), fontFamily = ChatManrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = Color(0xFF12123A),
            title = { Text("Session title", color = Color(0xFFF2F4FA), fontFamily = ChatManrope) },
            text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFF2F4FA), unfocusedTextColor = Color(0xFFF2F4FA),
                        focusedBorderColor = Color(0xFF447CFF), unfocusedBorderColor = Color(0xFF3A3A5C)))
            },
            confirmButton = {
                TextButton(onClick = { editing = false; val t = draft.trim(); if (t.isNotEmpty()) scope.launch { onRename(t) } }) {
                    Text("Save", color = Color(0xFF447CFF), fontFamily = ChatManrope, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel", color = Color(0xFF8A8AA6), fontFamily = ChatManrope) } },
        )
    }
}

@Composable
private fun SheetDivider() = HorizontalDivider(color = Color.White.copy(alpha = .06f), modifier = Modifier.padding(horizontal = 16.dp))

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFFB9BDD0), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color(0xFFF2F4FA), fontFamily = ChatManrope, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (value != null) Text(value, color = Color(0xFF8A8AA6), fontFamily = ChatManrope, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF5A5A78), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SheetToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFFB9BDD0), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color(0xFFF2F4FA), fontFamily = ChatManrope, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = Color(0xFF8A8AA6), fontFamily = ChatManrope, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF447CFF), checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF5D5D75), uncheckedThumbColor = Color(0xFFEDEDF2),
                uncheckedBorderColor = Color.Transparent))
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Color(0xFF8A8AA6), fontFamily = ChatManrope, fontSize = 13.sp, modifier = Modifier.width(100.dp))
        Text(value, color = Color(0xFFF2F4FA), fontFamily = ChatManrope, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChatHeader(
    title: String, bot: String, onBack: () -> Unit, onExecution: () -> Unit,
    onMenuAction: (String) -> Unit = {},
    thinking: Boolean, phase: String, tokPerSec: Float, thinkingTokPerSec: Float,
    toolName: String?, toolPreview: String?, toolRunning: Boolean, extraTools: Int,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val name = if (bot == "default") "Hermes" else bot
    Box(modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        ChatCircle(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", Color(0xFF0C1A3C), onBack)
        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            // The floating bot icon opens quick chat settings (official parity).
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center) {
                BlobAvatar(recipeFor(if (bot == "default") "hermes" else bot.lowercase()), size = 44.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, modifier = Modifier.widthIn(max = 190.dp).clip(CircleShape)
                    .background(Color(0xFF0C1A3C)).padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color(0xFFF2F4FA), fontFamily = ChatManrope, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (thinking && !toolRunning) {
                    Spacer(Modifier.width(6.dp))
                    val isThinking = phase == "thinking"
                    val shown = if (isThinking) thinkingTokPerSec else tokPerSec
                    val label = when {
                        isThinking && shown > 0.1f -> "thinking ${"%.1f".format(shown)} t/s"
                        isThinking -> "thinking\u2026"
                        shown > 0.1f -> "${"%.1f".format(shown)} tok/s"
                        else -> "wait\u2026"
                    }
                    Box(Modifier.clip(CircleShape).background(Color(0xFF0C1A3C))
                        .padding(horizontal = 9.dp, vertical = 4.dp)) {
                        Text(label, color = if (isThinking) Color(0xFF8B5CF6) else Color(0xFF447CFF),
                            fontSize = 12.sp, fontFamily = ChatManrope, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (thinking) LiveActivityPill(phase, toolName, toolPreview, extraTools)
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            ChatCircle(Icons.Filled.MoreHoriz, "More", Color(0xFF0C1A3C)) { expanded = true }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.width(196.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF0C1A3C))) {
                val items = listOf("Live Mode" to Icons.Outlined.GraphicEq, "Terminal" to Icons.Outlined.Terminal,
                    "Execution" to Icons.Outlined.Terminal, "Routines" to Icons.Outlined.Schedule,
                    "Artifacts" to Icons.Outlined.Inventory2, "Bot Screen" to Icons.Filled.Devices,
                    "Archive" to Icons.Outlined.Archive, "Delete" to Icons.Outlined.Delete)
                items.forEachIndexed { index, (label, icon) ->
                    if (index == 6) HorizontalDivider(color = Color.White.copy(alpha = .10f), modifier = Modifier.padding(horizontal = 16.dp))
                    DropdownMenuItem(text = { Text(label, fontSize = 17.sp, fontFamily = ChatManrope, color = if (label == "Delete") Color(0xFFFF6B6B) else Color(0xFFF2F4FA)) },
                        leadingIcon = { Icon(icon, null, tint = if (label == "Delete") Color(0xFFFF6B6B) else Color(0xFFF2F4FA), modifier = Modifier.size(20.dp)) },
                        onClick = { expanded = false; if (label == "Execution") onExecution() else onMenuAction(label) }, contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.height(42.dp))
                }
            }
        }
    }
}



/** Turn-group metadata for the official collapsed turn row (see turnGroupInfo). */
private class TurnGroupInfo(val anchor: Boolean, val key: String, val tools: List<UiToolCall>, val streaming: Boolean)
