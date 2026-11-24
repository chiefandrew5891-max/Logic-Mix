package com.akynex.logicmix.domain.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewPolicyTest {
    private val day = ReviewPolicy.DAY_MS
    private val cfg = ReviewPolicyConfig()
    private val ready = ReviewState(firstLaunchMs = 0L, completedTasks = 15, sessions = 3, attempts = emptyList())

    private class Mem : ReviewStorage {
        override var firstLaunchMs: Long? = null
        override var completedTasks = 0
        override var sessions = 0
        override var lastActivityMs = 0L
        override var attempts: List<Long> = emptyList()
    }

    @Test fun allThreeConditionsAreRequiredForTheFirstRequest() {
        assertNull(ReviewPolicy.check(ready, cfg, 3 * day))
        assertEquals(ReviewBlock.TOO_EARLY, ReviewPolicy.check(ready, cfg, 3 * day - 1))
        assertEquals(ReviewBlock.FEW_TASKS, ReviewPolicy.check(ready.copy(completedTasks = 14), cfg, 9 * day))
        assertEquals(ReviewBlock.FEW_SESSIONS, ReviewPolicy.check(ready.copy(sessions = 2), cfg, 9 * day))
    }

    @Test fun repeatNeedsSixtyDaysAndNeverMoreThanThreePerYear() {
        val t0 = 10 * day
        assertEquals(ReviewBlock.COOLDOWN, ReviewPolicy.check(ready.copy(attempts = listOf(t0)), cfg, t0 + 60 * day - 1))
        assertNull(ReviewPolicy.check(ready.copy(attempts = listOf(t0)), cfg, t0 + 60 * day))
        val three = listOf(t0, t0 + 61 * day, t0 + 122 * day)
        assertEquals(ReviewBlock.YEARLY_LIMIT, ReviewPolicy.check(ready.copy(attempts = three), cfg, t0 + 190 * day))
        // the oldest attempt leaves the rolling 12-month window
        assertNull(ReviewPolicy.check(ready.copy(attempts = three), cfg, t0 + 366 * day))
    }

    @Test fun clockMovedBackwardsDoesNotLoosenCooldown() {
        assertEquals(ReviewBlock.COOLDOWN, ReviewPolicy.check(ready.copy(attempts = listOf(100 * day)), cfg, 50 * day))
    }

    @Test fun disabledByRemoteConfig() {
        assertEquals(ReviewBlock.DISABLED, ReviewPolicy.check(ready, cfg.copy(enabled = false), 9 * day))
    }

    @Test fun gateCountsTasksAndSeparateSessionsAndSurvivesRestart() {
        val store = Mem()
        var now = 1_000L
        fun gate() = ReviewGate(store, { now })
        repeat(5) { gate().onTaskCompleted() }
        assertEquals(1, store.sessions) // all within one sitting
        now += ReviewGate.SESSION_GAP_MS + 1
        gate().onGameActivity()
        now += 10 * 60_000L
        gate().onTaskCompleted() // same session
        now += ReviewGate.SESSION_GAP_MS + 1
        gate().onTaskCompleted()
        assertEquals(7, store.completedTasks)
        assertEquals(3, store.sessions)
        assertEquals(1_000L, store.firstLaunchMs) // a new gate (new process) does not reset it
    }

    @Test fun tryAcquirePersistsTheAttemptSoItCannotRepeat() {
        val store = Mem()
        var now = 0L
        val gate = ReviewGate(store, { now })
        gate.ensureFirstLaunch()
        store.completedTasks = 20; store.sessions = 4
        now = 4 * day
        assertNull(gate.tryAcquire(cfg))
        assertEquals(listOf(4 * day), store.attempts)
        assertEquals(ReviewBlock.COOLDOWN, gate.tryAcquire(cfg))
        assertEquals(ReviewBlock.COOLDOWN, ReviewGate(store, { now + 1 }).tryAcquire(cfg))
    }

    @Test fun existingInstallsUseTheInstallDateAsFirstLaunch() {
        val store = Mem()
        ReviewGate(store, { 10 * day }, installTimeMs = { 2 * day }).ensureFirstLaunch()
        assertEquals(2 * day, store.firstLaunchMs)
    }
}
