package com.akynex.logicmix.feature.visual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.CategoryGames
import com.akynex.logicmix.domain.visual.VisualGame
import com.akynex.logicmix.domain.visual.geo.GeoGame
import com.akynex.logicmix.domain.visual.geo.VisualLevel
import com.akynex.logicmix.domain.visual.place.Cell
import com.akynex.logicmix.domain.visual.place.Interactive
import com.akynex.logicmix.domain.visual.place.NetGame
import com.akynex.logicmix.domain.visual.place.PlaceGame
import com.akynex.logicmix.domain.visual.place.Placement
import com.akynex.logicmix.domain.visual.place.SlideGame
import com.akynex.logicmix.domain.visual.place.VisualMix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val total: Int = VisualMix.total,
    val index: Int = 0,
    val game: VisualGame? = null,
    val geo: GeoGame? = null,
    val place: PlaceGame? = null,
    val slide: SlideGame? = null,
    val net: NetGame? = null,
    val family: com.akynex.logicmix.domain.visual.place.InteractiveFamily? = null,
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
        viewModelScope.launch {
            // Players who progressed before the interactive puzzles existed continue after what they already solved.
            var index = games.store.levelIndex(KEY)
            if (index == 0) {
                val solved = games.solvedIds()
                while (index < VisualMix.total && VisualMix.levelId(index) in solved) index++
                if (index > 0) games.store.setLevelIndex(KEY, index)
            }
            open(index)
        }
    }

    private suspend fun open(careerIndex: Int) {
        val total = VisualMix.total
        val index = if (arcade != null && careerIndex >= total) arcade.ordinal % total else careerIndex
        val level = withContext(Dispatchers.Default) { VisualMix.levelAt(index) }
        if (level == null) {
            _state.value = VisualUiState(loading = false, index = total, allDone = true)
            return
        }
        startedAt = System.currentTimeMillis()
        val saved = games.store.state(KEY, level.id) ?: games.store.state(LEGACY_KEY, level.id)
        analytics.openCategory(baseCtx)
        val (family, difficulty) = when (level) {
            is VisualMix.Level.Choice -> when (val c = level.level) {
                is VisualLevel.Classic -> "classic" to null
                is VisualLevel.Generated -> c.puzzle.family.name.lowercase() to c.puzzle.difficulty.toString()
            }
            is VisualMix.Level.Hands -> level.puzzle.family.name.lowercase() to level.puzzle.difficulty.toString()
        }
        analytics.puzzleStarted(level.id, baseCtx.copy(family = family, difficulty = difficulty, levelIndex = index + 1), resumed = saved != null)
        _state.value = when (level) {
            is VisualMix.Level.Choice -> when (val c = level.level) {
                is VisualLevel.Classic -> VisualUiState(loading = false, index = index, game = VisualGame.restore(c.puzzle, saved))
                is VisualLevel.Generated -> VisualUiState(loading = false, index = index, geo = GeoGame.restore(c.puzzle, saved))
            }
            is VisualMix.Level.Hands -> when (val h = level.puzzle) {
                is Interactive.Place -> VisualUiState(loading = false, index = index, family = h.family, place = PlaceGame.restore(h.puzzle, saved))
                is Interactive.Slide -> VisualUiState(loading = false, index = index, family = h.family, slide = SlideGame.restore(h.puzzle, saved))
                is Interactive.Net -> VisualUiState(loading = false, index = index, family = h.family, net = NetGame.restore(h.puzzle, saved))
            }
        }
    }

    private fun save(id: String, raw: String) { viewModelScope.launch { games.store.saveState(KEY, id, raw) } }

    /** Returns true when the piece stayed on the board; false sends it back to the tray. */
    fun placePiece(pieceId: Int, p: Placement): Boolean {
        val g = _state.value.place ?: return false
        val next = g.place(pieceId, p)
        if (next == null) { sfx.play(Sfx.WRONG); return false } // trying positions costs nothing
        _state.update { it.copy(place = next) }
        sfx.play(Sfx.PUZZLE_PLACE)
        if (next.solved) finish(next.puzzle.id, next.hintsUsed) else save(next.puzzle.id, next.encode())
        return true
    }

    fun liftPiece(pieceId: Int) = placeAction { it.remove(pieceId) }

    fun undoPlace() = placeAction { it.undo() }

    fun resetPlace() = placeAction { it.reset() }

    private fun placeAction(f: (PlaceGame) -> PlaceGame?) {
        val g = _state.value.place ?: return
        val next = f(g) ?: return
        sfx.play(Sfx.PUZZLE_MOVE)
        _state.update { it.copy(place = next) }
        save(next.puzzle.id, next.encode())
    }

    fun slideMove(blockId: Int, dx: Int, dy: Int): Boolean {
        val g = _state.value.slide ?: return false
        val next = g.move(blockId, dx, dy) ?: return false
        _state.update { it.copy(slide = next) }
        sfx.play(Sfx.PUZZLE_MOVE)
        if (next.solved) finish(next.puzzle.id, next.hintsUsed) else save(next.puzzle.id, next.encode())
        return true
    }

    fun slideUndo() = slideAction { it.undo() }

    fun slideReset() = slideAction { it.reset() }

    private fun slideAction(f: (SlideGame) -> SlideGame?) {
        val g = _state.value.slide ?: return
        val next = f(g) ?: return
        sfx.play(Sfx.PUZZLE_MOVE)
        _state.update { it.copy(slide = next) }
        save(next.puzzle.id, next.encode())
    }

    fun netTap(cell: Cell) {
        val g = _state.value.net ?: return
        val next = g.tap(cell)
        if (next == null) {
            if (g.solved) return
            sfx.play(Sfx.WRONG); analytics.attemptFailed()
            val failed = g.failedTap()
            _state.update { it.copy(net = failed) }
            save(failed.puzzle.id, failed.encode())
            return
        }
        _state.update { it.copy(net = next) }
        sfx.play(Sfx.PUZZLE_PLACE)
        finish(next.puzzle.id, next.hintsUsed)
    }

    private fun handsHint() {
        val s = _state.value
        s.place?.let { g -> g.hint()?.let { n -> hinted(); _state.update { it.copy(place = n) }; if (n.solved) finish(n.puzzle.id, n.hintsUsed) else save(n.puzzle.id, n.encode()) } }
        s.slide?.let { g -> g.hint()?.let { n -> hinted(); _state.update { it.copy(slide = n) }; if (n.solved) finish(n.puzzle.id, n.hintsUsed) else save(n.puzzle.id, n.encode()) } }
        s.net?.let { g ->
            // Reveals a correct square by playing it.
            val answer = g.validCells().firstOrNull() ?: return
            g.tap(answer)?.let { n -> val h = n.copy(hintsUsed = g.hintsUsed + 1); hinted(); _state.update { it.copy(net = h) }; finish(h.puzzle.id, h.hintsUsed) }
        }
    }

    private fun hinted() { sfx.play(Sfx.HINT); analytics.hintRequested(); analytics.hintUsed() }

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
        if (_state.value.family != null) return handsHint()
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
        _state.value.place?.let { return resetPlace() }
        _state.value.slide?.let { return slideReset() }
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
            val result = games.complete(puzzleId, startedAt, hintsUsed, GameCategory.VISUAL_LOGIC)
            // Same effect as the Next button, so the following Arcade stage gets a new puzzle.
            val served = _state.value.index
            if (arcade != null && games.store.levelIndex(KEY) == served) games.store.setLevelIndex(KEY, served + 1)
            _state.update { it.copy(xpAwarded = result.xpAwarded) }
            analytics.completed(result.xpAwarded)
            sfx.play(Sfx.CORRECT)
            if (result.xpAwarded > 0) sfx.play(Sfx.XP_GAIN)
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

    private companion object {
        const val KEY = "visual_logic_v2"
        const val LEGACY_KEY = "visual_logic"
    }
}
