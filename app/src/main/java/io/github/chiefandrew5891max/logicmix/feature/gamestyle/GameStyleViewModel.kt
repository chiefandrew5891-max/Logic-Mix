package io.github.chiefandrew5891max.logicmix.feature.gamestyle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chiefandrew5891max.logicmix.core.data.SettingsRepository
import io.github.chiefandrew5891max.logicmix.core.locale.effectiveLanguage
import io.github.chiefandrew5891max.logicmix.domain.content.CategoryAvailability
import io.github.chiefandrew5891max.logicmix.domain.content.GameCategory
import io.github.chiefandrew5891max.logicmix.domain.content.GameStyle
import io.github.chiefandrew5891max.logicmix.domain.content.GameStylePrefs
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GameStyleViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    /** Null until DataStore has delivered the first value, so the flow starts from the real saved choices. */
    val prefs: StateFlow<GameStylePrefs?> =
        settings.gameStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Categories offered for the active language (Fillwords is absent for zh-CN), in display order. */
    val categories: StateFlow<List<GameCategory>> =
        settings.effectiveLanguage().map { CategoryAvailability.ordered(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameCategory.entries.toList())

    fun confirm(style: GameStyle, trainer: Set<GameCategory>? = null, preferred: GameCategory? = null) {
        viewModelScope.launch { settings.confirmGameStyle(style, trainer, preferred) }
    }
}
