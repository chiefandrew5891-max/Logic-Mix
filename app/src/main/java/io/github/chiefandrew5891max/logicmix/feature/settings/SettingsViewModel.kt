package io.github.chiefandrew5891max.logicmix.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chiefandrew5891max.logicmix.core.data.AudioSettings
import io.github.chiefandrew5891max.logicmix.core.data.SettingsRepository
import io.github.chiefandrew5891max.logicmix.core.designsystem.ThemeMode
import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val audio = repository.audio
    val themeMode = repository.themeMode
    val language = repository.language

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    fun setMusicEnabled(v: Boolean) = launch { repository.setMusicEnabled(v) }
    fun setEffectsEnabled(v: Boolean) = launch { repository.setEffectsEnabled(v) }
    fun setMusicVolume(v: Float) = launch { repository.setMusicVolume(v) }
    fun setEffectsVolume(v: Float) = launch { repository.setEffectsVolume(v) }
    fun setVibrationEnabled(v: Boolean) = launch { repository.setVibrationEnabled(v) }
    fun setThemeMode(m: ThemeMode) = launch { repository.setThemeMode(m) }
    fun setLanguage(l: AppLanguage) = launch { repository.setLanguage(l) }
}

internal val DefaultAudio = AudioSettings()
