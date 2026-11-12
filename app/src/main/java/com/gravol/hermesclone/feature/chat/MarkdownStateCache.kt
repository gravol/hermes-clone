package com.gravol.hermesclone.feature.chat

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * v0.2.23 PERF: process-wide cache of parsed markdown.
 *
 * Policy ported from the official Nous app's decompiled renderer
 * (`defpackage.fnh` + `defpackage.jad` + `defpackage.d82`, reference/decompiled):
 *
 *  - parsed markup is memoised app-wide in an LRU keyed by the source text,
 *    budget 2,000,000 units where one unit is one source character;
 *  - entries longer than 500,000 characters are never cached;
 *  - content up to 4,000 characters is parsed synchronously, longer content is
 *    parsed off the composition thread.
 *
 * Why this matters here: `rememberMarkdownState` keys on its `Input`, so the
 * state — and with it the parsed AST — dies with the LazyColumn item's
 * composition. Every time a message scrolled back into the composition window
 * it was re-parsed. The official app parses each message once, ever, and keeps
 * the result in a long-lived cache.
 *
 * Deliberately NOT used while a message is streaming: the tail's content
 * changes on every delta, so a cache would only churn, and the immediate
 * (synchronous) parse is what keeps the streaming bubble from oscillating
 * between the loading and success heights.
 *
 * Measurement note (2026-11-08): on both the short countdown conversation and
 * the long transcript this change was neutral — only 2–4 markdown states are
 * constructed per full gesture run, so parsing was never the scroll cost. It is
 * kept because it removes repeated parse work on long transcripts and matches
 * the reference app's behaviour; it is not claimed as a smoothness fix.
 */
object MarkdownStateCache {
    /** LRU budget in source characters (official app uses 2,000,000). */
    private const val MAX_CHARS = 2_000_000

    /** Never cache content longer than this many characters. */
    private const val MAX_CACHED_ENTRY = 500_000

    /** Content at or under this length parses synchronously (official: 4,000). */
    const val SYNC_PARSE_LIMIT = 4_000

    private val lru = object : LruCache<String, MarkdownState>(MAX_CHARS) {
        override fun sizeOf(key: String, value: MarkdownState): Int = key.length.coerceAtLeast(1)
    }

    fun get(content: String): MarkdownState? =
        if (content.length > MAX_CACHED_ENTRY) null else lru.get(content)

    fun put(content: String, state: MarkdownState) {
        if (content.length > MAX_CACHED_ENTRY) return
        lru.put(content, state)
    }

    fun clear() = lru.evictAll()
}

/**
 * Drop-in replacement for `rememberMarkdownState` that survives the message's
 * LazyColumn item leaving and re-entering the composition window.
 */
@Composable
fun rememberCachedMarkdownState(content: String, streaming: Boolean): MarkdownState {
    if (!streaming) {
        MarkdownStateCache.get(content)?.let { return it }
    }
    val immediate = streaming || content.length <= MarkdownStateCache.SYNC_PARSE_LIMIT
    val state = rememberMarkdownState(content = content, immediate = immediate)
    if (!streaming) {
        SideEffect { MarkdownStateCache.put(content, state) }
    }
    return state
}
