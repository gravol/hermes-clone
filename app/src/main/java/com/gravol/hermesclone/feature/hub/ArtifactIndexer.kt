package com.gravol.hermesclone.feature.hub

import android.content.Context
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Client-side artifact indexer — official parity.
 *
 * The official app has NO server artifacts route: it scans every session's
 * messages on-device (oc0.java in the official dex) and keeps a per-session
 * JSON index under noBackupFilesDir/artifact-index. The rules below are a
 * 1:1 port of that extractor (verified against the official app's own
 * Artifacts screen as oracle, 2026-11-12):
 *
 *  - content: try JSON-parse; parseable → walk the tree (key-path rules),
 *    not parseable → plain-text scan.
 *  - tool_calls: JSON-walk only.
 *  - JSON string values under keys matching SAVE keys (write / create /
 *    download / export / generate / render / save / tts word-prefixed) or ARTIFACT keys
 *    (artifact_file/saved_to/screenshot_path/...) are accepted STRICT
 *    (path-ish without extension is enough).
 *  - JSON string values under keys matching output|stdout are TEXT-scanned.
 *    (NOT "path" — path keys are strict-only; that asymmetry is real.)
 *  - Text scan: MEDIA: lines (strict), markdown images/links (validated),
 *    bare http(s) URLs (validated), unix path tokens (must end in a known
 *    extension), windows drive paths, "Screenshot path:" lines (strict).
 *  - Validation: URL needs a known extension in its path; plain tokens are
 *    accepted only when they end in a known extension; pip cache paths,
 *    .whl files and /packages/ archives are always rejected.
 *  - Dedup per session by source; the row clock is the LAST occurrence.
 */

enum class ArtifactKind(val sectionLabel: String) {
    Images("Images"), Video("Video"), Audio("Audio"), Code("Code"),
    Documents("Documents"), Data("Data"), Archives("Archives"),
    Links("Links"), Other("Other Files"),
}

data class Artifact(
    val id: String,
    val kind: ArtifactKind,
    val source: String,
    val label: String,
    val sessionId: String,
    val sessionTitle: String,
    val profile: String,
    val createdAt: Double,
)

object ArtifactIndexer {

