package io.github.chiefandrew5891max.logicmix.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.chiefandrew5891max.logicmix.core.designsystem.ThemeMode
import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsDataStore
    val soundEnabled: Flow<Boolean> = store.data.map { preferences ->
        preferences[SOUND_ENABLED] ?: true
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        store.edit { preferences -> preferences[SOUND_ENABLED] = enabled }
    }

    val themeMode: Flow<ThemeMode> = store.data.map { ThemeMode.fromStored(it[THEME_MODE]) }
    val language: Flow<AppLanguage> = store.data.map { AppLanguage.fromStored(it[LANGUAGE]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.storedValue }
    }

    suspend fun setLanguage(language: AppLanguage) {
        store.edit { it[LANGUAGE] = language.storedValue }
    }

    private companion object {
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
    }
}
