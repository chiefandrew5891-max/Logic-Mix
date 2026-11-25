package com.akynex.logicmix

import com.akynex.logicmix.feature.splash.SplashMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashMotionTest {
    @Test fun segmentsStartAndEndAtRest() {
        assertEquals(0f, SplashMotion.amplitude(0f), 0f)
        assertEquals(0f, SplashMotion.amplitude(SplashMotion.DURATION), 1e-6f)
        assertEquals(0f, SplashMotion.orbitEnergy(SplashMotion.DURATION), 1e-6f)
        SplashMotion.Segment.values().forEach { assertEquals(0f, SplashMotion.spread(it, SplashMotion.DURATION), 1e-5f) }
    }

    @Test fun travelStaysWithinThreeToSevenPercent() {
        SplashMotion.Segment.values().filter { it != SplashMotion.Segment.Core }.forEach {
            val a = SplashMotion.amplitudeFor(it)
            assertTrue(a >= SplashMotion.MIN_AMPLITUDE && a <= SplashMotion.MAX_AMPLITUDE)
        }
    }

    @Test fun fadesInAndOut() {
        assertEquals(0f, SplashMotion.appear(0f), 0f)
        assertEquals(1f, SplashMotion.appear(0.5f), 0f)
        assertEquals(1f, SplashMotion.overlayAlpha(5.0f), 0f)
        assertEquals(0f, SplashMotion.overlayAlpha(SplashMotion.DURATION), 1e-6f)
    }

    @Test fun orbitAcceleratesSmoothlyToTopSpeed() {
        assertEquals(0f, SplashMotion.orbitTurns(0.4f), 0f)
        assertEquals(SplashMotion.ORBIT_START_SPEED, SplashMotion.orbitSpeed(0.5f), 1e-6f)
        assertEquals(SplashMotion.ORBIT_TOP_SPEED, SplashMotion.orbitSpeed(3.5f), 1e-6f)
        var prevTurns = 0f
        var prevSpeed = 0f
        var t = 0.5f
        while (t < 6f) {
            val turns = SplashMotion.orbitTurns(t)
            val v = SplashMotion.orbitSpeed(t)
            assertTrue(turns >= prevTurns && v >= prevSpeed)
            assertTrue(v <= SplashMotion.ORBIT_TOP_SPEED + 1e-4f)
            prevTurns = turns; prevSpeed = v; t += 0.01f
        }
    }

    @Test fun logoSizeFollowsTheCubePulse() {
        val t = 5.0f
        val logo = SplashMotion.logoPulse(t) - 1f
        val cube = SplashMotion.breathScale(t) - 1f
        assertTrue(logo * cube >= 0f)
        assertTrue(kotlin.math.abs(logo) <= 0.025f + 1e-6f)
        assertEquals(1f, SplashMotion.logoPulse(3.5f), 0f)
    }

    @Test fun orbitPointsLieOnScreenAndFrontIsLowerHalf() {
        for (i in 0 until 64) {
            val th = (i / 64f) * 6.2831855f
            val (x, y) = SplashMotion.orbitPoint(th)
            assertTrue(x in 0f..1024f && y in 0f..1024f)
        }
        val (_, yFront) = SplashMotion.orbitPoint(-1.5707964f)
        val (_, yBack) = SplashMotion.orbitPoint(1.5707964f)
        assertTrue(SplashMotion.isFront(-1.5f) && !SplashMotion.isFront(1.5f))
        assertTrue(yFront > yBack)
    }

    @Test fun logoRevealsFromCentreWithOvershoot() {
        assertEquals(0f, SplashMotion.logoAlpha(SplashMotion.logoProgress(0f)), 0f)
        assertEquals(-180f, SplashMotion.logoAngle(0f), 0f)
        assertEquals(0.07f, SplashMotion.logoScale(0f), 1e-6f)
        assertEquals(0f, SplashMotion.logoAngle(1f), 0f)
        assertEquals(1f, SplashMotion.logoScale(1f), 1e-6f)
        val peak = (0..100).maxOf { SplashMotion.logoScale(it / 100f) }
        assertTrue(peak in 1.05f..1.08f)
        assertTrue(SplashMotion.LOGO_START + SplashMotion.LOGO_DURATION + 0.5f <= SplashMotion.FADE_OUT_START)
    }
}