    // ── official regexes (oc0.java static block; SPACE = Kotlin \s family) ──
    private const val SP = "\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff"
    private const val Q = "\"'`"
    private val MD_IMG = Regex("!\\[([^\\]]*)\\]\\(([^)]+)\\)", RegexOption.IGNORE_CASE)
    private val MD_LINK = Regex("\\[([^\\]]*)\\]\\(([^)]+)\\)", RegexOption.IGNORE_CASE)
    private val URL_RE = Regex("https?://[^$SP<>$Q'`]+", RegexOption.IGNORE_CASE)
    private val UNIX = Regex("(?:^|[$SP($Q])((?:/|~[\\\\/]|\\.\\.?[\\\\/]|\\\\)[^$SP$Q<>]+(?:\\.[a-z0-9]{1,8})?)", RegexOption.IGNORE_CASE)
    private val WIN = Regex("(?:^|[$SP($Q])([A-Za-z]:[\\\\/][^$SP$Q<>]+(?:\\.[a-z0-9]{1,8})?)", RegexOption.IGNORE_CASE)
    private val IMG_EXT = Regex("\\.(?:png|jpe?g|gif|webp|svg|bmp)(?:\\?.*)?$", RegexOption.IGNORE_CASE)
    private val ART_EXT = Regex("\\.(?:png|jpe?g|gif|webp|svg|bmp|pdf|txt|json|md|csv|xlsx?|docx?|pptx?|html|zip|tar|gz|avi|flac|m4a|mkv|mp3|ogg|opus|wav|webm|mp4|mov)(?:\\?.*)?$", RegexOption.IGNORE_CASE)
    private val PATHISH = Regex("^(?:file:|/|[~.]?[\\\\/]|\\.\\.?[\\\\/]|[a-z]:[\\\\/]|\\\\)", RegexOption.IGNORE_CASE)
    private val SCHEME = Regex("^(?:https?|file)://", RegexOption.IGNORE_CASE)
    private val MEDIA_A = Regex("[`'?]MEDIA:\\s*(`[^`\\n]+`)", RegexOption.IGNORE_CASE)
    private val MEDIA_B = Regex("[`'?]MEDIA:\\s*('[^'\\n]+')", RegexOption.IGNORE_CASE)
    private val MEDIA_C = Regex("[`']?MEDIA:\\s*(\"[^\"\\n]+\")", RegexOption.IGNORE_CASE)
    private val MEDIA_D = Regex("[`'?]MEDIA:\\s*((?:~/|/|[A-Za-z]:[\\\\/])[^$SP]*)", RegexOption.IGNORE_CASE)
    private val SCREEN = Regex("Screenshot path:([^\\r\\n<>]+)", RegexOption.IGNORE_CASE)
    private val PIP1 = Regex("/(?:\\.cache/pip|library/caches/pip|appdata/local/pip/cache)/", RegexOption.IGNORE_CASE)
    private val PIP2 = Regex("\\.whl(?:\\.metadata)?$", RegexOption.IGNORE_CASE)
    private val PIP3 = Regex("/packages/.+\\.(?:tar\\.gz|tar\\.bz2|zip|egg)(?:\\.metadata)?$", RegexOption.IGNORE_CASE)
    private val TRAIL = Regex("[`'*_]{1,3}$", RegexOption.IGNORE_CASE)
    private val KEY_SAVE = Regex("(?:^|_)(?:creat(?:e|ion)|download|export|generat(?:e|ion)|render|save|speech|tts|write)(?:_|$)", RegexOption.IGNORE_CASE)
    private val KEY_ART = Regex("artifact_(?:file|image|path|url)|files?_(?:created|modified|written)|generated_(?:file|image|path|url)|media_tag|output_(?:file|path|url)|result_(?:file|path|url)|saved_to|screenshot_path", RegexOption.IGNORE_CASE)
    private val KEY_SCAN = Regex("output|stdout|path", RegexOption.IGNORE_CASE)

    /** Mirrors oc0.g + sb0.a(): 'image' | 'file' | null. */
    private fun prim(el: JsonElement?): String? = (el as? JsonPrimitive)?.contentOrNull

    internal fun classify(raw: String, strict: Boolean): ArtifactKind? {
        val s = raw
        if (SCHEME.containsMatchIn(s)) {
            val rest = s.substringAfter("://")
            val path = rest.split('?').first().split('#').first().replace('\\', '/')
            val ext = path.substringAfterLast('/')
            if (!ext.contains('.') || !ART_EXT.containsMatchIn(ext)) return null
            return if (IMG_EXT.containsMatchIn(ext)) ArtifactKind.Images else kindFromExt(ext)
        }
        val low = s.replace('\\', '/').lowercase(Locale.ROOT)
        if (PIP1.containsMatchIn(low) || PIP2.containsMatchIn(low) || PIP3.containsMatchIn(low)) return null
        if (ART_EXT.containsMatchIn(low)) return kindOf(low)
        if (!strict) return null
        if (PATHISH.containsMatchIn(s) || low.startsWith("data:image/")) return kindFromPathish(s)
        if (SCHEME.containsMatchIn(s)) return ArtifactKind.Links
        return null
    }

