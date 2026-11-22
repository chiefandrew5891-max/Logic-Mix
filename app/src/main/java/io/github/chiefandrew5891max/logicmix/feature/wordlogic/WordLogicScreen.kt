package io.github.chiefandrew5891max.logicmix.feature.wordlogic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.chiefandrew5891max.logicmix.R
import io.github.chiefandrew5891max.logicmix.core.designsystem.CategoryScaffold
import io.github.chiefandrew5891max.logicmix.core.designsystem.ChoiceCard
import io.github.chiefandrew5891max.logicmix.core.designsystem.ChoiceState
import io.github.chiefandrew5891max.logicmix.core.designsystem.GameHeader
import io.github.chiefandrew5891max.logicmix.core.designsystem.GameTool
import io.github.chiefandrew5891max.logicmix.core.designsystem.GameToolRow
import io.github.chiefandrew5891max.logicmix.core.designsystem.InfoNote
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTokens
import io.github.chiefandrew5891max.logicmix.core.designsystem.XpLine
import io.github.chiefandrew5891max.logicmix.domain.wordlogic.WordLogicType
import io.github.chiefandrew5891max.logicmix.feature.game.ActionButton
import io.github.chiefandrew5891max.logicmix.feature.game.Card

private fun WordLogicType.prompt(): Int = when (this) {
    WordLogicType.ODD_ONE_OUT -> R.string.wl_prompt_odd
    WordLogicType.ANALOGY -> R.string.wl_prompt_analogy
    WordLogicType.PAIR -> R.string.wl_prompt_pair
    WordLogicType.COMMON_CATEGORY -> R.string.wl_prompt_common
    WordLogicType.SEQUENCE -> R.string.wl_prompt_sequence
}

@Composable
fun WordLogicRoute(onBack: () -> Unit, viewModel: WordLogicViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val game = state.game
    CategoryScaffold {
        GameHeader(
            title = stringResource(R.string.category_word_logic),
            onBack = onBack,
            subtitle = if (game != null) stringResource(R.string.cat_level, state.index + 1, state.puzzles.size) else null,
        )
        if (state.fallback) InfoNote(stringResource(R.string.cat_fallback_notice))
        if (state.allDone) {
            Card {
                Text(stringResource(R.string.game_all_done), style = LogicMixTokens.typography.headlineMedium, color = LogicMixTokens.colors.success, textAlign = TextAlign.Center)
                Text(stringResource(R.string.game_all_done_body), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            }
            ActionButton(stringResource(R.string.cat_play_again), viewModel::playAgain, filled = true)
        }
        if (game != null) {
            val puzzle = game.puzzle
            Text(stringResource(puzzle.type.prompt()), style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (puzzle.statement().isNotEmpty()) {
                Card { Text(puzzle.statement(), style = LogicMixTokens.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center) }
            }
            puzzle.options.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM)) {
                    pair.forEach { option ->
                        val choice = when {
                            game.solved && option.id == puzzle.correctId -> ChoiceState.CORRECT
                            option.id in game.wrong -> ChoiceState.WRONG
                            option.id in game.eliminated || game.solved -> ChoiceState.DISABLED
                            else -> ChoiceState.IDLE
                        }
                        ChoiceCard(choice, { viewModel.choose(option.id) }, Modifier.weight(1f).fillMaxHeight()) {
                            Text(option.text, style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            if (game.hintShown && !game.solved) {
                Text(stringResource(R.string.cat_hint_line, puzzle.hint), color = LogicMixTokens.colors.gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (game.solved) {
                Card {
                    Text(stringResource(R.string.game_correct), style = LogicMixTokens.typography.headlineMedium, color = LogicMixTokens.colors.success, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.game_explanation, puzzle.explanation), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    XpLine(state.xpAwarded)
                }
                ActionButton(stringResource(R.string.game_next), viewModel::next, filled = true)
            } else {
                GameToolRow(listOf(GameTool(stringResource(R.string.cat_hint), viewModel::hint)))
            }
        }
    }
}
