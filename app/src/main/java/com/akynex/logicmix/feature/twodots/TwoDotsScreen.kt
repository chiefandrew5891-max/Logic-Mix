package com.akynex.logicmix.feature.twodots

import android.provider.Settings
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.CategoryScaffold
import com.akynex.logicmix.core.designsystem.GameHeader
import com.akynex.logicmix.core.designsystem.GameTool
import com.akynex.logicmix.core.designsystem.GameToolRow
import com.akynex.logicmix.core.designsystem.GoldStar
import com.akynex.logicmix.core.designsystem.GoldTrophy
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.core.designsystem.XpLine
import com.akynex.logicmix.domain.twodots.TwoDotsMode
import com.akynex.logicmix.feature.game.ActionButton
import com.akynex.logicmix.feature.game.Card

@Composable
fun TwoDotsRoute(onBack: () -> Unit, viewModel: TwoDotsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        viewModel.setAnimations(scale > 0f)
    }
    val game = state.game
    CategoryScaffold {
        GameHeader(
            title = stringResource(R.string.category_two_dots),
            onBack = onBack,
            onRestart = viewModel::restart,
            restartEnabled = game != null && !state.busy,
        )
        if (game != null) {
            // Arcade always plays CLASSIC (the mode with a finite goal), so the mode switch is not offered there.
            if (!state.arcade) GameToolRow(
                listOf(
                    GameTool(stringResource(R.string.td_mode_classic), { viewModel.setMode(TwoDotsMode.CLASSIC) }, !state.busy, game.mode == TwoDotsMode.CLASSIC),
                    GameTool(stringResource(R.string.td_mode_endless), { viewModel.setMode(TwoDotsMode.ENDLESS) }, !state.busy, game.mode == TwoDotsMode.ENDLESS),
                ),
            )
            Card {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM)) {
                    val shown by animateIntAsState(game.score, label = "score")
                    Stat(Modifier.weight(1f), stringResource(R.string.td_score), shown.toString(), if (state.gain > 0) "+${state.gain}" else null) { GoldStar(size = 22.dp) }
                    if (game.mode == TwoDotsMode.CLASSIC) {
                        Stat(Modifier.weight(1f), stringResource(R.string.td_moves_left), game.movesLeft.toString(), null) {}
                    }
                    Stat(Modifier.weight(1f), stringResource(R.string.td_best), state.bestScore.toString(), null) { GoldTrophy(size = 22.dp) }
                }
            }
            TwoDotsBoard(state, viewModel::begin, viewModel::drag, viewModel::end, viewModel::cancel)
            if (game.over) {
                Card {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.td_over), style = LogicMixTokens.typography.headlineMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.td_final_score, game.score), color = MaterialTheme.colorScheme.onSurface)
                        XpLine(state.xpAwarded)
                    }
                }
                ActionButton(stringResource(R.string.td_new_game), viewModel::restart, filled = true)
            } else {
                if (state.xpAwarded > 0) XpLine(state.xpAwarded)
                if (state.reshuffled) Note(stringResource(R.string.td_no_moves))
                if (state.noHint) Note(stringResource(R.string.td_no_hint))
                GameToolRow(listOf(GameTool(stringResource(R.string.td_hint), viewModel::hint, !state.busy)))
                Text(
                    stringResource(R.string.td_rules),
                    style = LogicMixTokens.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Stat(modifier: Modifier, label: String, value: String, gain: String?, icon: @Composable () -> Unit) {
    Column(modifier) {
        Text(label, style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            icon()
            Text(value, style = LogicMixTokens.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (gain != null) Text(gain, style = LogicMixTokens.typography.labelLarge, color = LogicMixTokens.colors.gold, maxLines = 1)
        }
    }
}
