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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlinx.coroutines.delay
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
    animate: Boolean = false,
) {
    var t by remember { mutableFloatStateOf(0f) }
    var blinkOn by remember { mutableStateOf(false) }
    // Look & Feel ▸ Reduce motion ("keep bot characters still") — the official
    // switch stops idle breathing/blinking, so it gates the same animation loop
    // as the system animator scale.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion by remember {
        com.gravol.hermesclone.di.AppModule.provideDataStoreManager(ctx)
            .getBoolean(com.gravol.hermesclone.feature.settings.SettingsKeys.REDUCE_MOTION)
    }.collectAsState(initial = null)
    val reduced = reduceMotion == true || if (animate) {
        try {
            android.provider.Settings.Global.getFloat(
                ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) < 0.5f
        } catch (e: Exception) { false }
    } else false
    val anim = animate && !reduced
    if (anim) {
        LaunchedEffect(recipe.seed) {
            var start = -1L
            while (true) {
                withInfiniteAnimationFrameNanos { nanos ->
                    if (start < 0) start = nanos
                    t = (nanos - start) / 1_000_000_000f
                }
            }
        }
        // Official idle blink spec (motions-v2.json): first blink 2.6 s, then
        // random 3.4-6.2 s intervals, 190 ms duration.
        LaunchedEffect(recipe.seed) {
            val rnd = java.util.Random((recipe.seed and 0xFFFFFFFFuL).toLong())
            delay(2300L + rnd.nextLong(600L))
            while (true) {
                blinkOn = true
                delay(190L)
                blinkOn = false
                delay(3400L + rnd.nextLong(2800L))
            }
        }
    }
    Canvas(modifier = modifier.size(size)) {
        drawBlob(recipe, t, anim, blinkOn)
    }
}

