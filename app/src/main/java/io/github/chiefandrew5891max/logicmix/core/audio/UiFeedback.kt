package io.github.chiefandrew5891max.logicmix.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * The two standard UI sounds of Logic Mix (plus a light haptic tick). Every tappable card, button or
 * option on any screen calls [tap]; closing, cancelling or leaving a screen calls [back].
 */
interface UiFeedback {
    fun tap()
    fun back()
}

val LocalUiFeedback = compositionLocalOf<UiFeedback> {
    object : UiFeedback {
        override fun tap() = Unit
        override fun back() = Unit
    }
}

/**
 * Slider position (0..1) to amplitude. Loudness is perceived roughly logarithmically, so a linear
 * amplitude makes the lower half of the slider sound almost unchanged; squaring it gives 50% ≈ -12 dB
 * and 1% ≈ -80 dB (silent). Used for both music and effects.
 */
fun volumeGain(slider: Float): Float {
    val v = slider.coerceIn(0f, 1f)
    return v * v
}

/** Wraps a click handler so it first plays the standard tap sound (and haptic tick). */
@Composable
fun withTap(onClick: () -> Unit): () -> Unit {
    val feedback = LocalUiFeedback.current
    return { feedback.tap(); onClick() }
}

/** Same for closing, cancelling or leaving: plays the standard back sound. */
@Composable
fun withBack(onClick: () -> Unit): () -> Unit {
    val feedback = LocalUiFeedback.current
    return { feedback.back(); onClick() }
}