    private fun kindOf(low: String): ArtifactKind = when {
        IMG_EXT.containsMatchIn(low) -> ArtifactKind.Images
        Regex("\\.(?:mp4|mov|m4v|avi|mkv|webm|mpg|mpeg|wmv|flv)(?:\\?.*)?$").containsMatchIn(low) -> ArtifactKind.Video
        Regex("\\.(?:mp3|wav|m4a|aac|flac|ogg|oga|opus|aiff|aif|caf)(?:\\?.*)?$").containsMatchIn(low) -> ArtifactKind.Audio
        Regex("\\.(?:pdf|docx?|rtf|txt|md|markdown|mdx|pages|odt|epub|tex|key|pptx?|odp)(?:\\?.*)?$").containsMatchIn(low) -> ArtifactKind.Documents
        Regex("\\.(?:json|json5|jsonl|ndjson|ya?ml|toml|xml|csv|tsv|plist|ini|env|cfg|conf|log|parquet|sqlite3?|db|xls|xlsx|ods|numbers|xcconfig)(?:\\?.*)?$").containsMatchIn(low) -> ArtifactKind.Data
        Regex("\\.(?:zip|tar|gz|tgz|bz2|xz|7z|rar|dmg|pkg|jar|war|iso|ipa|apk)(?:\\?.*)?$").containsMatchIn(low) -> ArtifactKind.Archives
        else -> ArtifactKind.Other
    }

    private fun kindFromExt(ext: String): ArtifactKind = kindOf("x." + ext.substringAfterLast('.'))
    private fun kindFromPathish(s: String): ArtifactKind {
        val tail = s.replace('\\', '/').substringAfterLast('/')
        return if (tail.contains('.')) kindOf(tail.lowercase(Locale.ROOT)) else ArtifactKind.Documents
    }

    private fun stripQuotes(s: String): String {
        var t = s.trim()
        repeat(3) {
            if (t.length >= 2 && t.first() in "\"'`" && t.last() == t.first()) {
                t = t.substring(1, t.length - 1).trim()
            }
        }
        return TRAIL.replace(t, "").trimEnd(',', '.', ';', ')', '`')
    }

    internal class Cand(val raw: String, val strict: Boolean)

    private fun mediaUnwrap(u: String): String {
        if (!u.startsWith("#media:")) return u
        for (rx in listOf(MEDIA_A, MEDIA_B, MEDIA_C, MEDIA_D))
            rx.find(u)?.let { return stripQuotes(it.groupValues[1]) }
        return u
    }

    internal fun scanText(text: String, out: MutableList<Cand>) {
        for (rx in listOf(MEDIA_A, MEDIA_B, MEDIA_C, MEDIA_D))
            for (m in rx.findAll(text)) out.add(Cand(stripQuotes(m.groupValues[1]), true))
        for (m in MD_IMG.findAll(text)) {
            var u = stripQuotes(m.groupValues[2])
            u = mediaUnwrap(u)
            if (classify(u, false) != null) out.add(Cand(u, false))
        }
        for (m in MD_LINK.findAll(text)) {
            if (m.range.first > 0 && text[m.range.first - 1] == '!') continue
            var u = stripQuotes(m.groupValues[2])
            u = mediaUnwrap(u)
            if (classify(u, false) != null) out.add(Cand(u, false))
        }
        for (m in URL_RE.findAll(text)) {
            if (classify(m.value, false) != null) out.add(Cand(m.value, false))
        }
        for (m in UNIX.findAll(text)) out.add(Cand(stripQuotes(m.groupValues[1]), false))
        for (m in WIN.findAll(text)) out.add(Cand(stripQuotes(m.groupValues[1]), false))
        for (m in SCREEN.findAll(text)) out.add(Cand(stripQuotes(m.groupValues[1]), true))
    }

    internal fun scanJson(node: JsonElement, key: String?, out: MutableList<Cand>) = scanJson(node, key, "", out)

