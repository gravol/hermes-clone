// Reimplementation of the official generated-blob avatar system.
//
// Algorithm recovered from the decompiled app (mb1.java:1378-1413):
//   seed  = FNV-1a 64 over UTF-8(profileId)
//   rng   = SplitMix64(seed), three draws:
//     draw1 → body form:  <.28 round | <.58 organic | <.72 boxy |
//                           <.84 rectangle | <.93 cloud | else sun
//     draw2 → eye style:  classic round tall wide sleepy curious tiny bold
//     draw3 → palette:    one of 12 (hue-turns, s, v) triples
//   body  = HSV→RGB with floor(c*255+0.5), alpha ff, FLAT finish
//   eyes  = luminance(unrounded rgb) < 0.45 ? #F7F8F2 : #171923
//
// Agents whose profiles carry a saved avatar recipe override the generator;
// those recipes live in the official app's private ProfileAvatars/recipes.json
// and aren't extractable from a non-debuggable install, so the observed ones
// are pinned below from the reference screenshots (colors sampled pixel-exact).
package com.gravol.hermesclone.ui.orb

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class BlobForm { Round, Organic, Boxy, Rectangle, Cloud, Sun }
enum class EyeStyle { Classic, Round, Tall, Wide, Sleepy, Curious, Tiny, Bold }

data class BlobRecipe(
    val seed: ULong,
    val form: BlobForm,
    val eyes: EyeStyle,
    val body: Color,
    val eyeColor: Color,
)

private val PALETTE = arrayOf(
    floatArrayOf(0.01f, 0.58f, 0.96f), floatArrayOf(0.07f, 0.72f, 0.96f),
    floatArrayOf(0.13f, 0.66f, 0.94f), floatArrayOf(0.24f, 0.54f, 0.88f),
    floatArrayOf(0.41f, 0.44f, 0.91f), floatArrayOf(0.49f, 0.63f, 0.76f),
    floatArrayOf(0.56f, 0.50f, 0.94f), floatArrayOf(0.62f, 0.65f, 0.82f),
    floatArrayOf(0.72f, 0.57f, 0.90f), floatArrayOf(0.82f, 0.47f, 0.92f),
    floatArrayOf(0.94f, 0.53f, 0.94f), floatArrayOf(0.62f, 0.12f, 0.36f),
)

private class SplitMix64(var state: ULong) {
    fun nextDouble(): Double {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return ((z xor (z shr 31)) shr 11).toDouble() / 9007199254740992.0
    }
}

/** Unrounded HSV→RGB components (0..1) — the eye-color rule needs raw values. */
private fun hsvRaw(h: Float, s: Float, v: Float): FloatArray {
    val i = (h * 6f) % 6f
    val f = i - i.toInt()
    val p = v * (1 - s); val q = v * (1 - f * s); val t = v * (1 - (1 - f) * s)
    return when (i.toInt()) {
        0 -> floatArrayOf(v, t, p)
        1 -> floatArrayOf(q, v, p)
        2 -> floatArrayOf(p, v, t)
        3 -> floatArrayOf(p, q, v)
        4 -> floatArrayOf(t, p, v)
        else -> floatArrayOf(v, p, q)
    }
}

private fun recipeForUncached(profileId: String): BlobRecipe {
    var seed = 0xCBF29CE484222325uL
    for (b in profileId.toByteArray(Charsets.UTF_8)) {
        seed = (seed xor b.toUByte().toULong()) * 0x100000001B3uL
    }
    val rng = SplitMix64(seed)
    val d1 = rng.nextDouble()
    val form = when {
        d1 < 0.28 -> BlobForm.Round
        d1 < 0.58 -> BlobForm.Organic
        d1 < 0.72 -> BlobForm.Boxy
        d1 < 0.84 -> BlobForm.Rectangle
        d1 < 0.93 -> BlobForm.Cloud
        else -> BlobForm.Sun
    }
    val eyes = EyeStyle.entries[(rng.nextDouble() * 8).toInt().coerceIn(0, 7)]
    val hsv = PALETTE[(rng.nextDouble() * 12).toInt().coerceIn(0, 11)]
    val raw = hsvRaw(hsv[0], hsv[1], hsv[2])
    val body = Color(
        (raw[0] * 255f + 0.5f).toInt().coerceIn(0, 255),
        (raw[1] * 255f + 0.5f).toInt().coerceIn(0, 255),
        (raw[2] * 255f + 0.5f).toInt().coerceIn(0, 255),
    )
    val lum = 0.2126 * raw[0] + 0.7152 * raw[1] + 0.0722 * raw[2]
    val eyeColor = if (lum < 0.45) Color(0xFFF7F8F2) else Color(0xFF171923)
    return BlobRecipe(seed, form, eyes, body, eyeColor)
}

