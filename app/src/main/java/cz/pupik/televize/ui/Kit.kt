package cz.pupik.televize.ui

import android.view.KeyEvent as AKey
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import cz.pupik.televize.R

// ---------- barvy (stejná noční paleta jako Domácnost) ----------
object C {
    val night = Color(0xFF0A0D18)
    val cyan = Color(0xFF38BDF8)
    val indigo = Color(0xFF818CF8)
    val pink = Color(0xFFF472B6)
    val text = Color(0xFFF4F6FF)
    val dim = Color(0xB3E6EAFF)
    val faint = Color(0x73E6EAFF)
}

// ---------- písmo Manrope (proměnné) ----------
@OptIn(ExperimentalTextApi::class)
private fun w(x: Int) = Font(R.font.manrope, FontWeight(x), variationSettings = FontVariation.Settings(FontVariation.weight(x)))
val Manrope = FontFamily(w(200), w(300), w(400), w(500), w(600), w(700), w(800))

fun ts(size: TextUnit, weight: Int = 500, color: Color = C.text, spacing: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = Manrope, fontWeight = FontWeight(weight), fontSize = size, color = color, letterSpacing = spacing)

// ---------- Liquid Glass ----------
/** Průsvitné sklo: jemná výplň, světlá hrana nahoře, odlesk vlevo nahoře. */
fun Modifier.glass(shape: Shape, focused: Boolean = false, tint: Color = Color.White, strong: Boolean = false): Modifier {
    val fillTop = if (focused) 0.30f else if (strong) 0.16f else 0.11f
    val fillBot = if (focused) 0.14f else if (strong) 0.07f else 0.045f
    return this
        .shadow(
            if (focused) 28.dp else 10.dp, shape,
            ambientColor = if (focused) tint else Color.Black,
            spotColor = if (focused) tint else Color.Black,
        )
        .background(Brush.verticalGradient(listOf(Color(0xFF151A2E), Color(0xFF0E1222))), shape)
        .background(Brush.verticalGradient(listOf(tint.copy(alpha = fillTop), tint.copy(alpha = fillBot))), shape)
        .border(
            if (focused) 2.dp else 1.dp,
            Brush.verticalGradient(
                if (focused) listOf(Color.White, Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.85f))
                else listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.16f))
            ),
            shape,
        )
        .drawWithContent {
            drawContent()
            // odlesk – jako světlo dopadající na sklo zleva shora
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = if (focused) 0.22f else 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.15f, 0f),
                    radius = size.maxDimension * 0.7f,
                )
            )
        }
}

/**
 * Prvek, na který jde najet ovladačem: OK = klik, podržet OK = dlouhý stisk.
 * Při najetí se pružně zvětší.
 */
@Composable
fun Focusable(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLong: (() -> Unit)? = null,
    scale: Float = 1.08f,
    focusMod: Modifier = Modifier,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val s by animateFloatAsState(if (focused) scale else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "s")
    val st = remember { BooleanArray(2) } // [stisknuto, dlouhý stisk už proběhl]
    Box(
        modifier
            .graphicsLayer { scaleX = s; scaleY = s }
            .then(focusMod)
            .onKeyEvent { ev ->
                val k = ev.nativeKeyEvent
                val ok = k.keyCode == AKey.KEYCODE_DPAD_CENTER || k.keyCode == AKey.KEYCODE_ENTER ||
                    k.keyCode == AKey.KEYCODE_NUMPAD_ENTER || k.keyCode == AKey.KEYCODE_BUTTON_A
                if (!ok) return@onKeyEvent false
                when (k.action) {
                    AKey.ACTION_DOWN -> {
                        if (k.repeatCount == 0) { st[0] = true; st[1] = false }
                        else if (onLong != null && !st[1] && st[0]) { st[1] = true; onLong() }
                        true
                    }
                    AKey.ACTION_UP -> {
                        val fire = st[0] && !st[1]
                        st[0] = false; st[1] = false
                        if (fire) onClick()
                        true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = src),
    ) { content(focused) }
}

val R12: Dp = 12.dp
fun pill() = RoundedCornerShape(50)
fun rounded(r: Dp) = RoundedCornerShape(r)
