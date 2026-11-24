package com.akynex.logicmix.core.monetization

import android.app.Activity
import android.app.Application
import dagger.hilt.android.qualifiers.ApplicationContext
import com.akynex.logicmix.core.ads.AdAnalytics
import com.akynex.logicmix.core.ads.AdMobRewardedGateway
import com.akynex.logicmix.core.ads.RewardedAdController
import com.akynex.logicmix.core.arcade.ArcadeBridge
import com.akynex.logicmix.core.analytics.CrashKind
import com.akynex.logicmix.core.analytics.CrashReporter
import com.akynex.logicmix.core.analytics.FirebaseAnalyticsSink
import com.akynex.logicmix.core.analytics.FirebaseCrashReporter
import com.akynex.logicmix.core.analytics.GameAnalytics
import com.akynex.logicmix.core.analytics.GuardedAnalyticsTracker
import com.akynex.logicmix.core.config.AppConfigSource
import com.akynex.logicmix.core.config.FirebaseConfigSource
import com.akynex.logicmix.core.consent.ConsentController
import com.akynex.logicmix.core.consent.ConsentState
import com.akynex.logicmix.core.consent.SharedPrefsConsentStore
import com.akynex.logicmix.core.consent.UmpConsentManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Единая точка сборки аналитики, Crashlytics, Remote Config, согласия и рекламы. Если Firebase не настроен
 * (нет google-services.json), всё превращается в no-op, игра работает как раньше.
 */
@Singleton
class Monetization @Inject constructor(@ApplicationContext context: android.content.Context) {
    private val app = context.applicationContext as Application
    private val sink = FirebaseAnalyticsSink(app)
    private val crash = FirebaseCrashReporter(app)
    private val _consent = MutableStateFlow(ConsentState())
    val consentState: StateFlow<ConsentState> get() = _consent

    val consent = ConsentController(SharedPrefsConsentStore(app)) { s ->
        _consent.value = s
        sink.applyConsent(s)
        crash.applyConsent(s)
    }

    val config: AppConfigSource = FirebaseConfigSource(app)
    val tracker = GuardedAnalyticsTracker(sink, allowed = { consent.analyticsAllowed })
    val crashReporter: CrashReporter get() = crash
    private val ump = UmpConsentManager()

    private val gateway = AdMobRewardedGateway(app) { crash.recordNonFatal(CrashKind.ADS_SDK, it) }
    val rewarded = RewardedAdController(
        gateway = gateway,
        analytics = AdAnalytics(tracker) { config.current() },
        config = { config.current() },
        canRequestAds = { consent.adsAllowed },
    )

    init {
        _consent.value = consent.state
        sink.applyConsent(consent.state)
        crash.applyConsent(consent.state)
    }

    val arcade = ArcadeBridge()

    /** Persistent review counters (completed tasks, sessions, attempt history); fed from the same game events as analytics. */
    val reviewGate = com.akynex.logicmix.domain.review.ReviewGate(
        com.akynex.logicmix.core.review.SharedPrefsReviewStorage(app),
        installTimeMs = { runCatching { app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime }.getOrNull() },
    ).also { it.ensureFirstLaunch() }

    /** Set by ReviewCoordinator; null until it exists. */
    @Volatile var reviewListener: com.akynex.logicmix.core.review.ReviewEvents? = null

    /** True while the UMP consent form or privacy options may be on screen. */
    @Volatile var umpBusy = false
        private set

    fun newGameAnalytics() = GameAnalytics(
        tracker,
        onCompleted = { report ->
            arcade.report(report)
            runCatching { reviewGate.onTaskCompleted(); reviewListener?.onTaskCompleted() }
        },
        onPuzzleStarted = {
            runCatching { reviewGate.onGameActivity(); reviewListener?.onGameActivity() }
        },
    )

    /** Из MainActivity.onCreate: форма согласия показывается, только если она требуется. */
    fun gatherConsent(activity: Activity) {
        umpBusy = true
        ump.gather(activity) { can, personalized ->
            umpBusy = false
            onGathered(can, personalized)
        }
    }

    private fun onGathered(can: Boolean, personalized: Boolean) {
        consent.onUmpResult(can, personalized)
        if (can) rewarded.prepare("startup")
    }

    fun privacyOptionsRequired(activity: Activity) = ump.privacyOptionsRequired(activity)

    fun showPrivacyOptions(activity: Activity) = ump.showPrivacyOptions(activity) { can, personalized ->
        consent.onUmpResult(can, personalized)
    }
}
