package com.gravol.hermesclone.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gravol.hermesclone.di.AppModule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Session Details sheet — parity with the official app's sheet (parity audit
 * 2026-11-10, gap G1). Three cards fed by live RPCs:
 *
 *  Context  `session.context_breakdown` — big used/max figure, percent, colored
 *           segment bar, one row per category. The SERVER decides order, labels
 *           and which categories appear; colors are OUR palette keyed by id —
 *           the wire's `color` field is a desktop CSS var, no hex (verified
 *           live: probe 2026-11-12 returns var(--context-usage-*)).
 *  Model    model/provider from chat state (session.info), context window from
 *           the breakdown's context_max.
 *  Usage    `session.usage` — calls / input / output / total.
 *
 * Layout + colors sampled pixel-wise from the official sheet evidence
 * (off_c13/off_c14_session_details.png, 1080x2400).
 */

// --- official palette, sampled from off_c13_session_details.png ---------------
private val SdSheetBg = Color(0xFF12123A)
private val SdCardBorder = Color(0x2EFFFFFF)
private val SdInk = Color(0xFFF4F1EB)
private val SdSecondary = Color(0xFFABA9A5)
private val SdTrack = Color(0xFF242447)
private val SdDotSystemPrompt = Color(0xFF82828B)
private val SdDotToolDefinitions = Color(0xFFFF9F0A)
private val SdDotRules = Color(0xFFB983FF)          // category not present in the sampled session
private val SdDotSkills = Color(0xFFFFD60A)         // category not present in the sampled session
private val SdDotMcp = Color(0xFFFF6482)            // category not present in the sampled session
private val SdDotSubagentDefinitions = Color(0xFF63E6E2)
private val SdDotMemory = Color(0xFF30D158)
private val SdDotConversation = Color(0xFF0A84FF)
private val SdDotFallback = Color(0xFF82828B)

private fun sdCategoryColor(id: String): Color = when (id) {
    "system_prompt" -> SdDotSystemPrompt
    "tool_definitions" -> SdDotToolDefinitions
    "rules" -> SdDotRules
    "skills" -> SdDotSkills
    "mcp" -> SdDotMcp
    "subagent_definitions" -> SdDotSubagentDefinitions
    "memory" -> SdDotMemory
    "conversation" -> SdDotConversation
    else -> SdDotFallback
}

/** Official number style: 7136 -> "7.2K", 1165 -> "1.2K", 146432 -> "146K", 1_250_000 -> "1.3M". */
internal fun formatTokensShort(v: Long): String = when {
    v >= 1_000_000 -> trimTrailingZero("%.1fM".format(v / 1_000_000.0))
    v >= 10_000 -> "${v / 1000}K"
    v >= 1_000 -> trimTrailingZero("%.1fK".format(v / 1000.0))
    else -> v.toString()
}

private fun trimTrailingZero(s: String): String =
    s.replace(Regex("^(\\d+)\\.0([KM])$"), "$1$2")

