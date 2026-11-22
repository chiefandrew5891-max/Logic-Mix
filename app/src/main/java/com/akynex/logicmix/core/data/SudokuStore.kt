package com.akynex.logicmix.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import com.akynex.logicmix.domain.sudoku.SudokuDifficulty
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.sudokuDataStore by preferencesDataStore(name = "sudoku_session")

data class SudokuSaved(val difficulty: SudokuDifficulty, val seed: Long, val game: String, val elapsedSec: Long)

/** The single unfinished Sudoku; cleared on completion. XP stays in Room via ProgressRepository. */
@Singleton
class SudokuStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.sudokuDataStore)

    suspend fun active(): SudokuSaved? {
        val p = store.data.first()
        val seed = p[SEED] ?: return null
        val game = p[GAME] ?: return null
        return SudokuSaved(SudokuDifficulty.fromStored(p[DIFFICULTY]), seed, game, (p[ELAPSED] ?: 0L).coerceAtLeast(0))
    }

    suspend fun save(difficulty: SudokuDifficulty, seed: Long, game: String, elapsedSec: Long) {
        store.edit {
            it[DIFFICULTY] = difficulty.name
            it[SEED] = seed
            it[GAME] = game
            it[ELAPSED] = elapsedSec
        }
    }

    suspend fun clear() {
        store.edit {
            it.remove(SEED)
            it.remove(GAME)
            it.remove(ELAPSED)
        }
    }

    private companion object {
        val DIFFICULTY = stringPreferencesKey("difficulty")
        val SEED = longPreferencesKey("seed")
        val GAME = stringPreferencesKey("game")
        val ELAPSED = longPreferencesKey("elapsed")
    }
}