// ─── official empty-state mascot ───
// Geometry is the official catalog's "organic" form (reference/resources/
// unknown/characters/catalog.json): a 360-sample radius profile, normalized so
// the silhouette fills its canvas. Overlaid against the official mascot on the
// Pixel 8 that outline scores IoU 0.92 (edge-only difference).
// Body/eye colours and eye geometry were measured on the official app:
// 126x127 px blob, 13x27 px capsule eyes centred +-13.25 px, #78C1EA / #171923.
private val MASCOT_PROFILE = floatArrayOf(
    0.9992f, 0.5139f, 0.9996f, 0.5229f, 1.0000f, 0.5319f, 1.0000f, 0.5409f, 0.9999f, 0.5499f, 0.9995f, 0.5589f, 0.9989f, 0.5679f, 0.9981f, 0.5769f,
    0.9971f, 0.5859f, 0.9959f, 0.5948f, 0.9944f, 0.6037f, 0.9929f, 0.6126f, 0.9910f, 0.6215f, 0.9891f, 0.6303f, 0.9869f, 0.6390f, 0.9847f, 0.6478f,
    0.9822f, 0.6564f, 0.9796f, 0.6651f, 0.9768f, 0.6737f, 0.9740f, 0.6822f, 0.9710f, 0.6907f, 0.9680f, 0.6992f, 0.9648f, 0.7076f, 0.9616f, 0.7160f,
    0.9582f, 0.7244f, 0.9548f, 0.7328f, 0.9513f, 0.7411f, 0.9477f, 0.7494f, 0.9440f, 0.7577f, 0.9403f, 0.7660f, 0.9365f, 0.7742f, 0.9326f, 0.7824f,
    0.9286f, 0.7907f, 0.9246f, 0.7989f, 0.9204f, 0.8071f, 0.9162f, 0.8152f, 0.9118f, 0.8234f, 0.9073f, 0.8314f, 0.9027f, 0.8395f, 0.8980f, 0.8475f,
    0.8931f, 0.8554f, 0.8880f, 0.8633f, 0.8828f, 0.8710f, 0.8775f, 0.8787f, 0.8719f, 0.8862f, 0.8662f, 0.8937f, 0.8602f, 0.9009f, 0.8541f, 0.9081f,
    0.8477f, 0.9150f, 0.8411f, 0.9217f, 0.8344f, 0.9282f, 0.8274f, 0.9346f, 0.8202f, 0.9406f, 0.8129f, 0.9464f, 0.8053f, 0.9520f, 0.7975f, 0.9572f,
    0.7896f, 0.9623f, 0.7815f, 0.9669f, 0.7732f, 0.9714f, 0.7647f, 0.9754f, 0.7562f, 0.9793f, 0.7475f, 0.9827f, 0.7387f, 0.9859f, 0.7297f, 0.9886f,
    0.7207f, 0.9912f, 0.7116f, 0.9933f, 0.7024f, 0.9952f, 0.6932f, 0.9967f, 0.6839f, 0.9980f, 0.6746f, 0.9989f, 0.6653f, 0.9995f, 0.6560f, 1.0000f,
    0.6466f, 1.0000f, 0.6374f, 0.9999f, 0.6281f, 0.9994f, 0.6189f, 0.9989f, 0.6098f, 0.9979f, 0.6007f, 0.9969f, 0.5917f, 0.9956f, 0.5828f, 0.9943f,
    0.5739f, 0.9927f, 0.5651f, 0.9910f, 0.5565f, 0.9892f, 0.5479f, 0.9873f, 0.5394f, 0.9854f, 0.5310f, 0.9834f, 0.5227f, 0.9813f, 0.5145f, 0.9793f,
    0.5064f, 0.9773f, 0.4983f, 0.9753f, 0.4904f, 0.9734f, 0.4825f, 0.9715f, 0.4746f, 0.9698f, 0.4668f, 0.9681f, 0.4591f, 0.9666f, 0.4513f, 0.9651f,
    0.4436f, 0.9638f, 0.4359f, 0.9627f, 0.4281f, 0.9617f, 0.4203f, 0.9608f, 0.4125f, 0.9601f, 0.4047f, 0.9596f, 0.3967f, 0.9592f, 0.3887f, 0.9590f,
    0.3806f, 0.9590f, 0.3724f, 0.9590f, 0.3640f, 0.9593f, 0.3555f, 0.9596f, 0.3469f, 0.9602f, 0.3381f, 0.9608f, 0.3292f, 0.9615f, 0.3201f, 0.9622f,
    0.3108f, 0.9631f, 0.3014f, 0.9639f, 0.2917f, 0.9648f, 0.2820f, 0.9656f, 0.2720f, 0.9665f, 0.2619f, 0.9672f, 0.2516f, 0.9678f, 0.2412f, 0.9682f,
    0.2306f, 0.9686f, 0.2200f, 0.9686f, 0.2093f, 0.9686f, 0.1986f, 0.9681f, 0.1877f, 0.9674f, 0.1770f, 0.9663f, 0.1662f, 0.9649f, 0.1555f, 0.9631f,
    0.1449f, 0.9608f, 0.1344f, 0.9582f, 0.1242f, 0.9550f, 0.1141f, 0.9514f, 0.1043f, 0.9472f, 0.0947f, 0.9428f, 0.0856f, 0.9375f, 0.0766f, 0.9321f,
    0.0684f, 0.9258f, 0.0602f, 0.9193f, 0.0528f, 0.9121f, 0.0456f, 0.9047f, 0.0391f, 0.8966f, 0.0329f, 0.8883f, 0.0274f, 0.8795f, 0.0223f, 0.8704f,
    0.0178f, 0.8609f, 0.0139f, 0.8511f, 0.0103f, 0.8412f, 0.0075f, 0.8308f, 0.0049f, 0.8205f, 0.0031f, 0.8098f, 0.0015f, 0.7992f, 0.0007f, 0.7882f,
    0.0000f, 0.7774f, 0.0000f, 0.7664f, 0.0002f, 0.7555f, 0.0008f, 0.7446f, 0.0017f, 0.7338f, 0.0030f, 0.7230f, 0.0045f, 0.7123f, 0.0062f, 0.7017f,
    0.0082f, 0.6913f, 0.0103f, 0.6810f, 0.0125f, 0.6708f, 0.0149f, 0.6608f, 0.0174f, 0.6510f, 0.0199f, 0.6413f, 0.0224f, 0.6318f, 0.0250f, 0.6225f,
    0.0275f, 0.6133f, 0.0299f, 0.6044f, 0.0323f, 0.5955f, 0.0346f, 0.5869f, 0.0368f, 0.5783f, 0.0388f, 0.5699f, 0.0407f, 0.5617f, 0.0424f, 0.5535f,
    0.0440f, 0.5454f, 0.0453f, 0.5375f, 0.0465f, 0.5296f, 0.0474f, 0.5217f, 0.0482f, 0.5139f, 0.0486f, 0.5061f, 0.0490f, 0.4983f, 0.0490f, 0.4905f,
    0.0490f, 0.4827f, 0.0486f, 0.4748f, 0.0482f, 0.4669f, 0.0475f, 0.4589f, 0.0467f, 0.4509f, 0.0457f, 0.4427f, 0.0446f, 0.4345f, 0.0435f, 0.4261f,
    0.0422f, 0.4176f, 0.0408f, 0.4090f, 0.0395f, 0.4003f, 0.0380f, 0.3914f, 0.0366f, 0.3824f, 0.0352f, 0.3733f, 0.0339f, 0.3640f, 0.0327f, 0.3546f,
    0.0316f, 0.3451f, 0.0305f, 0.3355f, 0.0297f, 0.3258f, 0.0290f, 0.3160f, 0.0286f, 0.3061f, 0.0284f, 0.2962f, 0.0284f, 0.2862f, 0.0288f, 0.2762f,
    0.0294f, 0.2661f, 0.0304f, 0.2562f, 0.0316f, 0.2462f, 0.0335f, 0.2364f, 0.0355f, 0.2266f, 0.0381f, 0.2170f, 0.0409f, 0.2075f, 0.0443f, 0.1982f,
    0.0480f, 0.1891f, 0.0522f, 0.1802f, 0.0569f, 0.1716f, 0.0619f, 0.1632f, 0.0674f, 0.1552f, 0.0732f, 0.1473f, 0.0795f, 0.1400f, 0.0861f, 0.1328f,
    0.0932f, 0.1262f, 0.1004f, 0.1197f, 0.1082f, 0.1139f, 0.1161f, 0.1082f, 0.1244f, 0.1031f, 0.1328f, 0.0981f, 0.1415f, 0.0937f, 0.1504f, 0.0895f,
    0.1594f, 0.0857f, 0.1685f, 0.0822f, 0.1777f, 0.0790f, 0.1871f, 0.0761f, 0.1964f, 0.0734f, 0.2058f, 0.0710f, 0.2151f, 0.0687f, 0.2245f, 0.0667f,
    0.2338f, 0.0648f, 0.2431f, 0.0631f, 0.2523f, 0.0614f, 0.2615f, 0.0599f, 0.2706f, 0.0584f, 0.2796f, 0.0570f, 0.2885f, 0.0556f, 0.2973f, 0.0542f,
    0.3061f, 0.0528f, 0.3147f, 0.0514f, 0.3233f, 0.0500f, 0.3318f, 0.0485f, 0.3403f, 0.0470f, 0.3487f, 0.0454f, 0.3570f, 0.0438f, 0.3653f, 0.0421f,
    0.3735f, 0.0403f, 0.3818f, 0.0385f, 0.3900f, 0.0366f, 0.3982f, 0.0347f, 0.4064f, 0.0327f, 0.4146f, 0.0307f, 0.4229f, 0.0286f, 0.4312f, 0.0265f,
    0.4395f, 0.0244f, 0.4478f, 0.0223f, 0.4562f, 0.0202f, 0.4647f, 0.0182f, 0.4732f, 0.0161f, 0.4817f, 0.0141f, 0.4904f, 0.0122f, 0.4991f, 0.0104f,
    0.5078f, 0.0086f, 0.5167f, 0.0070f, 0.5256f, 0.0054f, 0.5345f, 0.0041f, 0.5435f, 0.0029f, 0.5526f, 0.0019f, 0.5617f, 0.0011f, 0.5708f, 0.0005f,
    0.5800f, 0.0002f, 0.5892f, 0.0000f, 0.5984f, 0.0002f, 0.6076f, 0.0006f, 0.6168f, 0.0014f, 0.6260f, 0.0022f, 0.6351f, 0.0036f, 0.6443f, 0.0052f,
    0.6533f, 0.0072f, 0.6623f, 0.0093f, 0.6711f, 0.0119f, 0.6799f, 0.0148f, 0.6886f, 0.0179f, 0.6972f, 0.0214f, 0.7056f, 0.0252f, 0.7139f, 0.0294f,
    0.7221f, 0.0337f, 0.7301f, 0.0385f, 0.7379f, 0.0433f, 0.7455f, 0.0487f, 0.7530f, 0.0541f, 0.7602f, 0.0599f, 0.7674f, 0.0658f, 0.7742f, 0.0721f,
    0.7809f, 0.0785f, 0.7874f, 0.0851f, 0.7937f, 0.0919f, 0.7998f, 0.0988f, 0.8057f, 0.1059f, 0.8115f, 0.1131f, 0.8170f, 0.1205f, 0.8224f, 0.1278f,
    0.8276f, 0.1354f, 0.8327f, 0.1429f, 0.8376f, 0.1505f, 0.8424f, 0.1581f, 0.8470f, 0.1658f, 0.8516f, 0.1734f, 0.8560f, 0.1811f, 0.8604f, 0.1887f,
    0.8648f, 0.1964f, 0.8690f, 0.2040f, 0.8732f, 0.2116f, 0.8774f, 0.2191f, 0.8816f, 0.2266f, 0.8857f, 0.2341f, 0.8898f, 0.2416f, 0.8940f, 0.2490f,
    0.8981f, 0.2564f, 0.9022f, 0.2638f, 0.9064f, 0.2711f, 0.9105f, 0.2785f, 0.9147f, 0.2859f, 0.9189f, 0.2932f, 0.9230f, 0.3006f, 0.9272f, 0.3080f,
    0.9314f, 0.3155f, 0.9355f, 0.3229f, 0.9396f, 0.3305f, 0.9436f, 0.3380f, 0.9476f, 0.3457f, 0.9516f, 0.3534f, 0.9554f, 0.3612f, 0.9592f, 0.3690f,
    0.9629f, 0.3770f, 0.9665f, 0.3849f, 0.9699f, 0.3931f, 0.9732f, 0.4012f, 0.9763f, 0.4095f, 0.9793f, 0.4178f, 0.9821f, 0.4263f, 0.9848f, 0.4347f,
    0.9872f, 0.4433f, 0.9894f, 0.4520f, 0.9915f, 0.4607f, 0.9933f, 0.4694f, 0.9949f, 0.4782f, 0.9963f, 0.4871f, 0.9975f, 0.4960f, 0.9984f, 0.5049f,
)

