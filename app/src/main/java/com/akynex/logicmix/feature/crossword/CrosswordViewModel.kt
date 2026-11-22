package com.akynex.logicmix.feature.crossword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.CategoryGames
import com.akynex.logicmix.core.data.SettingsRepository
import com.akynex.logicmix.core.locale.effectiveLanguage
import com.akynex.logicmix.domain.content.ContentLanguage
import com.akynex.logicmix.domain.crossword.CrosswordEntry
import com.akynex.logicmix.domain.crossword.CrosswordGame
import com.akynex.logicmix.domain.crossword.CrosswordCareer
import com.akynex.logicmix.domain.crossword.CrosswordPuzzle
import com.akynex.logicmix.core.analytics.GameContext
import com.akynex.logicmix.domain.content.GameCategory
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CheckResult { NONE, MISTAKES, OK }

data class CrosswordUiState(
    val loading: Boolean = true,
    val puzzles: List<CrosswordPuzzle> = emptyList(),
    val language: ContentLanguage = ContentLanguage.EN,
    val unsupported: Boolean = false,
    val index: Int = 0,
    val game: CrosswordGame? = null,
    val check: CheckResult = CheckResult.NONE,
    val allDone: Boolean = false,
    val xpAwarded: Int = 0,
)

@HiltViewModel
class CrosswordViewModel @Inject constructor(
    private val games: CategoryGames,
    private val settings: SettingsRepository,
    private val sfx: SoundEffectsController,
    monetization: com.akynex.logicmix.core.monetization.Monetization,
) : ViewModel() {
    private val analytics = monetization.newGameAnalytics()
    /** Non-null while hosted by Arcade: serves the next unsolved Career level and replays by stage count once the content is used up. */
    private val arcade = monetization.arcade.activeFor(com.akynex.logicmix.domain.content.GameCategory.MINI_CROSSWORD)
    override fun onCleared() { analytics.leave() }

    private val _state = MutableStateFlow(CrosswordUiState())
    val state: StateFlow<CrosswordUiState> = _state.asStateFlow()
    private var startedAt = System.currentTimeMillis()
    private var key = KEY_PREFIX

    init {
        // The interface language picks the whole pack; progress is stored per language so packs never mix.
        viewModelScope.launch {
            settings.effectiveLanguage().distinctUntilChanged().collect { language ->
                val content = ContentLanguage.forInterface(language)
                val puzzles = withContext(Dispatchers.Default) { CrosswordCareer.forLanguage(content) }
                key = "$KEY_PREFIX${content.tag}"
                _state.value = CrosswordUiState(loading = false, puzzles = puzzles.orEmpty(), language = content, unsupported = puzzles == null)
                if (puzzles != null) open(games.store.levelIndex(key))
            }
        }
    }

    private suspend fun open(careerIndex: Int) {
        val puzzles = _state.value.puzzles
        val index = if (arcade != null && careerIndex >= puzzles.size && puzzles.isNotEmpty()) arcade.ordinal % puzzles.size else careerIndex
        if (index >= puzzles.size) {
            _state.update { it.copy(index = puzzles.size, game = null, allDone = true, xpAwarded = 0, check = CheckResult.NONE) }
            return
        }
        val puzzle = puzzles[index]
        val game = CrosswordGame.restore(puzzle, games.store.state(key, puzzle.id))
        startedAt = System.currentTimeMillis()
        val ctx = GameContext(GameCategory.MINI_CROSSWORD, family = "mini_crossword", mode = "career",
            levelIndex = index + 1, contentLanguage = _state.value.language.tag)
        analytics.openCategory(ctx)
        analytics.puzzleStarted(puzzle.id, ctx, resumed = game.hintsUsed > 0)
        _state.update { it.copy(index = index, game = game, allDone = false, xpAwarded = 0, check = CheckResult.NONE) }
    }

    private fun update(sound: Sfx? = null, check: CheckResult = CheckResult.NONE, change: (CrosswordGame) -> CrosswordGame) {
        val game = _state.value.game ?: return
        if (game.solved) return
        val next = change(game)
        if (next == game && check == CheckResult.NONE) return
        sound?.let { sfx.play(it) }
        _state.update { it.copy(game = next, check = check) }
        if (next.solved) finish(next) else viewModelScope.launch { games.store.saveState(key, next.puzzle.id, next.encode()) }
    }

    fun selectCell(cell: Int) = update(Sfx.CARD_SELECT) { it.select(cell) }

    fun selectEntry(entry: CrosswordEntry) = update(Sfx.CARD_SELECT) { it.selectEntry(entry) }

    fun letter(ch: Char) = update(Sfx.LETTER_INPUT) { it.input(ch) }

    fun delete() = update(Sfx.LETTER_DELETE) { it.delete() }

    fun checkWord() = check { it.checkWord() }

    fun checkAll() = check { it.checkAll() }

    private fun check(change: (CrosswordGame) -> CrosswordGame) {
        val game = _state.value.game ?: return
        if (game.solved) return
        val next = change(game)
        val result = if (next.wrong.isEmpty()) CheckResult.OK else CheckResult.MISTAKES
        sfx.play(if (result == CheckResult.OK) Sfx.WORD_FOUND else Sfx.WRONG)
        if (result == CheckResult.MISTAKES) analytics.attemptFailed()
        _state.update { it.copy(game = next, check = result) }
    }

    fun hintLetter() = hint { it.hintLetter() }

    fun hintWord() = hint { it.hintWord() }

    private fun hint(change: (CrosswordGame) -> CrosswordGame) {
        val before = _state.value.game?.takeIf { !it.solved } ?: return
        analytics.hintRequested()
        update(Sfx.HINT, change = change)
        if ((_state.value.game?.hintsUsed ?: 0) > before.hintsUsed) analytics.hintUsed()
    }

    fun restart() {
        val game = _state.value.game ?: return
        if (game.solved) return
        val next = game.restart()
        analytics.restarted()
        _state.update { it.copy(game = next, check = CheckResult.NONE) }
        viewModelScope.launch { games.store.saveState(key, next.puzzle.id, next.encode()) }
    }

    private fun finish(game: CrosswordGame) {
        viewModelScope.launch {
            games.store.clearState(key, game.puzzle.id)
            val result = games.complete(game.puzzle.id, startedAt, game.hintsUsed)
            // Same effect as the Next button, so the following Arcade stage gets a new puzzle.
            val served = _state.value.index
            if (arcade != null && games.store.levelIndex(key) == served) games.store.setLevelIndex(key, served + 1)
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
            games.store.setLevelIndex(key, index)
            open(index)
        }
    }

    fun playAgain() {
        viewModelScope.launch {
            games.store.setLevelIndex(key, 0)
            open(0)
        }
    }

    private companion object { const val KEY_PREFIX = "mini_crossword_" }
}
