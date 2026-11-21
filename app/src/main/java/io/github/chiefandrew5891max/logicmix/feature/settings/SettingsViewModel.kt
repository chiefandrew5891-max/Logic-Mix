package io.github.chiefandrew5891max.logicmix.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chiefandrew5891max.logicmix.core.audio.Sfx
import io.github.chiefandrew5891max.logicmix.core.audio.SoundEffectsController
import io.github.chiefandrew5891max.logicmix.core.data.AudioSettings
import io.github.chiefandrew5891max.logicmix.core.data.SettingsRepository
import io.github.chiefandrew5891max.logicmix.core.designsystem.ThemeMode
import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage
import io.github.chiefandrew5891max.logicmix.domain.content.GameStyle
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val sfx: SoundEffectsController,
) : ViewModel() {
    val audio = repository.audio
    val themeMode = repository.themeMode
    val language = repository.language
    val gameStyle = repository.gameStyle

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    fun setMusicEnabled(v: Boolean) = launch { sfx.play(Sfx.TOGGLE); repository.setMusicEnabled(v) }
    // The click is heard both when switching effects off (forced) and on (after the setting is saved).
    fun setEffectsEnabled(v: Boolean) = launch {
        if (!v) sfx.play(Sfx.TOGGLE, force = true)
        repository.setEffectsEnabled(v)
        if (v) sfx.play(Sfx.TOGGLE)
    }
    fun setMusicVolume(v: Float) = launch { repository.setMusicVolume(v) }
    fun setEffectsVolume(v: Float) = launch { repository.setEffectsVolume(v) }
    fun setVibrationEnabled(v: Boolean) = launch {
        if (!v) sfx.play(Sfx.TOGGLE)
        repository.setVibrationEnabled(v)
        if (v) sfx.play(Sfx.TOGGLE)
    }
    fun setThemeMode(m: ThemeMode) = launch { sfx.play(Sfx.BUTTON_TAP); repository.setThemeMode(m) }
    fun setLanguage(l: AppLanguage) = launch { sfx.play(Sfx.BUTTON_TAP); repository.setLanguage(l) }
    fun selectArcade() = launch { repository.confirmGameStyle(GameStyle.ARCADE) }
}

internal val DefaultAudio = AudioSettings()
