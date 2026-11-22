package com.akynex.logicmix.feature.orb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.LocalProfileRepository
import com.akynex.logicmix.core.data.OrbStore
import com.akynex.logicmix.core.data.ProgressRepository
import com.akynex.logicmix.domain.arcade.ArcadeGoals
import com.akynex.logicmix.domain.game.AttemptOutcome
import com.akynex.logicmix.domain.game.GameMode
import com.akynex.logicmix.domain.orb.OrbGame
import com.akynex.logicmix.domain.orb.OrbMove
import com.akynex.logicmix.domain.orb.OrbRules
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MovingBall(val color: Int, val path: List<Int>)

/** One animation frame group: the board to draw plus which cells move, vanish or appear during [millis]. */
class OrbStage(
    val cells: List<Int>,
    val millis: Int,
    val key: Int,
    val moving: MovingBall? = null,
    val clearing: Set<Int> = emptySet(),
    val spawning: Set<Int> = emptySet(),
)

data class OrbUiState(
    val game: OrbGame? = null,
    val stage: OrbStage? = null,
    val selected: Int? = null,
    val best: Int = 0,
    val hint: Pair<Int, Int>? = null,
    val noHint: Boolean = false,
    val xpAwarded: Int = 0,
    /** Arcade only: removals needed to finish the stage; null in normal play, where the game stays endless. */
    val goal: Int? = null,
) {
    val busy: Boolean get() = stage != null
    val shownCells: List<Int> get() = stage?.cells ?: game?.cells.orEmpty()
}

