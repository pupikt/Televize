package cz.pupik.televize.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import cz.pupik.televize.R
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val speed: Float)

/**
 * Noční obloha: hvězdy, dva závoje polární záře (předrozmazané textury, jen se pomalu
 * posouvají) a zdola záře v barvě právě vybrané aplikace.
 */
@Composable
fun Aurora(animate: Boolean, tint: Color = C.indigo, calm: Float = 0f) {
    val a = ImageBitmap.imageResource(R.drawable.aurora_a)
    val b = ImageBitmap.imageResource(R.drawable.aurora_b)
    val stars = remember {
        val r = Random(11)
        List(70) { Star(r.nextFloat(), r.nextFloat() * 0.62f, 0.5f + r.nextFloat() * 1.3f, r.nextFloat() * 6.28f, 0.5f + r.nextFloat() * 2f) }
    }
    val t = if (animate) {
        val tr = rememberInfiniteTransition(label = "a")
        val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(90_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
        v
    } else 0.31f
    val glow by animateColorAsState(tint, tween(900), label = "tint")

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val ph = (2 * PI * t).toFloat()
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1024), Color(0xFF070A16), Color(0xFF05060D))))

        // hvězdy
        for (s in stars) {
            val tw = 0.45f + 0.55f * sin(ph * 40f * s.speed + s.phase)
            drawCircle(Color.White.copy(alpha = (0.15f + 0.6f * tw) * (1f - 0.5f * s.y)), s.r * density, Offset(s.x * w, s.y * h))
        }

        // závoje – každý jede jinak rychle a jinam, občas zesílí
        veil(b, w, h, dx = 0.07f * sin(ph * 2f + 1.2f), dy = -0.10f + 0.03f * sin(ph * 3f), sx = 1.30f, alpha = (0.50f + 0.14f * sin(ph * 5f)) * (1f - 0.35f * calm))
        veil(a, w, h, dx = 0.09f * sin(ph * 3f + 0.4f), dy = -0.16f + 0.04f * sin(ph * 2f + 2f), sx = 1.22f, alpha = (0.62f + 0.18f * sin(ph * 4f + 1f)) * (1f - 0.35f * calm))

        // záře v barvě vybrané aplikace – zespodu, jako světlo z obrazovky na zdi
        val c = Offset(w * 0.5f, h * 1.12f)
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.42f), glow.copy(alpha = 0.14f), Color.Transparent), c, w * 0.62f), w * 0.62f, c)
        val c2 = Offset(w * 0.08f, h * 0.55f)
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.10f), Color.Transparent), c2, w * 0.4f), w * 0.4f, c2)

        // ztmavení krajů
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)), Offset(w / 2, h / 2), w * 0.72f))
    }
}

private fun DrawScope.veil(img: ImageBitmap, w: Float, h: Float, dx: Float, dy: Float, sx: Float, alpha: Float) {
    val dw = w * sx
    val dh = h * 0.95f
    drawImage(
        img,
        dstOffset = IntOffset(((w - dw) / 2 + dx * w).toInt(), (dy * h).toInt()),
        dstSize = IntSize(dw.toInt(), dh.toInt()),
        alpha = alpha.coerceIn(0f, 1f),
        blendMode = BlendMode.Screen,
        filterQuality = FilterQuality.Low,
    )
}
