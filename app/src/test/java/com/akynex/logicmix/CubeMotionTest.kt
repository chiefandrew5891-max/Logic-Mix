package com.akynex.logicmix

import com.akynex.logicmix.feature.splash.CubeMotion
import com.akynex.logicmix.feature.splash.CubeMotionConfig
import com.akynex.logicmix.feature.splash.SplashMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeMotionTest {
    @Test fun homeAmplitudeIsExactlyOneThirdOfSplash() {
        val home = CubeMotionConfig.Home.pulseAmplitude
        for (seg in SplashMotion.Segment.entries) for (i in 0 until 40) {
            val t = i * 0.25f
            // Splash at full amplitude (ramp = 1) vs home: travel and scale deltas are 3x smaller.
            assertEquals(CubeMotion.travel(seg, t, 1f) / 3f, CubeMotion.travel(seg, t, home), 1e-6f)
            assertEquals((CubeMotion.segmentScale(seg, t, 1f) - 1f) / 3f, CubeMotion.segmentScale(seg, t, home) - 1f, 1e-6f)
        }
        assertEquals((CubeMotion.coreScale(0.3f, 1f) - 1f) / 3f, CubeMotion.coreScale(0.3f, home) - 1f, 1e-6f)
        assertEquals((CubeMotion.breathScale(0.3f, 1f) - 1f) / 3f, CubeMotion.breathScale(0.3f, home) - 1f, 1e-6f)
    }

    @Test fun pulseMatchesSplashWaveAtFullAmplitude() {
        for (seg in SplashMotion.Segment.entries) for (i in 0 until 15) {
            val t = 2f + i * 0.2f // after the splash amplitude ramp
            assertEquals(SplashMotion.spread(seg, t), CubeMotion.spread(seg, t), 1e-5f)
        }
    }

    @Test fun pulseLoopHasNoSeam() {
        for (seg in SplashMotion.Segment.entries) {
            assertEquals(CubeMotion.spread(seg, 0f), CubeMotion.spread(seg, CubeMotion.REFERENCE_CYCLE), 1e-4f)
        }
        assertEquals(CubeMotion.coreScale(0f, 1f), CubeMotion.coreScale(CubeMotion.REFERENCE_CYCLE, 1f), 1e-4f)
        assertEquals(CubeMotion.breathScale(0f, 1f), CubeMotion.breathScale(CubeMotion.REFERENCE_CYCLE, 1f), 1e-4f)
        val end = CubeMotion.pulseTime(9.999999, 10f)
        assertTrue(end > 9.99f)
        assertEquals(0f, CubeMotion.pulseTime(10.0, 10f), 1e-5f)
    }

    @Test fun orbitSpeedIsConstantAndWraps() {
        val dt = 0.05
        var prev = CubeMotion.orbitTurns(0.0, 10f)
        for (i in 1 until 400) {
            val cur = CubeMotion.orbitTurns(i * dt, 10f)
            val step = ((cur - prev) + 1f) % 1f
            assertEquals(dt.toFloat() / 10f, step, 1e-5f)
            prev = cur
        }
    }
}
