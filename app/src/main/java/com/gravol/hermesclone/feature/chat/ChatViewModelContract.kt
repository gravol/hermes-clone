package com.gravol.hermesclone.feature.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.gravol.hermesclone.core.network.JsonRpcClient

/**
 * Shared contract for chat ViewModels — both the legacy SSE [ChatViewModel]
 * and the new dashboard [DashboardChatViewModel] implement this.
 * Allows [ChatScreen] to work with either backend without changes.
 */
abstract class ChatViewModelContract(application: Application) : AndroidViewModel(application) {
    abstract var uiState: ChatUiState
    /** Set by /new or /reset — ChatScreen observes and navigates to the fresh session. */
    open var resetTargetSession: String? by mutableStateOf(null)
    abstract fun init(sessionId: String, title: String?)
    abstract fun loadMessages()
    abstract fun sendMessage(text: String)
    abstract fun stopStreaming()
    /** Retry the last assistant response — re-send the previous user prompt. */
    abstract fun retry()
    abstract fun toggleThinking(messageId: String)
    /** Approve the current pending tool approval request. */
    open fun approveCurrentTool(approveAll: Boolean = false) {}
    /** Deny the current pending tool approval request. */
    open fun denyCurrentTool(denyAll: Boolean = false) {}
    /** Respond to the current pending clarify request. */
    open fun respondToClarify(answer: String) {}
    /** Toggle the Tasks card (agent todo list) expanded state. */
    open fun toggleTodosExpanded() {}
    /** Send a message with an attached image (base64, pre-downscaled). */
    open fun sendMessageWithImage(text: String, imageBase64: String, filename: String?) {}
    open fun sendMessageWithImages(text: String, imagesB64: List<String>) {}
    // v0.8.0: client-side message queue — hold a send until the current turn
    // ends, cancel it before it drains. (The gateway's own busy-queue has no
    // cancel RPC; a locally-held message is cancellable by construction.)
    open fun queueMessage(text: String) {}
    open fun cancelQueued(index: Int) {}
    /** Send a message with an attached file (base64 + mime), uploaded via `file.attach`. */
    open fun sendMessageWithFile(text: String, fileBase64: String, mimeType: String, filename: String?) {}
    /** Report whether the chat screen is currently visible (background-turn tracking). */
    open fun setScreenVisible(visible: Boolean) {}
    /** Dismiss the "completed while away" banner. */
    open fun clearCompletedWhileAway() {}
    /** Slash-command completion (server-ranked). */
    open suspend fun completeSlash(text: String): List<JsonRpcClient.SlashItem> = emptyList()

    /** Model picker (v0.1.88): model options + persisting the pick. */
    open suspend fun loadModelOptions(): JsonRpcClient.ModelOptionsResult =
        JsonRpcClient.ModelOptionsResult()

    open fun saveModelPick(model: String, reasoning: String, thinkingOn: Boolean = true) {}

    /** v0.1.89: mid-session switch via /model + /reasoning slash commands. */
    open fun applyModelToSession(model: String, reasoning: String, thinkingOn: Boolean = true) {}

    /** Chat Settings sheet: write a new session title (session.title RPC). */
    open suspend fun renameSession(title: String): Boolean = false

    // ── Message actions (official long-press menu, 2026-11-08) ──
    /** Set/retract this user's emoji reaction on a message (message.react). */
    open fun toggleReaction(messageId: String, emoji: String) {}
    /** Rewind the last user turn server-side (session.undo). */
    open fun undoLastTurn() {}
    /** Rewind to before [messageId] and submit [text] (prompt.submit + truncate). */
    open fun rewindAndResubmit(messageId: String, text: String) {}
    /** Fork the session keeping everything up to [messageId] (session.branch). */
    open fun branchFromMessage(messageId: String, onCreated: (String) -> Unit) {}
    /** How many stored messages a branch from [messageId] would carry over. */
    open suspend fun branchMessageCount(messageId: String): Int = 0

    // ── YOLO mode (v0.1.4) — session-scoped approval-skip toggle ──
    /** Load the current YOLO state from the server (for the composer button). */
    /** Toggle YOLO mode on/off for this session. */
    open fun setYolo(enabled: Boolean) {}

    // ── Connection notice (v0.5.5) — honest status for the chat top bar ──
    /** Set only by the dashboard backend; the legacy SSE ViewModel leaves it null. */
    open var connectionNotice: ConnectionNotice? by mutableStateOf(null)
    /** Called by the UI after the transient "reconnected" notice has been visible. */
    open fun clearConnectionNotice() {}
}
