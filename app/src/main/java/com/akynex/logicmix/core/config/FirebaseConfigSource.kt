package com.akynex.logicmix.core.config

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Remote Config поверх безопасных локальных значений. Fetch асинхронный и не блокирует запуск; ошибка сети оставляет
 * предыдущее/дефолтное значение. current() читается при старте новой головоломки, поэтому начатые игры не меняются.
 */
class FirebaseConfigSource(context: Context) : AppConfigSource {
    @Volatile private var config: AppConfig = AppConfig.DEFAULT
    private val rc: FirebaseRemoteConfig? = runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseRemoteConfig.getInstance()
    }.getOrNull()

    private val keys = listOf(
        AppConfig.Companion.Keys.REWARDED_ENABLED, AppConfig.Companion.Keys.INTERSTITIAL_ENABLED, AppConfig.Companion.Keys.NATIVE_ENABLED,
        AppConfig.Companion.Keys.REWARDED_HINT_ENABLED, AppConfig.Companion.Keys.OFFER_COOLDOWN_S, AppConfig.Companion.Keys.MAX_OFFERS_SESSION,
        AppConfig.Companion.Keys.AD_RETRY_S, AppConfig.Companion.Keys.INTERSTITIAL_MIN_INTERVAL_S, AppConfig.Companion.Keys.INTERSTITIAL_EVERY_N,
        AppConfig.Companion.Keys.FREE_HINTS, AppConfig.Companion.Keys.EXPERIMENT_VARIANT, AppConfig.Companion.Keys.DISABLED_FEATURES,
        AppConfig.Companion.Keys.LOG_AD_REVENUE,
        AppConfig.Companion.Keys.REVIEW_ENABLED, AppConfig.Companion.Keys.REVIEW_MIN_DAYS, AppConfig.Companion.Keys.REVIEW_MIN_TASKS,
        AppConfig.Companion.Keys.REVIEW_MIN_SESSIONS, AppConfig.Companion.Keys.REVIEW_COOLDOWN_DAYS, AppConfig.Companion.Keys.REVIEW_MAX_PER_YEAR,
    )

    init {
        rc?.let { r ->
            runCatching {
                r.setConfigSettingsAsync(FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(12 * 3600L).build())
                val d = AppConfig.DEFAULT
                r.setDefaultsAsync(mapOf(
                    AppConfig.Companion.Keys.REWARDED_ENABLED to d.rewardedEnabled.toString(),
                    AppConfig.Companion.Keys.INTERSTITIAL_ENABLED to d.interstitialEnabled.toString(),
                    AppConfig.Companion.Keys.NATIVE_ENABLED to d.nativeEnabled.toString(),
                    AppConfig.Companion.Keys.REWARDED_HINT_ENABLED to d.rewardedHintEnabled.toString(),
                    AppConfig.Companion.Keys.OFFER_COOLDOWN_S to d.offerCooldownSeconds.toString(),
                    AppConfig.Companion.Keys.MAX_OFFERS_SESSION to d.maxOffersPerSession.toString(),
                    AppConfig.Companion.Keys.AD_RETRY_S to d.adRetrySeconds.toString(),
                    AppConfig.Companion.Keys.INTERSTITIAL_MIN_INTERVAL_S to d.interstitialMinIntervalSeconds.toString(),
                    AppConfig.Companion.Keys.INTERSTITIAL_EVERY_N to d.interstitialEveryNPuzzles.toString(),
                    AppConfig.Companion.Keys.FREE_HINTS to d.freeHintsPerPuzzle.toString(),
                    AppConfig.Companion.Keys.EXPERIMENT_VARIANT to d.experimentVariant,
                    AppConfig.Companion.Keys.DISABLED_FEATURES to "",
                    AppConfig.Companion.Keys.LOG_AD_REVENUE to d.logAdRevenueInApp.toString(),
                    AppConfig.Companion.Keys.REVIEW_ENABLED to d.review.enabled.toString(),
                    AppConfig.Companion.Keys.REVIEW_MIN_DAYS to d.review.minDaysSinceFirstLaunch.toString(),
                    AppConfig.Companion.Keys.REVIEW_MIN_TASKS to d.review.minCompletedTasks.toString(),
                    AppConfig.Companion.Keys.REVIEW_MIN_SESSIONS to d.review.minSessions.toString(),
                    AppConfig.Companion.Keys.REVIEW_COOLDOWN_DAYS to d.review.cooldownDays.toString(),
                    AppConfig.Companion.Keys.REVIEW_MAX_PER_YEAR to d.review.maxAttemptsPerYear.toString(),
                ))
                refresh(r)                                   // ранее активированные значения (если есть)
                r.fetchAndActivate().addOnCompleteListener { refresh(r) }
            }
        }
    }

    private fun refresh(r: FirebaseRemoteConfig) {
        config = runCatching { AppConfig.sanitize(keys.associateWith { r.getString(it) }) }.getOrDefault(AppConfig.DEFAULT)
    }

    override fun current(): AppConfig = config
}
