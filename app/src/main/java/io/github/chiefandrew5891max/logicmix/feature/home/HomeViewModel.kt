package io.github.chiefandrew5891max.logicmix.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chiefandrew5891max.logicmix.core.data.ActiveSessionProvider
import io.github.chiefandrew5891max.logicmix.core.data.LocalProfileRepository
import io.github.chiefandrew5891max.logicmix.core.data.ProgressRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val profiles: LocalProfileRepository,
    private val progress: ProgressRepository,
    sessions: ActiveSessionProvider,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        progress.observeProgress(LocalProfileRepository.LOCAL_PROFILE_ID),
        progress.observeStreak(LocalProfileRepository.LOCAL_PROFILE_ID),
        sessions.hasActiveSession,
        HomeStateMapper::map,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            profiles.ensureProfile()
            progress.ensureProgress(LocalProfileRepository.LOCAL_PROFILE_ID)
        }
    }
}
