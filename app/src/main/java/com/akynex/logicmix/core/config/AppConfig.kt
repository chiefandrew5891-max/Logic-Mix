package com.akynex.logicmix.core.config

import com.akynex.logicmix.domain.review.ReviewPolicyConfig

/**
 * Типизированная удалённая конфигурация. Безопасные значения по умолчанию = текущее поведение приложения:
 * без сети, без Firebase и при мусорных значениях игра работает как раньше. Конфигурация читается при старте
 * новой головоломки и не влияет на уже начатые головоломки и сохранённые ответы.
 */
data class AppConfig(
    val rewardedEnabled: Boolean = true,
    val interstitialEnabled: Boolean = false,
    val nativeEnabled: Boolean = false,
    val rewardedHintEnabled: Boolean = true,
    val offerCooldownSeconds: Int = 60,
    val maxOffersPerSession: Int = 5,
    val adRetrySeconds: Int = 30,
    val interstitialMinIntervalSeconds: Int = 180,
    val interstitialEveryNPuzzles: Int = 3,
    /** -1 = экономика подсказок не менялась (поведение приложения как сейчас). */
    val freeHintsPerPuzzle: Int = UNCHANGED,
    val experimentVariant: String = "control",
    val disabledFeatures: Set<String> = emptySet(),
    val logAdRevenueInApp: Boolean = false,
    val review: ReviewPolicyConfig = ReviewPolicyConfig(),
) {
    fun featureEnabled(key: String) = key !in disabledFeatures

    companion object {
        const val UNCHANGED = -1
        val DEFAULT = AppConfig()

        object Keys {
            const val REWARDED_ENABLED = "ads_rewarded_enabled"
            const val INTERSTITIAL_ENABLED = "ads_interstitial_enabled"
            const val NATIVE_ENABLED = "ads_native_enabled"
            const val REWARDED_HINT_ENABLED = "rewarded_hint_enabled"
            const val OFFER_COOLDOWN_S = "ads_offer_cooldown_s"
            const val MAX_OFFERS_SESSION = "ads_max_offers_per_session"
            const val AD_RETRY_S = "ads_retry_s"
            const val INTERSTITIAL_MIN_INTERVAL_S = "ads_interstitial_min_interval_s"
            const val INTERSTITIAL_EVERY_N = "ads_interstitial_every_n"
            const val FREE_HINTS = "free_hints_per_puzzle"
            const val EXPERIMENT_VARIANT = "experiment_variant"
            const val DISABLED_FEATURES = "disabled_features"
            const val LOG_AD_REVENUE = "log_ad_revenue_in_app"
            const val REVIEW_ENABLED = "review_enabled"
            const val REVIEW_MIN_DAYS = "review_min_days"
            const val REVIEW_MIN_TASKS = "review_min_tasks"
            const val REVIEW_MIN_SESSIONS = "review_min_sessions"
            const val REVIEW_COOLDOWN_DAYS = "review_cooldown_days"
            const val REVIEW_MAX_PER_YEAR = "review_max_per_year"
        }

        private val variant = Regex("^[a-z0-9_]{1,40}$")

        /** Строгий разбор: неверный тип/диапазон → значение по умолчанию, никогда не исключение. */
        fun sanitize(raw: Map<String, String?>): AppConfig {
            val d = DEFAULT
            fun bool(k: String, def: Boolean) = when (raw[k]?.trim()?.lowercase()) { "true" -> true; "false" -> false; else -> def }
            fun int(k: String, def: Int, r: IntRange) = raw[k]?.trim()?.toIntOrNull()?.takeIf { it in r } ?: def
            return AppConfig(
                rewardedEnabled = bool(Keys.REWARDED_ENABLED, d.rewardedEnabled),
                interstitialEnabled = bool(Keys.INTERSTITIAL_ENABLED, d.interstitialEnabled),
                nativeEnabled = bool(Keys.NATIVE_ENABLED, d.nativeEnabled),
                rewardedHintEnabled = bool(Keys.REWARDED_HINT_ENABLED, d.rewardedHintEnabled),
                offerCooldownSeconds = int(Keys.OFFER_COOLDOWN_S, d.offerCooldownSeconds, 0..3600),
                maxOffersPerSession = int(Keys.MAX_OFFERS_SESSION, d.maxOffersPerSession, 0..20),
                adRetrySeconds = int(Keys.AD_RETRY_S, d.adRetrySeconds, 10..600),
                interstitialMinIntervalSeconds = int(Keys.INTERSTITIAL_MIN_INTERVAL_S, d.interstitialMinIntervalSeconds, 60..3600),
                interstitialEveryNPuzzles = int(Keys.INTERSTITIAL_EVERY_N, d.interstitialEveryNPuzzles, 2..20),
                freeHintsPerPuzzle = int(Keys.FREE_HINTS, d.freeHintsPerPuzzle, -1..10),
                experimentVariant = raw[Keys.EXPERIMENT_VARIANT]?.trim()?.takeIf { variant.matches(it) } ?: d.experimentVariant,
                disabledFeatures = raw[Keys.DISABLED_FEATURES].orEmpty().split(',').map { it.trim() }
                    .filter { variant.matches(it) }.take(20).toSet(),
                logAdRevenueInApp = bool(Keys.LOG_AD_REVENUE, d.logAdRevenueInApp),
                review = ReviewPolicyConfig(
                    enabled = bool(Keys.REVIEW_ENABLED, d.review.enabled),
                    minDaysSinceFirstLaunch = int(Keys.REVIEW_MIN_DAYS, d.review.minDaysSinceFirstLaunch, 0..365),
                    minCompletedTasks = int(Keys.REVIEW_MIN_TASKS, d.review.minCompletedTasks, 0..10_000),
                    minSessions = int(Keys.REVIEW_MIN_SESSIONS, d.review.minSessions, 0..1_000),
                    cooldownDays = int(Keys.REVIEW_COOLDOWN_DAYS, d.review.cooldownDays, 1..730),
                    maxAttemptsPerYear = int(Keys.REVIEW_MAX_PER_YEAR, d.review.maxAttemptsPerYear, 0..12),
                ),
            )
        }
    }
}

/** Источник конфигурации; current() всегда возвращает валидное значение и не блокирует. */
interface AppConfigSource {
    fun current(): AppConfig
}

class StaticConfigSource(private val config: AppConfig = AppConfig.DEFAULT) : AppConfigSource {
    override fun current() = config
}
