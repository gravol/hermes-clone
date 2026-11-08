@file:OptIn(ExperimentalMaterial3Api::class)

package com.gravol.hermesclone.feature.chat

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Message actions — the official app's long-press menu and the sheets behind it.
 *
 * Measured against `ai.nousresearch.hermes.mobile` on the Pixel 8 (1080x2400 @356dpi):
 * floating card anchored to the pressed message, a `Nov 8, 01:23` timestamp header,
 * 44dp rows with 20dp outlined icons and 15sp labels, hairline dividers between
 * groups, and a red final row for the destructive undo. Row set by message role:
 *
 *   any message : Copy · Select · Share…                (client-side)
 *                 Add reaction… · Branch from here      (message.react / session.branch)
 *   user message: Edit & resend · Restore & run again   (prompt.submit + truncate)
 *   last user   : Undo Last Turn                        (session.undo)
 */

/** Palette sampled from the official popup (see work/msg-actions/ev). */
private val PopupBg = Color(0xFF0C1A3C)
private val RowLabel = Color(0xFFF2F4FA)
private val MutedLabel = Color(0xFF8A8AA6)
private val Accent = Color(0xFF447CFF)
private val Danger = Color(0xFFFF6B5A)
private val Hairline = Color(0x1FFFFFFF)
private val SheetBg = Color(0xFF12123A)
private val CardBg = Color(0xFF181846)

/** What the long-press menu was opened on. */
data class MessageActionTarget(
    val message: UiMessage,
    /** Bounds of the pressed bubble in root coordinates (px). */
    val bounds: Rect,
    /** The message is the last user turn → the destructive Undo row applies. */
    val isLastUserTurn: Boolean,
)

internal enum class MessageActionRow(val label: String, val icon: ImageVector, val danger: Boolean = false) {
    COPY("Copy", Icons.Outlined.ContentCopy),
    SELECT("Select", Icons.Outlined.TextFields),
    SHARE("Share…", Icons.Outlined.Share),
    REACT("Add reaction…", Icons.Outlined.AddReaction),
    BRANCH("Branch from here", Icons.Outlined.AccountTree),
    EDIT_RESEND("Edit & resend", Icons.Outlined.Edit),
    RESTORE_RUN("Restore & run again", Icons.Outlined.Refresh),
    UNDO("Undo Last Turn", Icons.Outlined.Undo, danger = true),
}

/**
 * The floating action card. One Popup, focusable so back/tap-outside dismiss it
 * exactly like the official sheet.
 */
@Composable
internal fun MessageActionsPopup(
    target: MessageActionTarget,
    timestamp: String,
    onDismiss: () -> Unit,
    onAction: (MessageActionRow) -> Unit,
) {
    val rows = buildList {
        add(MessageActionRow.COPY); add(MessageActionRow.SELECT); add(MessageActionRow.SHARE)
        add(MessageActionRow.REACT); add(MessageActionRow.BRANCH)
        if (target.message.role == "user") {
            add(MessageActionRow.EDIT_RESEND); add(MessageActionRow.RESTORE_RUN)
            if (target.isLastUserTurn) add(MessageActionRow.UNDO)
        }
    }
    val density = LocalDensity.current
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val cardW = 244.dp
    val rowH = 44.dp
    val headerH = 30.dp
    val dividerH = 14.dp
    val dividers = (if (target.message.role == "user") 2 else 1)
    val cardH = headerH + rowH * rows.size + dividerH * dividers + 10.dp

    val cardHPx = with(density) { cardH.toPx() }
    val gapPx = with(density) { 6.dp.toPx() }
    val marginPx = with(density) { 12.dp.toPx() }
    val screenHPx = with(density) { screenH.toPx() }
    val leftPx = with(density) { 16.dp.toPx() }

    // Prefer above the message (official behaviour); fall back to below, clamped.
    val topPx = if (target.bounds.top - cardHPx - gapPx > marginPx) {
        target.bounds.top - cardHPx - gapPx
    } else {
        (target.bounds.bottom + gapPx).coerceAtMost(screenHPx - cardHPx - marginPx)
    }.coerceAtLeast(marginPx)

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(leftPx.toInt(), topPx.toInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(cardW)
                .clip(RoundedCornerShape(18.dp))
                .background(PopupBg)
                .border(1.dp, Hairline, RoundedCornerShape(18.dp))
                .padding(vertical = 5.dp),
        ) {
            Text(
                text = timestamp,
                color = MutedLabel,
                fontSize = 11.sp,
                fontFamily = ChatManrope,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
            )
            rows.forEachIndexed { i, row ->
                val startsGroup = i == 0 || i == 3 || i == 5
                if (startsGroup && i > 0) {
                    HorizontalDivider(
                        color = Hairline,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = dividerH / 2),
                    )
                }
                ActionRowView(row) { onDismiss(); onAction(row) }
            }
        }
    }
}

@Composable
private fun ActionRowView(row: MessageActionRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = row.icon,
            contentDescription = null,
            tint = if (row.danger) Danger else RowLabel,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = row.label,
            color = if (row.danger) Danger else RowLabel,
            fontSize = 15.sp,
            fontFamily = ChatManrope,
        )
    }
}

/** "Select" — the message text in a full-height sheet, selectable with the system toolbar. */
@Composable
fun SelectMessageSheet(text: String, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        scrimColor = Color(0xB307071E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF3A3A5C)) },
    ) {
        SelectionContainer {
            Text(
                text = text,
                color = RowLabel,
                fontSize = 17.sp,
                lineHeight = 25.sp,
                fontFamily = ChatManrope,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 420.dp)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            )
        }
    }
}

