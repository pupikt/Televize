package cz.pupik.televize.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

/** Vlastní ikonky počasí – kreslené, ve stylu skla. */
@Composable
fun WeatherIcon(code: Int, day: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val cloudy = code in 2..99
        val heavyCloud = code >= 3
        when {
            code <= 1 -> if (day) sun(Offset(s * .5f, s * .5f), s * .2f) else moon(Offset(s * .5f, s * .5f), s * .26f)
            code == 2 -> {
                if (day) sun(Offset(s * .36f, s * .36f), s * .15f) else moon(Offset(s * .36f, s * .34f), s * .18f)
                cloud(Offset(s * .56f, s * .64f), s * .78f)
            }
            code == 45 || code == 48 -> {
                cloud(Offset(s * .5f, s * .45f), s * .8f)
                for (i in 0..2) drawLine(Color.White.copy(alpha = .7f), Offset(s * (.18f + i * .05f), s * (.72f + i * .1f)), Offset(s * (.82f - i * .05f), s * (.72f + i * .1f)), s * .045f, StrokeCap.Round)
            }
            else -> {
                if (cloudy || heavyCloud) cloud(Offset(s * .5f, s * .42f), s * .86f)
                when (code) {
                    in 51..67, in 80..82 -> for (i in 0..2) {
                        val x = s * (.32f + i * .18f)
                        drawLine(C.cyan, Offset(x, s * .66f), Offset(x - s * .06f, s * .86f), s * .05f, StrokeCap.Round)
                    }
                    in 71..77, 85, 86 -> for (i in 0..2) drawCircle(Color.White, s * .045f, Offset(s * (.32f + i * .18f), s * (.74f + (i % 2) * .08f)))
                    in 95..99 -> {
                        val p = Path().apply {
                            moveTo(s * .54f, s * .58f); lineTo(s * .42f, s * .76f); lineTo(s * .52f, s * .76f); lineTo(s * .44f, s * .94f); lineTo(s * .64f, s * .70f); lineTo(s * .54f, s * .70f); close()
                        }
                        drawPath(p, Color(0xFFFDE68A))
                    }
                }
            }
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0x66FDE68A), Color.Transparent), c, r * 2.6f), r * 2.6f, c)
    drawCircle(Brush.linearGradient(listOf(Color(0xFFFFF4C2), Color(0xFFFCD34D)), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), r, c)
    for (i in 0 until 8) {
        val a = i * Math.PI.toFloat() / 4
        drawLine(Color(0xFFFDE68A), Offset(c.x + cos(a) * r * 1.45f, c.y + sin(a) * r * 1.45f), Offset(c.x + cos(a) * r * 1.85f, c.y + sin(a) * r * 1.85f), r * .22f, StrokeCap.Round)
    }
}

private fun DrawScope.moon(c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0x55C7D2FE), Color.Transparent), c, r * 2.2f), r * 2.2f, c)
    val p = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r))
    }
    val cut = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(c.x - r * .35f, c.y - r * 1.25f, c.x + r * 1.55f, c.y + r * .65f))
    }
    val m = Path().apply { op(p, cut, androidx.compose.ui.graphics.PathOperation.Difference) }
    drawPath(m, Brush.linearGradient(listOf(Color(0xFFF1F5FF), Color(0xFFC7D2FE)), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)))
}

private fun DrawScope.cloud(c: Offset, w: Float) {
    val h = w * .42f
    val p = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(c.x - w * .42f, c.y - h * .05f, c.x + w * .42f, c.y + h * .5f, h * .28f, h * .28f))
        addOval(androidx.compose.ui.geometry.Rect(Offset(c.x - w * .30f, c.y - h * .42f), Size(w * .36f, w * .36f)))
        addOval(androidx.compose.ui.geometry.Rect(Offset(c.x - w * .08f, c.y - h * .72f), Size(w * .44f, w * .44f)))
    }
    drawPath(p, Brush.verticalGradient(listOf(Color.White.copy(alpha = .95f), Color(0xFFB8C2E8)), c.y - h * .8f, c.y + h * .5f))
}

