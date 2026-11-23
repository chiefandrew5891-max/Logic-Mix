package com.akynex.logicmix.feature.arcade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akynex.logicmix.core.arcade.ArcadeAnalytics
import com.akynex.logicmix.core.arcade.ArcadeStore
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.SettingsRepository
import com.akynex.logicmix.core.locale.effectiveLanguage
import com.akynex.logicmix.core.monetization.Monetization
import com.akynex.logicmix.domain.arcade.ArcadeAchievement
import com.akynex.logicmix.domain.arcade.ArcadeEngine
import com.akynex.logicmix.domain.arcade.ArcadeRun
import com.akynex.logicmix.domain.arcade.ArcadeStage
import com.akynex.logicmix.domain.arcade.ArcadeStats
import com.akynex.logicmix.domain.arcade.StageOutcome
import com.akynex.logicmix.domain.arcade.StageReport
import com.akynex.logicmix.domain.arcade.StageResult
import com.akynex.logicmix.domain.content.CategoryAvailability
import com.akynex.logicmix.domain.content.GameCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Compact result of a resolved stage, shown by [ArcadeResultToast]. [xp] is the player XP the puzzle paid into the shared
 * ledger; [award] are Arcade run points. The big Level Up scene is not part of this: it belongs to the player level only.
 * [id] is unique per resolved stage so effects run once.
 */
data class Celebration(
    val id: String,
    val award: Int,
    val bonus: Int,
    val xp: Int,
    val streak: Int,
    val achievements: List<ArcadeAchievement>,
    val skipped: Boolean = false,
)

data class ArcadeUiState(
    val loading: Boolean = true,
    val selected: Set<GameCategory> = emptySet(),
    /** Selected categories that cannot be played in the current content language. */
    val unavailable: Set<GameCategory> = emptySet(),
    val saved: ArcadeRun? = null,
    val run: ArcadeRun? = null,
    val stats: ArcadeStats = ArcadeStats(),
    val celebration: Celebration? = null,
    val finished: Boolean = false,
) {
    val playable: Set<GameCategory> get() = selected - unavailable
    val playing: Boolean get() = run != null
}

