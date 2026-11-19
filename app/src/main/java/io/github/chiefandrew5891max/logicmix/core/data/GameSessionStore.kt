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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.gameSessionDataStore by preferencesDataStore(name = "game_session")

data class StoredSession(val puzzleId: String, val hintsUsed: Int, val startedAt: Long)

/** Persists the unfinished puzzle and its used hints, so a restart cannot reset the no-hint bonus. */
@Singleton
class GameSessionStore internal constructor(private val store: DataStore<Preferences>) : ActiveSessionProvider {
    @Inject constructor(@ApplicationContext context: Context) : this(context.gameSessionDataStore)

    val session: Flow<StoredSession?> = store.data.map { p ->
        p[PUZZLE_ID]?.let { StoredSession(it, (p[HINTS] ?: 0).coerceAtLeast(0), p[STARTED_AT] ?: 0L) }
    }

    override val hasActiveSession: Flow<Boolean> = session.map { it != null }

    /** Reopening the same puzzle keeps its hints; any other puzzle starts fresh. */
    suspend fun begin(puzzleId: String, now: Long): StoredSession {
        store.edit {
            if (it[PUZZLE_ID] != puzzleId) {
                it[PUZZLE_ID] = puzzleId
                it[HINTS] = 0
                it[STARTED_AT] = now
            }
        }
        return session.first()!!
    }

    suspend fun setHintsUsed(puzzleId: String, hintsUsed: Int) {
        store.edit { if (it[PUZZLE_ID] == puzzleId) it[HINTS] = hintsUsed.coerceAtLeast(0) }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val PUZZLE_ID = stringPreferencesKey("puzzle_id")
        val HINTS = intPreferencesKey("hints_used")
        val STARTED_AT = longPreferencesKey("started_at")
    }
}
