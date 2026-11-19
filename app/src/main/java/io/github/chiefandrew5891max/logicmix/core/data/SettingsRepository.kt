package io.github.chiefandrew5891max.logicmix.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
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

data class AudioSettings(
    val musicEnabled: Boolean = true,
    val effectsEnabled: Boolean = true,
    val musicVolume: Float = DEFAULT_VOLUME,
    val effectsVolume: Float = DEFAULT_VOLUME,
    val vibrationEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_VOLUME = 0.8f
    }
}

class SettingsRepository internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.settingsDataStore)

    // Legacy sound_enabled is only read as a fallback; users keep their old choice.
    val audio: Flow<AudioSettings> = store.data.map { p ->
        val legacy = p[SOUND_ENABLED] ?: true
        AudioSettings(
            musicEnabled = p[MUSIC_ENABLED] ?: legacy,
            effectsEnabled = p[EFFECTS_ENABLED] ?: legacy,
            musicVolume = (p[MUSIC_VOLUME] ?: AudioSettings.DEFAULT_VOLUME).coerceIn(0f, 1f),
            effectsVolume = (p[EFFECTS_VOLUME] ?: AudioSettings.DEFAULT_VOLUME).coerceIn(0f, 1f),
            vibrationEnabled = p[VIBRATION_ENABLED] ?: true,
        )
    }

    suspend fun setMusicEnabled(enabled: Boolean) { store.edit { it[MUSIC_ENABLED] = enabled } }
    suspend fun setEffectsEnabled(enabled: Boolean) { store.edit { it[EFFECTS_ENABLED] = enabled } }
    suspend fun setMusicVolume(volume: Float) { store.edit { it[MUSIC_VOLUME] = volume.coerceIn(0f, 1f) } }
    suspend fun setEffectsVolume(volume: Float) { store.edit { it[EFFECTS_VOLUME] = volume.coerceIn(0f, 1f) } }
    suspend fun setVibrationEnabled(enabled: Boolean) { store.edit { it[VIBRATION_ENABLED] = enabled } }

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
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val EFFECTS_ENABLED = booleanPreferencesKey("effects_enabled")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val EFFECTS_VOLUME = floatPreferencesKey("effects_volume")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
    }
}
