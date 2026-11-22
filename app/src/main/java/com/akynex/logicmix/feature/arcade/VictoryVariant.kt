package com.akynex.logicmix.feature.arcade

import com.akynex.logicmix.core.audio.Sfx
import kotlin.random.Random

/** Normalised (0..1) rectangle or point inside the artwork, so overlays scale together with the image. */
data class Frac(val x: Float, val y: Float)
data class FracRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * One victory scene: a PNG (res/drawable-nodpi/victory_fanfare_<key>.png) and the WAV of the same colour
 * (assets/sfx/victory_fanfare_<key>.wav, played by SoundEffectsController) always travel as a pair.
 * Coordinates are measured on the supplied artwork: the two horn mouths and the empty ribbon interior.
 */
enum class VictoryVariant(
    val key: String,
    val sfx: Sfx,
    val aspect: Float,
    val leftHorn: Frac,
    val rightHorn: Frac,
    val ribbon: FracRect,
) {
    RED("red", Sfx.VICTORY_FANFARE_RED, 1754f / 896f, Frac(0.19f, 0.36f), Frac(0.81f, 0.36f), FracRect(0.27f, 0.50f, 0.73f, 0.70f)),
    PURPLE("purple", Sfx.VICTORY_FANFARE_PURPLE, 1752f / 897f, Frac(0.185f, 0.37f), Frac(0.816f, 0.37f), FracRect(0.27f, 0.51f, 0.73f, 0.71f)),
    BLUE("blue", Sfx.VICTORY_FANFARE_BLUE, 1755f / 896f, Frac(0.19f, 0.37f), Frac(0.815f, 0.37f), FracRect(0.27f, 0.52f, 0.73f, 0.71f)),
    ;

    val drawableName get() = "victory_fanfare_$key"
}

object VictoryPicker {
    /** Random variant that never equals [last] (so the same scene does not appear twice in a row). */
    fun next(last: VictoryVariant?, random: Random = Random.Default): VictoryVariant {
        val pool = VictoryVariant.entries.filter { it != last }
        return pool[random.nextInt(pool.size)]
    }
}
