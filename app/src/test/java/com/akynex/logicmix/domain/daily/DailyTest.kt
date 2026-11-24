package com.akynex.logicmix.domain.daily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyTest {
    @Test fun everyLevelHasATierAndTiersGrow() {
        val tiers = (1..99).map(DailyConfig::tierFor)
        assertEquals(tiers.sorted(), tiers)
        assertEquals(setOf(1, 2, 3, 4, 5), tiers.toSet())
    }

    @Test fun tierStaysLockedForTheDayAndResetsNextDay() {
        val lock = DailyTierPolicy.resolve(null, "2026-11-24", 5)
        assertEquals(1, lock.tier)
        assertEquals(lock, DailyTierPolicy.resolve(lock, "2026-11-24", 40))
        assertEquals(4, DailyTierPolicy.resolve(lock, "2026-11-25", 40).tier)
    }

    @Test fun clockIsUtc() {
        val t = 1_795_506_343_000L // 2026-11-24T07:45:43Z
        assertEquals("2026-11-24", DailyClock.dayOf(t))
        assertEquals((16 * 3600 + 14 * 60 + 17) * 1000L, DailyClock.millisUntilNextDay(t))
    }

    @Test fun xpPaidOncePerPuzzle() {
        var p = DailyProgress("2026-11-24-t1")
        val (p1, first) = p.complete("a"); p = p1
        assertTrue(first)
        val (p2, again) = p.complete("a")
        assertFalse(again); assertEquals(1, p2.count); assertFalse(p2.complete)
        listOf("b", "c", "d", "e").forEach { p = p.complete(it).first }
        assertTrue(p.complete)
    }
}
