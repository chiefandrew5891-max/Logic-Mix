package com.akynex.logicmix.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.AudioSettings
import com.akynex.logicmix.core.data.SettingsRepository
import com.akynex.logicmix.core.designsystem.ThemeMode
import com.akynex.logicmix.core.locale.AppLanguage
import com.akynex.logicmix.domain.content.GameStyle
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val sfx: SoundEffectsController,
    private val monetization: com.akynex.logicmix.core.monetization.Monetization,
    private val review: com.akynex.logicmix.core.review.ReviewCoordinator,
) : ViewModel() {
    val consent = monetization.consentState
    fun setAnalyticsConsent(v: Boolean) = monetization.consent.setAnalytics(v)
    fun privacyOptionsRequired(a: android.app.Activity) = monetization.privacyOptionsRequired(a)
    fun showPrivacyOptions(a: android.app.Activity) = monetization.showPrivacyOptions(a)
    fun rateApp(context: android.content.Context) = review.rateAppClicked(context, com.akynex.logicmix.BuildConfig.APPLICATION_ID)
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