private fun saved(seed: ULong, form: BlobForm, eyes: EyeStyle, bodyArgb: Long) =
    BlobRecipe(seed, form, eyes, Color(bodyArgb.toInt()), Color(0xFF171923.toInt()))

// Live-gateway profiles whose avatars come from SAVED recipes (generator alone
// doesn't reproduce them). Colors sampled pixel-exact from the official app;
// shapes read off the same screenshots.
private val savedRecipeOverrides: Map<String, BlobRecipe> = mapOf(
    "hermes" to saved(0x484552u, BlobForm.Boxy, EyeStyle.Classic, 0xFF8B5CF6),
    "clerk" to saved(0x434C45u, BlobForm.Boxy, EyeStyle.Classic, 0xFF66DD4B),
    "link-curator" to saved(0x4C494Eu, BlobForm.Organic, EyeStyle.Classic, 0xFF4BDDC7),
    "job-logger" to saved(0x4A4F42u, BlobForm.Round, EyeStyle.Classic, 0xFF854BDD),
)

private val recipeCache = HashMap<String, BlobRecipe>()

fun recipeFor(profileId: String): BlobRecipe {
    val key = profileId.lowercase()
    savedRecipeOverrides[key]?.let { return it }
    return recipeCache.getOrPut(key) { recipeForUncached(key) }
}

// ─── renderer ───

@Composable
fun BlobAvatar(
    recipe: BlobRecipe,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    animate: Boolean = true,
) {
    var t by remember { mutableFloatStateOf(0f) }
    if (animate) {
        LaunchedEffect(recipe.seed) {
            var start = -1L
            withInfiniteAnimationFrameNanos { nanos ->
                if (start < 0) start = nanos
                t = (nanos - start) / 1_000_000_000f
            }
        }
    }
    Canvas(modifier = modifier.size(size)) {
        drawBlob(recipe, t, animate)
    }
}

private fun DrawScope.drawBlob(recipe: BlobRecipe, t: Float, animate: Boolean) {
    val w = size.width
    val h = size.height
    val phase = (recipe.seed and 0xFFFFuL).toFloat() / 65535f * 2f * PI.toFloat()

    // Idle motion: ±1.5% bob + ±2° sway.
    val bob = if (animate) sin(t * 1.3f + phase) * h * 0.015f else 0f
    val tilt = if (animate) sin(t * 0.8f + phase) * 2f else 0f

    // Blink: 130 ms every 2.4–5.2 s, phase-locked to the seed.
    val blinkPeriod = 2.4f + ((recipe.seed shr 5) and 0xFFuL).toFloat() / 255f * 2.8f
    val blinkOn = animate && (t + phase * blinkPeriod) % blinkPeriod < 0.13f

    rotate(degrees = tilt, pivot = Offset(w / 2, h / 2)) {
        val inset = w * 0.06f
        val body = when (recipe.form) {
            BlobForm.Round -> Path().apply { addOval(Rect(inset, inset, w - inset, h - inset)) }
            BlobForm.Boxy -> Path().apply {
                addRoundRect(RoundRect(Rect(inset, inset, w - inset, h - inset), CornerRadius(w * 0.28f)))
            }
            BlobForm.Rectangle -> Path().apply {
                addRoundRect(RoundRect(Rect(w * 0.13f, h * 0.06f, w * 0.87f, h * 0.94f), CornerRadius(w * 0.18f)))
            }
            BlobForm.Organic -> organicPath(recipe.seed)
            BlobForm.Cloud -> cloudPath(recipe.seed)
            BlobForm.Sun -> sunPath(recipe.seed)
        }
        drawPath(body, recipe.body)
        drawEyes(recipe.eyes, recipe.eyeColor, blinkOn, bob)
    }
}

/** Wobbly circle: 9 control radii jittered ±10% by the seed, pixel space. */
private fun DrawScope.organicPath(seed: ULong): Path {
    val rnd = Random(seed.toLong())
    val n = 9
    val radii = FloatArray(n) { (0.44f + (rnd.nextFloat() - 0.5f) * 0.10f) * size.minDimension }
    return smoothBlob(radii, n)
}

