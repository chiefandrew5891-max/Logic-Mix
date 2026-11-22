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
import com.akynex.logicmix.core.analytics.GameContext
import com.akynex.logicmix.domain.content.GameCategory
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
    monetization: com.akynex.logicmix.core.monetization.Monetization,
) : ViewModel() {
    private val analytics = monetization.newGameAnalytics()
    /** Non-null while hosted by Arcade: serves the next unsolved Career level and replays by stage count once the content is used up. */
    private val arcade = monetization.arcade.activeFor(com.akynex.logicmix.domain.content.GameCategory.WORD_LOGIC)
    override fun onCleared() { analytics.leave() }

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

    private suspend fun open(careerIndex: Int) {
        val puzzles = _state.value.puzzles
        val index = if (arcade != null && careerIndex >= puzzles.size && puzzles.isNotEmpty()) arcade.ordinal % puzzles.size else careerIndex
        if (index >= puzzles.size) {
            _state.update { it.copy(index = puzzles.size, game = null, allDone = true, xpAwarded = 0) }
            return
        }
        val puzzle = puzzles[index]
        val game = WordLogicGame.restore(puzzle, games.store.state(KEY, puzzle.id))
        startedAt = System.currentTimeMillis()
        val ctx = GameContext(GameCategory.WORD_LOGIC, family = puzzle.type.name.lowercase(), mode = "career",
            difficulty = puzzle.difficulty.toString(), levelIndex = index + 1, contentLanguage = puzzle.language.tag)
        analytics.openCategory(ctx)
        analytics.puzzleStarted(puzzle.id, ctx, resumed = game.hintsUsed > 0)
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
            analytics.attemptFailed()
            persist(next)
        }
    }

    fun hint() {
        val game = _state.value.game ?: return
        val next = game.hint()
        if (next === game) return
        sfx.play(Sfx.HINT)
        analytics.hintRequested(); analytics.hintUsed()
        _state.update { it.copy(game = next) }
        persist(next)
    }

    fun restart() {
        val game = _state.value.game ?: return
        if (game.solved) return
        val next = game.restart()
        analytics.restarted()
        _state.update { it.copy(game = next) }
        persist(next)
    }

    private fun finish(game: WordLogicGame) {
        viewModelScope.launch {
            games.store.clearState(KEY, game.puzzle.id)
            val result = games.complete(game.puzzle.id, startedAt, game.hintsUsed)
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

    private companion object { const val KEY = "word_logic" }
}
