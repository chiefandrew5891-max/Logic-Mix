package com.akynex.logicmix.feature.matchstick

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.LocalProfileRepository
import com.akynex.logicmix.core.data.ProgressRepository
import com.akynex.logicmix.domain.game.AttemptOutcome
import com.akynex.logicmix.domain.game.GameMode
import com.akynex.logicmix.domain.matchstick.MatchGame
import com.akynex.logicmix.domain.matchstick.MatchMove
import com.akynex.logicmix.domain.matchstick.MatchPuzzles
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
    monetization: com.akynex.logicmix.core.monetization.Monetization,
) : ViewModel() {
    private val analytics = monetization.newGameAnalytics()
    /** Non-null while hosted by Arcade: serves the next unsolved Career level and replays by stage count once the content is used up. */
    private val arcade = monetization.arcade.activeFor(com.akynex.logicmix.domain.content.GameCategory.MATCHSTICK)
    private val baseCtx = com.akynex.logicmix.core.analytics.GameContext(
        com.akynex.logicmix.domain.content.GameCategory.MATCHSTICK, family = "matchstick", mode = "career")
    override fun onCleared() { analytics.leave() }

    private val profileId = LocalProfileRepository.LOCAL_PROFILE_ID
    private val _state = MutableStateFlow(MatchUiState())
    val state: StateFlow<MatchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { loadNext() }
    }

    private suspend fun loadNext() {
        profiles.ensureProfile()
        progress.ensureProgress(profileId)
        val puzzle = MatchPuzzles.firstUnsolved(progress.solvedPuzzleIds(profileId).toSet())
            ?: arcade?.let { MatchPuzzles.build(it.ordinal % MatchPuzzles.COUNT) }
        _state.value = if (puzzle == null) MatchUiState(allDone = true)
        else MatchUiState(game = MatchGame(puzzle), startedAt = System.currentTimeMillis())
        if (puzzle != null) {
            analytics.openCategory(baseCtx)
            analytics.puzzleStarted(puzzle.id, baseCtx)
        }
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
            next.failed -> { sfx.play(Sfx.WRONG); analytics.attemptFailed() }
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
        if (game.solved) return
        sfx.play(Sfx.BUTTON_TAP)
        analytics.restarted()
        _state.update { it.copy(game = game.reset(), hint = null, noHint = false) }
    }

    fun hint() {
        val game = _state.value.game ?: return
        if (game.solved) return
        // If the player already strayed from every solution, restart from the beginning for the hint.
        val base = if (game.hintMove() == null) game.reset() else game
        val move = base.hintMove()
        sfx.play(Sfx.HINT)
        analytics.hintRequested()
        if (move != null) analytics.hintUsed()
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
            analytics.completed(result.xpAwarded)
            sfx.play(Sfx.CORRECT)
            if (result.xpAwarded > 0) sfx.play(Sfx.XP_GAIN)
            sfx.play(if (result.after.level > result.before.level) Sfx.LEVEL_UP else Sfx.LEVEL_COMPLETE)
        }
    }
}
