package com.gravol.hermesclone.feature.hub

/**
 * Model-id → display-name transform, reverse-engineered from the official app
 * (device-verified against all 9 ollama-gemma4 rows + the header, 2026-11-12).
 *
 * Rules (all proven by samples):
 *  - tag (after ':'): dropped when it is {latest, cloud} or multi-token
 *    (7b-instruct, 671b-cloud); kept when single non-latest/cloud (4b, 12b).
 *  - base split on '-' and '_' (NOT '.'): qwen2.5 stays one token.
 *  - letter→digit split inside a token only when the letter before the digit
 *    run is a vowel AND the letter run is >1 long:
 *    gemma4 → "Gemma 4", gemma3 → "Gemma 3", but qwen38, dynamic31, iq4, k3,
 *    v3.1, qwen3 stay glued.
 *  - capitalization: digit-initial tokens fully upper (4b→4B, 12b→12B);
 *    letter-initial tokens capitalize first only (iq4→Iq4, xs→Xs, v3.1→V3.1);
 *    brand map for deepseek/openai/xai/gpt; a digits token right after "gpt"
 *    joins with a hyphen ("GPT-6 Luna").
 */

private val BRAND_MAP = mapOf(
    "deepseek" to "DeepSeek", "openai" to "OpenAI", "xai" to "xAI",
    "chatgpt" to "ChatGPT", "gpt" to "GPT", "anthropic" to "Anthropic",
    "gemini" to "Gemini", "claude" to "Claude", "llama" to "LLaMA",
)

private fun capToken(t: String): String {
    BRAND_MAP[t.lowercase()]?.let { return it }
    return if (t.firstOrNull()?.isDigit() == true) t.uppercase()
    else t.replaceFirstChar { it.uppercase() }
}

fun humanizeModel(id: String): String {
    if (id.isBlank()) return id
    val base = id.substringBefore(':')
    val tag = id.substringAfter(':', "")
    val parts = base.split('-', '_').filter { it.isNotBlank() }.toMutableList()
    if (tag.isNotBlank()) {
        val tagTokens = tag.split('-', '_', '.')
        val keep = tagTokens.size == 1 && tag.lowercase() != "latest" && tag.lowercase() != "cloud"
        if (keep) parts.add(tag)
    }
    // vowel letter→digit split
    val split = mutableListOf<String>()
    for (p in parts) {
        val m = Regex("^([a-zA-Z]{2,})(\\d.*)$").find(p)
        if (m != null && m.groupValues[1].last().lowercaseChar() in "aeiou") {
            split.add(m.groupValues[1]); split.add(m.groupValues[2])
        } else split.add(p)
    }
    val capped = split.map(::capToken)
    // "GPT" + digits → "GPT-6"
    val joined = StringBuilder()
    var i = 0
    while (i < capped.size) {
        if (i + 1 < capped.size && capped[i] == "GPT" && capped[i + 1].first().isDigit()) {
            joined.append("GPT-${capped[i + 1]}"); i += 2
        } else { joined.append(capped[i]); i++ }
        if (i < capped.size) joined.append(' ')
    }
    return joined.toString()
}

/** 262144 → "262K" — official divides by 1000, not 1024 (device-verified). */
fun fmtContext(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1000}K".let { if (it.endsWith("000K")) "${n / 1_000_000}M" else it }
    n >= 1000 -> "${n / 1000}K"
    else -> n.toString()
}
