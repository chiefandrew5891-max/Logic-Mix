package com.akynex.logicmix.feature.splash

import kotlin.math.PI
import kotlin.math.sin

/**
 * Tunable, steady-state version of the splash cube motion (no Android types, unit-testable). The waveforms and their
 * frequencies are the splash ones; only the amplitude is scaled. All looping pieces are periodic in [REFERENCE_CYCLE]
 * seconds of pulse time, so the loop has no visible seam.
 */
data class CubeMotionConfig(
    /** Multiplier of every pulse amplitude relative to the splash (1 = splash). */
    val pulseAmplitude: Float = 1f,
    /** Seconds for one seamless loop of the pulse; [CubeMotion.REFERENCE_CYCLE] keeps the splash frequencies. */
    val pulseCycleSeconds: Float = CubeMotion.REFERENCE_CYCLE,
    /** Seconds per full revolution of the light elements on the orbit (constant angular speed). */
    val orbitSecondsPerTurn: Float = 10f,
    /** Number of light elements travelling along the orbit. */
    val orbitLights: Int = 3,
) {
    companion object {
        /** Calmer permanent version for the home screen: amplitude exactly one third of the splash. */
        val Home = CubeMotionConfig(pulseAmplitude = 1f / 3f)
    }
}

object CubeMotion {
    /** 0.9, 1.6 and 0.8 Hz all complete whole cycles in 10 s. */
    const val REFERENCE_CYCLE = 10f

    /** Pulse time in [0, REFERENCE_CYCLE): clock seconds mapped through the configured cycle length. */
    fun pulseTime(clockSeconds: Double, cycleSeconds: Float): Float {
        val phase = (clockSeconds / cycleSeconds) % 1.0
        return (phase * REFERENCE_CYCLE).toFloat()
    }

    /** Orbit position in revolutions [0, 1): linear in time, constant speed, wraps without a jump. */
    fun orbitTurns(clockSeconds: Double, secondsPerTurn: Float): Float = ((clockSeconds / secondsPerTurn) % 1.0).toFloat()

    /** Segment breathing factor in [0, 1]; same wave as the splash with the amplitude ramp held at 1. */
    fun spread(segment: SplashMotion.Segment, t: Float): Float {
        if (segment == SplashMotion.Segment.Core) return 0f
        val local = t - segment.delay
        return (0.5 + 0.5 * sin(2.0 * PI * (local * 0.9f) + segment.phase - PI / 2)).toFloat()
    }

    /** Travel of a segment in fractions of the cube size. */
    fun travel(segment: SplashMotion.Segment, t: Float, amp: Float): Float = spread(segment, t) * SplashMotion.amplitudeFor(segment) * amp

    fun segmentScale(segment: SplashMotion.Segment, t: Float, amp: Float): Float = 1f + 0.03f * amp * spread(segment, t)

    fun coreScale(t: Float, amp: Float): Float = 1f + 0.035f * amp * (0.5f + 0.5f * sin(2.0 * PI * t * 1.6).toFloat())

    /** Core glow is an intensity, not a size, so it is not amplitude-scaled. */
    fun coreGlow(t: Float): Float = 0.5f + 0.5f * sin(2.0 * PI * t * 1.6 - PI / 2).toFloat()

    fun breathScale(t: Float, amp: Float): Float = 1f + 0.015f * amp * sin(2.0 * PI * t * 0.8).toFloat()
}
