package com.akynex.logicmix.feature.sudoku

import com.akynex.logicmix.domain.content.GameCategory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.LocalProfileRepository
import com.akynex.logicmix.core.data.ProgressRepository
import com.akynex.logicmix.core.data.SudokuStore
import com.akynex.logicmix.domain.game.AttemptOutcome
import com.akynex.logicmix.domain.game.GameMode
import com.akynex.logicmix.domain.sudoku.SudokuDifficulty
import com.akynex.logicmix.domain.sudoku.SudokuGame
import com.akynex.logicmix.domain.sudoku.SudokuGenerator
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SudokuPhase { LOADING, PICK, PLAY }

data class SudokuUiState(
    val phase: SudokuPhase = SudokuPhase.LOADING,
    val game: SudokuGame? = null,
    val seed: Long = 0L,
    val selected: Int? = null,
    val notesMode: Boolean = false,
    val elapsedSec: Long = 0L,
    val solved: Boolean = false,
    val xpAwarded: Int = 0,
    val canResume: Boolean = false,
) {
    val difficulty get() = game?.puzzle?.difficulty
}

@HiltViewModel
class SudokuViewModel @Inject constructor(
    private val profiles: LocalProfileRepository,
    private val progress: ProgressRepository,
    private val store: SudokuStore,
    private val sfx: SoundEffectsController,
    monetization: com.akynex.logicmix.core.monetization.Monetization,
) : ViewModel() {
    private val analytics = monetization.newGameAnalytics()
    /** Non-null while this screen is hosted by Arcade: a fresh seeded puzzle, and the saved Career game stays untouched. */
    private val arcade = monetization.arcade.activeFor(com.akynex.logicmix.domain.content.GameCategory.SUDOKU)
    private fun ctx(d: SudokuDifficulty) = com.akynex.logicmix.core.analytics.GameContext(
        com.akynex.logicmix.domain.content.GameCategory.SUDOKU, family = "sudoku",
        mode = "endless", difficulty = d.name.lowercase())
    override fun onCleared() { analytics.leave() }

    private val profileId = LocalProfileRepository.LOCAL_PROFILE_ID
    private val _state = MutableStateFlow(SudokuUiState())
    val state: StateFlow<SudokuUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { restore() }
        viewModelScope.launch { tick() }
    }

    private suspend fun startArcade(stage: com.akynex.logicmix.domain.arcade.ArcadeStage) {
        val difficulty = when {
            stage.ordinal < 2 -> SudokuDifficulty.EASY
            stage.ordinal < 5 -> SudokuDifficulty.MEDIUM
            else -> SudokuDifficulty.HARD
        }
        _state.value = SudokuUiState(SudokuPhase.LOADING)
        val puzzle = withContext(Dispatchers.Default) { SudokuGenerator.generate(difficulty, stage.seed) }
        _state.value = SudokuUiState(SudokuPhase.PLAY, SudokuGame(puzzle), stage.seed)
        analytics.openCategory(ctx(difficulty))
        analytics.puzzleStarted(puzzle.id, ctx(difficulty))
    }

    private suspend fun restore() {
        arcade?.let { return startArcade(it) }
        val saved = store.active()
        val puzzle = saved?.let { withContext(Dispatchers.Default) { SudokuGenerator.generate(it.difficulty, it.seed) } }
        val game = if (puzzle != null && saved != null) SudokuGame.restore(puzzle, saved.game) else null
        _state.value = if (game != null && saved != null) {
            analytics.openCategory(ctx(game.puzzle.difficulty))
            if (!game.solved) analytics.puzzleStarted(game.puzzle.id, ctx(game.puzzle.difficulty), resumed = true, hints = game.hintsUsed)
            SudokuUiState(SudokuPhase.PLAY, game, saved.seed, elapsedSec = saved.elapsedSec, solved = game.solved)
        } else {
            SudokuUiState(SudokuPhase.PICK)
        }
    }

    // Counts only while the app is visible, so a backgrounded game does not run up the clock.
    private suspend fun tick() {
        var sinceSave = 0
        while (true) {
            delay(1000)
            val s = _state.value
            if (s.phase != SudokuPhase.PLAY || s.solved) continue
            if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) continue
            _state.update { it.copy(elapsedSec = it.elapsedSec + 1) }
            if (++sinceSave >= 5) { sinceSave = 0; persist() }
        }
    }

    private fun persist() {
        val s = _state.value
        val game = s.game ?: return
        if (arcade != null || s.phase != SudokuPhase.PLAY || s.solved) return
        viewModelScope.launch { store.save(game.puzzle.difficulty, s.seed, game.encode(), s.elapsedSec) }
    }

    fun start(difficulty: SudokuDifficulty) {
        if (_state.value.phase == SudokuPhase.LOADING) return
        _state.value = SudokuUiState(SudokuPhase.LOADING)
        viewModelScope.launch {
            val seed = System.currentTimeMillis()
            val puzzle = withContext(Dispatchers.Default) { SudokuGenerator.generate(difficulty, seed) }
            _state.value = SudokuUiState(SudokuPhase.PLAY, SudokuGame(puzzle), seed)
            analytics.openCategory(ctx(difficulty))
            analytics.selectDifficulty(ctx(difficulty))
            analytics.puzzleStarted(puzzle.id, ctx(difficulty))
            persist()
        }
    }

    /** Back to the difficulty picker (after finishing, or to abandon nothing: an unfinished game stays saved). */
    fun newGame() {
        if (!_state.value.solved) analytics.abandoned()
        _state.value = SudokuUiState(SudokuPhase.PICK)
    }

    fun select(cell: Int) = _state.update { if (it.solved) it else it.copy(selected = cell) }

    fun toggleNotes() = _state.update { it.copy(notesMode = !it.notesMode) }

    private fun apply(change: (SudokuGame) -> SudokuGame?, sound: (SudokuGame, SudokuGame) -> Unit) {
        val s = _state.value
        val game = s.game ?: return
        if (s.solved) return
        val next = change(game) ?: return
        if (next === game) return
        sound(game, next)
        _state.update { it.copy(game = next) }
        if (next.solved) finish(next) else persist()
    }

    fun input(digit: Int) {
        val cell = _state.value.selected ?: return
        val notes = _state.value.notesMode
        apply({ if (notes) it.toggleNote(cell, digit) else it.place(cell, digit) }) { _, next ->
            sfx.play(if (!notes && cell in next.conflicts) Sfx.WRONG else Sfx.NUMBER_INPUT)
        }
    }

    fun erase() {
        val cell = _state.value.selected ?: return
        apply({ it.erase(cell) }) { _, _ -> sfx.play(Sfx.NUMBER_DELETE) }
    }

    fun undo() = apply({ it.undo() }) { _, _ -> sfx.play(Sfx.BUTTON_TAP) }

    fun redo() = apply({ it.redo() }) { _, _ -> sfx.play(Sfx.BUTTON_TAP) }

    fun hint() {
        val s = _state.value
        val target = s.game?.hintTarget(s.selected) ?: return
        _state.update { it.copy(selected = target) }
        analytics.hintRequested()
        apply({ it.hint(s.selected) }) { _, _ -> sfx.play(Sfx.HINT); analytics.hintUsed() }
    }

    fun reset() {
        if (_state.value.game?.solved == false) analytics.restarted()
        apply({ it.reset() }) { _, _ -> sfx.play(Sfx.BUTTON_TAP) }
        _state.update { it.copy(elapsedSec = 0, selected = null) }
        persist()
    }

    private fun finish(game: SudokuGame) {
        val s = _state.value
        _state.update { it.copy(solved = true) }
        viewModelScope.launch {
            if (arcade == null) store.clear()
            profiles.ensureProfile()
            progress.ensureProgress(profileId)
            val now = System.currentTimeMillis()
            val result = progress.recordAttempt(
                profileId, game.puzzle.id, GameMode.CAREER, AttemptOutcome.CORRECT,
                now - s.elapsedSec * 1000, now, game.hintsUsed,
                GameCategory.SUDOKU, game.puzzle.difficulty.name.lowercase(),
            )
            _state.update { it.copy(xpAwarded = result.xpAwarded) }
            analytics.completed(result.xpAwarded)
            if (result.xpAwarded > 0) sfx.play(Sfx.XP_GAIN)
            sfx.play(Sfx.LEVEL_COMPLETE)
        }
    }
}
