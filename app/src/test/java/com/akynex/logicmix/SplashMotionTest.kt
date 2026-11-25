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
        assertEquals(1f, SplashMotion.overlayAlpha(3.4f), 0f)
        assertEquals(0f, SplashMotion.overlayAlpha(SplashMotion.DURATION), 1e-6f)
    }

    @Test fun orbitAcceleratesMonotonically() {
        var prev = -1f
        var prevStep = 0f
        var t = 1.5f
        while (t < 3.3f) {
            val turns = SplashMotion.orbitTurns(t)
            assertTrue(turns >= prev)
            if (t < 3.2f) { val step = turns - prev; if (prev >= 0f && t > 1.55f) assertTrue(step >= prevStep - 1e-4f); prevStep = step }
            prev = turns; t += 0.01f
        }
        assertEquals(0f, SplashMotion.orbitTurns(1f), 0f)
        // Calmer than before: never faster than 1.1 rev/s.
        assertTrue((SplashMotion.orbitTurns(3.3f) - SplashMotion.orbitTurns(3.2f)) / 0.1f <= 1.11f)
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
}
