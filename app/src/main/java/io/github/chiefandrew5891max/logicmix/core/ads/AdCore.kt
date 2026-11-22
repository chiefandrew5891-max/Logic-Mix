package io.github.chiefandrew5891max.logicmix.core.ads

import io.github.chiefandrew5891max.logicmix.core.analytics.AnalyticsEvent
import io.github.chiefandrew5891max.logicmix.core.analytics.AnalyticsTracker
import io.github.chiefandrew5891max.logicmix.core.analytics.GameContext
import io.github.chiefandrew5891max.logicmix.core.analytics.analyticsKey
import io.github.chiefandrew5891max.logicmix.core.config.AppConfig

enum class AdFormat(val key: String) { REWARDED("rewarded"), INTERSTITIAL("interstitial"), NATIVE("native") }

/** Награда определяется здесь, а не в экране. */
enum class RewardType(val key: String) { EXTRA_HINT("extra_hint") }

object AdEv {
    const val OFFER_SHOWN = "ad_offer_shown"
    const val OFFER_ACCEPT = "ad_offer_accept"
    const val OFFER_DECLINE = "ad_offer_decline"
    const val REQUEST = "ad_request"
    const val LOADED = "ad_loaded"
    const val LOAD_FAILED = "ad_load_failed"
    // Не "ad_impression": это имя Firebase использует для автоматического события при связке AdMob↔Firebase.
    const val IMPRESSION = "ad_impression_confirmed"
    const val CLICKED = "ad_clicked"
    const val OPENED = "ad_opened"
    const val DISMISSED = "ad_dismissed"
    const val REWARD_EARNED = "ad_reward_earned"
    const val REWARD_GRANTED = "ad_reward_granted"
    const val REVENUE = "ad_revenue_paid"
}

/** События рекламы. Параметры: ad_format, placement, game_category, reward_type, error_code, value/currency/precision. */
class AdAnalytics(private val tracker: AnalyticsTracker, private val config: () -> AppConfig) {
    private fun log(name: String, format: AdFormat, placement: String, ctx: GameContext?, extra: Map<String, Any> = emptyMap(), dedupe: String? = null) {
        val p = HashMap<String, Any>(extra)
        p["ad_format"] = format.key
        p["placement"] = placement
        ctx?.let { p["game_category"] = it.category.analyticsKey }
        tracker.log(AnalyticsEvent(name, p, dedupe))
    }

    fun offerShown(f: AdFormat, placement: String, ctx: GameContext?) = log(AdEv.OFFER_SHOWN, f, placement, ctx)
    fun offerAccepted(f: AdFormat, placement: String, ctx: GameContext?) = log(AdEv.OFFER_ACCEPT, f, placement, ctx)
    fun offerDeclined(f: AdFormat, placement: String, ctx: GameContext?) = log(AdEv.OFFER_DECLINE, f, placement, ctx)
    fun request(f: AdFormat, placement: String) = log(AdEv.REQUEST, f, placement, null)
    fun loaded(f: AdFormat, placement: String) = log(AdEv.LOADED, f, placement, null)
    fun loadFailed(f: AdFormat, placement: String, code: Int) = log(AdEv.LOAD_FAILED, f, placement, null, mapOf("error_code" to code.toLong()))
    fun impression(f: AdFormat, placement: String, ctx: GameContext?, show: String) = log(AdEv.IMPRESSION, f, placement, ctx, dedupe = "imp:$show")
    fun clicked(f: AdFormat, placement: String, ctx: GameContext?, show: String) = log(AdEv.CLICKED, f, placement, ctx, dedupe = "clk:$show")
    fun opened(f: AdFormat, placement: String, ctx: GameContext?, show: String) = log(AdEv.OPENED, f, placement, ctx, dedupe = "opn:$show")
    fun dismissed(f: AdFormat, placement: String, ctx: GameContext?, rewarded: Boolean, show: String) =
        log(AdEv.DISMISSED, f, placement, ctx, mapOf("rewarded" to rewarded), "dsm:$show")
    fun rewardEarned(placement: String, ctx: GameContext?, r: RewardType, show: String) =
        log(AdEv.REWARD_EARNED, AdFormat.REWARDED, placement, ctx, mapOf("reward_type" to r.key), "ern:$show")
    fun rewardGranted(placement: String, ctx: GameContext?, r: RewardType, show: String) =
        log(AdEv.REWARD_GRANTED, AdFormat.REWARDED, placement, ctx, mapOf("reward_type" to r.key), "grt:$show")

    /**
     * Доход только из onPaidEvent SDK и только если включён logAdRevenueInApp. По умолчанию выключено: при связке
     * AdMob↔Firebase доход уже поступает автоматически, и второй канал удвоил бы суммы.
     */
    fun revenue(f: AdFormat, placement: String, ctx: GameContext?, valueMicros: Long, currency: String, precision: Int) {
        if (!config().logAdRevenueInApp || valueMicros <= 0 || currency.length != 3) return
        log(AdEv.REVENUE, f, placement, ctx, mapOf("value" to valueMicros / 1_000_000.0, "currency" to currency, "precision" to precision.toLong()))
    }
}

/** Шлюз к SDK (реализация поверх Google Mobile Ads — в AdMobRewardedGateway; в тестах — fake). */
interface RewardedAdGateway {
    fun load(callback: LoadCallback)
    fun show(callbacks: ShowCallbacks)

    interface LoadCallback { fun onLoaded(); fun onFailed(code: Int) }
    interface ShowCallbacks {
        fun onOpened(); fun onImpression(); fun onClicked(); fun onRewardEarned(); fun onDismissed()
        fun onFailedToShow(code: Int); fun onPaid(valueMicros: Long, currency: String, precision: Int)
    }
}

