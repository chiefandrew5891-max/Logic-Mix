package com.akynex.logicmix.core.review

import com.akynex.logicmix.core.analytics.AnalyticsEvent
import com.akynex.logicmix.core.analytics.AnalyticsTracker
import com.akynex.logicmix.core.analytics.Ev
import com.akynex.logicmix.core.analytics.P
import com.akynex.logicmix.domain.review.ReviewGate
import com.akynex.logicmix.domain.review.ReviewMoment
import com.akynex.logicmix.domain.review.ReviewPolicyConfig

/** Starts the official Google Play review flow for [host] (an Activity in production). [done] gets null or the failure. */
fun interface ReviewLauncher<H> {
    fun launch(host: H, done: (Throwable?) -> Unit)
}

/**
 * The only place that decides to call the In-App Review API. Guarantees: policy limits (via [ReviewGate]), at most one
 * attempt per process, no overlapping flows, and no exception reaching the game. The Play API never says whether a form was
 * shown or what the user chose, so "finished" only means the flow returned.
 */
class ReviewRequester<H>(
    private val gate: ReviewGate,
    private val config: () -> ReviewPolicyConfig,
    private val launcher: ReviewLauncher<H>,
    private val tracker: AnalyticsTracker,
) {
    private var inFlight = false
    private var attemptedThisProcess = false
    private var eligibleLogged = false

    /** [busy] must be true while a fanfare, ad, consent form or any other overlay is on screen. */
    fun request(host: H?, moment: ReviewMoment, busy: () -> Boolean): Boolean {
        if (host == null) return false
        synchronized(this) {
            if (inFlight || attemptedThisProcess || busy()) return false
            val cfg = config()
            if (gate.check(cfg) != null) return false
            if (!eligibleLogged) {
                eligibleLogged = true
                tracker.log(AnalyticsEvent(Ev.REVIEW_ELIGIBLE, mapOf(P.REVIEW_MOMENT to moment.key)))
            }
            if (gate.tryAcquire(cfg) != null) return false
            inFlight = true
            attemptedThisProcess = true
        }
        val params = mapOf<String, Any>(P.REVIEW_MOMENT to moment.key, P.REVIEW_ATTEMPT_NO to gate.attemptCount.toLong())
        tracker.log(AnalyticsEvent(Ev.REVIEW_REQUEST_ATTEMPT, params))
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        val complete: (Throwable?) -> Unit = { error ->
            if (finished.compareAndSet(false, true)) {
                synchronized(this) { inFlight = false }
                if (error == null) tracker.log(AnalyticsEvent(Ev.REVIEW_FLOW_FINISHED, params))
                else tracker.log(AnalyticsEvent(Ev.REVIEW_REQUEST_ERROR, params + (P.ERROR_TYPE to (error::class.simpleName ?: "unknown").take(40))))
            }
        }
        runCatching { launcher.launch(host, complete) }.onFailure(complete)
        return true
    }
}
