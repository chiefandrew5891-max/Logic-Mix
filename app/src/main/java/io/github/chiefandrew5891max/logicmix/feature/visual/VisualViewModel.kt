package io.github.chiefandrew5891max.logicmix.feature.visual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chiefandrew5891max.logicmix.core.audio.Sfx
import io.github.chiefandrew5891max.logicmix.core.audio.SoundEffectsController
import io.github.chiefandrew5891max.logicmix.core.data.CategoryGames
import io.github.chiefandrew5891max.logicmix.domain.visual.VisualGame
import io.github.chiefandrew5891max.logicmix.domain.visual.geo.GeoGame
import io.github.chiefandrew5891max.logicmix.domain.visual.geo.VisualCareer
import io.github.chiefandrew5891max.logicmix.domain.visual.geo.VisualLevel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisualUiState(
    val loading: Boolean = true,
    val total: Int = VisualCareer.total,
    val index: Int = 0,
    val game: VisualGame? = null,
    val geo: GeoGame? = null,
    val allDone: Boolean = false,
    val xpAwarded: Int = 0,
)

@HiltViewModel
class VisualViewModel @Inject constructor(
    private val games: CategoryGames,
    private val sfx: SoundEffectsController,
) : ViewModel() {
    private val _state = MutableStateFlow(VisualUiState())
    val state: StateFlow<VisualUiState> = _state.asStateFlow()
    private var startedAt = System.currentTimeMillis()

    init {
        viewModelScope.launch { open(games.store.levelIndex(KEY)) }
    }

    private suspend fun open(index: Int) {
        val level = VisualCareer.levelAt(index)
        if (level == null) {
            _state.value = VisualUiState(loading = false, index = VisualCareer.total, allDone = true)
            return
        }
        startedAt = System.currentTimeMillis()
        val saved = games.store.state(KEY, level.id)
        _state.value = when (level) {
            is VisualLevel.Classic -> VisualUiState(loading = false, index = index, game = VisualGame.restore(level.puzzle, saved))
            is VisualLevel.Generated -> VisualUiState(loading = false, index = index, geo = GeoGame.restore(level.puzzle, saved))
        }
    }

    private fun persist(game: VisualGame) {
        viewModelScope.launch { games.store.saveState(KEY, game.puzzle.id, game.encode()) }
    }

    private fun persist(game: GeoGame) {
        viewModelScope.launch { games.store.saveState(KEY, game.puzzle.id, game.encode()) }
    }

    fun choose(optionId: String) {
        _state.value.geo?.let { return chooseGeo(it, optionId) }
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
        _state.value.geo?.let { return hintGeo(it) }
        val game = _state.value.game ?: return
        val next = game.hint()
        if (next === game) return
        sfx.play(Sfx.HINT)
        _state.update { it.copy(game = next) }
        persist(next)
    }

    fun restart() {
        _state.value.geo?.let { return restartGeo(it) }
        val game = _state.value.game ?: return
        if (game.solved) return
        val next = game.restart()
        _state.update { it.copy(game = next) }
        persist(next)
    }

    private fun chooseGeo(game: GeoGame, optionId: String) {
        val next = game.choose(optionId)
        if (next === game) return
        sfx.play(Sfx.CARD_SELECT)
        _state.update { it.copy(geo = next) }
        if (next.solved) finish(next.puzzle.id, next.hintsUsed) else {
            sfx.play(Sfx.WRONG)
            persist(next)
        }
    }

    private fun hintGeo(game: GeoGame) {
        val next = game.hint()
        if (next === game) return
        sfx.play(Sfx.HINT)
        _state.update { it.copy(geo = next) }
        persist(next)
    }

    private fun restartGeo(game: GeoGame) {
        if (game.solved) return
        val next = game.restart()
        _state.update { it.copy(geo = next) }
        persist(next)
    }

    private fun finish(game: VisualGame) = finish(game.puzzle.id, game.hintsUsed)

    private fun finish(puzzleId: String, hintsUsed: Int) {
        viewModelScope.launch {
            games.store.clearState(KEY, puzzleId)
            val result = games.complete(puzzleId, startedAt, hintsUsed)
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
