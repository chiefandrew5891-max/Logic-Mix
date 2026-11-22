package com.akynex.logicmix.feature.twodots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.LocalProfileRepository
import com.akynex.logicmix.core.data.ProgressRepository
import com.akynex.logicmix.core.data.TwoDotsStore
import com.akynex.logicmix.domain.game.AttemptOutcome
import com.akynex.logicmix.domain.game.GameMode
import com.akynex.logicmix.domain.twodots.TwoDotsGame
import com.akynex.logicmix.domain.twodots.TwoDotsHint
import com.akynex.logicmix.domain.twodots.TwoDotsMode
import com.akynex.logicmix.domain.twodots.TwoDotsMove
import com.akynex.logicmix.domain.twodots.TwoDotsRules
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One animation step: the board to draw plus which dots vanish, fall or appear during [millis]. */
class TwoDotsStage(
    val cells: List<Int>,
    val millis: Int,
    val key: Int,
    val clearing: Set<Int> = emptySet(),
    val drop: List<Int>? = null,
    val fresh: Set<Int> = emptySet(),
    val wave: Boolean = false,
    val origin: Int = 0,
)

data class TwoDotsUiState(
    val game: TwoDotsGame? = null,
    val stage: TwoDotsStage? = null,
    val path: List<Int> = emptyList(),
    val best: Map<TwoDotsMode, Int> = emptyMap(),
    val hint: TwoDotsHint? = null,
    val noHint: Boolean = false,
    val reshuffled: Boolean = false,
    val gain: Int = 0,
    val gainKey: Int = 0,
    val xpAwarded: Int = 0,
    val arcade: Boolean = false,
) {
    val busy: Boolean get() = stage != null
    val shownCells: List<Int> get() = stage?.cells ?: game?.cells.orEmpty()
    val bestScore: Int get() = maxOf(best[game?.mode] ?: 0, game?.score ?: 0)
}

