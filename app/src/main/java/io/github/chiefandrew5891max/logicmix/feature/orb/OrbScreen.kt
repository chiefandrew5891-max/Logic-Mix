package io.github.chiefandrew5891max.logicmix.feature.orb

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.chiefandrew5891max.logicmix.R
import io.github.chiefandrew5891max.logicmix.core.designsystem.GameHeader
import io.github.chiefandrew5891max.logicmix.core.designsystem.GameToolButton
import io.github.chiefandrew5891max.logicmix.core.designsystem.GoldStar
import io.github.chiefandrew5891max.logicmix.core.designsystem.GoldTrophy
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTokens
import io.github.chiefandrew5891max.logicmix.feature.game.Card

@Composable
fun OrbRoute(onBack: () -> Unit, viewModel: OrbViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    OrbScreen(state, onBack, viewModel)
}

@Composable
private fun OrbScreen(state: OrbUiState, onBack: () -> Unit, vm: OrbViewModel) {
    val colors = LogicMixTokens.colors
    val game = state.game
    var confirmNew by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(colors.backgroundBrush).safeDrawingPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LogicMixTokens.spacing)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM),
        ) {
            GameHeader(stringResource(R.string.orb_title), onBack)
            if (game != null) {
                InfoPanel(game.score, maxOf(state.best, game.score), game.next)
                OrbBoard(state, vm::tap)
                if (game.over) {
                    Card {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.orb_over), style = LogicMixTokens.typography.headlineMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            Text(stringResource(R.string.orb_final_score, game.score), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                if (state.xpAwarded > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        GoldStar(size = 20.dp)
                        Text(
                            "  " + stringResource(R.string.game_xp_earned, state.xpAwarded),
                            style = LogicMixTokens.typography.titleMedium,
                            color = colors.gold,
                        )
                    }
                }
                if (state.noHint) {
                    Text(stringResource(R.string.orb_no_hint), color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS)) {
                    GameToolButton(stringResource(R.string.orb_undo), vm::undo, Modifier.weight(1f), enabled = game.canUndo && !state.busy)
                    GameToolButton(stringResource(R.string.orb_hint), vm::hint, Modifier.weight(1f), enabled = !game.over && !state.busy)
                    GameToolButton(stringResource(R.string.orb_new), { if (game.over) vm.newGame() else confirmNew = true }, Modifier.weight(1f), enabled = !state.busy)
                }
                Text(
                    stringResource(R.string.orb_rules),
                    style = LogicMixTokens.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text(stringResource(R.string.orb_new_title)) },
            text = { Text(stringResource(R.string.orb_new_body)) },
            confirmButton = { TextButton({ confirmNew = false; vm.newGame() }) { Text(stringResource(R.string.orb_new)) } },
            dismissButton = { TextButton({ confirmNew = false }) { Text(stringResource(R.string.sudoku_cancel)) } },
        )
    }
}

@Composable
private fun InfoPanel(score: Int, best: Int, next: List<Int>) {
    val shownScore by animateIntAsState(score, label = "score")
    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM)) {
            Stat(Modifier.weight(1f), stringResource(R.string.orb_score), shownScore.toString()) { GoldStar(size = 22.dp) }
            Stat(Modifier.weight(1f), stringResource(R.string.orb_best), best.toString()) { GoldTrophy(size = 22.dp) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.orb_next), style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                Row {
                    next.forEach { OrbBall(it, 26.dp) }
                }
            }
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, label: String, value: String, icon: @Composable () -> Unit) {
    Column(modifier) {
        Text(label, style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            icon()
            Text(value, style = LogicMixTokens.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}