@HiltViewModel
class OrbViewModel @Inject constructor(
    private val profiles: LocalProfileRepository,
    private val progress: ProgressRepository,
    private val store: OrbStore,
    private val sfx: SoundEffectsController,
    monetization: com.akynex.logicmix.core.monetization.Monetization,
) : ViewModel() {
    private val analytics = monetization.newGameAnalytics()
    /** Non-null while hosted by Arcade: a fresh seeded game whose goal is OrbRules.XP_SCORE; the saved Career game stays untouched. */
    private val arcade = monetization.arcade.activeFor(com.akynex.logicmix.domain.content.GameCategory.ORB_LINES)
    private val goal: Int? = arcade?.let(ArcadeGoals::orbLines)
    private val ctx = com.akynex.logicmix.core.analytics.GameContext(com.akynex.logicmix.domain.content.GameCategory.ORB_LINES, family = "orb_lines", mode = "endless")
    override fun onCleared() { analytics.leave() }

    private val profileId = LocalProfileRepository.LOCAL_PROFILE_ID
    private val _state = MutableStateFlow(OrbUiState())
    val state: StateFlow<OrbUiState> = _state.asStateFlow()
    private var seed = 0L
    private var startedAt = 0L
    private var stageKey = 0
    private var xpClaimed = false

    init {
        viewModelScope.launch {
            val saved = store.load()
            val restored = if (arcade != null) null else saved.game?.let { OrbGame.restore(it) }?.takeIf { !it.over }
            if (restored != null) {
                seed = saved.seed
                startedAt = saved.startedAt
            } else {
                seed = arcade?.seed ?: System.currentTimeMillis()
                startedAt = System.currentTimeMillis()
            }
            _state.value = OrbUiState(game = restored ?: OrbGame.new(seed), best = saved.best, goal = goal)
            analytics.openCategory(ctx)
            analytics.puzzleStarted("orb-$seed", ctx, resumed = restored != null, hints = restored?.hintsUsed ?: 0)
            if (restored != null) claimXp(restored)
        }
    }

    private fun persist(game: OrbGame) {
        val best = maxOf(_state.value.best, game.score)
        _state.update { it.copy(best = best) }
        if (arcade != null) return
        viewModelScope.launch {
            store.save(game.encode(), seed, startedAt, best)
            if (game.over) store.clearGame()
        }
    }

    fun tap(cell: Int) {
        val s = _state.value
        val g = s.game ?: return
        if (s.busy || g.over || cell !in g.cells.indices) return
        if (g.cells[cell] != 0) {
            if (s.selected == cell) {
                _state.update { it.copy(selected = null) }
            } else {
                sfx.play(Sfx.ORB_SELECT)
                _state.update { it.copy(selected = cell, hint = null, noHint = false) }
            }
            return
        }
        val from = s.selected ?: return
        val move = g.move(from, cell)
        if (move == null) {
            sfx.play(Sfx.WRONG)
            return
        }
        viewModelScope.launch { animate(g, from, cell, move) }
    }

    private suspend fun show(stage: OrbStage) {
        _state.update { it.copy(stage = stage) }
        delay(stage.millis.toLong())
    }

    private suspend fun animate(g: OrbGame, from: Int, to: Int, move: OrbMove) {
        val color = g.cells[from]
        sfx.play(Sfx.ORB_MOVE)
        _state.update { it.copy(selected = null, hint = null, noHint = false) }
        val start = g.cells.toMutableList().also { it[from] = 0 }
        show(OrbStage(start, (move.path.size * 38).coerceIn(140, 420), ++stageKey, moving = MovingBall(color, move.path)))
        val moved = start.toMutableList().also { it[to] = color }
        if (move.clearedByMove.isNotEmpty()) {
            comboSound(move.clearedByMove.size)
            show(OrbStage(moved, 320, ++stageKey, clearing = move.clearedByMove))
        } else {
            val withSpawn = moved.toMutableList()
            move.spawned.forEachIndexed { i, cell -> withSpawn[cell] = g.next[i] }
            if (move.spawned.isNotEmpty()) sfx.play(Sfx.ORB_SPAWN)
            show(OrbStage(withSpawn, 240, ++stageKey, spawning = move.spawned.toSet()))
            if (move.clearedBySpawn.isNotEmpty()) {
                comboSound(move.clearedBySpawn.size)
                show(OrbStage(withSpawn, 320, ++stageKey, clearing = move.clearedBySpawn))
            }
        }
        _state.update { it.copy(game = move.game, stage = null) }
        persist(move.game)
        claimXp(move.game)
        if (move.game.over) sfx.play(Sfx.GAME_OVER)
    }

    private fun comboSound(removed: Int) = sfx.play(Sfx.ORB_POP)

    private fun solved(game: OrbGame): Boolean = if (goal != null) game.lines >= goal else game.score >= OrbRules.XP_SCORE

    private fun claimXp(game: OrbGame) {
        if (xpClaimed || !solved(game)) return
        xpClaimed = true
        viewModelScope.launch {
            profiles.ensureProfile()
            progress.ensureProgress(profileId)
            val result = progress.recordAttempt(
                profileId, "orb-$seed", GameMode.CAREER, AttemptOutcome.CORRECT,
                startedAt, System.currentTimeMillis(), game.hintsUsed,
            )
            _state.update { it.copy(xpAwarded = result.xpAwarded) }
            analytics.completed(result.xpAwarded)
            if (result.xpAwarded > 0) {
                sfx.play(Sfx.XP_GAIN)
                if (result.after.level > result.before.level) sfx.play(Sfx.LEVEL_UP)
            }
        }
    }

    fun undo() {
        val s = _state.value
        val g = s.game ?: return
        if (s.busy || !g.canUndo) return
        sfx.play(Sfx.BUTTON_TAP)
        val back = g.undo()
        _state.update { it.copy(game = back, selected = null, hint = null, noHint = false) }
        persist(back)
    }

    fun hint() {
        val s = _state.value
        val g = s.game ?: return
        if (s.busy || g.over) return
        val move = g.hintMove()
        if (move == null) {
            _state.update { it.copy(noHint = true, hint = null) }
            return
        }
        sfx.play(Sfx.HINT)
        analytics.hintRequested(); analytics.hintUsed()
        val next = g.withHint()
        _state.update { it.copy(game = next, hint = move, selected = move.first, noHint = false) }
        persist(next)
    }

    fun newGame() {
        if (_state.value.busy) return
        sfx.play(Sfx.BUTTON_TAP)
        analytics.restarted()
        seed = System.currentTimeMillis()
        startedAt = seed
        xpClaimed = false
        val g = OrbGame.new(seed)
        analytics.puzzleStarted("orb-$seed", ctx)
        _state.update { OrbUiState(game = g, best = it.best, goal = goal) }
        persist(g)
    }
}
