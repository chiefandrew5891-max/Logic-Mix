package com.akynex.logicmix.feature.splash

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Pure timeline math of the startup splash (no Android types), so it can be unit-tested. All times are seconds. */
object SplashMotion {
    /** 0-3.5 s energy build-up, 3.5-4.5 s logo reveal, 4.5-6 s final composition and fade. */
    const val CUBE_END = 6.0f
    const val DURATION = 6.0f
    const val REDUCED_DURATION = 1.2f
    const val FADE_IN_END = 0.5f
    const val RAMP_END = 1.5f
    const val HOLD_END = 5.5f
    const val FADE_OUT_START = 5.5f

    /** Segment travel as a fraction of the cube size (3-7%). */
    const val MIN_AMPLITUDE = 0.03f
    const val MAX_AMPLITUDE = 0.07f

    /** Orbit ellipse in the 1024 px layer space, fitted to the artwork. */
    const val ORBIT_CX = 502f
    const val ORBIT_CY = 511f
    const val ORBIT_A = 432f
    const val ORBIT_B = 187f
    const val ORBIT_ANGLE_DEG = 151.7f

    enum class Segment(val dx: Float, val dy: Float, val delay: Float, val phase: Float) {
        Core(0f, 0f, 0f, 0f),
        Top(0f, -1f, 0.00f, 0.0f),
        Left(-0.9f, 0.4f, 0.12f, 1.7f),
        Right(0.9f, 0.4f, 0.24f, 3.4f),
        Bottom(0f, 1f, 0.36f, 5.1f),
    }

    const val LOGO_START = 3.5f
    const val LOGO_DURATION = 1.0f // reveal; then the composition is held until FADE_OUT_START and the overlay fades
    const val LOGO_START_SCALE = 0.07f
    const val LOGO_START_ANGLE = -180f

    fun logoProgress(t: Float): Float = ((t - LOGO_START) / LOGO_DURATION).coerceIn(0f, 1f)

    /** Grows from 7% through a ~106% overshoot back to 100%. */
    fun logoScale(p: Float): Float {
        val u = p - 1f
        val back = 1f + 2.4f * u * u * u + 1.4f * u * u
        return LOGO_START_SCALE + (1f - LOGO_START_SCALE) * back
    }

    /** Turns from -180 degrees to exactly 0; only the logo rotates. */
    fun logoAngle(p: Float): Float { val u = 1f - p; return LOGO_START_ANGLE * u * u * u }

    fun logoAlpha(p: Float): Float = smooth(p / 0.2f)

    fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    /** Overall opacity of the artwork: fades in over 0.5 s. */
    fun appear(t: Float): Float = smooth(t / FADE_IN_END)

    /** Opacity of the whole overlay: 1 until [FADE_OUT_START], then fades to 0 at [DURATION]. */
    fun overlayAlpha(t: Float, total: Float = DURATION): Float =
        1f - smooth((t - (total - (DURATION - FADE_OUT_START))) / (DURATION - FADE_OUT_START))

    /** 0 -> 1 during 0.5-1.5 s, hold until 5.5 s, back to 0 at 6.0 s. */
    fun amplitude(t: Float): Float = when {
        t < FADE_IN_END -> 0f
        t < RAMP_END -> smooth((t - FADE_IN_END) / (RAMP_END - FADE_IN_END))
        t < HOLD_END -> 1f
        else -> 1f - smooth((t - HOLD_END) / (CUBE_END - HOLD_END))
    }

    /** Signed travel factor in [0, 1] of a segment (breathes apart and back, never past its own amplitude). */
    fun spread(segment: Segment, t: Float): Float {
        if (segment == Segment.Core) return 0f
        val local = t - segment.delay
        val wave = 0.5f + 0.5f * sin(2.0 * PI * (local * 0.9f) + segment.phase - PI / 2).toFloat()
        return amplitude(t) * wave
    }

