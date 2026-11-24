package com.akynex.logicmix.feature.levelup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.LevelUpCoordinator
import com.akynex.logicmix.core.data.LevelUpEvent
import com.akynex.logicmix.feature.arcade.VictoryPicker
import com.akynex.logicmix.feature.arcade.VictoryVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class LevelUpViewModel @Inject constructor(
    private val coordinator: LevelUpCoordinator,
    private val sfx: SoundEffectsController,
    private val review: com.akynex.logicmix.core.review.ReviewCoordinator,
) : ViewModel() {
    val pending: StateFlow<LevelUpEvent?> = coordinator.pending
    private var lastVariant: VictoryVariant? = null
    private var soundFor: String? = null

    init { coordinator.start() }

    /** One of the three scenes, picked at random once per level; the picture and its WAV always travel together. */
    fun variantFor(event: LevelUpEvent): VictoryVariant = VictoryPicker.next(lastVariant).also { lastVariant = it }

    /** Starts the fanfare with the animation, once per level (rotation-safe). Muted sounds are handled by the controller. */
    fun playFanfare(event: LevelUpEvent, variant: VictoryVariant) {
        synchronized(this) {
            if (soundFor == event.id) return
            soundFor = event.id
        }
        sfx.play(variant.sfx)
    }

    fun done(event: LevelUpEvent) {
        VictoryVariant.entries.forEach { sfx.stop(it.sfx) }
        coordinator.dismiss(event.id)
        review.onLevelUpClosed()
    }
}

/** Shown above all navigation, so every category and Arcade share the same Player Level Up scene. */
@Composable
fun LevelUpHost(viewModel: LevelUpViewModel = hiltViewModel()) {
    val event by viewModel.pending.collectAsState()
    val current = event ?: return
    val variant = remember(current.id) { viewModel.variantFor(current) }
    LevelUpScene(
        event = current,
        variant = variant,
        onShown = { viewModel.playFanfare(current, variant) },
        onDone = { viewModel.done(current) },
    )
}
