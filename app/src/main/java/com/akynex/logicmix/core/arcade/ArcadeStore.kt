package com.akynex.logicmix.core.arcade

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akynex.logicmix.domain.arcade.ArcadeCodec
import com.akynex.logicmix.domain.arcade.ArcadeRun
import com.akynex.logicmix.domain.arcade.ArcadeStats
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.arcadeDataStore by preferencesDataStore(name = "arcade")

/** Current marathon and lifetime statistics, kept in their own DataStore so Room, Career and the other modes are never touched. */
@Singleton
class ArcadeStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.arcadeDataStore)

    suspend fun run(): ArcadeRun? = ArcadeCodec.decode(store.data.first()[RUN])

    suspend fun stats(): ArcadeStats = ArcadeCodec.decodeStats(store.data.first()[STATS])

    suspend fun save(run: ArcadeRun?, stats: ArcadeStats) {
        store.edit {
            if (run == null) it.remove(RUN) else it[RUN] = ArcadeCodec.encode(run)
            it[STATS] = ArcadeCodec.encode(stats)
        }
    }

    private companion object {
        val RUN = stringPreferencesKey("run")
        val STATS = stringPreferencesKey("stats")
    }
}
