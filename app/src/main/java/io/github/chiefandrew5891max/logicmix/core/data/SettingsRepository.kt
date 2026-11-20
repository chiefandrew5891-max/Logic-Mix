package io.github.chiefandrew5891max.logicmix.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.chiefandrew5891max.logicmix.core.designsystem.ThemeMode
import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage
import io.github.chiefandrew5891max.logicmix.domain.content.GameCategory
import io.github.chiefandrew5891max.logicmix.domain.content.GameStyle
import io.github.chiefandrew5891max.logicmix.domain.content.GameStylePrefs
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

    // Absent keys (existing installs) read as "onboarding not done", so the selector is shown once.
    val gameStyle: Flow<GameStylePrefs> = store.data.map { p ->
        GameStylePrefs(
            onboardingDone = p[ONBOARDING_DONE] ?: false,
            style = GameStyle.fromStored(p[GAME_STYLE]),
            trainerCategories = p[TRAINER_CATEGORIES].orEmpty().mapNotNull(::category).toSet(),
            preferredCategory = category(p[PREFERRED_CATEGORY]),
        )
    }

    /** Saves a confirmed style together with its selection in one atomic edit. */
    suspend fun confirmGameStyle(style: GameStyle, trainer: Set<GameCategory>? = null, preferred: GameCategory? = null) {
        store.edit {
            it[ONBOARDING_DONE] = true
            it[GAME_STYLE] = style.name
            if (trainer != null) it[TRAINER_CATEGORIES] = trainer.map(GameCategory::name).toSet()
            if (preferred != null) it[PREFERRED_CATEGORY] = preferred.name
        }
    }

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("game_style_onboarding_done")
        val GAME_STYLE = stringPreferencesKey("game_style")
        val TRAINER_CATEGORIES = stringSetPreferencesKey("trainer_categories")
        val PREFERRED_CATEGORY = stringPreferencesKey("preferred_category")
        fun category(name: String?): GameCategory? = GameCategory.entries.firstOrNull { it.name == name }

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