    private fun scanJson(node: JsonElement, key: String?, path: String, out: MutableList<Cand>) {
        when (node) {
            is JsonPrimitive -> if (node.isString) {
                val v = node.content
                if (KEY_SAVE.containsMatchIn(path) || KEY_ART.containsMatchIn(path)) {
                    if (PATHISH.containsMatchIn(v) || SCHEME.containsMatchIn(v)) out.add(Cand(v, true))
                }
                if (KEY_SCAN.containsMatchIn(path) && v.length < 2000) scanText(v, out)
                // tool-call arguments arrive as a JSON *string*; re-parse so nested path keys are seen
                val t = v.trimStart()
                if (t.startsWith("{") || t.startsWith("[")) {
                    runCatching { scanJson(Json.parseToJsonElement(v), key, path, out) }
                }
            }
            is JsonObject -> for ((k, v) in node) scanJson(v, k, if (path.isEmpty()) k else "$path.$k", out)
            is JsonArray -> node.forEachIndexed { i, v -> scanJson(v, key, "$path.$i", out) }
        }
    }

    /** Extract artifacts from one session's messages (official semantics).
     *  Value = kind + LAST occurrence timestamp (the official row clock). */
    internal fun extract(messages: List<JsonObject>): List<Triple<String, ArtifactKind, Double>> {
        val last = LinkedHashMap<String, Triple<ArtifactKind, Double, Int>>()
        var seq = 0
        for (msg in messages) {
            val ts = prim(msg["timestamp"])?.toDoubleOrNull() ?: 0.0
            val cands = mutableListOf<Cand>()
            val content = prim(msg["content"])
            if (!content.isNullOrEmpty()) {
                val parsed = runCatching { Json.parseToJsonElement(content) }.getOrNull()
                if (parsed != null) scanJson(parsed, null, cands) else scanText(content, cands)
            }
            val tc = msg["tool_calls"]
            val tcStr = prim(tc)
            if (tcStr != null) {
                runCatching { scanJson(Json.parseToJsonElement(tcStr), null, cands) }
                    .onFailure { scanText(tcStr, cands) }
            } else if (tc is JsonArray) {
                scanJson(tc, null, cands)
            }
            // strict candidates win dedup (official order); every occurrence moves the clock
            for (c in cands.sortedByDescending { c2 -> c2.strict }) {
                val prev = last[c.raw]
                if (prev != null) {
                    if (!c.strict) { last[c.raw] = Triple(prev.first, ts, ++seq); continue }
                    val k2 = classify(c.raw, true) ?: continue
                    last[c.raw] = Triple(k2, ts, ++seq); continue
                }
                val kind = classify(c.raw, c.strict) ?: continue
                last[c.raw] = Triple(kind, ts, ++seq)
            }
        }
        return last.entries.map { Triple(it.key, it.value.first, it.value.second) }
    }

    // ── indexing engine ────────────────────────────────────────────────

