package com.akynex.logicmix.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.akynex.logicmix.core.audio.MusicController
import com.akynex.logicmix.core.data.ActiveSessionProvider
import com.akynex.logicmix.core.data.LocalProfileRepository
import com.akynex.logicmix.core.data.ProgressRepository
import com.akynex.logicmix.core.data.SettingsRepository
import com.akynex.logicmix.core.locale.effectiveLanguage
import com.akynex.logicmix.domain.content.CategoryAvailability
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MusicPanelState(val playing: Boolean = false, val available: Boolean = false)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val profiles: LocalProfileRepository,
    private val progress: ProgressRepository,
    sessions: ActiveSessionProvider,
    settings: SettingsRepository,
    private val musicController: MusicController,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        progress.observeProgress(LocalProfileRepository.LOCAL_PROFILE_ID),
        progress.observeStreak(LocalProfileRepository.LOCAL_PROFILE_ID),
        sessions.hasActiveSession,
        settings.effectiveLanguage().map { CategoryAvailability.available(it) },
        settings.gameStyle,
        HomeStateMapper::map,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Play/Pause mirrors the existing music switch; the buttons are inactive when no tracks are bundled. */
    val music: StateFlow<MusicPanelState> = settings.audio
        .map { MusicPanelState(playing = it.musicEnabled && musicController.hasTracks, available = musicController.hasTracks) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicPanelState())

    fun togglePlayback() = musicController.togglePlayback()
    fun nextTrack() = musicController.skipToNext()
    fun previousTrack() = musicController.skipToPrevious()

    init {
        viewModelScope.launch {
            profiles.ensureProfile()
            progress.ensureProgress(LocalProfileRepository.LOCAL_PROFILE_ID)
        }
    }
}
