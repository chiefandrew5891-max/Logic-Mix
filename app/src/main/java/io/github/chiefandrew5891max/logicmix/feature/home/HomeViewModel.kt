package io.github.chiefandrew5891max.logicmix.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chiefandrew5891max.logicmix.core.data.LocalProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: LocalProfileRepository) : ViewModel() {
    val profile = repository.profile

    init {
        viewModelScope.launch { repository.ensureProfile() }
    }
}
