package io.github.chiefandrew5891max.logicmix.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage
import io.github.chiefandrew5891max.logicmix.domain.wordsearch.WordSearchLevel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.wordSearchDataStore by preferencesDataStore(name = "wordsearch_progress")

data class WordSearchSession(
    val language: ContentLanguage,
    val levelId: String,
    val index: Int,
    val seed: Long,
    val mainFound: Set<String>,
    val extraFound: Set<String>,
    val hintsUsed: Int,
    val xpAwarded: Int,
    val completed: Boolean,
    val startedAt: Long,
)

/**
 * Word-search progress, kept separately for every content language: keys are prefixed with the
 * language tag, so English progress never touches Italian progress. Global XP stays in Room.
 */
@Singleton
class WordSearchStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.wordSearchDataStore)

    suspend fun currentIndex(language: ContentLanguage): Int =
        (store.data.first()[intPreferencesKey("ws.${language.tag}.current")] ?: 0).coerceAtLeast(0)

    suspend fun setCurrentIndex(language: ContentLanguage, index: Int) {
        store.edit { it[intPreferencesKey("ws.${language.tag}.current")] = index }
    }

    suspend fun session(language: ContentLanguage, index: Int): WordSearchSession? {
        val p = store.data.first()
        val id = p[stringPreferencesKey(key(language, index, "id"))] ?: return null
        return WordSearchSession(
            language = language,
            levelId = id,
            index = index,
            seed = p[longPreferencesKey(key(language, index, "seed"))] ?: 0L,
            mainFound = p[stringSetPreferencesKey(key(language, index, "main"))].orEmpty(),
            extraFound = p[stringSetPreferencesKey(key(language, index, "extra"))].orEmpty(),
            hintsUsed = (p[intPreferencesKey(key(language, index, "hints"))] ?: 0).coerceAtLeast(0),
            xpAwarded = p[intPreferencesKey(key(language, index, "xp"))] ?: 0,
            completed = p[booleanPreferencesKey(key(language, index, "done"))] ?: false,
            startedAt = p[longPreferencesKey(key(language, index, "started"))] ?: 0L,
        )
    }

    /** Opens the level; an existing session (found words, hints) is kept untouched. */
    suspend fun begin(level: WordSearchLevel, now: Long): WordSearchSession {
        store.edit {
            val idKey = stringPreferencesKey(key(level.language, level.index, "id"))
            if (it[idKey] == null) {
                it[idKey] = level.id
                it[longPreferencesKey(key(level.language, level.index, "seed"))] = level.seed
                it[longPreferencesKey(key(level.language, level.index, "started"))] = now
            }
        }
        return session(level.language, level.index)!!
    }

    suspend fun addWord(level: WordSearchLevel, word: String, main: Boolean) {
        store.edit {
            val k = stringSetPreferencesKey(key(level.language, level.index, if (main) "main" else "extra"))
            it[k] = it[k].orEmpty() + word
        }
    }

    /** Clears the found words of an unfinished level. Hints used, start time and awarded XP are kept, so a restart cannot re-earn rewards. */
    suspend fun resetProgress(level: WordSearchLevel) {
        store.edit {
            it.remove(stringSetPreferencesKey(key(level.language, level.index, "main")))
            it.remove(stringSetPreferencesKey(key(level.language, level.index, "extra")))
        }
    }

    suspend fun setHintsUsed(level: WordSearchLevel, hints: Int) {
        store.edit { it[intPreferencesKey(key(level.language, level.index, "hints"))] = hints.coerceAtLeast(0) }
    }

    suspend fun complete(level: WordSearchLevel, xpAwarded: Int) {
        store.edit {
            it[booleanPreferencesKey(key(level.language, level.index, "done"))] = true
            it[intPreferencesKey(key(level.language, level.index, "xp"))] = xpAwarded
        }
    }

    private fun key(language: ContentLanguage, index: Int, field: String) = "ws.${language.tag}.$index.$field"
}
