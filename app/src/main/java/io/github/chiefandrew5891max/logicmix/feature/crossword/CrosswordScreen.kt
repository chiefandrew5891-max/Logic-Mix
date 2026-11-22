package io.github.chiefandrew5891max.logicmix.feature.crossword

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.chiefandrew5891max.logicmix.R
import io.github.chiefandrew5891max.logicmix.core.designsystem.CategoryScaffold
import io.github.chiefandrew5891max.logicmix.core.designsystem.GameHeader
import io.github.chiefandrew5891max.logicmix.core.designsystem.GameTool
import io.github.chiefandrew5891max.logicmix.core.designsystem.GameToolRow
import io.github.chiefandrew5891max.logicmix.core.designsystem.InfoNote
import io.github.chiefandrew5891max.logicmix.core.designsystem.LetterKeyboard
import io.github.chiefandrew5891max.logicmix.core.designsystem.LetterLayouts
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTokens
import io.github.chiefandrew5891max.logicmix.core.designsystem.XpLine
import io.github.chiefandrew5891max.logicmix.domain.crossword.CrosswordGame
import io.github.chiefandrew5891max.logicmix.domain.crossword.Direction
import io.github.chiefandrew5891max.logicmix.feature.game.ActionButton
import io.github.chiefandrew5891max.logicmix.feature.game.Card

@Composable
fun CrosswordRoute(onBack: () -> Unit, viewModel: CrosswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val game = state.game
    CategoryScaffold {
        GameHeader(
            title = stringResource(R.string.category_mini_crossword),
            onBack = onBack,
            subtitle = if (game != null) stringResource(R.string.cat_level, state.index + 1, state.puzzles.size) else null,
            onRestart = viewModel::restart,
            restartEnabled = game != null && !game.solved,
        )
        if (state.unsupported) {
            InfoNote(stringResource(R.string.cw_unsupported_language))
            return@CategoryScaffold
        }
        if (state.allDone) {
            Card {
                Text(stringResource(R.string.game_all_done), style = LogicMixTokens.typography.headlineMedium, color = LogicMixTokens.colors.success, textAlign = TextAlign.Center)
                Text(stringResource(R.string.game_all_done_body), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            }
            ActionButton(stringResource(R.string.cat_play_again), viewModel::playAgain, filled = true)
        }
        if (game == null) return@CategoryScaffold
        Grid(game, viewModel::selectCell)
        val active = game.activeEntry
        Text(
            active?.let { "${game.puzzle.numberOf(it)}. ${it.clue}" } ?: stringResource(R.string.cw_pick_clue),
            color = LogicMixTokens.colors.gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        when (state.check) {
            CheckResult.MISTAKES -> Text(stringResource(R.string.cw_incorrect), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            CheckResult.OK -> Text(stringResource(R.string.cw_check_ok), color = LogicMixTokens.colors.success, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            CheckResult.NONE -> Unit
        }
        if (game.solved) {
            Card {
                Text(stringResource(R.string.cw_solved), style = LogicMixTokens.typography.headlineMedium, color = LogicMixTokens.colors.success, textAlign = TextAlign.Center)
                XpLine(state.xpAwarded)
            }
            ActionButton(stringResource(R.string.game_next), viewModel::next, filled = true)
        } else {
            LetterKeyboard(LetterLayouts.forLanguage(state.language), viewModel::letter, viewModel::delete)
            GameToolRow(listOf(GameTool(stringResource(R.string.cw_hint_letter), viewModel::hintLetter), GameTool(stringResource(R.string.cw_hint_word), viewModel::hintWord)))
            GameToolRow(listOf(GameTool(stringResource(R.string.cw_check_word), viewModel::checkWord), GameTool(stringResource(R.string.cw_check_all), viewModel::checkAll)))
        }
        Clues(game, Direction.ACROSS, R.string.cw_across, viewModel)
        Clues(game, Direction.DOWN, R.string.cw_down, viewModel)
    }
}

@Composable
private fun Grid(game: CrosswordGame, onCell: (Int) -> Unit) {
    val colors = LogicMixTokens.colors
    val size = game.puzzle.size
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val cell = (minOf(maxWidth, 420.dp) - 4.dp * (size - 1)) / size
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(size) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(size) { c ->
                        val index = r * size + c
                        val used = index in game.puzzle.solution
                        val shape = LogicMixTokens.radii.small
                        // Light theme: opaque white base so the app gradient never shows through the cells.
                        val base = if (colors.isDark) Color.Transparent else Color.White
                        val bg = when {
                            !used -> Color.Transparent
                            index == game.selected -> colors.gold.copy(alpha = 0.55f).compositeOver(base)
                            index in game.wrong -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f).compositeOver(base)
                            index in game.activeCells -> colors.gold.copy(alpha = 0.22f).compositeOver(base)
                            else -> if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.White
                        }
                        Box(
                            Modifier.size(cell).clip(shape).background(bg)
                                .border(BorderStroke(if (used) 1.dp else 0.dp, if (used) colors.cardBorder else Color.Transparent), shape)
                                .clickable(enabled = used, onClick = { onCell(index) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            game.puzzle.numbers[index]?.let {
                                Text("$it", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 1.dp))
                            }
                            game.letters[index]?.let {
                                Text(it.toString(), style = LogicMixTokens.typography.titleLarge, color = if (index in game.revealed) colors.gold else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Clues(game: CrosswordGame, direction: Direction, title: Int, vm: CrosswordViewModel) {
    val list = game.puzzle.entries.filter { it.direction == direction }.sortedBy { game.puzzle.numberOf(it) }
    if (list.isEmpty()) return
    Text(stringResource(title), style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    list.forEach { entry ->
        val done = game.isEntryCorrect(entry)
        val isActive = entry == game.activeEntry
        Row(
            Modifier.fillMaxWidth().clip(LogicMixTokens.radii.small)
                .background(if (isActive) LogicMixTokens.colors.gold.copy(alpha = 0.18f) else Color.Transparent)
                .clickable(onClick = { vm.selectEntry(entry) })
                .padding(LogicMixTokens.spacingS),
            horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS),
        ) {
            Text("${game.puzzle.numberOf(entry)}.", color = LogicMixTokens.colors.gold)
            Text(
                "${entry.clue} (${entry.length})",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (done) 0.5f else 1f),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
