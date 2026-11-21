package io.github.chiefandrew5891max.logicmix.feature.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.chiefandrew5891max.logicmix.R
import io.github.chiefandrew5891max.logicmix.core.designsystem.FitLabel
import io.github.chiefandrew5891max.logicmix.core.designsystem.GameHeader
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixIcon
import io.github.chiefandrew5891max.logicmix.core.designsystem.rememberButtonFit
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTokens

@Composable
fun GameRoute(onBack: () -> Unit, viewModel: GameViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    GameScreen(state, onBack, viewModel::onInput, viewModel::submit, viewModel::useHint, viewModel::next, viewModel::restart)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameScreen(
    state: GameUiState,
    onBack: () -> Unit,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onHint: () -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit = {},
) {
    val colors = LogicMixTokens.colors
    Box(Modifier.fillMaxSize().background(colors.backgroundBrush).safeDrawingPadding().imePadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LogicMixTokens.spacing)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacing),
        ) {
            Header(state, onBack, onRestart)
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(LogicMixTokens.spacingL), Alignment.Center) { CircularProgressIndicator() }
                state.finished || state.puzzle == null -> FinishedCard(onBack)
                else -> {
                    val puzzle = state.puzzle
                    Card {
                        Text(stringResource(R.string.game_find_next), style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            puzzle.sequence.forEach { NumberChip(it.toString(), false) }
                            NumberChip(if (state.solved) puzzle.answer.toString() else "?", true)
                        }
                    }
                    if (state.shownHints.isNotEmpty()) {
                        Card {
                            state.shownHints.forEachIndexed { i, hint ->
                                Text(
                                    stringResource(R.string.game_hint_line, i + 1, hint),
                                    style = LogicMixTokens.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    if (!state.solved) {
                        OutlinedTextField(
                            value = state.input,
                            onValueChange = onInput,
                            label = { Text(stringResource(R.string.game_answer_label)) },
                            singleLine = true,
                            isError = state.feedback == Feedback.WRONG || state.feedback == Feedback.INVALID,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                            shape = LogicMixTokens.radii.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    FeedbackText(state)
                    if (state.solved) {
                        ActionButton(stringResource(R.string.game_next), onNext, filled = true)
                    } else {
                        ActionButton(stringResource(R.string.game_submit), onSubmit, filled = true, enabled = state.canSubmit)
                        ActionButton(
                            stringResource(R.string.game_hint_button, state.hintsUsed, puzzle.hints.size),
                            onHint,
                            filled = false,
                            enabled = state.canUseHint,
                            icon = R.drawable.ic_info,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: GameUiState, onBack: () -> Unit, onRestart: () -> Unit) {
    val puzzle = state.puzzle
    GameHeader(
        title = stringResource(R.string.game_title),
        onBack = onBack,
        subtitle = puzzle?.let { stringResource(R.string.game_task_info, state.number, state.total, it.difficulty) },
        onRestart = onRestart,
        restartEnabled = puzzle != null && !state.solved,
    )
}

@Composable
private fun FeedbackText(state: GameUiState) {
    val colors = LogicMixTokens.colors
    val target = when (state.feedback) {
        Feedback.CORRECT -> colors.success
        Feedback.WRONG, Feedback.INVALID -> MaterialTheme.colorScheme.error
        Feedback.NONE -> Color.Transparent
    }
    val color by animateColorAsState(target, label = "feedback")
    AnimatedVisibility(state.feedback != Feedback.NONE) {
        Column(
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val message = when (state.feedback) {
                Feedback.CORRECT -> R.string.game_correct
                Feedback.WRONG -> R.string.game_wrong
                else -> R.string.game_invalid
            }
            Text(stringResource(message), style = LogicMixTokens.typography.headlineMedium, color = color, textAlign = TextAlign.Center)
            if (state.solved) {
                Text(
                    if (state.xpAwarded > 0) stringResource(R.string.game_xp_earned, state.xpAwarded) else stringResource(R.string.game_no_xp),
                    style = LogicMixTokens.typography.titleMedium,
                    color = LogicMixTokens.colors.gold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.game_explanation, state.puzzle?.explanation.orEmpty()),
                    style = LogicMixTokens.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FinishedCard(onBack: () -> Unit) {
    Card {
        Text(stringResource(R.string.game_all_done), style = LogicMixTokens.typography.headlineMedium, color = LogicMixTokens.colors.gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.game_all_done_body), style = LogicMixTokens.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    ActionButton(stringResource(R.string.back), onBack, filled = true)
}

@Composable
private fun NumberChip(text: String, highlight: Boolean) {
    val colors = LogicMixTokens.colors
    val shape = LogicMixTokens.radii.medium
    Box(
        Modifier
            .heightIn(min = 52.dp)
            .widthIn(min = 52.dp)
            .clip(shape)
            .background(if (highlight) colors.rewardBrush else colors.cardBrush)
            .border(BorderStroke(if (highlight) 2.dp else 1.dp, if (highlight) colors.gold else colors.cardBorder), shape)
            .padding(horizontal = LogicMixTokens.spacingM, vertical = LogicMixTokens.spacingS),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = LogicMixTokens.typography.headlineMedium,
            color = if (highlight) colors.gold else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun ActionButton(label: String, onClick: () -> Unit, filled: Boolean, enabled: Boolean = true, icon: Int? = null) {
    val colors = LogicMixTokens.colors
    val shape = LogicMixTokens.radii.extraLarge
    val background: Brush = if (filled) colors.primaryActionBrush else colors.cardBrush
    val content = if (filled) colors.onGold else MaterialTheme.colorScheme.onSurface
    val style = LogicMixTokens.typography.titleMedium
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val iconSpace = if (icon != null) 24.dp + LogicMixTokens.spacingS else 0.dp
        val fit = rememberButtonFit(listOf(label), style, maxWidth - iconSpace, LogicMixTokens.spacingL)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = LogicMixTokens.buttonHeight)
                .clip(shape)
                .background(background)
                .border(BorderStroke(if (filled) 0.dp else 1.dp, colors.cardBorder), shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = fit.padding.dp, vertical = LogicMixTokens.spacingM),
            horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (enabled) content else content.copy(alpha = 0.4f)
            if (icon != null) LogicMixIcon(icon, tint)
            FitLabel(label, fit, style, tint)
        }
    }
}

@Composable
internal fun Card(content: @Composable () -> Unit) {
    val colors = LogicMixTokens.colors
    val shape: Shape = LogicMixTokens.radii.large
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.cardBrush)
            .border(BorderStroke(1.dp, colors.cardBorder), shape)
            .padding(LogicMixTokens.spacing),
        verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
