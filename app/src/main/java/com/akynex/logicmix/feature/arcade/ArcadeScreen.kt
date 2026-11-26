package com.akynex.logicmix.feature.arcade

import com.akynex.logicmix.core.designsystem.BrandFooter
import com.akynex.logicmix.core.designsystem.LogicMixBackdrop
import com.akynex.logicmix.core.designsystem.LogicMixScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.arcade.ArcadeRules
import com.akynex.logicmix.domain.arcade.ArcadeRun
import com.akynex.logicmix.domain.arcade.ArcadeStage
import com.akynex.logicmix.domain.content.GameCategory
import com.akynex.logicmix.feature.crossword.CrosswordRoute
import com.akynex.logicmix.feature.game.GameRoute
import com.akynex.logicmix.feature.gamestyle.BackButton
import com.akynex.logicmix.feature.home.titleRes
import com.akynex.logicmix.feature.matchstick.MatchstickRoute
import com.akynex.logicmix.feature.orb.OrbRoute
import com.akynex.logicmix.feature.sudoku.SudokuRoute
import com.akynex.logicmix.feature.twodots.TwoDotsRoute
import com.akynex.logicmix.feature.visual.VisualRoute
import com.akynex.logicmix.feature.wordlogic.WordLogicRoute
import com.akynex.logicmix.feature.wordsearch.WordSearchRoute

@Composable
fun ArcadeRoute(onBack: () -> Unit, onEditCategories: () -> Unit, viewModel: ArcadeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val run = state.run
    if (state.loading) {
        LogicMixBackdrop(Modifier.fillMaxSize())
        return
    }
    if (run == null) {
        ArcadeLobby(state, onBack, onEditCategories, viewModel::startNew, viewModel::resume)
        return
    }
    val leave = { viewModel.pause() }
    BackHandler(onBack = leave)
    LogicMixScreen {
        Column(Modifier.fillMaxSize()) {
            ArcadePanel(run, onSkip = viewModel::skip, onExit = leave, canSkip = state.celebration == null)
            Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
                // Only an unresolved stage hosts a real game; a resolved one waits for its celebration to end.
                val stage = run.current
                if (run.outcome == com.akynex.logicmix.domain.arcade.StageOutcome.NONE) StageContent(stage, leave)
            }
        }
        state.celebration?.let { ArcadeResultToast(it, viewModel) }
    }
}

/** The real category screen, one ViewModel per stage so every stage starts clean while Arcade keeps its own run. */
@Composable
private fun StageContent(stage: ArcadeStage, onBack: () -> Unit) {
    val key = stage.id
    when (stage.category) {
        GameCategory.NUMBER_SEQUENCE -> GameRoute(onBack, hiltViewModel(key = key))
        GameCategory.MATCHSTICK -> MatchstickRoute(onBack, hiltViewModel(key = key))
        GameCategory.WORD_LOGIC -> WordLogicRoute(onBack, hiltViewModel(key = key))
        GameCategory.MINI_CROSSWORD -> CrosswordRoute(onBack, hiltViewModel(key = key))
        GameCategory.VISUAL_LOGIC -> VisualRoute(onBack, hiltViewModel(key = key))
        GameCategory.WORD_SEARCH -> WordSearchRoute(onBack, hiltViewModel(key = key))
        GameCategory.SUDOKU -> SudokuRoute(onBack, hiltViewModel(key = key))
        GameCategory.ORB_LINES -> OrbRoute(onBack, hiltViewModel(key = key))
        GameCategory.TWO_DOTS -> TwoDotsRoute(onBack, hiltViewModel(key = key))
    }
}

