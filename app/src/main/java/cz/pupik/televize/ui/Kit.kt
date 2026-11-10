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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
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
/**
 * Průsvitné sklo: pozadí prosvítá, tvar dělají odlesky hran.
 * [solid] = neprůhledný podklad se stínem (panely přes obsah).
 */
fun Modifier.glass(shape: Shape, focused: Boolean = false, tint: Color = Color.White, strong: Boolean = false, solid: Boolean = false): Modifier {
    val fillTop = if (focused) 0.34f else if (strong) 0.17f else 0.10f
    val fillBot = if (focused) 0.16f else if (strong) 0.08f else 0.035f
    var m = this
    if (solid) m = m
        .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
        .background(Brush.verticalGradient(listOf(Color(0xFF1A1F38), Color(0xFF0D1122))), shape)
    return m
        .background(Brush.verticalGradient(listOf(tint.copy(alpha = fillTop), tint.copy(alpha = fillBot))), shape)
        .border(
            if (focused) 1.5.dp else 1.dp,
            Brush.verticalGradient(
                if (focused) listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.7f))
                else listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.14f))
            ),
            shape,
        )
        .clip(shape)
        .drawWithContent {
            drawContent()
            // odlesk – světlo dopadající na sklo zleva shora
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = if (focused) 0.20f else 0.09f), Color.Transparent),
                    center = Offset(size.width * 0.12f, 0f),
                    radius = size.maxDimension * 0.65f,
                )
            )
        }
}

/** Náhledy (Paparazzi) neumí zaostřit – tady jde vynutit, co má vypadat jako vybrané. */
var previewFocus: String? = null
var previewMode = false

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
    onFocus: (() -> Unit)? = null,
    id: String? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val real by src.collectIsFocusedAsState()
    val focused = real || (id != null && id == previewFocus)
    if (onFocus != null) LaunchedEffect(focused) { if (focused) onFocus() }
    val s by animateFloatAsState(if (focused) scale else 1f, spring(dampingRatio = 0.5f, stiffness = 260f), label = "s")
    val st = remember { BooleanArray(2) } // [stisknuto, dlouhý stisk už proběhl]
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "p")
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = s * press; scaleY = s * press }
            .then(focusMod)
            .onKeyEvent { ev ->
                val k = ev.nativeKeyEvent
                val ok = k.keyCode == AKey.KEYCODE_DPAD_CENTER || k.keyCode == AKey.KEYCODE_ENTER ||
                    k.keyCode == AKey.KEYCODE_NUMPAD_ENTER || k.keyCode == AKey.KEYCODE_BUTTON_A
                if (!ok) return@onKeyEvent false
                when (k.action) {
                    AKey.ACTION_DOWN -> {
                        if (k.repeatCount == 0) { st[0] = true; st[1] = false; pressed = true }
                        else if (onLong != null && !st[1] && st[0]) { st[1] = true; pressed = false; onLong() }
                        true
                    }
                    AKey.ACTION_UP -> {
                        val fire = st[0] && !st[1]
                        st[0] = false; st[1] = false; pressed = false
                        if (fire) onClick()
                        true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = src),
    ) { content(focused) }
}

/** Nájezd při otevření obrazovky: prvek vyjede zdola a rozsvítí se, každý o chvilku později. */
@Composable
fun Modifier.enter(delayMs: Int, dy: Float = 26f): Modifier {
    if (previewMode) return this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 120f)) }
    return this.graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1f - a.value) * dy * density }
}

val R12: Dp = 12.dp
fun pill() = RoundedCornerShape(50)
fun rounded(r: Dp) = RoundedCornerShape(r)
