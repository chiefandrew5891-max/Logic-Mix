package io.github.chiefandrew5891max.logicmix.feature.matchstick

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chiefandrew5891max.logicmix.core.audio.Sfx
import io.github.chiefandrew5891max.logicmix.core.audio.SoundEffectsController
import io.github.chiefandrew5891max.logicmix.core.data.LocalProfileRepository
import io.github.chiefandrew5891max.logicmix.core.data.ProgressRepository
import io.github.chiefandrew5891max.logicmix.domain.game.AttemptOutcome
import io.github.chiefandrew5891max.logicmix.domain.game.GameMode
import io.github.chiefandrew5891max.logicmix.domain.matchstick.MatchGame
import io.github.chiefandrew5891max.logicmix.domain.matchstick.MatchMove
import io.github.chiefandrew5891max.logicmix.domain.matchstick.MatchPuzzles
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MatchUiState(
    val game: MatchGame? = null,
    val allDone: Boolean = false,
    val hint: MatchMove? = null,
    val noHint: Boolean = false,
    val xpAwarded: Int = 0,
    val startedAt: Long = 0L,
)

@HiltViewModel
class MatchstickViewModel @Inject constructor(
    private val profiles: LocalProfileRepository,
    private val progress: ProgressRepository,
    private val sfx: SoundEffectsController,
) : ViewModel() {
    private val profileId = LocalProfileRepository.LOCAL_PROFILE_ID
    private val _state = MutableStateFlow(MatchUiState())
    val state: StateFlow<MatchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { loadNext() }
    }

    private suspend fun loadNext() {
        profiles.ensureProfile()
        progress.ensureProgress(profileId)
        val puzzle = MatchPuzzles.firstUnsolved(progress.solvedPuzzleIds(profileId))
        _state.value = if (puzzle == null) MatchUiState(allDone = true)
        else MatchUiState(game = MatchGame(puzzle), startedAt = System.currentTimeMillis())
    }

    fun next() {
        viewModelScope.launch { loadNext() }
    }

    fun pickUp() = sfx.play(Sfx.MATCHSTICK_PICKUP)

    fun dragMoved() = sfx.play(Sfx.MATCHSTICK_MOVE)

    /** Returns true when the match was actually relocated. */
    fun place(from: Int, to: Int): Boolean {
        val game = _state.value.game ?: return false
        val next = game.move(from, to)
        if (next === game) return false
        _state.update { it.copy(game = next, hint = null, noHint = false) }
        sfx.play(Sfx.MATCHSTICK_PLACE)
        when {
            next.solved -> finish(next)
            next.failed -> sfx.play(Sfx.WRONG)
        }
        return true
    }

    fun undo() {
        val game = _state.value.game ?: return
        if (game.solved || !game.canUndo) return
        sfx.play(Sfx.BUTTON_TAP)
        _state.update { it.copy(game = game.undo(), hint = null, noHint = false) }
    }

    fun reset() {
        val game = _state.value.game ?: return
        if (game.solved || game.movesUsed == 0) return
        sfx.play(Sfx.BUTTON_TAP)
        _state.update { it.copy(game = game.reset(), hint = null, noHint = false) }
    }

    fun hint() {
        val game = _state.value.game ?: return
        if (game.solved) return
        // If the player already strayed from every solution, restart from the beginning for the hint.
        val base = if (game.hintMove() == null) game.reset() else game
        val move = base.hintMove()
        sfx.play(Sfx.HINT)
        if (move == null) {
            _state.update { it.copy(noHint = true) }
        } else {
            _state.update { it.copy(game = base.withHint(), hint = move, noHint = false) }
        }
    }

    private fun finish(game: MatchGame) {
        val started = _state.value.startedAt
        viewModelScope.launch {
            val result = progress.recordAttempt(
                profileId, game.puzzle.id, GameMode.CAREER, AttemptOutcome.CORRECT,
                started, System.currentTimeMillis(), game.hintsUsed,
            )
            _state.update { it.copy(xpAwarded = result.xpAwarded) }
            sfx.play(Sfx.CORRECT)
            if (result.xpAwarded > 0) sfx.play(Sfx.XP_GAIN)
            sfx.play(if (result.after.level > result.before.level) Sfx.LEVEL_UP else Sfx.LEVEL_COMPLETE)
        }
    }
}
