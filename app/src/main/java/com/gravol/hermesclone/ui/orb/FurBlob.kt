// Official "No matching agents" mascot: animated AGSL fur blob.
// character-fur.agsl is lifted verbatim from the official APK assets (app/src/main/assets).
// The fur shader samples a child `body` RuntimeShader for base color; eyes are flat ovals
// drawn on top, matching the reference frames.
package com.gravol.hermesclone.ui.orb

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// The fur shader composites over the body silhouette: alpha > 0.5 marks "inside",
// so the child paints the blob itself (anti-aliased ellipse in pixel space).
private const val BODY_AGSL = """
uniform float3 base;
uniform float2 c;
uniform float2 rr;
uniform float aa;
half4 main(float2 coord) {
    float2 d = (coord - c) / rr;
    float dist = length(d);
    float a = 1.0 - smoothstep(1.0 - aa, 1.0 + aa, dist);
    return half4(base * (0.92 + 0.08 * dist), a);
}
"""

@Composable
fun FurBlob(
    size: Dp,
    seed: Int = 7,
    baseColor: Color = Color(0xFF8FC4EE),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val furSrc = remember {
        runCatching { context.assets.open("character-fur.agsl").bufferedReader().readText() }.getOrNull()
    }
    val supported = furSrc != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) { delay(33L); t += 0.033f }
    }
    val src = furSrc
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val outer = if (supported && src != null) runCatching {
            RuntimeShader(src).apply {
                setInputShader("body", RuntimeShader(BODY_AGSL).apply {
                    setFloatUniform("base", baseColor.red, baseColor.green, baseColor.blue)
                    setFloatUniform("c", w / 2f, h / 2f)
                    setFloatUniform("rr", w * 0.34f, h * 0.34f)
                    setFloatUniform("aa", 0.03f)
                })
                setFloatUniform("time", t * 1000f)
                setFloatUniform("seed", seed.toFloat())
                setFloatUniform("hair", w * 0.16f)
                setFloatUniform("amount", 1.0f)
                setFloatUniform("pixelsPerUnit", w / 90f)
                setFloatUniform("center", w / 2f, h / 2f)
                setFloatUniform("radii", w * 0.34f, h * 0.34f)
                val a = t * 0.9f
                setFloatUniform("rotX", 1f, 0f, sin(a) * 0.08f)
                setFloatUniform("rotY", 0f, 1f, cos(a) * 0.10f)
                setFloatUniform("rotZ", sin(a * 0.7f) * 0.05f, cos(a * 0.6f) * 0.05f, 1f)
                setFloatUniform("comb", 0f, 0f)
            }
        }.getOrNull() else null

        if (outer != null) {
            drawRect(brush = ShaderBrush(outer))
        } else {
            val path = Path().apply {
                moveTo(w * 0.22f, h * 0.55f)
                cubicTo(w * 0.10f, h * 0.20f, w * 0.35f, h * 0.05f, w * 0.55f, h * 0.10f)
                cubicTo(w * 0.85f, h * 0.12f, w * 0.95f, h * 0.45f, w * 0.82f, h * 0.72f)
                cubicTo(w * 0.72f, h * 0.95f, w * 0.30f, h * 0.95f, w * 0.22f, h * 0.55f)
                close()
            }
            drawPath(path, baseColor)
        }
        // Flat eyes on top of the fur.
        val eyeR = w * 0.045f
        val eyeY = h * 0.42f
        drawCircle(Color(0xFF171923), eyeR, Offset(w * 0.42f, eyeY))
        drawCircle(Color(0xFF171923), eyeR, Offset(w * 0.58f, eyeY))
    }
}
