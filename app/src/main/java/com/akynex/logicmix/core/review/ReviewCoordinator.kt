package com.akynex.logicmix.core.review

import android.app.Activity
import android.content.Context
import com.akynex.logicmix.core.ads.OfferState
import com.akynex.logicmix.core.analytics.AnalyticsEvent
import com.akynex.logicmix.core.analytics.Ev
import com.akynex.logicmix.core.analytics.P
import com.akynex.logicmix.core.data.LevelUpCoordinator
import com.akynex.logicmix.core.monetization.Monetization
import com.akynex.logicmix.domain.review.ReviewMoment
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Game events the review logic listens to; fired by Monetization.newGameAnalytics for every category and Arcade. */
interface ReviewEvents {
    fun onTaskCompleted()
    fun onGameActivity()
}

/**
 * Connects game moments to [ReviewRequester]. The host Activity is attached only between onResume and onPause, so nothing is
 * requested while an ad, the UMP form or any other screen covers the game. Moments: after a completed task, after the Level Up
 * scene closes, on return to the main menu after play. Never at launch, never inside a puzzle.
 */
@Singleton
class ReviewCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val monetization: Monetization,
    private val levelUp: LevelUpCoordinator,
) : ReviewEvents {
    private val requester = ReviewRequester(
        gate = monetization.reviewGate,
        config = { monetization.config.current().review },
        launcher = PlayReviewLauncher(ReviewManagerFactory.create(context.applicationContext)),
        tracker = monetization.tracker,
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var host: WeakReference<Activity>? = null
    private var pendingTask: Job? = null
    @Volatile private var playedSinceMenu = false
    /** A puzzle is being solved: set when it starts, cleared when it is completed or the player returns to the menu. */
    @Volatile private var puzzleActive = false

    init { monetization.reviewListener = this }

    fun attach(activity: Activity) { host = WeakReference(activity) }
    fun detach(activity: Activity) { if (host?.get() === activity) host = null }

    private fun busy(): Boolean =
        puzzleActive ||
            levelUp.pending.value != null ||
            monetization.rewarded.state == OfferState.SHOWING ||
            monetization.umpBusy

    private fun tryRequest(moment: ReviewMoment) {
        runCatching { requester.request(host?.get(), moment, ::busy) }
    }

    override fun onGameActivity() { playedSinceMenu = true; puzzleActive = true }

    /** Short pause so the result of the task is seen first; re-checked afterwards (a Level Up scene may have started). */
    override fun onTaskCompleted() {
        playedSinceMenu = true
        puzzleActive = false
        pendingTask?.cancel()
        pendingTask = scope.launch { delay(TASK_PAUSE_MS); tryRequest(ReviewMoment.TASK_COMPLETED) }
    }

    fun onLevelUpClosed() {
        scope.launch { delay(CLOSE_PAUSE_MS); tryRequest(ReviewMoment.LEVEL_UP_CLOSED) }
    }

    /** Called when the main menu becomes the current screen; asks only if the player played since the last visit. */
    fun onMainMenu() {
        puzzleActive = false
        if (!playedSinceMenu) return
        playedSinceMenu = false
        scope.launch { delay(CLOSE_PAUSE_MS); tryRequest(ReviewMoment.MAIN_MENU) }
    }

    /** Settings button: the store page always opens, whatever the In-App Review API allows. */
    fun rateAppClicked(context: Context, appId: String) {
        monetization.tracker.log(AnalyticsEvent(Ev.RATE_APP_CLICKED))
        val target = PlayStoreOpener.open(context, appId) ?: return
        monetization.tracker.log(AnalyticsEvent(Ev.REVIEW_STORE_OPENED, mapOf(P.STORE_TARGET to target)))
    }

    private companion object {
        const val TASK_PAUSE_MS = 2_500L
        const val CLOSE_PAUSE_MS = 600L
    }
}
