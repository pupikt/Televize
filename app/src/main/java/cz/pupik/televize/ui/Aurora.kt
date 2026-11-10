package cz.pupik.televize.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Noční pozadí s pomalu se vlnící polární září. */
@Composable
fun Aurora(animate: Boolean, warm: Float = 0f) {
    val t = if (animate) {
        val tr = rememberInfiniteTransition(label = "a")
        val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
        v
    } else 0.18f
    Canvas(Modifier.fillMaxSize()) {
        drawRect(C.night)
        val a = (2 * PI * t).toFloat()
        blob(Offset(size.width * (0.18f + 0.10f * sin(a)), size.height * (0.10f + 0.08f * cos(a))), size.width * 0.55f, C.cyan.copy(alpha = 0.30f))
        blob(Offset(size.width * (0.78f + 0.08f * cos(a + 1.3f)), size.height * (0.22f + 0.10f * sin(a + 0.7f))), size.width * 0.50f, C.indigo.copy(alpha = 0.34f))
        blob(Offset(size.width * (0.55f + 0.14f * sin(a + 2.6f)), size.height * (1.02f + 0.06f * cos(a))), size.width * 0.48f, C.pink.copy(alpha = 0.14f + 0.16f * warm))
        // jemné ztmavení dole, ať je text čitelný
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startY = size.height * 0.55f))
    }
}

private fun DrawScope.blob(c: Offset, r: Float, color: Color) {
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = color.alpha * 0.35f), Color.Transparent), center = c, radius = r), radius = r, center = c)
}
