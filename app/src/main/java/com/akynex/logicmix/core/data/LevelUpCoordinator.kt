package com.akynex.logicmix.core.data

import android.content.Context
import com.akynex.logicmix.core.analytics.AnalyticsEvent
import com.akynex.logicmix.core.analytics.Ev
import com.akynex.logicmix.core.analytics.P
import com.akynex.logicmix.domain.progression.CelebratedLevelStorage
import com.akynex.logicmix.domain.progression.LevelUpGate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharedPrefsCelebratedLevel(context: Context) : CelebratedLevelStorage {
    private val prefs = context.getSharedPreferences("player_level_up", Context.MODE_PRIVATE)
    override var celebratedLevel: Int?
        get() = if (prefs.contains(KEY)) prefs.getInt(KEY, 1) else null
        set(value) { prefs.edit().apply { if (value == null) remove(KEY) else putInt(KEY, value) }.commit() }

    private companion object { const val KEY = "celebrated_level" }
}

/** Level the player just reached; [id] is unique per level so a scene runs once. */
data class LevelUpEvent(val level: Int, val totalXp: Int) { val id: String get() = "level-up-$level" }

/**
 * App-wide source of Player Level Up events. It watches the single XP total (every category and Arcade write to it), so no
 * screen decides on its own whether to show the fanfare. The analytics event player_level_up is sent at the same moment.
 */
@Singleton
class LevelUpCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val progress: ProgressRepository,
    private val monetization: com.akynex.logicmix.core.monetization.Monetization,
) {
    private val gate = LevelUpGate(SharedPrefsCelebratedLevel(context))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _pending = MutableStateFlow<LevelUpEvent?>(null)
    val pending: StateFlow<LevelUpEvent?> = _pending.asStateFlow()
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            progress.observeProgress(LocalProfileRepository.LOCAL_PROFILE_ID).collect { p ->
                val level = gate.onLevel(p.level) ?: return@collect
                monetization.tracker.log(AnalyticsEvent(Ev.PLAYER_LEVEL_UP, mapOf(P.PLAYER_LEVEL to level.toLong(), P.TOTAL_XP to p.totalXp.toLong())))
                _pending.value = LevelUpEvent(level, p.totalXp)
            }
        }
    }

    fun dismiss(id: String) {
        _pending.compareAndSet(_pending.value?.takeIf { it.id == id }, null)
    }
}
