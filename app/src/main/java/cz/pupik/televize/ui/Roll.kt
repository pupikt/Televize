package cz.pupik.televize.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.text.TextStyle

/** Přetáčecí číslice: při změně nová číslice vyjede zdola a stará odjede nahoru. */
@Composable
fun Roll(text: String, style: TextStyle) {
    Row {
        val n = text.length
        text.forEachIndexed { i, ch ->
            key(n - i) { // klíče zprava, ať se při 9:59 → 10:00 nepřeházejí
                AnimatedContent(
                    ch,
                    transitionSpec = {
                        (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 180f)) { it / 2 } + fadeIn()) togetherWith
                            (slideOutVertically { -it / 2 } + fadeOut())
                    },
                    label = "d",
                ) { c -> BasicText(c.toString(), style = style) }
            }
        }
    }
}
