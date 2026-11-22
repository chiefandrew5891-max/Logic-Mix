package com.akynex.logicmix.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.GameSessionStore
import com.akynex.logicmix.core.data.LocalProfileRepository
import com.akynex.logicmix.core.data.ProgressRepository
import com.akynex.logicmix.domain.game.AttemptOutcome
import com.akynex.logicmix.domain.game.GameMode
import com.akynex.logicmix.domain.game.NumberSequenceEngine
import com.akynex.logicmix.domain.game.numlogic.NumberLogicCareer
import com.akynex.logicmix.domain.game.NumberSequencePuzzle
import com.akynex.logicmix.domain.game.SolveResult
import com.akynex.logicmix.core.analytics.GameContext
import com.akynex.logicmix.domain.content.GameCategory
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Feedback { NONE, CORRECT, WRONG, INVALID }

data class GameUiState(
    val loading: Boolean = true,
    val finished: Boolean = false,
    val puzzle: NumberSequencePuzzle? = null,
    val number: Int = 0,
    val total: Int = NumberLogicCareer.total,
    val hintsUsed: Int = 0,
    val input: String = "",
    val feedback: Feedback = Feedback.NONE,
    val xpAwarded: Int = 0,
    val startedAt: Long = 0L,
) {
    val solved: Boolean get() = feedback == Feedback.CORRECT
    val shownHints: List<String> get() = puzzle?.hints?.take(hintsUsed).orEmpty()
    val canUseHint: Boolean get() = puzzle != null && !solved && hintsUsed < puzzle.hints.size
    val canSubmit: Boolean get() = puzzle != null && !solved && input.isNotBlank()
}

@HiltViewModel
class GameViewModel @Inject constructor(
    private val profiles: LocalProfileRepository,
    private val progress: ProgressRepository,
    private val sessions: GameSessionStore,
    private val sfx: SoundEffectsController,
    monetization: com.akynex.logicmix.core.monetization.Monetization,
) : ViewModel() {
    private val analytics = monetization.newGameAnalytics()
    private val genericCtx = GameContext(GameCategory.NUMBER_SEQUENCE, mode = "career")
    private fun ctx(p: NumberSequencePuzzle) = genericCtx.copy(
        family = p.family.name.lowercase(), difficulty = p.difficulty.toString(),
        levelIndex = NumberLogicCareer.indexOf(p.id) + 1, contentLanguage = "en",
    )

    override fun onCleared() { analytics.leave() }

    private val profileId = LocalProfileRepository.LOCAL_PROFILE_ID
    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _state.value = GameUiState(loading = true)
            profiles.ensureProfile()
            progress.ensureProgress(profileId)
            val solved = progress.solvedPuzzleIds(profileId).toSet()
            val resumed = sessions.session.first()?.puzzleId?.let(NumberLogicCareer::byId)?.takeIf { it.id !in solved }
            val puzzle = resumed ?: NumberLogicCareer.firstUnsolved(solved)
            if (puzzle == null) {
                sessions.clear()
                _state.value = GameUiState(loading = false, finished = true)
                return@launch
            }
            val session = sessions.begin(puzzle.id, System.currentTimeMillis())
            analytics.openCategory(genericCtx)
            analytics.puzzleStarted(puzzle.id, ctx(puzzle), resumed = resumed != null, hints = session.hintsUsed)
            _state.value = GameUiState(
                loading = false,
                puzzle = puzzle,
                number = NumberLogicCareer.indexOf(puzzle.id) + 1,
                hintsUsed = session.hintsUsed.coerceAtMost(puzzle.hints.size),
                startedAt = session.startedAt,
            )
        }
    }

    fun onInput(text: String) {
        val before = _state.value
        if (before.solved) return
        val accepted = text.take(MAX_INPUT)
        // One click per digit actually added; deleting or rejected input stays silent.
        if (accepted.length > before.input.length && accepted.last().isDigit()) sfx.play(Sfx.NUMBER_INPUT)
        else if (accepted.length < before.input.length) sfx.play(Sfx.NUMBER_DELETE)
        _state.update { if (it.solved) it else it.copy(input = accepted, feedback = Feedback.NONE) }
    }

    fun useHint() {
        val s = _state.value
        val puzzle = s.puzzle ?: return
        if (!s.canUseHint) return
        val used = s.hintsUsed + 1
        sfx.play(Sfx.HINT)
        analytics.hintRequested(); analytics.hintUsed()
        _state.update { it.copy(hintsUsed = used) }
        viewModelScope.launch { sessions.setHintsUsed(puzzle.id, used) }
    }

    fun submit() {
        val s = _state.value
        val puzzle = s.puzzle ?: return
        if (!s.canSubmit) return
        when (NumberSequenceEngine.checkAnswer(puzzle, s.input)) {
            SolveResult.InvalidInput -> _state.update { it.copy(feedback = Feedback.INVALID) }
            SolveResult.Incorrect -> {
                sfx.play(Sfx.WRONG)
                _state.update { it.copy(feedback = Feedback.WRONG) }
                analytics.attemptFailed()
                record(puzzle, s, AttemptOutcome.INCORRECT)
            }
            SolveResult.Correct -> {
                // Lock the screen immediately so a double tap cannot submit twice.
                sfx.play(Sfx.CORRECT)
                _state.update { it.copy(feedback = Feedback.CORRECT) }
                viewModelScope.launch {
                    val result = progress.recordAttempt(
                        profileId, puzzle.id, GameMode.CAREER, AttemptOutcome.CORRECT,
                        s.startedAt, System.currentTimeMillis(), s.hintsUsed,
                    )
                    sessions.clear()
                    _state.update { it.copy(xpAwarded = result.xpAwarded) }
                    analytics.completed(result.xpAwarded)
                    if (result.xpAwarded > 0) sfx.play(Sfx.XP_GAIN)
                    sfx.play(if (result.after.level > result.before.level) Sfx.LEVEL_UP else Sfx.LEVEL_COMPLETE)
                }
            }
        }
    }

    fun next() {
        if (_state.value.solved) load()
    }

    /** Clears this attempt's input and feedback. The hint counter is kept so a restart cannot win back the no-hint bonus. */
    fun restart() {
        if (_state.value.solved || _state.value.puzzle == null) return
        sfx.play(Sfx.BUTTON_TAP)
        analytics.restarted()
        _state.update { it.copy(input = "", feedback = Feedback.NONE) }
    }

    private fun record(puzzle: NumberSequencePuzzle, s: GameUiState, outcome: AttemptOutcome) {
        viewModelScope.launch {
            progress.recordAttempt(profileId, puzzle.id, GameMode.CAREER, outcome, s.startedAt, System.currentTimeMillis(), s.hintsUsed)
        }
    }

    private companion object {
        const val MAX_INPUT = 12
    }
}
