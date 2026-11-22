package com.akynex.logicmix.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akynex.logicmix.domain.content.ContentLanguage

/**
 * Key rows of the shared letter keyboard per content alphabet. Chinese has no letter layout and no crosswords
 * (see CrosswordPacks), so it returns no rows. The Latin grids drop accents except Ñ (es) and Ä Ö Ü (de).
 */
object LetterLayouts {
    private val latin = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

    fun forLanguage(language: ContentLanguage): List<String> = when (language) {
        ContentLanguage.RU -> listOf("ЙЦУКЕНГШЩЗХЪ", "ФЫВАПРОЛДЖЭ", "ЯЧСМИТЬБЮЁ")
        ContentLanguage.UK -> listOf("ЙЦУКЕНГШЩЗХЇ", "ФІВАПРОЛДЖЄ", "ҐЯЧСМИТЬБЮ")
        ContentLanguage.ES -> listOf("QWERTYUIOP", "ASDFGHJKLÑ", "ZXCVBNM")
        ContentLanguage.DE -> listOf("QWERTZUIOPÜ", "ASDFGHJKLÖÄ", "YXCVBNM")
        ContentLanguage.FR -> listOf("AZERTYUIOP", "QSDFGHJKLM", "WXCVBN")
        ContentLanguage.ZH_CN -> emptyList()
        ContentLanguage.EN, ContentLanguage.IT -> latin
    }

    fun alphabet(language: ContentLanguage): Set<Char> = forLanguage(language).flatMap { it.toList() }.toSet()
}

private const val DELETE_WEIGHT = 1.5f

/**
 * The one letter keyboard of Logic Mix: same key shape, height and typography in every game.
 * Callers own the sounds (LETTER_INPUT / LETTER_DELETE) so a press never plays two effects.
 * Keys share the row width by weight and the label shrinks with the key, so even 12-key rows never clip.
 */
@Composable
fun LetterKeyboard(
    rows: List<String>,
    onLetter: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val gap = LogicMixTokens.spacingXs
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = minOf(maxWidth, 560.dp)
        val widest = rows.withIndex().maxOfOrNull { (i, r) -> r.length + if (i == rows.lastIndex) DELETE_WEIGHT else 0f } ?: 1f
        val keyWidth = (width - gap * (widest.toInt())) / widest
        val labelSize = (keyWidth.value * 0.55f).coerceIn(9f, 18f).sp
        Column(Modifier.width(width).align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    row.forEach { ch -> Key(ch.toString(), enabled, keyWidth, labelSize) { onLetter(ch) } }
                    if (index == rows.lastIndex) Key("⌫", enabled, keyWidth * DELETE_WEIGHT, labelSize, onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, enabled: Boolean, width: Dp, labelSize: TextUnit, onClick: () -> Unit) {
    val colors = LogicMixTokens.colors
    val shape = LogicMixTokens.radii.small
    Box(
        Modifier
            .width(width)
            .height(44.dp)
            .clip(shape)
            .background(colors.cardBrush)
            .border(BorderStroke(1.dp, colors.cardBorder), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = LogicMixTokens.typography.titleMedium, fontSize = labelSize, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
    }
}
