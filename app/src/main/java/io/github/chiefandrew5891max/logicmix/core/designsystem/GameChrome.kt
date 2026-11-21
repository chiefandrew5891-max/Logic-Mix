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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.chiefandrew5891max.logicmix.R

/** Top bar of a game screen: back arrow, title and optional subtitle. The caller supplies the (sound-playing) back handler. */
@Composable
fun GameHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val colors = LogicMixTokens.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM)) {
        Box(
            Modifier
                .size(LogicMixTokens.minTouchTarget)
                .clip(LogicMixTokens.radii.medium)
                .background(colors.cardBrush)
                .border(BorderStroke(1.dp, colors.cardBorder), LogicMixTokens.radii.medium)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            LogicMixIcon(R.drawable.ic_back, MaterialTheme.colorScheme.onSurface, contentDescription = stringResource(R.string.back))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = LogicMixTokens.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = LogicMixTokens.typography.labelMedium, color = colors.gold)
        }
        trailing()
    }
}

/** Rounded bottom-bar action in the card style used across games. */
@Composable
fun GameToolButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = LogicMixTokens.colors
    val shape = LogicMixTokens.radii.medium
    Box(
        modifier
            .heightIn(min = LogicMixTokens.minTouchTarget)
            .clip(shape)
            .background(colors.cardBrush)
            .border(BorderStroke(1.dp, colors.cardBorder), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = LogicMixTokens.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
            textAlign = TextAlign.Center,
        )
    }
}
