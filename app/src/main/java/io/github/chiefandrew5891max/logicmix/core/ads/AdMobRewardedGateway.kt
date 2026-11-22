package io.github.chiefandrew5891max.logicmix.core.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import io.github.chiefandrew5891max.logicmix.BuildConfig

/** Единственное место, где используется Google Mobile Ads SDK для rewarded. Всё остальное работает через интерфейс. */
class AdMobRewardedGateway(
    private val app: Application,
    private val onSdkProblem: (Throwable) -> Unit = {},
) : RewardedAdGateway {
    private var ad: RewardedAd? = null
    private var activity: Activity? = null
    private var initialized = false

    init {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { activity = a }
            override fun onActivityDestroyed(a: Activity) { if (activity === a) activity = null }
            override fun onActivityCreated(a: Activity, s: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, o: Bundle) = Unit
        })
    }

    /** SDK инициализируется только после того, как согласие позволяет запрашивать рекламу. */
    private fun ensureInit(context: Context) {
        if (initialized) return
        initialized = true
        runCatching { MobileAds.initialize(context) {} }.onFailure(onSdkProblem)
    }

    override fun load(callback: RewardedAdGateway.LoadCallback) {
        runCatching {
            ensureInit(app)
            RewardedAd.load(app, BuildConfig.ADMOB_REWARDED_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewarded: RewardedAd) { ad = rewarded; callback.onLoaded() }
                override fun onAdFailedToLoad(error: LoadAdError) { ad = null; callback.onFailed(error.code) }
            })
        }.onFailure { onSdkProblem(it); callback.onFailed(-1) }
    }

    override fun show(callbacks: RewardedAdGateway.ShowCallbacks) {
        val a = ad
        val act = activity
        if (a == null || act == null) { callbacks.onFailedToShow(-2); return }
        ad = null
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callbacks.onOpened()
            override fun onAdImpression() = callbacks.onImpression()
            override fun onAdClicked() = callbacks.onClicked()
            override fun onAdDismissedFullScreenContent() = callbacks.onDismissed()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = callbacks.onFailedToShow(error.code)
        }
        a.setOnPaidEventListener { v -> callbacks.onPaid(v.valueMicros, v.currencyCode, v.precisionType) }
        runCatching { a.show(act) { callbacks.onRewardEarned() } }.onFailure { onSdkProblem(it); callbacks.onFailedToShow(-3) }
    }
}
