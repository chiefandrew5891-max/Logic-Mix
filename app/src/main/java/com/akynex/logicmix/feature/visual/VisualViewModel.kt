package com.akynex.logicmix.feature.visual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.CategoryGames
import com.akynex.logicmix.domain.visual.VisualGame
import com.akynex.logicmix.domain.visual.geo.GeoGame
import com.akynex.logicmix.domain.visual.geo.VisualCareer
import com.akynex.logicmix.domain.visual.geo.VisualLevel
import com.akynex.logicmix.core.analytics.GameContext
import com.akynex.logicmix.domain.content.GameCategory
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
    monetization: com.akynex.logicmix.core.monetization.Monetization,
) : ViewModel() {
    private val analytics = monetization.newGameAnalytics()
    /** Non-null while hosted by Arcade: serves the next unsolved Career level and replays by stage count once the content is used up. */
    private val arcade = monetization.arcade.activeFor(com.akynex.logicmix.domain.content.GameCategory.VISUAL_LOGIC)
    private val baseCtx = GameContext(GameCategory.VISUAL_LOGIC, mode = "career")
    override fun onCleared() { analytics.leave() }

    private val _state = MutableStateFlow(VisualUiState())
    val state: StateFlow<VisualUiState> = _state.asStateFlow()
    private var startedAt = System.currentTimeMillis()

    init {
        viewModelScope.launch { open(games.store.levelIndex(KEY)) }
    }

    private suspend fun open(careerIndex: Int) {
        val index = if (arcade != null && careerIndex >= VisualCareer.total) arcade.ordinal % VisualCareer.total else careerIndex
        val level = VisualCareer.levelAt(index)
        if (level == null) {
            _state.value = VisualUiState(loading = false, index = VisualCareer.total, allDone = true)
            return
        }
        startedAt = System.currentTimeMillis()
        val saved = games.store.state(KEY, level.id)
        analytics.openCategory(baseCtx)
        val (family, difficulty) = when (level) {
            is VisualLevel.Classic -> "classic" to null
            is VisualLevel.Generated -> level.puzzle.family.name.lowercase() to level.puzzle.difficulty.toString()
        }
        analytics.puzzleStarted(level.id, baseCtx.copy(family = family, difficulty = difficulty, levelIndex = index + 1), resumed = saved != null)
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
            analytics.attemptFailed()
            persist(next)
        }
    }

    fun hint() {
        _state.value.geo?.let { return hintGeo(it) }
        val game = _state.value.game ?: return
        val next = game.hint()
        if (next === game) return
        sfx.play(Sfx.HINT)
        analytics.hintRequested(); analytics.hintUsed()
        _state.update { it.copy(game = next) }
        persist(next)
    }

    fun restart() {
        _state.value.geo?.let { return restartGeo(it) }
        val game = _state.value.game ?: return
        if (game.solved) return
        val next = game.restart()
        analytics.restarted()
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
            analytics.attemptFailed()
            persist(next)
        }
    }

    private fun hintGeo(game: GeoGame) {
        val next = game.hint()
        if (next === game) return
        sfx.play(Sfx.HINT)
        analytics.hintRequested(); analytics.hintUsed()
        _state.update { it.copy(geo = next) }
        persist(next)
    }

    private fun restartGeo(game: GeoGame) {
        if (game.solved) return
        val next = game.restart()
        analytics.restarted()
        _state.update { it.copy(geo = next) }
        persist(next)
    }

    private fun finish(game: VisualGame) = finish(game.puzzle.id, game.hintsUsed)

    private fun finish(puzzleId: String, hintsUsed: Int) {
        viewModelScope.launch {
            games.store.clearState(KEY, puzzleId)
            val result = games.complete(puzzleId, startedAt, hintsUsed)
            // Same effect as the Next button, so the following Arcade stage gets a new puzzle.
            val served = _state.value.index
            if (arcade != null && games.store.levelIndex(KEY) == served) games.store.setLevelIndex(KEY, served + 1)
            _state.update { it.copy(xpAwarded = result.xpAwarded) }
            analytics.completed(result.xpAwarded)
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
