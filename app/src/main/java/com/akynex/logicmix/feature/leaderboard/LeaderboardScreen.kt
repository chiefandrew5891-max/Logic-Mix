package com.akynex.logicmix.feature.leaderboard

import com.akynex.logicmix.core.designsystem.BrandFooter
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.GameHeader
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.core.games.GamesCoordinator
import com.akynex.logicmix.core.games.GamesStatus
import com.akynex.logicmix.domain.games.BoardEntry
import com.akynex.logicmix.domain.games.BoardStanding
import com.akynex.logicmix.domain.games.LeaderboardKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@StringRes
fun LeaderboardKey.titleRes(): Int = when (this) {
    LeaderboardKey.TOTAL_XP -> R.string.lb_total_xp
    LeaderboardKey.ARCADE -> R.string.lb_arcade
    LeaderboardKey.NUMBER_LOGIC -> R.string.lb_number_logic
    LeaderboardKey.VISUAL_LOGIC -> R.string.lb_visual_logic
    LeaderboardKey.MINI_CROSSWORD -> R.string.lb_mini_crossword
    LeaderboardKey.WORD_SEARCH -> R.string.lb_word_search
    LeaderboardKey.WORD_LOGIC -> R.string.lb_word_logic
    LeaderboardKey.SUDOKU -> R.string.lb_sudoku
    LeaderboardKey.MATCHSTICK -> R.string.lb_matchstick
    LeaderboardKey.ORB_LINES -> R.string.lb_orb_lines
    LeaderboardKey.TWO_DOTS -> R.string.lb_two_dots
}

/** What the number on a board means, so the metrics are never ambiguous. */
@StringRes
fun LeaderboardKey.metricRes(): Int = when (this) {
    LeaderboardKey.TOTAL_XP -> R.string.lb_metric_xp
    LeaderboardKey.ARCADE, LeaderboardKey.ORB_LINES, LeaderboardKey.TWO_DOTS -> R.string.lb_metric_best_score
    else -> R.string.lb_metric_category_xp
}

data class BoardUi(val loading: Boolean = true, val standing: BoardStanding? = null, val top: List<BoardEntry>? = null)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(private val games: GamesCoordinator) : ViewModel() {
    val gamesState = games.state
    val boards: List<LeaderboardKey> = games.ids.configured
    private val _selected = MutableStateFlow(boards.firstOrNull())
    val selected: StateFlow<LeaderboardKey?> = _selected
    private val _ui = MutableStateFlow(BoardUi())
    val ui: StateFlow<BoardUi> = _ui

    init { _selected.value?.let(::load) }

    fun select(key: LeaderboardKey) { _selected.value = key; load(key) }
    fun refresh() { _selected.value?.let(::load) }
    fun connect() = games.signIn()
    fun openFull(key: LeaderboardKey) = games.openFullBoard(key)

    private fun load(key: LeaderboardKey) {
        _ui.value = BoardUi()
        viewModelScope.launch {
            games.sync()
            val standing = games.standing(key)
            val top = games.top(key)
            _ui.update { if (_selected.value == key) BoardUi(false, standing, top) else it }
        }
    }
}

@Composable
fun LeaderboardScreen(onBack: () -> Unit, viewModel: LeaderboardViewModel = hiltViewModel()) {
    val colors = LogicMixTokens.colors
    val games by viewModel.gamesState.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val ui by viewModel.ui.collectAsState()
    Box(Modifier.fillMaxSize().background(colors.backgroundBrush)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(LogicMixTokens.spacing)
                .widthIn(max = 560.dp).align(Alignment.TopCenter),
            verticalArrangement = com.akynex.logicmix.core.designsystem.stickyFooterArrangement(LogicMixTokens.spacingM),
        ) {
            GameHeader(stringResource(R.string.leaderboards_title), onBack)
            when {
                viewModel.boards.isEmpty() -> Note(stringResource(R.string.lb_not_configured))
                games.status != GamesStatus.SIGNED_IN -> {
                    Note(stringResource(R.string.lb_sign_in_needed))
                    OutlinedButton({ viewModel.connect() }, Modifier.fillMaxWidth().heightIn(min = LogicMixTokens.minTouchTarget)) {
                        Text(stringResource(R.string.games_connect))
                    }
                }
                else -> {
                    viewModel.boards.forEach { key ->
                        val on = key == selected
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = LogicMixTokens.minTouchTarget)
                                .clip(LogicMixTokens.radii.medium)
                                .background(colors.cardBrush)
                                .border(BorderStroke(if (on) 2.dp else 1.dp, if (on) colors.gold else colors.cardBorder), LogicMixTokens.radii.medium)
                                .clickable(role = Role.Button) { viewModel.select(key) }
                                .padding(horizontal = LogicMixTokens.spacingM),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(key.titleRes()), style = LogicMixTokens.typography.titleMedium, color = if (on) colors.gold else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    selected?.let { key -> BoardDetails(key, ui, onOpen = { viewModel.openFull(key) }, onRefresh = viewModel::refresh) }
                }
            }
            BrandFooter()
        }
    }
}

@Composable
private fun Note(text: String) =
    Text(text, style = LogicMixTokens.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun BoardDetails(key: LeaderboardKey, ui: BoardUi, onOpen: () -> Unit, onRefresh: () -> Unit) {
    val colors = LogicMixTokens.colors
    Column(
        Modifier.fillMaxWidth().clip(LogicMixTokens.radii.large).background(colors.cardBrush)
            .border(BorderStroke(1.dp, colors.cardBorder), LogicMixTokens.radii.large).padding(LogicMixTokens.spacingM),
        verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS),
    ) {
        Text(stringResource(key.titleRes()), style = LogicMixTokens.typography.titleLarge, color = colors.gold)
        Note(stringResource(key.metricRes()))
        when {
            ui.loading -> Note(stringResource(R.string.lb_loading))
            ui.standing == null && ui.top == null -> {
                Note(stringResource(R.string.lb_unavailable))
                OutlinedButton(onRefresh, Modifier.fillMaxWidth().heightIn(min = LogicMixTokens.minTouchTarget)) { Text(stringResource(R.string.lb_retry)) }
            }
            else -> {
                val s = ui.standing
                Text(
                    if (s?.rank != null) stringResource(R.string.lb_my_position, s.rank, s.score.orEmpty()) else stringResource(R.string.lb_no_score_yet),
                    style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                )
                ui.top.orEmpty().forEach { e ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS)) {
                        Text(e.rank, color = colors.gold, modifier = Modifier.widthIn(min = 32.dp), style = LogicMixTokens.typography.bodyLarge)
                        Text(e.name, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, style = LogicMixTokens.typography.bodyLarge)
                        Text(e.score, color = MaterialTheme.colorScheme.onSurfaceVariant, style = LogicMixTokens.typography.bodyLarge)
                    }
                }
                OutlinedButton(onOpen, Modifier.fillMaxWidth().heightIn(min = LogicMixTokens.minTouchTarget)) { Text(stringResource(R.string.lb_open_google)) }
            }
        }
    }
}
