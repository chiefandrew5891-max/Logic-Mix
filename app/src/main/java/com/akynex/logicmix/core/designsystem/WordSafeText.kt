package com.akynex.logicmix.core.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Smallest share of the original font size [WordSafeText] may shrink to. */
const val WORD_SAFE_MIN_SCALE = 0.7f
private const val WORD_SAFE_STEP = 0.92f

/**
 * Text that never splits a word across lines: whole words wrap onto further lines first (up to [maxLines]); if a single
 * word is still wider than the area, or the lines do not fit, the font is reduced in small steps down to
 * [WORD_SAFE_MIN_SCALE] of the original. Used for card titles, mode and category names.
 */
@Composable
fun WordSafeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color = Color.Unspecified,
    maxLines: Int = 2,
    textAlign: TextAlign? = null,
) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    var scale by remember(text, style, maxLines, widthDp) { mutableFloatStateOf(1f) }
    val base: TextUnit = if (style.fontSize == TextUnit.Unspecified) 16.sp else style.fontSize
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            fontSize = base * scale,
            lineHeight = if (style.lineHeight == TextUnit.Unspecified) TextUnit.Unspecified else style.lineHeight * scale,
        ),
        color = color,
        maxLines = maxLines,
        textAlign = textAlign,
        overflow = TextOverflow.Clip,
        onTextLayout = { r ->
            val ends = (0 until r.lineCount).map { r.getLineEnd(it, visibleEnd = false) }
            val bad = breaksInsideWord(text, ends) || r.didOverflowHeight || r.didOverflowWidth
            if (bad && scale > WORD_SAFE_MIN_SCALE) scale = (scale * WORD_SAFE_STEP).coerceAtLeast(WORD_SAFE_MIN_SCALE)
        },
    )
}
