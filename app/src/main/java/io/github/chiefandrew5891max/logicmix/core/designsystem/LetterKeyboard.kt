package io.github.chiefandrew5891max.logicmix.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage

/** Key rows of the shared letter keyboard per content alphabet. Chinese has no letter layout (see CrosswordPacks). */
object LetterLayouts {
    private val latin = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
    private val cyrillicRu = listOf("ЙЦУКЕНГШЩЗХЪ", "ФЫВАПРОЛДЖЭ", "ЯЧСМИТЬБЮ")

    fun forLanguage(language: ContentLanguage): List<String> = when (language) {
        ContentLanguage.RU -> cyrillicRu
        else -> latin
    }
}

/**
 * The one letter keyboard of Logic Mix: same key shape, height and typography in every game.
 * Callers own the sounds (LETTER_INPUT / LETTER_DELETE) so a press never plays two effects.
 */
@Composable
fun LetterKeyboard(
    rows: List<String>,
    onLetter: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth().widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingXs)) {
        rows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingXs)) {
                row.forEach { ch -> Key(ch.toString(), enabled) { onLetter(ch) } }
                if (index == rows.lastIndex) Key("⌫", enabled, weight = 1.5f, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun RowScope.Key(label: String, enabled: Boolean, weight: Float = 1f, onClick: () -> Unit) {
    val colors = LogicMixTokens.colors
    val shape = LogicMixTokens.radii.small
    Box(
        Modifier
            .weight(weight)
            .height(44.dp)
            .clip(shape)
            .background(colors.cardBrush)
            .border(BorderStroke(1.dp, colors.cardBorder), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
    }
}
