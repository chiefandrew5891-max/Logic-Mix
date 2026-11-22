package com.akynex.logicmix.feature.visual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.CategoryGames
import com.akynex.logicmix.domain.visual.VisualGame
import com.akynex.logicmix.domain.visual.VisualPacks
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisualUiState(
    val loading: Boolean = true,
    val total: Int = VisualPacks.all.size,
    val index: Int = 0,
    val game: VisualGame? = null,
    val allDone: Boolean = false,
    val xpAwarded: Int = 0,
)

@HiltViewModel
class VisualViewModel @Inject constructor(
    private val games: CategoryGames,
    private val sfx: SoundEffectsController,
) : ViewModel() {
    private val puzzles = VisualPacks.all
    private val _state = MutableStateFlow(VisualUiState())
    val state: StateFlow<VisualUiState> = _state.asStateFlow()
    private var startedAt = System.currentTimeMillis()

    init {
        viewModelScope.launch { open(games.store.levelIndex(KEY)) }
    }

    private suspend fun open(index: Int) {
        if (index >= puzzles.size) {
            _state.value = VisualUiState(loading = false, index = puzzles.size, allDone = true)
            return
        }
        val puzzle = puzzles[index]
        startedAt = System.currentTimeMillis()
        _state.value = VisualUiState(loading = false, index = index, game = VisualGame.restore(puzzle, games.store.state(KEY, puzzle.id)))
    }

    private fun persist(game: VisualGame) {
        viewModelScope.launch { games.store.saveState(KEY, game.puzzle.id, game.encode()) }
    }

    fun choose(optionId: String) {
        val game = _state.value.game ?: return
        val next = game.choose(optionId)
        if (next === game) return
        sfx.play(Sfx.CARD_SELECT)
        _state.update { it.copy(game = next) }
        if (next.solved) finish(next) else {
            sfx.play(Sfx.WRONG)
            persist(next)
        }
    }

    fun hint() {
        val game = _state.value.game ?: return
        val next = game.hint()
        if (next === game) return
        sfx.play(Sfx.HINT)
        _state.update { it.copy(game = next) }
        persist(next)
    }

    fun restart() {
        val game = _state.value.game ?: return
        if (game.solved) return
        val next = game.restart()
        _state.update { it.copy(game = next) }
        persist(next)
    }

    private fun finish(game: VisualGame) {
        viewModelScope.launch {
            games.store.clearState(KEY, game.puzzle.id)
            val result = games.complete(game.puzzle.id, startedAt, game.hintsUsed)
            _state.update { it.copy(xpAwarded = result.xpAwarded) }
            sfx.play(Sfx.CORRECT)
            if (result.xpAwarded > 0) sfx.play(Sfx.XP_GAIN)
            if (result.after.level > result.before.level) sfx.play(Sfx.LEVEL_UP)
        }
    }

    fun next() {
        viewModelScope.launch {
            val index = _state.value.index + 1
            games.store.setLevelIndex(KEY, index)
            open(index)
        }
    }

    fun playAgain() {
        viewModelScope.launch {
            games.store.setLevelIndex(KEY, 0)
            open(0)
        }
    }

    private companion object { const val KEY = "visual_logic" }
}
