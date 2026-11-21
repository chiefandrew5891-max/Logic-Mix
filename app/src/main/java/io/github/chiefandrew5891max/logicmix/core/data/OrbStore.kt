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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.orbDataStore by preferencesDataStore(name = "orb_lines")

data class OrbSaved(val game: String?, val seed: Long, val startedAt: Long, val best: Int)

/** The unfinished Orb Lines game plus the all-time best score (which survives new games). */
@Singleton
class OrbStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.orbDataStore)

    suspend fun load(): OrbSaved {
        val p = store.data.first()
        return OrbSaved(p[GAME], p[SEED] ?: 0L, p[STARTED] ?: 0L, (p[BEST] ?: 0).coerceAtLeast(0))
    }

    suspend fun save(game: String, seed: Long, startedAt: Long, best: Int) {
        store.edit {
            it[GAME] = game
            it[SEED] = seed
            it[STARTED] = startedAt
            it[BEST] = best
        }
    }

    suspend fun clearGame() {
        store.edit {
            it.remove(GAME)
            it.remove(SEED)
            it.remove(STARTED)
        }
    }

    private companion object {
        val GAME = stringPreferencesKey("game")
        val SEED = longPreferencesKey("seed")
        val STARTED = longPreferencesKey("started")
        val BEST = intPreferencesKey("best")
    }
}