private fun JsonObject.longOr(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
private fun JsonObject.strOr(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailsSheet(
    sessionDbKey: String,
    model: String?,
    provider: String?,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var breakdown by remember { mutableStateOf<JsonObject?>(null) }
    var usage by remember { mutableStateOf<JsonObject?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(sessionDbKey) {
        val rpc = AppModule.provideRpcClient()
        try {
            val cb = rpc.sessionContextBreakdown(sessionDbKey)
            val us = rpc.sessionUsage(sessionDbKey)
            breakdown = cb
            usage = us
        } catch (e: Exception) {
            com.gravol.hermesclone.core.network.DebugLog.log(
                "RPC", "SessionDetails", "fetch failed: ${e.message}")
            failed = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SdSheetBg,
        scrimColor = Color(0xB307071E),
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.94f).systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 15.dp)
                .padding(bottom = 32.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            // Header: back arrow left, centered title (official layout)
            Box(Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterStart).size(44.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = 0.05f)),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = SdInk, modifier = Modifier.size(22.dp))
                }
                Text("Session Details", color = SdInk, fontFamily = ChatManrope,
                    fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    modifier = Modifier.align(Alignment.Center).padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(14.dp))

            if (failed && breakdown == null) {
                Text("Couldn't load session details.", color = SdSecondary,
                    fontFamily = ChatManrope, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = TextAlign.Center)
                return@Column
            }

            // ── Context card ────────────────────────────────────────────────
            SdSectionLabel("Context")
            val cb = breakdown
            val cats = remember(cb) {
                cb?.get("categories")?.jsonArray?.mapNotNull { el ->
                    val o = el.jsonObject
                    val id = o.strOr("id") ?: return@mapNotNull null
                    SdCategory(id, o.strOr("label") ?: id, o.longOr("tokens") ?: 0L)
                } ?: emptyList()
            }
            val used = cb?.longOr("context_used")
            val max = cb?.longOr("context_max")
            val percent = cb?.longOr("context_percent")?.toInt()
            Column(SdCard) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp),
                    verticalAlignment = Alignment.Bottom) {
                    Text(used?.let { formatTokensShort(it) } ?: "—", color = SdInk,
                        fontFamily = ChatManrope, fontWeight = FontWeight.Bold, fontSize = 36.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("of ${max?.let { formatTokensShort(it) } ?: "?"}", color = SdSecondary,
                        fontFamily = ChatManrope, fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 4.dp))
                    Spacer(Modifier.weight(1f))
                    if (percent != null) {
                        Text("$percent%", color = SdSecondary, fontFamily = ChatManrope,
                            fontSize = 17.sp, modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                SdSegmentBar(cats, max)
                Spacer(Modifier.height(14.dp))
                if (cats.isEmpty()) {
                    Text(if (cb != null) "No category detail yet — context figures are estimated."
                         else "Loading…",
                        color = SdSecondary, fontFamily = ChatManrope, fontSize = 14.sp,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp))
                }
                cats.forEachIndexed { i, c ->
                    if (i > 0) SdCardDivider()
                    SdDotRow(c.label, formatTokensShort(c.tokens), sdCategoryColor(c.id))
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Model card ──────────────────────────────────────────────────
            Spacer(Modifier.height(22.dp))
            SdSectionLabel("Model")
            Column(SdCard) {
                SdIconRow(Icons.Filled.Settings, "Model", model ?: "—")
                SdCardDivider()
                SdIconRow(Icons.Filled.Memory, "Provider",
                    provider?.takeIf { it.isNotBlank() } ?: "—")
                SdCardDivider()
                SdIconRow(Icons.Filled.HorizontalRule, "Context window",
                    max?.let { formatTokensShort(it) } ?: "—")
            }

            // ── Usage card ──────────────────────────────────────────────────
            Spacer(Modifier.height(22.dp))
            SdSectionLabel("Usage")
            Column(SdCard) {
                val calls = usage?.longOr("calls")
                val input = usage?.longOr("input")
                val output = usage?.longOr("output")
                val total = usage?.longOr("total")
                SdIconRow(Icons.Filled.SwapHoriz, "Calls", (calls ?: 0).toString())
                SdCardDivider()
                SdIconRow(Icons.Filled.ArrowDownward, "Input", (input ?: 0).toString())
                SdCardDivider()
                SdIconRow(Icons.Filled.ArrowUpward, "Output", (output ?: 0).toString())
                SdCardDivider()
                SdIconRow(Icons.Filled.Functions, "Total", (total ?: 0).toString())
            }
        }
    }
}

private data class SdCategory(val id: String, val label: String, val tokens: Long)

/** Official card style: same fill as the sheet, hairline border, 20dp radius. */
private val SdCard: Modifier
    @Composable get() = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(SdSheetBg)
        .border(BorderStroke(1.dp, SdCardBorder), RoundedCornerShape(24.dp))

@Composable
private fun SdSectionLabel(text: String) {
    Text(text, color = SdSecondary, fontFamily = ChatManrope, fontSize = 14.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 24.dp))
}

@Composable
private fun SdCardDivider() {
    HorizontalDivider(color = Color.White.copy(alpha = 0.06f),
        modifier = Modifier.padding(horizontal = 18.dp))
}

@Composable
private fun SdDotRow(label: String, value: String, dot: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(label, color = SdInk, fontFamily = ChatManrope, fontSize = 16.sp,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = SdSecondary, fontFamily = ChatManrope, fontSize = 16.sp)
    }
}

@Composable
private fun SdIconRow(icon: androidx.compose.ui.graphics.vector.ImageVector,
                      label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = SdInk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = SdInk, fontFamily = ChatManrope, fontSize = 16.sp,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = SdSecondary, fontFamily = ChatManrope, fontSize = 16.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp))
    }
}

/**
 * Proportional segment bar exactly like the official: one segment per category
 * with tokens > 0, sized against the WINDOW (context_max), so the filled length
 * reads as context_used and the rest of the track is free space.
 */
@Composable
private fun SdSegmentBar(cats: List<SdCategory>, max: Long?) {
    val capacity = (max ?: 0L).coerceAtLeast(1L)
    val usedTokens = cats.sumOf { it.tokens }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(11.dp)
        .clip(RoundedCornerShape(4.dp)).background(SdTrack),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        cats.forEach { c ->
            if (c.tokens > 0) {
                Box(Modifier.weight(c.tokens.toFloat()).fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp)).background(sdCategoryColor(c.id)))
            }
        }
        val free = (capacity - usedTokens).coerceAtLeast(0L)
        if (free > 0) {
            // transparent remainder — the SdTrack background shows through
            Box(Modifier.weight(free.toFloat()).fillMaxHeight())
        }
    }
}