private val MASCOT_BODY = Color(0xFF78C1EA)
private val MASCOT_EYE = Color(0xFF171923)

// Idle breathing, measured from the official mascot (361 frames @30 fps,
// 2026-11-07): one 3.96 s cycle, height +-1.72% and width -+0.52% of the blob
// (counter-phase), bbox centre rising +-0.64% -> the squash pivots near the
// bottom edge (0.872 h), not the centre. No continuous tilt: tilt only exists
// as an expression step.
private const val BREATHE_PERIOD_S = 3.96f
private const val BREATHE_SCALE_Y = 0.0172f
private const val BREATHE_SCALE_X = 0.0052f
private const val BREATHE_PIVOT_Y = 0.872f

// Idle expression walk, transcribed from the official motions-v2.json "idle"
// motion: hold/transition pairs in ms, eye x offsets from the measured holds
// (neutral 0, glanceRight -14.4 px, tiltLeft +6.8 px, glanceLeft +14.4 px on a
// 126 px blob). Only x was separable from the capture; the spec's small y
// offsets are inside the breathing noise.
//
// tiltDeg: the official also rotates the EYE PAIR (not the body — measured body
// axis ratio unchanged) per step: neutral dcy 0.0 px, glanceRight +0.8 px,
// tiltLeft +3.6 px across the 26.7 px eye spacing (left eye lower), i.e. -1.9°
// and -7.7°. That matches the spec: tiltDeg ≈ 0.96 * expression dy
// (tiltLeft dy -8 -> +7.7°, glanceRight dy -2 -> +1.9°). glanceLeft (+1.9°) is
// the spec mirror of glanceRight and was not captured.
private class IdleStep(val dx: Float, val tiltDeg: Float, val holdMs: Int, val transitionMs: Int)
private val IDLE_STEPS = arrayOf(
    IdleStep(0f, 0f, 3800, 800),
    IdleStep(-14.4f / 126f, -1.9f, 1800, 700),
    IdleStep(0f, 0f, 3000, 700),
    IdleStep(6.8f / 126f, -7.7f, 2200, 900),
    IdleStep(0f, 0f, 2600, 800),
    IdleStep(14.4f / 126f, 1.9f, 1800, 700),
)
private val IDLE_CYCLE_MS = IDLE_STEPS.sumOf { it.holdMs + it.transitionMs }

