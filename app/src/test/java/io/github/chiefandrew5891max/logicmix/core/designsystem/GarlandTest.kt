package io.github.chiefandrew5891max.logicmix.core.designsystem

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
}