enum class OfferState { IDLE, LOADING, READY, UNAVAILABLE, SHOWING }

/**
 * Состояние rewarded-рекламы. Награда выдаётся ровно один раз, только после onRewardEarned SDK; запрос ≠ показ,
 * открытие ≠ награда, закрытие ≠ просмотр. Все колбэки привязаны к конкретному показу и повторы игнорируются.
 * Вызывать с главного потока.
 */
class RewardedAdController(
    private val gateway: RewardedAdGateway,
    private val analytics: AdAnalytics,
    private val config: () -> AppConfig,
    private val canRequestAds: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    var onStateChanged: (OfferState) -> Unit = {},
) {
    var state = OfferState.IDLE
        private set
    private var failedAt = Long.MIN_VALUE
    private var offersThisSession = 0
    private var lastOfferAt = Long.MIN_VALUE
    private var showCounter = 0
    private var loadToken = 0

    private fun set(s: OfferState) { if (state != s) { state = s; onStateChanged(s) } }

    private val enabled get() = config().let { it.rewardedEnabled && it.rewardedHintEnabled && it.featureEnabled("rewarded") }

    /** Можно ли показывать игроку предложение сейчас (частота, лимит сессии, согласие, переключатели). */
    fun offerAllowed(): Boolean {
        val c = config()
        if (!enabled || !canRequestAds() || offersThisSession >= c.maxOffersPerSession) return false
        return lastOfferAt == Long.MIN_VALUE || clock() - lastOfferAt >= c.offerCooldownSeconds * 1000L
    }

    fun prepare(placement: String) {
        if (!enabled || !canRequestAds()) return
        if (state == OfferState.LOADING || state == OfferState.READY || state == OfferState.SHOWING) return
        if (state == OfferState.UNAVAILABLE && clock() - failedAt < config().adRetrySeconds * 1000L) return
        set(OfferState.LOADING)
        val token = ++loadToken
        analytics.request(AdFormat.REWARDED, placement)
        gateway.load(object : RewardedAdGateway.LoadCallback {
            override fun onLoaded() {
                if (token != loadToken || state != OfferState.LOADING) return
                analytics.loaded(AdFormat.REWARDED, placement)
                set(OfferState.READY)
            }
            override fun onFailed(code: Int) {
                if (token != loadToken || state != OfferState.LOADING) return
                failedAt = clock()
                analytics.loadFailed(AdFormat.REWARDED, placement, code)
                set(OfferState.UNAVAILABLE)
            }
        })
    }

    /** Экран вызывает один раз при реальном появлении предложения (не из рекомпозиции). */
    fun offerShown(placement: String, ctx: GameContext?) {
        offersThisSession++
        lastOfferAt = clock()
        analytics.offerShown(AdFormat.REWARDED, placement, ctx)
    }

    fun declined(placement: String, ctx: GameContext?) = analytics.offerDeclined(AdFormat.REWARDED, placement, ctx)

    /** @return true, если показ начат. Двойной тап и показ без готовой рекламы → false. */
    fun accept(placement: String, ctx: GameContext?, grant: (RewardType) -> Unit): Boolean {
        if (state != OfferState.READY) return false
        set(OfferState.SHOWING)
        val show = "r${++showCounter}"
        var earned = false
        var granted = false
        var finished = false
        analytics.offerAccepted(AdFormat.REWARDED, placement, ctx)
        gateway.show(object : RewardedAdGateway.ShowCallbacks {
            override fun onOpened() { if (!finished) analytics.opened(AdFormat.REWARDED, placement, ctx, show) }
            override fun onImpression() { if (!finished) analytics.impression(AdFormat.REWARDED, placement, ctx, show) }
            override fun onClicked() { if (!finished) analytics.clicked(AdFormat.REWARDED, placement, ctx, show) }
            override fun onPaid(valueMicros: Long, currency: String, precision: Int) =
                analytics.revenue(AdFormat.REWARDED, placement, ctx, valueMicros, currency, precision)
            override fun onRewardEarned() {
                if (finished || earned) return
                earned = true
                analytics.rewardEarned(placement, ctx, RewardType.EXTRA_HINT, show)
                if (!granted) {
                    granted = true
                    try { grant(RewardType.EXTRA_HINT) } finally {
                        analytics.rewardGranted(placement, ctx, RewardType.EXTRA_HINT, show)
                    }
                }
            }
            override fun onDismissed() {
                if (finished) return
                finished = true
                analytics.dismissed(AdFormat.REWARDED, placement, ctx, earned, show)
                set(OfferState.IDLE)
                prepare(placement)
            }
            override fun onFailedToShow(code: Int) {
                if (finished) return
                finished = true
                analytics.loadFailed(AdFormat.REWARDED, placement, code)
                failedAt = clock()
                set(OfferState.UNAVAILABLE)
            }
        })
        return true
    }
}

/** Interstitial: только на естественных переходах между решёнными заданиями; по умолчанию выключен. */
object InterstitialPolicy {
    fun canShow(config: AppConfig, consentOk: Boolean, naturalBreak: Boolean, puzzlesSinceLast: Int, msSinceLast: Long): Boolean =
        config.interstitialEnabled && config.featureEnabled("interstitial") && consentOk && naturalBreak &&
            puzzlesSinceLast >= config.interstitialEveryNPuzzles && msSinceLast >= config.interstitialMinIntervalSeconds * 1000L
}