@HiltViewModel
class ArcadeViewModel @Inject constructor(
    private val store: ArcadeStore,
    private val settings: SettingsRepository,
    private val monetization: Monetization,
    private val sfx: SoundEffectsController,
) : ViewModel() {
    private val bridge = monetization.arcade
    private val analytics = ArcadeAnalytics(monetization.tracker)
    private val _state = MutableStateFlow(ArcadeUiState())
    val state: StateFlow<ArcadeUiState> = _state.asStateFlow()
    private var language: com.akynex.logicmix.core.locale.AppLanguage? = null
    private var firstSelection = true
    private var soundPlayedFor: String? = null

    init {
        viewModelScope.launch {
            val saved = store.run()
            _state.update { it.copy(saved = saved, stats = store.stats(), loading = false) }
        }
        viewModelScope.launch {
            settings.gameStyle.collect { prefs ->
                if (!firstSelection && prefs.arcadeCategories != _state.value.selected) analytics.configurationChanged(prefs.arcadeCategories)
                firstSelection = false
                _state.update { it.copy(selected = prefs.arcadeCategories) }
                refreshAvailability()
            }
        }
        viewModelScope.launch {
            settings.effectiveLanguage().collect { lang ->
                language = lang
                refreshAvailability()
            }
        }
        bridge.onCompleted = ::onStageCompleted
        bridge.onExhausted = { stage -> viewModelScope.launch { skip(stage, "content_exhausted") } }
    }

    private fun refreshAvailability() {
        val lang = language ?: return
        val available = CategoryAvailability.available(lang)
        _state.update { it.copy(unavailable = it.selected.filterNot(available::contains).toSet()) }
        // A marathon in progress keeps its own set, minus whatever cannot be played in the current language.
        val run = _state.value.run
        if (run != null) {
            val narrowed = ArcadeEngine.restrictTo(run, available)
            if (narrowed == null) stopRun() else if (narrowed != run) publish(narrowed)
        }
    }

    /** Starts a new marathon from the saved selection. The previous unfinished marathon is replaced. */
    fun startNew() {
        val s = _state.value
        val categories = s.playable.toList()
        val run = ArcadeEngine.start(categories, System.currentTimeMillis()) ?: return
        analytics.start(run)
        _state.update { it.copy(stats = it.stats.copy(runsStarted = it.stats.runsStarted + 1), saved = null, finished = false) }
        publish(run)
    }

    fun resume() {
        val saved = _state.value.saved ?: return
        val lang = language
        val run = if (lang != null) ArcadeEngine.restrictTo(saved, CategoryAvailability.available(lang)) else saved
        if (run == null) return
        analytics.resume(run)
        _state.update { it.copy(saved = null, finished = false) }
        publish(run)
    }

    private fun publish(run: ArcadeRun, celebration: Celebration? = _state.value.celebration) {
        // Resolved stages are only waiting for their celebration to end, so the next one opens straight away on restore.
        val ready = if (run.outcome != StageOutcome.NONE && celebration == null) ArcadeEngine.advance(run) else run
        bridge.stage = if (ready.outcome == StageOutcome.NONE) ready.current else null
        _state.update { it.copy(run = ready, celebration = celebration) }
        persist(ready)
    }

    private fun persist(run: ArcadeRun?) {
        val stats = _state.value.stats
        viewModelScope.launch { store.save(run, stats) }
    }

    private fun onStageCompleted(stage: ArcadeStage, report: StageReport) {
        // May arrive off the main thread; the lock plus the engine's outcome check make a repeated callback a no-op.
        synchronized(this) {
            val run = _state.value.run ?: return
            if (run.current != stage || run.outcome != StageOutcome.NONE) return
            val result = ArcadeEngine.complete(run, _state.value.stats, report)
            if (result.accepted) applyResult(stage, report, result)
        }
    }

    private fun applyResult(stage: ArcadeStage, report: StageReport, result: StageResult) {
        val award = result.award ?: return
        analytics.stageComplete(result.run, stage, report, award)
        if (result.run.streak == 3 || result.run.streak == 5 || result.run.streak % 10 == 0) analytics.streak(result.run)
        if (result.levelUp) analytics.levelUp(result.run)
        val celebration = Celebration(
            id = stage.id, award = award.total, bonus = award.bonus, xp = report.xp,
            streak = result.run.streak, achievements = result.achievements,
        )
        bridge.stage = null
        _state.update { it.copy(run = result.run, stats = result.stats, celebration = celebration) }
        persist(result.run)
    }

    /** Player gives the stage up: no points, streak ends, and the marathon moves on. */
    fun skip() {
        val run = _state.value.run ?: return
        viewModelScope.launch { skip(run.current, "skip") }
    }

    private fun skip(stage: ArcadeStage, reason: String) {
        val run = _state.value.run ?: return
        if (run.current != stage || run.outcome != StageOutcome.NONE) return
        analytics.stageFail(run, stage, reason)
        val skipped = ArcadeEngine.skip(run)
        bridge.stage = null
        _state.update { it.copy(run = skipped, celebration = Celebration("skip-${stage.id}", 0, 0, 0, 0, emptyList(), skipped = true)) }
        persist(skipped)
    }

    /** Only short accents for streaks and achievements, once per result (rotation-safe); no fanfare here. */
    suspend fun playCelebrationSounds(c: Celebration) {
        if (c.skipped) return
        synchronized(this) {
            if (soundPlayedFor == c.id) return
            soundPlayedFor = c.id
        }
        when {
            c.streak >= 3 -> sfx.play(Sfx.STREAK)
            c.bonus > 0 -> sfx.play(Sfx.BONUS_POINTS)
        }
        if (c.achievements.isNotEmpty()) {
            delay(600)
            sfx.play(Sfx.ACHIEVEMENT)
        }
    }

    /** Called when the celebration ends (timeout or tap); safe to call twice. */
    fun celebrationDone(id: String) {
        val s = _state.value
        val run = s.run ?: return
        if (s.celebration?.id != id) return
        val next = ArcadeEngine.advance(run)
        bridge.stage = next.current.takeIf { next.outcome == StageOutcome.NONE }
        _state.update { it.copy(run = next, celebration = null) }
        persist(next)
        sfx.play(Sfx.SCREEN_TRANSITION)
    }

    /** Leaves the marathon with progress saved, so Resume continues from the same stage. */
    fun pause() {
        val run = _state.value.run ?: return
        analytics.exit(run)
        val resolved = if (run.outcome != StageOutcome.NONE) ArcadeEngine.advance(run) else run
        bridge.stage = null
        _state.update { it.copy(saved = resolved, run = null, celebration = null) }
        persist(resolved)
    }

    private fun stopRun() {
        bridge.stage = null
        _state.update { it.copy(run = null, celebration = null, saved = null, finished = true) }
        persist(null)
    }

    override fun onCleared() {
        bridge.stage = null
        bridge.onCompleted = null
        bridge.onExhausted = null
    }
}
