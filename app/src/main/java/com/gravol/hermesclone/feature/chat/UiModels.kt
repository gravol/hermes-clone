package com.gravol.hermesclone.feature.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Shared UI data models used by both DashboardChatViewModel and ChatScreen.
 * Originally defined in ChatViewModel.kt (legacy SSE ViewModel), extracted during legacy stack cleanup.
 */

/** One author's emoji reaction on a message (gateway: display_metadata.reactions). */
data class UiReaction(val emoji: String, val author: String = "user") {
    val isMine: Boolean get() = author == "user"
}

/** Parse the reaction list the gateway keeps in a message's display_metadata sidecar. */
fun parseReactions(meta: JsonObject?): List<UiReaction> {
    val arr = meta?.get("reactions") as? JsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val emoji = o["emoji"]?.jsonPrimitive?.content ?: return@mapNotNull null
        if (emoji.isBlank()) return@mapNotNull null
        UiReaction(emoji = emoji, author = o["author"]?.jsonPrimitive?.content ?: "user")
    }
}

/** Collapse reactions into (emoji, count, mine) chips, preserving first-seen order. */
fun reactionChips(reactions: List<UiReaction>): List<Triple<String, Int, Boolean>> {
    val order = LinkedHashMap<String, Pair<Int, Boolean>>()
    for (r in reactions) {
        val prev = order[r.emoji]
        order[r.emoji] = ((prev?.first ?: 0) + 1) to ((prev?.second ?: false) || r.isMine)
    }
    return order.map { (emoji, v) -> Triple(emoji, v.first, v.second) }
}

data class UiMessage(
    val id: String,
    val role: String,      // "user" | "assistant"
    val content: String = "",
    val isStreaming: Boolean = false,
    val isWaitingForFirstEvent: Boolean = false,  // true from send until first SSE event
    val isCommandAck: Boolean = false,  // system-ish acknowledgement line (e.g. /steer) — rendered distinctly
    val thinkingText: String? = null,
    // v0.8.1: earlier thinking segments of THIS turn, each closed by a tool
    // call starting — rendered as separate THOUGHT cards interleaved with the
    // tool rows at the position they were interrupted (official-app layout).
    val priorThinking: List<ThinkingSeg> = emptyList(),
    val thinkingExpanded: Boolean = true,
    val thinkingHasContent: Boolean = false,  // true once first assistant.delta arrives
    val toolCalls: List<UiToolCall> = emptyList(),
    val usage: UiUsage? = null,
    val timestamp: Long = System.currentTimeMillis(),
    /** Persisted messages.id — required by message.react / truncation params. */
    val rowId: Long? = null,
    /** Emoji reactions from display_metadata.reactions. */
    val reactions: List<UiReaction> = emptyList(),
)

data class ThinkingSeg(val beforeToolId: String?, val text: String)

data class UiToolCall(
    val id: String,
    val toolName: String,
    val preview: String? = null,
    val args: String? = null,
    val result: String? = null,
    val summary: String? = null,
    val startedAt: Long? = null,
    val completed: Boolean = false,
    /** Wall-clock ms when tool.complete arrived (turn-duration math for the
     *  official "Worked for X min, Y sec · N tools" collapsed row). */
    val completedAt: Long? = null,
    val inlineDiff: String? = null,
)

data class UiUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val estimatedCostUsd: Double? = null,
    /** Server-reported predicted tokens per second (e.g. Ollama timings). */
    val predictedPerSecond: Float? = null,
)

/** One item in the agent's live task list (todo tool state). */
data class UiTodo(
    val id: String,
    val content: String,
    val status: String = "pending",  // pending | in_progress | completed | cancelled
) {
    val isDone: Boolean get() = status == "completed" || status == "cancelled"
    val isActive: Boolean get() = status == "in_progress"
}

data class ChatUiState(
    val sessionTitle: String = "",
    val messages: List<UiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val scrollGeneration: Long = 0L,  // bumped on every SSE-driven list mutation; triggers auto-scroll
    val pendingApproval: PendingApproval? = null,  // non-null when tool needs approval
    val pendingClarify: PendingClarify? = null,    // non-null when clarification is needed
    // Live context-window occupancy (from session.info usage.context_used/context_max).
    // Null until the server reports a real reading.
    val contextUsed: Long? = null,
    val contextMax: Long? = null,
    // v0.1.88: current model + reasoning effort (from resume info / config).
    val currentModel: String? = null,
    val currentReasoning: String? = null,
    // Effective provider slug for this session (from session.info). Empty until the
    // server reports one; shown by the Session Details Model card.
    val currentProvider: String? = null,
    // Session working dir (from session.resume info.cwd) — shown by the
    // official Chat Settings sheet's Folder row.
    val currentCwd: String? = null,
    // v0.1.4: YOLO mode (skip approvals this session). Toggled from the
    // composer YOLO button. Not persisted across sessions.
    val yoloEnabled: Boolean = false,
    // Agent task list (todo tool state, from tool.complete events / history replay).
    // Non-empty = the Tasks card shows above the message list.
    val todos: List<UiTodo> = emptyList(),
    val todosExpanded: Boolean = false,
    // v0.8.0: messages held client-side until the current turn ends (queued
    // chips above the composer, each cancellable). Never sent to the gateway
    // until drained.
    val queuedMessages: List<String> = emptyList(),
    // True when a turn completed while the chat screen wasn't visible (v0.1.60).
    // Shown as a banner on re-entry until dismissed.
    val completedWhileAway: Boolean = false,
    // v0.1.110: server-reported live tokens/sec during streaming (from
    // /v1/chat/completions timings.predicted_per_second). Replaces the
    // stale-char-count estimate in the live panel header.
    val liveTokPerSec: Float? = null,
)

/**
 * A tool call waiting for user approval.
 * @property toolName Name of the tool being called.
 * @property toolArgs Formatted arguments/params for the tool.
 * @property description Human-readable description of what the command will do.
 * @property requestId Server-side request ID for correlation.
 */
data class PendingApproval(
    val toolName: String,
    val toolArgs: String = "",
    val description: String = "",
    val requestId: String = "",
)

/**
 * A clarification request waiting for user input.
 * @property requestId Server-side ID for the clarification request.
 * @property question The question the server is asking the user.
 * @property choices Optional preset answer choices offered by the server (rendered as chips).
 */
data class PendingClarify(
    val requestId: String,
    val question: String = "",
    val choices: List<String> = emptyList(),
)
