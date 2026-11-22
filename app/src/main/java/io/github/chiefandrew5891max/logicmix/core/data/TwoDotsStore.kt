package io.github.chiefandrew5891max.logicmix.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chiefandrew5891max.logicmix.domain.twodots.TwoDotsMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.twoDotsDataStore by preferencesDataStore(name = "two_dots")

data class TwoDotsSaved(val mode: TwoDotsMode, val game: String?, val startedAt: Long, val best: Map<TwoDotsMode, Int>)

/** Per mode: the unfinished game and the all-time best score (which survives new games); plus the last chosen mode. */
@Singleton
class TwoDotsStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.twoDotsDataStore)

    suspend fun load(): TwoDotsSaved {
        val p = store.data.first()
        val mode = TwoDotsMode.entries.firstOrNull { it.name == p[MODE] } ?: TwoDotsMode.CLASSIC
        return TwoDotsSaved(
            mode, p[keyGame(mode)], p[keyStarted(mode)] ?: 0L,
            TwoDotsMode.entries.associateWith { (p[keyBest(it)] ?: 0).coerceAtLeast(0) },
        )
    }

    suspend fun loadGame(mode: TwoDotsMode): Pair<String?, Long> {
        val p = store.data.first()
        return p[keyGame(mode)] to (p[keyStarted(mode)] ?: 0L)
    }

    suspend fun setMode(mode: TwoDotsMode) {
        store.edit { it[MODE] = mode.name }
    }

    suspend fun save(mode: TwoDotsMode, game: String, startedAt: Long, best: Int) {
        store.edit {
            it[MODE] = mode.name
            it[keyGame(mode)] = game
            it[keyStarted(mode)] = startedAt
            it[keyBest(mode)] = best
        }
    }

    suspend fun clearGame(mode: TwoDotsMode) {
        store.edit {
            it.remove(keyGame(mode))
            it.remove(keyStarted(mode))
        }
    }

    private companion object {
        val MODE = stringPreferencesKey("mode")
        fun keyGame(mode: TwoDotsMode) = stringPreferencesKey("game_${mode.name}")
        fun keyStarted(mode: TwoDotsMode) = longPreferencesKey("started_${mode.name}")
        fun keyBest(mode: TwoDotsMode) = intPreferencesKey("best_${mode.name}")
    }
}