/** Emoji palette for "Add reaction…" (official uses the full emoji picker; this is a curated set). */
private val ReactionPalette = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂",
    "🙃", "😉", "😊", "🥰", "😍", "🤩", "😘", "😗", "😚",
    "😙", "🥲", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗",
    "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏",
    "😒", "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤", "😴",
    "😷", "🤒", "🤕", "🤢", "🤮", "🥵", "🥶", "🥴", "😵",
    "🤯", "🤠", "🥳", "😎", "🤓", "🧐", "😕", "😟", "🙁",
    "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰",
    "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩",
    "😫", "🥱", "😤", "😡", "😠", "🤬", "💀", "💩", "🤡",
    "👍", "👎", "👏", "🙌", "🙏", "👌", "✌️", "🤝", "💪",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💯",
    "🔥", "✨", "🎉", "✅", "❌", "⚠️", "👀", "🚀", "⭐",
)

/** "Add reaction…" — dark sheet titled "Add Reaction" with a 9-column emoji grid. */
@Composable
fun AddReactionSheet(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        scrimColor = Color(0xB307071E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF3A3A5C)) },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Cancel",
                    color = Accent,
                    fontSize = 15.sp,
                    fontFamily = ChatManrope,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
                )
                Text(
                    "Add Reaction",
                    color = RowLabel,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ChatManrope,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(64.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(9),
                modifier = Modifier.fillMaxWidth().height(360.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(ReactionPalette) { emoji ->
                    Box(
                        Modifier.size(38.dp).clickable { onDismiss(); onPick(emoji) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(emoji, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

/** "Branch from here" confirmation — official wording, message preview and carried count. */
@Composable
fun BranchConfirmSheet(
    isUser: Boolean,
    messageText: String,
    carriedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        scrimColor = Color(0xB307071E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF3A3A5C)) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Branch",
                    color = RowLabel,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ChatManrope,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Branch",
                    color = Accent,
                    fontSize = 15.sp,
                    fontFamily = ChatManrope,
                    modifier = Modifier.clickable(onClick = onConfirm).padding(4.dp),
                )
            }
            Text(
                "Branching from",
                color = MutedLabel,
                fontSize = 12.sp,
                fontFamily = ChatManrope,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .padding(14.dp),
            ) {
                Text(
                    if (isUser) "You" else "Hermes",
                    color = MutedLabel,
                    fontSize = 12.sp,
                    fontFamily = ChatManrope,
                )
                Text(
                    messageText.take(280),
                    color = RowLabel,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontFamily = ChatManrope,
                    maxLines = 5,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Messages carried over", color = RowLabel, fontSize = 15.sp, fontFamily = ChatManrope)
                Spacer(Modifier.weight(1f))
                Text("$carriedCount", color = MutedLabel, fontSize = 15.sp, fontFamily = ChatManrope)
            }
            Text(
                "A new conversation starts with everything up to this message. This one is left as it is, " +
                    "and the branch is listed under it.",
                color = MutedLabel,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = ChatManrope,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** "Undo Last Turn" confirmation dialog (official wording). */
@Composable
fun UndoLastTurnDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141B3D),
        title = {
            Text("Undo Last Turn?", color = RowLabel, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = ChatManrope)
        },
        text = {
            Text(
                "Removes this message and the reply after it from the conversation.",
                color = Color(0xFFC9C9D6), fontSize = 14.sp, lineHeight = 20.sp, fontFamily = ChatManrope,
            )
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text("Undo Last Turn", color = Danger, fontSize = 14.sp, fontFamily = ChatManrope)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Accent, fontSize = 14.sp, fontFamily = ChatManrope)
            }
        },
    )
}

/** The "EDITING EARLIER MESSAGE" bar the official app shows above the composer. */
@Composable
fun EditingMessageBar(text: String, onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F1B3F))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Edit, null, tint = Accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "EDITING EARLIER MESSAGE",
                color = Accent,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = ChatManrope,
            )
            Text(
                text.take(120).replace("\n", " "),
                color = MutedLabel,
                fontSize = 12.sp,
                maxLines = 1,
                fontFamily = ChatManrope,
            )
        }
        Icon(
            Icons.Filled.Close,
            contentDescription = "Cancel editing",
            tint = MutedLabel,
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onCancel),
        )
    }
}

/**
 * Reaction chips under a message (official renders one pill per emoji + a "+"
 * shortcut that reopens the picker). Tapping a pill toggles this user's reaction.
 */
@Composable
fun ReactionChips(
    reactions: List<UiReaction>,
    onToggle: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val chips = reactionChips(reactions)
    if (chips.isEmpty()) return
    Row(
        Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { (emoji, count, mine) ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(if (mine) Color(0xFF1D2761) else Color(0xFF14224A))
                    .border(1.dp, if (mine) Accent else Color(0x24FFFFFF), CircleShape)
                    .clickable { onToggle(emoji) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(emoji, fontSize = 13.sp)
                if (count > 1) {
                    Spacer(Modifier.width(4.dp))
                    Text("$count", color = RowLabel, fontSize = 11.sp, fontFamily = ChatManrope)
                }
            }
        }
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFF14224A))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AddReaction, "Add reaction", tint = MutedLabel, modifier = Modifier.size(14.dp))
        }
    }
}