    /** Travel in fractions of the cube size for a segment; magnitude between MIN and MAX at full spread. */
    fun amplitudeFor(segment: Segment): Float = when (segment) {
        Segment.Top, Segment.Bottom -> 0.07f
        Segment.Left, Segment.Right -> 0.06f
        Segment.Core -> 0f
    }

    /** Size pulse of a segment about the cube centre (no rotation); the per-segment delay keeps them out of phase. */
    fun segmentScale(segment: Segment, t: Float): Float = 1f + 0.03f * spread(segment, t)

    fun coreScale(t: Float): Float = 1f + 0.035f * amplitude(t) * (0.5f + 0.5f * sin(2.0 * PI * t * 1.6).toFloat())
    fun coreGlow(t: Float): Float = amplitude(t) * (0.5f + 0.5f * sin(2.0 * PI * t * 1.6 - PI / 2).toFloat())
    /** The single shared pulse in [-1, 1] driving the cube breathing and the logo size, so the two are always in sync. */
    fun pulse(t: Float): Float = sin(2.0 * PI * t * 0.8).toFloat()
    fun breathScale(t: Float): Float = 1f + 0.015f * amplitude(t) * pulse(t)

    /** Logo size factor (+-2.5%) from the shared [pulse]; blended in once the logo has settled at 4.5 s. */
    fun logoPulse(t: Float): Float =
        1f + 0.025f * amplitude(t) * smooth((t - (LOGO_START + LOGO_DURATION)) / 0.3f) * pulse(t)

    const val ORBIT_START = 0.5f
    const val ORBIT_FULL = 3.5f
    const val ORBIT_START_SPEED = 0.2f // revolutions per second at ORBIT_START
    const val ORBIT_TOP_SPEED = 3.0f // revolutions per second from ORBIT_FULL on

    /** Energy-pulse strength: ramps in from 0.5 s, off again with the final fade. */
    fun orbitEnergy(t: Float): Float = smooth((t - ORBIT_START) / 0.4f) * (1f - smooth((t - FADE_OUT_START) / (DURATION - FADE_OUT_START)))

    /** Revolutions per second: eases quadratically from the start speed to the top speed and never decreases. */
    fun orbitSpeed(t: Float): Float {
        val s = ((t - ORBIT_START) / (ORBIT_FULL - ORBIT_START)).coerceIn(0f, 1f)
        return ORBIT_START_SPEED + (ORBIT_TOP_SPEED - ORBIT_START_SPEED) * s * s
    }

    /** Revolutions completed at time t (the integral of [orbitSpeed]). */
    fun orbitTurns(t: Float): Float {
        val d = ORBIT_FULL - ORBIT_START
        val tau = (t - ORBIT_START).coerceAtLeast(0f)
        val inRamp = tau.coerceAtMost(d)
        val ramp = ORBIT_START_SPEED * inRamp + (ORBIT_TOP_SPEED - ORBIT_START_SPEED) * inRamp * inRamp * inRamp / (3f * d * d)
        return ramp + ORBIT_TOP_SPEED * (tau - inRamp)
    }

    /** Position on the orbit ellipse for angle [theta] (radians). Returns x, y in layer space. */
    fun orbitPoint(theta: Float): Pair<Float, Float> {
        val phi = Math.toRadians(ORBIT_ANGLE_DEG.toDouble())
        val ex = ORBIT_A * cos(theta.toDouble())
        val ey = ORBIT_B * sin(theta.toDouble())
        val x = ORBIT_CX + ex * cos(phi) - ey * sin(phi)
        val y = ORBIT_CY + ex * sin(phi) + ey * cos(phi)
        return x.toFloat() to y.toFloat()
    }

    /** The near half of the ellipse is drawn over the cube: it is on the lower side of the major axis. */
    fun isFront(theta: Float): Boolean = sin(theta.toDouble()) < 0.0

    /** Deterministic pseudo-random value in [0, 1) from an index and a salt. */
    fun hash01(i: Int, salt: Int): Float {
        var h = i * 374761393 + salt * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777216f
    }
}