/** Čárové ikonky do tlačítek. */
@Composable
fun LineIcon(kind: String, size: Dp, color: Color = C.text) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val st = Stroke(width = s * .09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            "moon" -> {
                val c = Offset(s * .5f, s * .5f); val r = s * .36f
                val p = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
                val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - r * .2f, c.y - r * 1.3f, c.x + r * 1.6f, c.y + r * .5f)) }
                drawPath(Path().apply { op(p, cut, androidx.compose.ui.graphics.PathOperation.Difference) }, color)
            }
            "timer" -> {
                drawCircle(color, s * .34f, Offset(s * .5f, s * .56f), style = st)
                drawLine(color, Offset(s * .5f, s * .56f), Offset(s * .5f, s * .38f), s * .09f, StrokeCap.Round)
                drawLine(color, Offset(s * .4f, s * .1f), Offset(s * .6f, s * .1f), s * .09f, StrokeCap.Round)
            }
            "grid" -> for (i in 0..1) for (j in 0..1)
                drawRoundRect(color, Offset(s * (.14f + i * .4f), s * (.14f + j * .4f)), Size(s * .3f, s * .3f), androidx.compose.ui.geometry.CornerRadius(s * .08f), style = st)
            "gear" -> {
                drawCircle(color, s * .13f, Offset(s * .5f, s * .5f), style = st)
                for (i in 0 until 8) {
                    val a = i * Math.PI.toFloat() / 4
                    drawLine(color, Offset(s * .5f + cos(a) * s * .26f, s * .5f + sin(a) * s * .26f), Offset(s * .5f + cos(a) * s * .40f, s * .5f + sin(a) * s * .40f), s * .1f, StrokeCap.Round)
                }
                drawCircle(color, s * .27f, Offset(s * .5f, s * .5f), style = Stroke(s * .07f))
            }
            "power" -> {
                drawArc(color, -60f, 300f, false, Offset(s * .18f, s * .2f), Size(s * .64f, s * .64f), style = st)
                drawLine(color, Offset(s * .5f, s * .1f), Offset(s * .5f, s * .46f), s * .09f, StrokeCap.Round)
            }
            "down" -> {
                drawLine(color, Offset(s * .5f, s * .12f), Offset(s * .5f, s * .68f), s * .09f, StrokeCap.Round)
                drawLine(color, Offset(s * .28f, s * .48f), Offset(s * .5f, s * .7f), s * .09f, StrokeCap.Round)
                drawLine(color, Offset(s * .72f, s * .48f), Offset(s * .5f, s * .7f), s * .09f, StrokeCap.Round)
                drawLine(color, Offset(s * .2f, s * .88f), Offset(s * .8f, s * .88f), s * .09f, StrokeCap.Round)
            }
            "home" -> {
                val p = Path().apply { moveTo(s * .14f, s * .5f); lineTo(s * .5f, s * .16f); lineTo(s * .86f, s * .5f); moveTo(s * .24f, s * .42f); lineTo(s * .24f, s * .86f); lineTo(s * .76f, s * .86f); lineTo(s * .76f, s * .42f) }
                drawPath(p, color, style = st)
            }
            "tv" -> {
                drawRoundRect(color, Offset(s * .08f, s * .18f), Size(s * .84f, s * .56f), androidx.compose.ui.geometry.CornerRadius(s * .08f), style = st)
                drawLine(color, Offset(s * .32f, s * .88f), Offset(s * .68f, s * .88f), s * .09f, StrokeCap.Round)
                drawLine(color, Offset(s * .5f, s * .74f), Offset(s * .5f, s * .88f), s * .09f, StrokeCap.Round)
            }
            "check" -> {
                val p = Path().apply { moveTo(s * .18f, s * .52f); lineTo(s * .42f, s * .76f); lineTo(s * .84f, s * .26f) }
                drawPath(p, color, style = st)
            }
            "star" -> {
                val p = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) s * .44f else s * .19f
                    val a = -Math.PI.toFloat() / 2 + i * Math.PI.toFloat() / 5
                    val x = s * .5f + cos(a) * r; val y = s * .54f + sin(a) * r
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close(); drawPath(p, color)
            }
        }
    }
}