/** One compact row: stage, score, level, streak, with a thin level-progress bar. */
@Composable
private fun ArcadePanel(run: ArcadeRun, onSkip: () -> Unit, onExit: () -> Unit, canSkip: Boolean) {
    val colors = LogicMixTokens.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.cardBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = LogicMixTokens.spacingM, vertical = LogicMixTokens.spacingS),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM)) {
            Stat(Modifier.weight(1f), stringResource(R.string.arcade_stage), run.stage.toString())
            Stat(Modifier.weight(1.3f), stringResource(R.string.arcade_score), run.score.toString())
            Stat(Modifier.weight(1f), stringResource(R.string.arcade_level), run.level.toString())
            Stat(Modifier.weight(1f), stringResource(R.string.arcade_streak), run.streak.toString())
            TextButton(onSkip, enabled = canSkip) { Text(stringResource(R.string.arcade_skip), color = colors.gold) }
            TextButton(onExit) { Text(stringResource(R.string.arcade_exit)) }
        }
        LinearProgressIndicator(
            progress = { ArcadeRules.levelProgress(run.score) },
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            color = colors.gold,
        )
    }
}

@Composable
private fun Stat(modifier: Modifier, label: String, value: String) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = LogicMixTokens.typography.titleMedium, color = LogicMixTokens.colors.gold, maxLines = 1)
    }
}

@Composable
private fun ArcadeLobby(
    state: ArcadeUiState,
    onBack: () -> Unit,
    onEditCategories: () -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
) {
    LogicMixScreen(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LogicMixTokens.spacing)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = com.akynex.logicmix.core.designsystem.stickyFooterArrangement(LogicMixTokens.spacing),
        ) {
            BackButton(onBack)
            Text(
                stringResource(R.string.arcade_title), style = LogicMixTokens.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            val names = state.selected.sortedBy { it.ordinal }.map { stringResource(it.titleRes()) }.joinToString(", ")
            Text(
                if (state.selected.isEmpty()) stringResource(R.string.style_arcade_empty)
                else stringResource(R.string.arcade_selected, state.selected.size, names),
                style = LogicMixTokens.typography.bodyMedium,
                color = if (state.selected.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            if (state.unavailable.isNotEmpty()) {
                Text(
                    stringResource(R.string.arcade_unavailable, state.unavailable.sortedBy { it.ordinal }.map { stringResource(it.titleRes()) }.joinToString(", ")),
                    style = LogicMixTokens.typography.bodyMedium, color = LogicMixTokens.colors.gold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.finished) {
                Text(stringResource(R.string.arcade_nothing_left), style = LogicMixTokens.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            state.saved?.let { saved ->
                Button(onResume, Modifier.fillMaxWidth().heightIn(min = LogicMixTokens.minTouchTarget)) {
                    Text(stringResource(R.string.arcade_resume, saved.stage, saved.score), style = LogicMixTokens.typography.titleMedium)
                }
            }
            val startModifier = Modifier.fillMaxWidth().heightIn(min = LogicMixTokens.minTouchTarget)
            if (state.saved == null) {
                Button(onStart, startModifier, enabled = state.playable.isNotEmpty()) {
                    Text(stringResource(R.string.arcade_new), style = LogicMixTokens.typography.titleMedium)
                }
            } else {
                OutlinedButton(onStart, startModifier, enabled = state.playable.isNotEmpty()) { Text(stringResource(R.string.arcade_new)) }
                Text(stringResource(R.string.arcade_new_warning), style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            OutlinedButton(onEditCategories, Modifier.fillMaxWidth().heightIn(min = LogicMixTokens.minTouchTarget)) {
                Text(stringResource(R.string.arcade_edit))
            }
            StatsCard(state)
            BrandFooter()
        }
    }
}

@Composable
private fun StatsCard(state: ArcadeUiState) {
    val colors = LogicMixTokens.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.cardBrush, LogicMixTokens.radii.medium)
            .border(BorderStroke(1.dp, colors.cardBorder), LogicMixTokens.radii.medium)
            .padding(LogicMixTokens.spacingM),
        verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS),
    ) {
        Text(stringResource(R.string.arcade_best_score, state.stats.bestScore), color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.arcade_best_streak, state.stats.bestStreak), color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.arcade_total_stages, state.stats.totalStages), color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.arcade_achievements_count, state.stats.achievements.size), color = colors.gold)
    }
}