@HiltViewModel
class TwoDotsViewModel @Inject constructor(
    private val profiles: LocalProfileRepository,
    private val progress: ProgressRepository,
    private val store: TwoDotsStore,
    private val sfx: SoundEffectsController,
    monetization: com.akynex.logicmix.core.monetization.Monetization,
) : ViewModel() {
    private val analytics = monetization.newGameAnalytics()
    /** Non-null while hosted by Arcade: a fresh seeded CLASSIC game whose goal is TwoDotsRules.XP_SCORE; saved games stay untouched. */
    private val arcade = monetization.arcade.activeFor(com.akynex.logicmix.domain.content.GameCategory.TWO_DOTS)
    private fun ctx(mode: TwoDotsMode) = com.akynex.logicmix.core.analytics.GameContext(com.akynex.logicmix.domain.content.GameCategory.TWO_DOTS, family = "two_dots", mode = mode.name.lowercase())
    override fun onCleared() { analytics.leave() }

    private val profileId = LocalProfileRepository.LOCAL_PROFILE_ID
    private val _state = MutableStateFlow(TwoDotsUiState())
    val state: StateFlow<TwoDotsUiState> = _state.asStateFlow()
    private var startedAt = 0L
    private var stageKey = 0
    private var gainKey = 0
    private var claimedId: String? = null
    private var animate = true
    private var traceLength = 0

    init {
        viewModelScope.launch {
            val saved = store.load()
            val restored = if (arcade != null) null else TwoDotsGame.restore(saved.game)?.takeIf { !it.over && it.mode == saved.mode }
            val game = restored ?: if (arcade != null) TwoDotsGame.new(TwoDotsMode.CLASSIC, arcade.seed) else TwoDotsGame.new(saved.mode, System.currentTimeMillis())
            startedAt = if (restored != null && saved.startedAt > 0) saved.startedAt else System.currentTimeMillis()
            _state.value = TwoDotsUiState(game = game, best = saved.best, arcade = arcade != null)
            analytics.openCategory(ctx(game.mode))
            analytics.puzzleStarted(game.xpId, ctx(game.mode), resumed = restored != null, hints = game.hintsUsed)
            if (restored != null) claimXp(game)
        }
    }

    /** With system animations off every stage is skipped, so the logic never waits. */
    fun setAnimations(enabled: Boolean) {
        animate = enabled
    }

    private fun ms(value: Int) = if (animate) value else 0

    private fun persist(game: TwoDotsGame) {
        val best = _state.value.best.toMutableMap()
        best[game.mode] = maxOf(best[game.mode] ?: 0, game.score)
        _state.update { it.copy(best = best) }
        if (arcade != null) return
        val started = startedAt
        viewModelScope.launch {
            store.save(game.mode, game.encode(), started, best[game.mode] ?: 0)
            if (game.over) store.clearGame(game.mode)
        }
    }

    fun begin(cell: Int) {
        val s = _state.value
        val g = s.game ?: return
        if (s.busy || g.over || cell !in 0 until TwoDotsRules.CELLS) return
        traceLength = 1
        sfx.play(Sfx.LETTER_SELECT)
        _state.update { it.copy(path = listOf(cell), hint = null, noHint = false, reshuffled = false) }
    }

    fun drag(cell: Int) {
        val s = _state.value
        val g = s.game ?: return
        if (s.busy || s.path.isEmpty()) return
        val next = TwoDotsRules.extend(g.cells, s.path, cell)
        if (next == s.path) return
        // Rising notes only when the chain grows; stepping back stays silent and the next growth resumes from that step.
        if (next.size > traceLength) sfx.playTrace(next.size - 1)
        traceLength = next.size
        _state.update { it.copy(path = next) }
    }

    fun cancel() {
        traceLength = 0
        _state.update { it.copy(path = emptyList()) }
    }

    fun end() {
        val s = _state.value
        val g = s.game ?: return
        val path = s.path
        traceLength = 0
        if (s.busy) return
        val move = if (path.size >= 2) g.play(path) else null
        if (move == null) {
            _state.update { it.copy(path = emptyList()) }
            return
        }
        val first = TwoDotsStage(g.cells, ms(if (move.closed) 380 else 220), ++stageKey, clearing = move.removed, wave = move.closed, origin = path[0])
        _state.update { it.copy(path = emptyList(), stage = first, hint = null, noHint = false) }
        viewModelScope.launch { animate(move, first) }
    }

    private suspend fun show(stage: TwoDotsStage) {
        _state.update { it.copy(stage = stage) }
        if (stage.millis > 0) delay(stage.millis.toLong())
    }

    private suspend fun animate(move: TwoDotsMove, first: TwoDotsStage) {
        persist(move.game)
        sfx.play(Sfx.ORB_POP, if (move.closed) 1.12f else 1f)
        show(first)
        sfx.play(Sfx.ORB_SPAWN)
        show(TwoDotsStage(move.collapse.cells, ms(320), ++stageKey, drop = move.collapse.drop, fresh = move.collapse.fresh))
        val key = ++gainKey
        _state.update { it.copy(game = move.game, stage = null, gain = move.gained, gainKey = key, reshuffled = move.reshuffled) }
        claimXp(move.game)
        if (move.game.over) sfx.play(Sfx.GAME_OVER)
        delay(1000)
        _state.update { if (it.gainKey == key) it.copy(gain = 0) else it }
    }

    private fun claimXp(game: TwoDotsGame) {
        if (!game.xpReady || claimedId == game.xpId) return
        claimedId = game.xpId
        val started = startedAt
        viewModelScope.launch {
            profiles.ensureProfile()
            progress.ensureProgress(profileId)
            val result = progress.recordAttempt(
                profileId, game.xpId, GameMode.CAREER, AttemptOutcome.CORRECT, started, System.currentTimeMillis(), game.hintsUsed,
            )
            analytics.completed(result.xpAwarded)
            if (result.xpAwarded > 0) {
                _state.update { it.copy(xpAwarded = result.xpAwarded) }
                sfx.play(Sfx.XP_GAIN)
                if (result.after.level > result.before.level) sfx.play(Sfx.LEVEL_UP)
            }
        }
    }

    fun hint() {
        val s = _state.value
        val g = s.game ?: return
        if (s.busy || g.over) return
        val found = TwoDotsRules.findHint(g.cells)
        if (found == null) {
            sfx.play(Sfx.WRONG)
            _state.update { it.copy(noHint = true, hint = null) }
            return
        }
        sfx.play(Sfx.HINT)
        analytics.hintRequested(); analytics.hintUsed()
        val next = g.withHint()
        _state.update { it.copy(game = next, hint = found, noHint = false) }
        persist(next)
    }

    /** The confirmation dialog lives in the shared Restart button; this just starts over in the current mode. */
    fun restart() {
        val s = _state.value
        val g = s.game ?: return
        if (s.busy) return
        analytics.restarted()
        startFresh(g.mode)
    }

    fun setMode(mode: TwoDotsMode) {
        val s = _state.value
        val g = s.game ?: return
        if (s.busy || g.mode == mode || arcade != null) return
        sfx.play(Sfx.BUTTON_TAP)
        analytics.selectMode(ctx(mode))
        viewModelScope.launch {
            store.setMode(mode)
            val (raw, started) = store.loadGame(mode)
            val restored = TwoDotsGame.restore(raw)?.takeIf { !it.over && it.mode == mode }
            if (restored == null) {
                startFresh(mode)
            } else {
                startedAt = if (started > 0) started else System.currentTimeMillis()
                _state.update { it.copy(game = restored, path = emptyList(), hint = null, noHint = false, reshuffled = false, gain = 0, xpAwarded = 0) }
                claimXp(restored)
            }
        }
    }

    private fun startFresh(mode: TwoDotsMode) {
        startedAt = System.currentTimeMillis()
        val game = TwoDotsGame.new(mode, startedAt)
        analytics.puzzleStarted(game.xpId, ctx(mode))
        _state.update {
            TwoDotsUiState(game = game, best = it.best, arcade = arcade != null)
        }
        persist(game)
    }
}