private fun smoothstep(x: Float): Float = x * x * (3f - 2f * x)

/** Current idle-step value (dx fraction or tilt degrees), smoothstep-blended. */
private inline fun idleStepValue(tMs: Long, pick: (IdleStep) -> Float): Float {
    var rem = (tMs % IDLE_CYCLE_MS).toFloat()
    for (i in IDLE_STEPS.indices) {
        val step = IDLE_STEPS[i]
        val next = IDLE_STEPS[(i + 1) % IDLE_STEPS.size]
        if (rem < step.holdMs) return pick(step)
        rem -= step.holdMs
        if (rem < step.transitionMs && step.transitionMs > 0) {
            val k = smoothstep(rem / step.transitionMs)
            return pick(step) + (pick(next) - pick(step)) * k
        }
        rem -= step.transitionMs
    }
    return pick(IDLE_STEPS[0])
}

/**
 * The official chat/search empty-state mascot. Animates idle breathing, blinks
 * (motions-v2 idle: 190 ms, first at 2.6 s, then random 3.4-6.2 s) and the
 * idle eye-glance walk. Honours the system animator duration scale.
 */
@Composable
fun MascotAvatar(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion by remember {
        com.gravol.hermesclone.di.AppModule.provideDataStoreManager(ctx)
            .getBoolean(com.gravol.hermesclone.feature.settings.SettingsKeys.REDUCE_MOTION)
    }.collectAsState(initial = null)
    val reduced = reduceMotion == true || remember {
        try {
            android.provider.Settings.Global.getFloat(
                ctx.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) < 0.5f
        } catch (e: Exception) {
            false
        }
    }
    var tMs by remember { mutableLongStateOf(0L) }
    var blinkStart by remember { mutableLongStateOf(-1L) }
    if (!reduced) {
        LaunchedEffect(Unit) {
            var start = -1L
            while (true) {
                withInfiniteAnimationFrameNanos { nanos ->
                    if (start < 0L) start = nanos
                    tMs = (nanos - start) / 1_000_000L
                }
            }
        }
        LaunchedEffect(Unit) {
            val rnd = java.util.Random()
            delay(2600L)
            while (true) {
                blinkStart = tMs
                delay(190L)
                blinkStart = -1L
                delay(3400L + (rnd.nextDouble() * 2800L).toLong())
            }
        }
    }
    Canvas(modifier = modifier.size(size)) {
        drawMascot(tMs, blinkStart, reduced)
    }
}

