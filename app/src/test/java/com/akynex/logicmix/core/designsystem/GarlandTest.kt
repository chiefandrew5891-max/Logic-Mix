package com.akynex.logicmix.core.designsystem

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GarlandTest {
    private fun run(e: GarlandEngine, seconds: Float, playing: Boolean, animated: Boolean = true, onFrame: () -> Unit = {}) {
        repeat((seconds * 60).toInt()) { e.advance(1 / 60f, playing, animated); onFrame() }
    }

    @Test fun schedulerNeverRepeatsAndCoversModes() {
        val s = GarlandScheduler(Random(1))
        var last = s.nextMode()
        var beforeLast: GarlandMode? = null
        val seen = mutableSetOf(last)
        repeat(500) {
            val m = s.nextMode()
            assertNotEquals(last, m)
            assertNotEquals(beforeLast, m)
            seen += m
            beforeLast = last
            last = m
        }
        assertEquals(7, seen.size)
    }

    @Test fun schedulerIsDeterministicAndOrderIsNotFixed() {
        val a = GarlandScheduler(Random(7)); val b = GarlandScheduler(Random(7))
        val seqA = List(20) { a.nextMode() }
        assertEquals(seqA, List(20) { b.nextMode() })
        assertNotEquals(GarlandMode.entries.toList(), seqA.take(7))
    }

    @Test fun durationInRange() {
        val s = GarlandScheduler(Random(3))
        repeat(200) { assertTrue(s.nextDurationSeconds() in 4f..9f) }
    }

    @Test fun coordinatesNormalizedAndScaled() {
        GarlandGeometry.bulbs.forEach { assertTrue(it.x in 0f..1f && it.y in 0f..1f) }
        assertEquals(8, GarlandGeometry.bulbs.size)
        val px = GarlandGeometry.toPixels(1000f, 500f)
        val big = GarlandGeometry.toPixels(2000f, 1000f)
        px.indices.forEach {
            assertEquals(px[it].first * 2, big[it].first, 1e-3f)
            assertEquals(px[it].second * 2, big[it].second, 1e-3f)
        }
        assertTrue(GarlandGeometry.bulbs.zipWithNext().all { (a, b) -> a.x < b.x })
    }

    @Test fun levelsStayInRangeAndAreSmooth() {
        val e = GarlandEngine(Random(5))
        var prev = e.levels.copyOf()
        var maxJump = 0f
        run(e, 120f, true) {
            e.levels.forEachIndexed { i, v ->
                assertTrue(v in 0f..1f)
                maxJump = maxOf(maxJump, kotlin.math.abs(v - prev[i]))
            }
            prev = e.levels.copyOf()
        }
        assertTrue("jump $maxJump", maxJump < 0.12f)
    }

    @Test fun allModesProduceLightAtSomePoint() {
        val e = GarlandEngine(Random(11))
        val modes = mutableSetOf<GarlandMode>()
        run(e, 200f, true) { modes += e.mode }
        assertEquals(7, modes.size)
    }

    @Test fun pauseSettlesToRestAndPlayResumes() {
        val e = GarlandEngine(Random(2))
        run(e, 10f, true)
        run(e, 3f, false)
        e.levels.forEach { assertEquals(GarlandEngine.REST, it, 1e-3f) }
        run(e, 0.3f, true)
        assertTrue(e.live > 0f && e.live < 1f)
        run(e, 3f, true)
        assertEquals(1f, e.live, 1e-4f)
    }

    @Test fun noAnimationWhenSystemAnimationsOff() {
        val e = GarlandEngine(Random(2))
        run(e, 10f, true, animated = false) { e.levels.forEach { assertEquals(GarlandEngine.REST, it, 1e-6f) } }
    }

    @Test fun kickLiftsLightsBrieflyThenEnds() {
        val e = GarlandEngine(Random(4))
        run(e, 0.5f, false)
        e.kick(true)
        var peak = 0f
        run(e, 0.3f, false) { peak = maxOf(peak, e.levels.max()) }
        assertTrue(peak > GarlandEngine.REST + 0.2f)
        run(e, 0.5f, false)
        e.levels.forEach { assertEquals(GarlandEngine.REST, it, 1e-3f) }
    }

    @Test fun bulbLookIsZeroWhenOffAndFullWhenOn() {
        val off = BulbLook.of(0f)
        listOf(off.glowAlpha, off.haloAlpha, off.bodyAlpha, off.coreAlpha).forEach { assertEquals(0f, it, 0f) }
        val on = BulbLook.of(1f)
        listOf(on.glowAlpha, on.haloAlpha, on.bodyAlpha, on.coreAlpha).forEach { assertTrue(it > 0.25f && it <= 1f) }
        assertEquals(BulbLook.of(1f), BulbLook.of(7f))
        assertEquals(BulbLook.of(0f), BulbLook.of(-3f))
    }

    @Test fun coreAndHaloFadeTogether() {
        var prev = BulbLook.of(0f)
        for (i in 1..100) {
            val cur = BulbLook.of(i / 100f)
            assertTrue(cur.coreAlpha >= prev.coreAlpha && cur.haloAlpha >= prev.haloAlpha)
            assertTrue(cur.glowAlpha >= prev.glowAlpha && cur.bodyAlpha >= prev.bodyAlpha)
            assertTrue(cur.glowRadius >= prev.glowRadius && cur.coreRadius >= prev.coreRadius)
            prev = cur
        }
        // A dim bulb never keeps a strong halo around a dead core.
        val dim = BulbLook.of(0.15f)
        assertTrue(dim.haloAlpha < 0.2f && dim.coreAlpha < 0.2f)
    }

    @Test fun modesReallySwitchBulbsOnAndOff() {
        val e = GarlandEngine(Random(21))
        val lowest = FloatArray(8) { 1f }
        val highest = FloatArray(8)
        run(e, 200f, true) { e.levels.forEachIndexed { i, v -> lowest[i] = minOf(lowest[i], v); highest[i] = maxOf(highest[i], v) } }
        lowest.forEach { assertTrue("min $it", it < 0.12f) }
        highest.forEach { assertTrue("max $it", it > 0.88f) }
    }

    @Test fun overlayHasNoDarkeningMask() {
        val src = java.io.File("src/main/java/com/akynex/logicmix/core/designsystem/LogicMixGarlandOverlay.kt").readText()
        assertTrue(src.contains("BulbLook.of"))
        listOf("Dim", "2A0B3D", "Color.Black", "darkR").forEach { assertTrue(it, !src.contains(it)) }
    }

    @Test fun nextAndPreviousSendTheWaveInOppositeDirections() {
        fun peakFrames(forward: Boolean): IntArray {
            val e = GarlandEngine(Random(9))
            run(e, 0.5f, false)
            e.kick(forward)
            val first = IntArray(8) { -1 }
            repeat(30) { f ->
                e.advance(1 / 60f, false, true)
                e.levels.forEachIndexed { i, v -> if (first[i] < 0 && v > 0.5f) first[i] = f }
            }
            return first
        }
        val fwd = peakFrames(true)
        val back = peakFrames(false)
        assertTrue(fwd[0] >= 0 && fwd[7] > fwd[0])
        assertTrue(back[7] >= 0 && back[0] > back[7])
    }

    @Test fun pauseFadesEveryBulbSmoothlyWithoutDarkJump() {
        val e = GarlandEngine(Random(6))
        run(e, 8f, true)
        var prev = e.levels.copyOf()
        run(e, 3f, false) {
            e.levels.forEachIndexed { i, v -> assertTrue(kotlin.math.abs(v - prev[i]) < 0.1f) }
            prev = e.levels.copyOf()
        }
    }

    @Test fun litBulbIsBrightWithWhiteCoreAndLocalHalo() {
        val on = BulbLook.of(1f)
        assertEquals(1f, on.bodyAlpha, 1e-6f)
        assertEquals(1f, on.coreAlpha, 1e-6f)
        assertTrue(on.specAlpha > 0.5f)
        assertTrue(on.glowRadius in 2.5f..3.5f)
        assertTrue(on.coreRadius < on.bodyRadius && on.bodyRadius < on.haloRadius && on.haloRadius < on.glowRadius)
        val off = BulbLook.of(0f)
        assertEquals(0f, off.glowAlpha + off.haloAlpha + off.bodyAlpha + off.coreAlpha + off.specAlpha, 0f)
        for (i in 0..100) BulbLook.of(i / 100f).let {
            listOf(it.glowAlpha, it.haloAlpha, it.bodyAlpha, it.coreAlpha, it.specAlpha).forEach { a -> assertTrue(a in 0f..1f) }
        }
    }
}
