package io.github.chiefandrew5891max.logicmix.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chiefandrew5891max.logicmix.domain.game.AttemptOutcome
import io.github.chiefandrew5891max.logicmix.domain.game.GameMode
import io.github.chiefandrew5891max.logicmix.domain.progression.AttemptResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.categoryDataStore by preferencesDataStore(name = "category_games")

/**
 * Per-category level pointer and the serialized state of the unfinished level, for the fixed-level
 * categories (word logic, mini crosswords, visual logic). Each category uses its own key prefix, so
 * clearing one never touches another category or other games' stores.
 */
@Singleton
class CategoryStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.categoryDataStore)

    suspend fun levelIndex(category: String): Int = (store.data.first()[intPreferencesKey("$category.level")] ?: 0).coerceAtLeast(0)

    suspend fun setLevelIndex(category: String, index: Int) {
        store.edit { it[intPreferencesKey("$category.level")] = index.coerceAtLeast(0) }
    }

    suspend fun state(category: String, puzzleId: String): String? = store.data.first()[stateKey(category, puzzleId)]

    suspend fun saveState(category: String, puzzleId: String, raw: String) {
        store.edit { it[stateKey(category, puzzleId)] = raw }
    }

    suspend fun clearState(category: String, puzzleId: String) {
        store.edit { it.remove(stateKey(category, puzzleId)) }
    }

    private fun stateKey(category: String, puzzleId: String) = stringPreferencesKey("$category.state.$puzzleId")
}

/** Single path from a solved level to the shared XP system; repeats are rejected by ProgressRepository. */
@Singleton
class CategoryGames @Inject constructor(
    val store: CategoryStore,
    private val profiles: LocalProfileRepository,
    private val progress: ProgressRepository,
) {
    private val profileId = LocalProfileRepository.LOCAL_PROFILE_ID

    suspend fun solvedIds(): Set<String> {
        profiles.ensureProfile()
        progress.ensureProgress(profileId)
        return progress.solvedPuzzleIds(profileId).toSet()
    }

    suspend fun complete(puzzleId: String, startedAt: Long, hintsUsed: Int): AttemptResult {
        profiles.ensureProfile()
        progress.ensureProgress(profileId)
        return progress.recordAttempt(profileId, puzzleId, GameMode.CAREER, AttemptOutcome.CORRECT, startedAt, System.currentTimeMillis(), hintsUsed)
    }
}