    data class State(
        val artifacts: List<Artifact> = emptyList(),
        val scanned: Int = 0,
        val total: Int = 0,
        val indexing: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false

    /** Kick a full scan once per app process (incremental via on-disk index). */
    fun ensureStarted(context: Context) {
        if (started) return
        synchronized(this) { if (started) return; started = true }
        scope.launch { runScan(context) }
    }

    private fun kindName(k: ArtifactKind) = k.name

    private suspend fun runScan(context: Context) {
        val dir = File(context.noBackupFilesDir, "artifact-index").apply { mkdirs() }
        val profiles = try {
            com.gravol.hermesclone.di.AppModule.provideRpcClient().profilesList()
        } catch (e: Exception) { emptyList() }
        val rows = mutableListOf<Triple<String, String, JsonRpcSessionRow>>()
        for (p in profiles) {
            var offset = 0
            while (true) {
                val r = DashboardApiClient.apiGet(
                    "/api/sessions?archived=include&limit=100&offset=$offset&profile=${p.name}")
                if (r !is NetworkResult.Success) break
                val arr = (r.data as? JsonObject)?.get("sessions") as? JsonArray ?: break
                if (arr.isEmpty()) break
                for (el in arr) {
                    val o = el as? JsonObject ?: continue
                    val src = o["source"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (src in setOf("cron", "oneshot", "ntfy", "webui")) continue
                    val id = o["id"]?.jsonPrimitive?.contentOrNull ?: continue
                    val title = o["title"]?.jsonPrimitive?.contentOrNull
                    val mc = o["message_count"]?.jsonPrimitive?.longOrNull ?: 0
                    rows.add(Triple(p.name, title ?: "", JsonRpcSessionRow(id, mc)))
                }
                offset += arr.size
                if (arr.size < 100) break
            }
        }
        rows.sortByDescending { it.third.messageCount }
        _state.value = _state.value.copy(total = rows.size, scanned = 0, indexing = true)

        val all = _state.value.artifacts.toMutableList()
        for ((profile, title, row) in rows) {
            val key = sha1(profile + "\u0000" + row.id)
            val file = File(dir, "$key.json")
            var entries: List<Artifact>? = null
            if (file.isFile) {
                entries = runCatching {
                    Json.parseToJsonElement(file.readText()).jsonArray.map { el ->
                        val o = el.jsonObject
                        Artifact(
                            id = o["id"]?.jsonPrimitive?.content ?: "",
                            kind = ArtifactKind.valueOf(o["kind"]?.jsonPrimitive?.content ?: "Other"),
                            source = o["source"]?.jsonPrimitive?.content ?: "",
                            label = o["label"]?.jsonPrimitive?.content ?: "",
                            sessionId = o["sessionId"]?.jsonPrimitive?.content ?: "",
                            sessionTitle = o["sessionTitle"]?.jsonPrimitive?.content ?: "",
                            profile = o["profile"]?.jsonPrimitive?.content ?: profile,
                            createdAt = o["createdAt"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                        )
                    }
                }.getOrNull()
            }
            if (entries == null) {
                entries = indexSession(profile, title, row.id).orEmpty()
                runCatching {
                    file.writeText(Json.encodeToString(JsonArray.serializer(), JsonArray(entries.map { a ->
                        buildJsonObject {
                            put("id", a.id); put("kind", kindName(a.kind)); put("source", a.source)
                            put("label", a.label); put("sessionId", a.sessionId)
                            put("sessionTitle", a.sessionTitle); put("profile", a.profile)
                            put("createdAt", a.createdAt)
                        }
                    })))
                }
            }
            all.removeAll { it.sessionId == row.id && it.profile == profile }
            all.addAll(entries)
            _state.value = _state.value.copy(
                artifacts = all.sortedByDescending { it.createdAt },
                scanned = _state.value.scanned + 1,
            )
        }
        _state.value = _state.value.copy(indexing = false)
    }

    private data class JsonRpcSessionRow(val id: String, val messageCount: Long)

    private suspend fun fetchMessages(profile: String, sid: String): List<JsonObject>? {
        val r = DashboardApiClient.apiGet(
            "/api/sessions/${java.net.URLEncoder.encode(sid, "UTF-8")}/messages?limit=500&profile=${java.net.URLEncoder.encode(profile, "UTF-8")}")
        if (r !is NetworkResult.Success) return null
        return ((r.data as? JsonObject)?.get("messages") as? JsonArray)?.mapNotNull { it as? JsonObject }
    }

    private suspend fun indexSession(profile: String, title: String, sid: String): List<Artifact>? {
        val msgs = fetchMessages(profile, sid) ?: return null
        var displayTitle = title
        if (displayTitle.isBlank()) {
            // official fallback: first user message's first line, truncated
            val first = msgs.firstOrNull { it["role"]?.jsonPrimitive?.contentOrNull == "user" }
                ?.get("content")?.jsonPrimitive?.contentOrNull
            displayTitle = first?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(60)?.trim() ?: ""
        }
        return extract(msgs).map { (source, kind, ts) ->
            Artifact(
                id = sha1("$profile\u0000$sid\u0000$source").take(16),
                kind = kind,
                source = source,
                label = source.substringAfterLast('/').substringAfterLast('\\'),
                sessionId = sid,
                sessionTitle = displayTitle,
                profile = profile,
                createdAt = ts,
            )
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
