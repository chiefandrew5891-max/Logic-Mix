package com.akynex.logicmix.feature.wordlogic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.CategoryGames
import com.akynex.logicmix.core.data.SettingsRepository
import com.akynex.logicmix.core.locale.effectiveLanguage
import com.akynex.logicmix.domain.content.ContentLanguage
import com.akynex.logicmix.domain.wordlogic.WordLogicGame
import com.akynex.logicmix.domain.wordlogic.WordLogicPacks
import com.akynex.logicmix.domain.wordlogic.WordLogicPuzzle
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WordLogicUiState(
    val loading: Boolean = true,
    val puzzles: List<WordLogicPuzzle> = emptyList(),
    val index: Int = 0,
    val game: WordLogicGame? = null,
    val fallback: Boolean = false,
    val allDone: Boolean = false,
    val xpAwarded: Int = 0,
)

@HiltViewModel
class WordLogicViewModel @Inject constructor(
    private val games: CategoryGames,
    private val settings: SettingsRepository,
    private val sfx: SoundEffectsController,
) : ViewModel() {
    private val _state = MutableStateFlow(WordLogicUiState())
    val state: StateFlow<WordLogicUiState> = _state.asStateFlow()
    private var startedAt = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            val resolved = WordLogicPacks.resolve(ContentLanguage.forInterface(settings.effectiveLanguage().first()))
            val index = games.store.levelIndex(KEY)
            _state.value = WordLogicUiState(loading = false, puzzles = resolved.puzzles, fallback = resolved.isFallback)
            open(index)
        }
    }

    private suspend fun open(index: Int) {
        val puzzles = _state.value.puzzles
        if (index >= puzzles.size) {
            _state.update { it.copy(index = puzzles.size, game = null, allDone = true, xpAwarded = 0) }
            return
        }
        val puzzle = puzzles[index]
        val game = WordLogicGame.restore(puzzle, games.store.state(KEY, puzzle.id))
        startedAt = System.currentTimeMillis()
        _state.update { it.copy(index = index, game = game, allDone = false, xpAwarded = 0) }
    }

    private fun persist(game: WordLogicGame) {
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

    private fun finish(game: WordLogicGame) {
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

    private companion object { const val KEY = "word_logic" }
}
