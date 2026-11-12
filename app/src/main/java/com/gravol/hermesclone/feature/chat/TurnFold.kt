package com.gravol.hermesclone.feature.chat

/**
 * Official turn-fold parity (v0.2.25): the official app buckets a turn's tool
 * calls into six categories and joins count-phrases with the locale list
 * formatter ("Read a file, ran a command, and made 2 tool calls").
 *
 * Ground truth extracted 2026-11-12 from the official APK (jadx of
 * work/official/official-base.apk):
 *  - plurals chat_tool_run_{commands,edits,reads,searches,pages,other}
 *  - enum t8g name table + bucket map s9g (Command→Commands, EditFiles→Edits,
 *    ReadFile→Reads, WebSearch→Searches, WebPage→Pages, everything else→Other)
 *  - sgg.java duration format ("<60 → 'N sec'; <3600 → 'N min[, M sec]';
 *    else 'N hr[, M min]'", zero parts omitted) and the null-duration
 *    fallback string "Worked" (never "Worked for 0 sec").
 *  - phrase order = first-occurrence order in the turn (LinkedHashMap in
 *    r8g.java), first letter capitalized, java ListFormatter join.
 */
enum class ToolBucket { Commands, Edits, Reads, Searches, Pages, Other }

private val bucketExact = mapOf(
    // Command
    "terminal" to ToolBucket.Commands, "bash" to ToolBucket.Commands,
    "exec_command" to ToolBucket.Commands, "shell" to ToolBucket.Commands,
    // WebSearch
    "web_search" to ToolBucket.Searches, "search_query" to ToolBucket.Searches,
    "browser_search" to ToolBucket.Searches,
    // ReadFile
    "read_file" to ToolBucket.Reads, "open_file" to ToolBucket.Reads,
    // EditFiles
    "write_file" to ToolBucket.Edits, "edit_file" to ToolBucket.Edits,
    "apply_patch" to ToolBucket.Edits,
    // WebPage
    "web_fetch" to ToolBucket.Pages, "web_extract" to ToolBucket.Pages,
)

// Official prefix fallbacks (t8g `.m` lists, matched on the lowercased,
// hyphen-normalized name). Order follows the enum declaration order.
private val bucketPrefixes = listOf(
    "exec_command" to ToolBucket.Commands, "shell" to ToolBucket.Commands,
    "web_search" to ToolBucket.Searches, "search_query" to ToolBucket.Searches,
    "browser_search" to ToolBucket.Searches,
    "read_file" to ToolBucket.Reads,
    "write_file" to ToolBucket.Edits, "apply_patch" to ToolBucket.Edits,
    "edit_file" to ToolBucket.Edits,
    "web_fetch" to ToolBucket.Pages, "web_extract" to ToolBucket.Pages,
)

fun classifyToolBucket(name: String): ToolBucket {
    val n = name.trim().lowercase().replace('-', '_')
    bucketExact[n]?.let { return it }
    for ((p, b) in bucketPrefixes) if (n.startsWith(p)) return b
    return ToolBucket.Other
}

/** Count phrase per bucket, mirroring the official plurals exactly. */
private fun bucketPhrase(b: ToolBucket, count: Int): String = when (b) {
    ToolBucket.Commands -> if (count == 1) "ran a command" else "ran $count commands"
    ToolBucket.Edits -> if (count == 1) "edited a file" else "edited $count files"
    ToolBucket.Reads -> if (count == 1) "read a file" else "read $count files"
    ToolBucket.Searches -> if (count == 1) "searched the web" else "searched the web $count times"
    ToolBucket.Pages -> if (count == 1) "read a webpage" else "read $count webpages"
    ToolBucket.Other -> if (count == 1) "made a tool call" else "made $count tool calls"
}

/**
 * Official ListFormatter join (en): "A" / "A and B" / "A, B, and C",
 * then the first letter is upper-cased.
 */
fun joinPhrases(parts: List<String>): String {
    val joined = when (parts.size) {
        0 -> return ""
        1 -> parts[0]
        2 -> "${parts[0]} and ${parts[1]}"
        else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
    }
    return joined.replaceFirstChar { it.uppercaseChar() }
}

/**
 * Summary label for a turn's tool calls in FIRST-OCCURRENCE bucket order —
 * the official collapsed live label and the expanded green sub-pill label
 * are the same string.
 */
fun turnSummaryLabel(toolNames: List<String>): String {
    val order = LinkedHashSet<ToolBucket>()
    val counts = HashMap<ToolBucket, Int>()
    for (n in toolNames) {
        val b = classifyToolBucket(n)
        order.add(b)
        counts[b] = (counts[b] ?: 0) + 1
    }
    return joinPhrases(order.map { bucketPhrase(it, counts[it]!!) })
}

/**
 * Official duration phrasing (sgg.java): <60s "N sec" (rounded), <1h
 * "N min[, M sec]" (zero sec omitted), else "N hr[, M min]" (zero min
 * omitted). Null/absent → null so the caller falls back to plain "Worked".
 */
fun formatWorkedDuration(secs: Long?): String? {
    if (secs == null || secs < 0) return null
    if (secs < 60) return "$secs sec"
    if (secs < 3600) {
        val m = secs / 60; val s = secs % 60
        return if (s > 0) "$m min, $s sec" else "$m min"
    }
    val h = secs / 3600; val m = (secs % 3600) / 60
    return if (m > 0) "$h hr, $m min" else "$h hr"
}
