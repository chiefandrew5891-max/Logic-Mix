package io.github.chiefandrew5891max.logicmix.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.chiefandrew5891max.logicmix.R

enum class ChoiceState { IDLE, WRONG, CORRECT, DISABLED }

/** Rounded answer card shared by the choice games: soft shake-free reaction, a small pop when correct. */
@Composable
fun ChoiceCard(state: ChoiceState, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = LogicMixTokens.colors
    val shape = LogicMixTokens.radii.large
    val border by animateColorAsState(
        when (state) {
            ChoiceState.CORRECT -> colors.success
            ChoiceState.WRONG -> MaterialTheme.colorScheme.error
            else -> colors.cardBorder
        },
        label = "choice-border",
    )
    val scale by animateFloatAsState(
        if (state == ChoiceState.CORRECT) 1.05f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "choice-scale",
    )
    Box(
        modifier
            .scale(scale)
            .alpha(if (state == ChoiceState.WRONG || state == ChoiceState.DISABLED) 0.55f else 1f)
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(colors.cardBrush)
            .border(BorderStroke(if (state == ChoiceState.IDLE || state == ChoiceState.DISABLED) 1.dp else 3.dp, border), shape)
            .clickable(enabled = state == ChoiceState.IDLE, role = Role.Button, onClick = onClick)
            .padding(LogicMixTokens.spacingS),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Background, safe-area insets, scrolling and the 560 dp tablet cap shared by the category games. */
@Composable
fun CategoryScaffold(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(LogicMixTokens.colors.backgroundBrush).safeDrawingPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LogicMixTokens.spacing)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM),
            content = content,
        )
    }
}

@Composable
fun InfoNote(text: String) {
    Text(text, style = LogicMixTokens.typography.labelMedium, color = LogicMixTokens.colors.gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
fun XpLine(xpAwarded: Int) {
    Text(
        if (xpAwarded > 0) stringResource(R.string.game_xp_earned, xpAwarded) else stringResource(R.string.game_no_xp),
        style = LogicMixTokens.typography.titleMedium,
        color = LogicMixTokens.colors.gold,
        textAlign = TextAlign.Center,
    )
}
