package com.akynex.logicmix.core.review

import com.akynex.logicmix.core.analytics.AnalyticsEvent
import com.akynex.logicmix.core.analytics.AnalyticsTracker
import com.akynex.logicmix.domain.review.ReviewGate
import com.akynex.logicmix.domain.review.ReviewMoment
import com.akynex.logicmix.domain.review.ReviewPolicyConfig
import com.akynex.logicmix.domain.review.ReviewPolicy
import com.akynex.logicmix.domain.review.ReviewStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRequesterTest {
    private class Mem : ReviewStorage {
        override var firstLaunchMs: Long? = 0L
        override var completedTasks = 20
        override var sessions = 5
        override var lastActivityMs = 0L
        override var attempts: List<Long> = emptyList()
    }

    private class Log : AnalyticsTracker {
        val names = mutableListOf<String>()
        override fun log(event: AnalyticsEvent) { names += event.name }
    }

    private val now = 10 * ReviewPolicy.DAY_MS
    private val store = Mem()
    private val log = Log()
    private var launches = 0
    private var finish: ((Throwable?) -> Unit)? = null
    private var config = ReviewPolicyConfig()

    private fun requester(autoFinish: Throwable? = null, manual: Boolean = false) = ReviewRequester<String>(
        ReviewGate(store, { now }), { config },
        { _, done -> launches++; if (manual) finish = done else done(autoFinish) }, log,
    )

    @Test fun allowedMomentLaunchesOnceAndLogsEligibleAttemptFinished() {
        val r = requester()
        assertTrue(r.request("host", ReviewMoment.MAIN_MENU) { false })
        assertEquals(listOf("review_eligible", "review_request_attempt", "review_flow_finished"), log.names)
        assertEquals(1, store.attempts.size)
        // repeated callbacks / recomposition: nothing more happens, even from a brand new requester (new process)
        assertFalse(r.request("host", ReviewMoment.TASK_COMPLETED) { false })
        assertFalse(requester().request("host", ReviewMoment.MAIN_MENU) { false })
        assertEquals(1, launches)
    }

    @Test fun busyScreenOrMissingHostNeverRequestsAndDoesNotSpendTheAttempt() {
        val r = requester()
        assertFalse(r.request("host", ReviewMoment.TASK_COMPLETED) { true }) // fanfare, ad, consent form, puzzle in progress
        assertFalse(r.request(null, ReviewMoment.TASK_COMPLETED) { false })
        assertEquals(0, launches); assertTrue(store.attempts.isEmpty()); assertTrue(log.names.isEmpty())
        assertTrue(r.request("host", ReviewMoment.LEVEL_UP_CLOSED) { false })
    }

    @Test fun belowThresholdsOrDisabledDoesNothing() {
        store.completedTasks = 14
        assertFalse(requester().request("h", ReviewMoment.MAIN_MENU) { false })
        store.completedTasks = 20
        config = config.copy(enabled = false)
        assertFalse(requester().request("h", ReviewMoment.MAIN_MENU) { false })
        assertEquals(0, launches); assertTrue(log.names.isEmpty())
    }

    @Test fun playErrorIsLoggedQuietlyAndTheAttemptStaysSpent() {
        val r = requester(autoFinish = IllegalStateException("no play"))
        assertTrue(r.request("h", ReviewMoment.MAIN_MENU) { false })
        assertEquals(listOf("review_eligible", "review_request_attempt", "review_request_error"), log.names)
        assertEquals(1, store.attempts.size)
    }

    @Test fun launcherThrowingIsContainedAndOverlappingFlowsAreBlocked() {
        val boom = ReviewRequester<String>(ReviewGate(store, { now }), { config }, { _, _ -> error("x") }, log)
        assertTrue(boom.request("h", ReviewMoment.MAIN_MENU) { false })
        assertTrue(log.names.contains("review_request_error"))
        val slow = requester(manual = true)
        store.attempts = emptyList()
        assertTrue(slow.request("h", ReviewMoment.MAIN_MENU) { false })
        assertFalse(slow.request("h", ReviewMoment.MAIN_MENU) { false })
        finish?.invoke(null); finish?.invoke(null) // duplicate callback is ignored
        assertEquals(1, log.names.count { it == "review_flow_finished" })
    }

    @Test fun neverEmitsFictionalOutcomeEvents() {
        requester().request("h", ReviewMoment.MAIN_MENU) { false }
        assertTrue(log.names.none { it == "review_submitted" || it == "rating_received" })
    }
}