private fun DrawScope.drawMascot(tMs: Long, blinkStart: Long, still: Boolean) {
    val w = size.width
    val h = size.height
    val s = if (still) 0f else sin(tMs / 1000f * (2f * PI.toFloat() / BREATHE_PERIOD_S))
    val scaleY = 1f + BREATHE_SCALE_Y * s
    val scaleX = 1f - BREATHE_SCALE_X * s
    scale(scaleX, scaleY, pivot = Offset(w / 2, h * BREATHE_PIVOT_Y)) {
        val body = Path()
        val n = MASCOT_PROFILE.size / 2
        for (i in 0 until n) {
            val x = MASCOT_PROFILE[i * 2] * w
            val y = MASCOT_PROFILE[i * 2 + 1] * h
            if (i == 0) body.moveTo(x, y) else body.lineTo(x, y)
        }
        body.close()
        drawPath(body, MASCOT_BODY)

        val close = if (blinkStart >= 0L) {
            val phase = ((tMs - blinkStart).coerceAtLeast(0L) / 190f).coerceIn(0f, 1f)
            sin(phase * PI.toFloat()) * 0.60f
        } else 0f
        val eyeW = w * 0.1032f
        val eyeH = h * 0.2126f * (1f - close)
        val eyeDx = idleStepValue(tMs) { it.dx } * w
        val eyeTilt = idleStepValue(tMs) { it.tiltDeg }
        val eyeCy = h * 0.5f
        val halfW = eyeW / 2f
        val eyeCap = minOf(halfW, eyeH / 2f)
        // Official tilts the eye PAIR per idle step (tiltLeft -> left eye lower,
        // right eye higher, ~7.7° across the pair); the body itself stays upright.
        rotate(degrees = eyeTilt, pivot = Offset(w / 2f + eyeDx, eyeCy)) {
            for (side in intArrayOf(-1, 1)) {
                drawRoundRect(
                    color = MASCOT_EYE,
                    topLeft = Offset(w / 2f + eyeDx + side * w * 0.1052f - halfW, eyeCy - eyeH / 2f),
                    size = Size(eyeW, eyeH),
                    cornerRadius = CornerRadius(eyeCap, eyeCap),
                )
            }
        }
    }
}

private fun DrawScope.drawBlob(recipe: BlobRecipe, t: Float, animate: Boolean, blinkOn: Boolean) {
    val w = size.width
    val h = size.height
    val phase = (recipe.seed and 0xFFFFuL).toFloat() / 65535f * 2f * PI.toFloat()

    // Official idle breathe, frame-measured on the official mascot (361 frames
    // @30 fps, 2026-11-07): single 3.96 s cycle — height ±3.5%, width ∓2%
    // (volume-preserving counter-phase), vertical ±1.1% of size in phase with
    // height. No continuous tilt (official tilt is an expression step only).
    val s = if (animate) sin(t * (2f * PI.toFloat() / 3.96f) + phase) else 0f
    val scaleY = 1f + 0.035f * s
    val scaleX = 1f - 0.020f * s
    val bob = 0.011f * h * s

    translate(left = 0f, top = bob) {
     scale(scaleX, scaleY, pivot = Offset(w / 2, h / 2)) {
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
        drawEyes(recipe.eyes, recipe.eyeColor, blinkOn, 0f)
     }
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