private fun DrawScope.cloudPath(seed: ULong): Path {
    val rnd = Random(seed.toLong())
    val n = 6
    val base = size.minDimension
    val path = Path()
    for (i in 0 until n) {
        val a = i * 2f * PI.toFloat() / n
        val r = (0.34f + 0.06f + rnd.nextFloat() * 0.06f) * base
        val x = size.width / 2 + cos(a) * r
        val y = size.height / 2 + sin(a) * r
        if (i == 0) path.moveTo(x, y)
        else {
            val am = (i - 0.5f) * 2f * PI.toFloat() / n
            val rm = r * 1.35f
            path.quadraticTo(
                size.width / 2 + cos(am) * rm, size.height / 2 + sin(am) * rm, x, y)
        }
    }
    path.close()
    return path
}

private fun DrawScope.sunPath(seed: ULong): Path {
    val rnd = Random(seed.toLong())
    val n = 9 + rnd.nextInt(4)
    val base = size.minDimension
    val path = Path()
    for (i in 0 until n * 2) {
        val a = i * PI.toFloat() / n
        val r = (if (i % 2 == 0) 0.46f else 0.33f) * base
        val x = size.width / 2 + cos(a) * r
        val y = size.height / 2 + sin(a) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun DrawScope.smoothBlob(radii: FloatArray, n: Int): Path {
    val path = Path()
    val cx = size.width / 2
    val cy = size.height / 2
    fun pt(i: Int): Offset {
        val a = i * 2f * PI.toFloat() / n
        return Offset(cx + cos(a) * radii[i % n], cy + sin(a) * radii[i % n])
    }
    val p0 = pt(0); path.moveTo(p0.x, p0.y)
    for (i in 1..n) {
        val p1 = pt(i)
        val am = (i - 0.5f) * 2f * PI.toFloat() / n
        val rm = (radii[(i - 1) % n] + radii[i % n]) / 2f * 1.16f
        path.cubicTo(
            cx + cos(am - 0.22f) * rm, cy + sin(am - 0.22f) * rm,
            cx + cos(am + 0.22f) * rm, cy + sin(am + 0.22f) * rm,
            p1.x, p1.y)
    }
    path.close()
    return path
}

private fun DrawScope.drawEyes(style: EyeStyle, color: Color, blink: Boolean, bob: Float) {
    val w = size.width; val h = size.height
    val cy = h * 0.42f + bob
    val gap = w * 0.135f
    fun eye(dx: Float, rx: Float, ry: Float) {
        val ryEff = if (blink) h * 0.012f else ry
        drawOval(color, Offset(w / 2 + dx, cy), Size(rx * 2, ryEff * 2))
    }
    when (style) {
        EyeStyle.Classic -> { eye(-gap, w * 0.055f, h * 0.085f); eye(gap, w * 0.055f, h * 0.085f) }
        EyeStyle.Round -> { eye(-gap, w * 0.065f, w * 0.065f); eye(gap, w * 0.065f, w * 0.065f) }
        EyeStyle.Tall -> { eye(-gap, w * 0.045f, h * 0.115f); eye(gap, w * 0.045f, h * 0.115f) }
        EyeStyle.Wide -> { eye(-gap, w * 0.085f, h * 0.055f); eye(gap, w * 0.085f, h * 0.055f) }
        EyeStyle.Sleepy -> {
            eye(-gap, w * 0.055f, h * 0.05f); eye(gap, w * 0.055f, h * 0.05f)
            drawRect(color, Offset(w / 2 - gap - w * 0.06f, cy - h * 0.075f), Size(w * 0.12f, h * 0.03f))
            drawRect(color, Offset(w / 2 + gap - w * 0.06f, cy - h * 0.075f), Size(w * 0.12f, h * 0.03f))
        }
        EyeStyle.Curious -> {
            eye(-gap, w * 0.045f, h * 0.07f)
            drawOval(color, Offset(w / 2 + gap, cy - h * 0.02f + bob), Size(w * 0.15f, h * 0.19f))
        }
        EyeStyle.Tiny -> { eye(-gap, w * 0.035f, h * 0.05f); eye(gap, w * 0.035f, h * 0.05f) }
        EyeStyle.Bold -> { eye(-gap, w * 0.075f, h * 0.11f); eye(gap, w * 0.075f, h * 0.11f) }
    }
}
