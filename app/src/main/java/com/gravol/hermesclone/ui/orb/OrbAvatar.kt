// Ray-marched "orb" agent avatars, rendered with the app's own AGSL shaders.
package com.gravol.hermesclone.ui.orb

import android.app.Application
import android.graphics.RuntimeShader
import androidx.annotation.RawRes
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gravol.hermesclone.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * One agent identity: which orb shader + the three accent colours.
 *
 * Provisional derivation (until the exact official hash is pinned): stable
 * hash of the agent key picks a hue from the observed palette and one of the
 * five orb variants. The official app derives accent + variant from the agent
 * identity the same way — deterministic per agent, no server data.
 */
data class OrbIdentity(
    @param:RawRes val shaderRes: Int,
    val surface: Color,
    val core: Color,
    val halo: Color,
    val shape: OrbShape,
)

enum class OrbShape { Squircle, Circle, Droplet }

private val palette = listOf(
    0xFF7C50F5, // purple (Hermes)
    0xFF48D8C0, // teal (link-curator)
    0xFF66DD4D, // green (clerk)
    0xFFB072F0, // lilac (job-logger)
    0xFF5B8DEF, // blue
    0xFFE8785F, // coral
    0xFFD050C8, // magenta
    0xFFE8C84A, // gold
)

private val variants = intArrayOf(
    R.raw.orb_nova, R.raw.orb_tide, R.raw.orb_ember, R.raw.orb_pulsar, R.raw.orb_quasar,
)

private val shapes = OrbShape.values()

fun orbIdentityFor(agentKey: String): OrbIdentity {
    // FNV-1a 64-bit: stable across processes/runs, unlike name-ordering luck.
    var h = -0x340d5a8e1e0d9b0dL // == 0xcbf29ce484222325 offset basis
    for (c in agentKey.lowercase()) {
        h = h.xor(c.code.toLong())
        h *= 0x100000001b3L
    }
    h = h.xor(h ushr 33).and(0x7fffffffL)
    val accent = Color(palette[(h % palette.size).toInt()])
    return OrbIdentity(
        shaderRes = variants[((h ushr 3) % variants.size).toInt()],
        surface = accent,
        core = accent.lighten(0.42f),
        halo = accent.darken(0.35f),
        shape = shapes[((h ushr 7) % 3).toInt()],
    )
}

private fun Color.lighten(f: Float): Color = mix(Color.White, f)
private fun Color.darken(f: Float): Color = mix(Color(0xFF06061C), f)
private fun Color.mix(other: Color, f: Float): Color = Color(
    red = red + (other.red - red) * f,
    green = green + (other.green - green) * f,
    blue = blue + (other.blue - blue) * f,
    alpha = 1f,
)

// Shader source cache: read each raw AGSL once per process.
private val sourceCache = ConcurrentHashMap<Int, String>()

private suspend fun shaderSource(app: Application, @RawRes res: Int): String =
    sourceCache.getOrPut(res) {
        withContext(Dispatchers.IO) {
            app.resources.openRawResource(res).bufferedReader().readText()
        }
    }

/**
 * Animated orb tile + the two dark "eyes" the official avatars carry.
 * [size] is the tile edge; eyes scale with it.
 */
@Composable
fun OrbAvatar(
    identity: OrbIdentity,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val app = LocalContext.current.applicationContext as Application
    val source by produceState<String?>(initialValue = null, identity.shaderRes) {
        value = runCatching { shaderSource(app, identity.shaderRes) }.getOrNull()
    }
    val shapeMod = when (identity.shape) {
        OrbShape.Squircle -> modifier.clip(RoundedCornerShape(percent = 28))
        OrbShape.Circle -> modifier.clip(RoundedCornerShape(percent = 50))
        // Teardrop approximated until the exact path is recovered.
        OrbShape.Droplet -> modifier.clip(RoundedCornerShape(topStart = 72.dp, topEnd = 72.dp, bottomStart = 72.dp, bottomEnd = 8.dp))
    }
    Box(
        modifier = shapeMod
            .size(size)
            .background(identity.halo)
    ) {
        val shader: android.graphics.RuntimeShader? = remember(source) {
            source?.let { s -> runCatching { android.graphics.RuntimeShader(s) }.getOrNull() }
        }
        var frame by remember { mutableLongStateOf(0L) }
        if (shader != null && animate) {
            LaunchedEffect(shader) {
                var start = -1L
                withInfiniteAnimationFrameNanos { nanos ->
                    if (start < 0) start = nanos
                    frame = (nanos - start) / 1_000_000L
                }
            }
        }
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            if (shader != null) {
                shader.setFloatUniform("resolution", w, h)
                shader.setFloatUniform("time", frame / 1000f)
                shader.setFloatUniform("detail", 1.0f)
                shader.setFloatUniform("paper", 0.0f)
                shader.setColorUniform("surfaceColor", identity.surface.toArgb())
                shader.setColorUniform("coreColor", identity.core.toArgb())
                shader.setColorUniform("haloColor", identity.halo.toArgb())
                runCatching { drawRect(brush = ShaderBrush(shader), size = Size(w, h)) }
                    .onFailure { drawRect(identity.surface) }
            } else {
                drawRect(identity.surface)
            }
            // Eyes: two dark vertical ellipses, upper-middle of the tile.
            val eyeW = w * 0.085f
            val eyeH = h * 0.13f
            val eyeY = h * 0.40f
            val gap = w * 0.14f
            val eyeColor = Color(0xFF141033)
            drawOval(eyeColor, Offset(w / 2 - gap, eyeY), Size(eyeW * 2, eyeH * 2))
            drawOval(eyeColor, Offset(w / 2 + gap, eyeY), Size(eyeW * 2, eyeH * 2))
        }
    }
}
