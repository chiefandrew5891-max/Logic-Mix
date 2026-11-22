package com.akynex.logicmix.core.analytics

import com.akynex.logicmix.core.ads.AdAnalytics
import com.akynex.logicmix.core.ads.AdEv
import com.akynex.logicmix.core.ads.InterstitialPolicy
import com.akynex.logicmix.core.ads.OfferState
import com.akynex.logicmix.core.ads.RewardType
import com.akynex.logicmix.core.ads.RewardedAdController
import com.akynex.logicmix.core.ads.RewardedAdGateway
import com.akynex.logicmix.core.config.AppConfig
import com.akynex.logicmix.core.consent.ConsentController
import com.akynex.logicmix.core.consent.ConsentState
import com.akynex.logicmix.core.consent.InMemoryConsentStore
import com.akynex.logicmix.domain.content.GameCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSink(var fail: Boolean = false) : AnalyticsSink {
    val sent = mutableListOf<Pair<String, Map<String, Any>>>()
    var enabled = true
    override fun send(name: String, params: Map<String, Any>) { if (fail) error("boom"); sent += name to params }
    override fun setCollectionEnabled(enabled: Boolean) { this.enabled = enabled }
    fun names() = sent.map { it.first }
}

class FakeGateway : RewardedAdGateway {
    var load: RewardedAdGateway.LoadCallback? = null
    var show: RewardedAdGateway.ShowCallbacks? = null
    var loads = 0
    override fun load(callback: RewardedAdGateway.LoadCallback) { loads++; load = callback }
    override fun show(callbacks: RewardedAdGateway.ShowCallbacks) { show = callbacks }
}

class AnalyticsTest {
    private val ctx = GameContext(GameCategory.VISUAL_LOGIC, family = "odd_one_out", mode = "career", levelIndex = 3, contentLanguage = "ru")
    private var now = 1_000L
    private val sink = FakeSink()
    private val problems = mutableListOf<String>()
    private var allowed = true
    private val tracker = GuardedAnalyticsTracker(sink, { allowed }, { m, _ -> problems += m })
    private fun ga() = GameAnalytics(tracker) { now }

    @Test fun allEventNamesAndParamsAreValidForFirebase() {
        val g = ga()
        g.openCategory(ctx); g.selectDifficulty(ctx.copy(difficulty = "easy")); g.selectMode(ctx)
        g.puzzleStarted("p1", ctx); g.hintRequested(); g.hintUsed(); g.attemptFailed(); g.restarted(); now += 5_000
        g.completed(xp = 20); g.puzzleStarted("p2", ctx); g.skipped(); g.puzzleStarted("p3", ctx); g.leave()
        assertTrue(problems.toString(), problems.isEmpty())
        assertTrue(sink.names().all { it in Ev.game })
        assertEquals(Ev.game, sink.names().toSet())
        assertTrue(sink.sent.none { it.second.containsKey("puzzle_id") })
    }

    @Test fun completeCarriesContextDurationAttemptsHints() {
        val g = ga(); g.openCategory(ctx); g.puzzleStarted("p1", ctx); g.hintUsed(); g.attemptFailed(); now += 7_000; g.completed(15)
        val done = sink.sent.single { it.first == Ev.PUZZLE_COMPLETE }.second
        assertEquals("visual_logic", done[P.GAME_CATEGORY]); assertEquals("odd_one_out", done[P.PUZZLE_FAMILY])
        assertEquals(7_000L, done[P.DURATION_MS]); assertEquals(2L, done[P.ATTEMPT_COUNT]); assertEquals(1L, done[P.HINT_COUNT])
        assertEquals("solved", done[P.RESULT]); assertEquals("ru", done[P.CONTENT_LANGUAGE])
        assertEquals(15L, sink.sent.single { it.first == Ev.XP_EARNED }.second[P.XP])
    }

    @Test fun repeatedCallsDoNotDuplicate() {
        val g = ga()
        repeat(5) { g.openCategory(ctx); g.puzzleStarted("p1", ctx) }   // «рекомпозиция»
        g.completed(); g.completed(); g.leave(); g.leave()
        assertEquals(1, sink.names().count { it == Ev.CATEGORY_OPEN })
        assertEquals(1, sink.names().count { it == Ev.PUZZLE_START })
        assertEquals(1, sink.names().count { it == Ev.PUZZLE_COMPLETE })
        assertEquals(1, sink.names().count { it == Ev.SESSION_END })
        assertEquals(0, sink.names().count { it == Ev.PUZZLE_ABANDON })
    }

    @Test fun exitReasonsAreDistinguished() {
        val g = ga(); g.openCategory(ctx); g.puzzleStarted("a", ctx); g.puzzleStarted("b", ctx); g.leave()
        val reasons = sink.sent.filter { it.first == Ev.PUZZLE_ABANDON }.map { it.second[P.EXIT_REASON] }
        assertEquals(listOf("switched_task", "explicit_exit"), reasons)
    }

    @Test fun disabledOrNoConsentSendsNothing() {
        allowed = false
        val g = ga(); g.openCategory(ctx); g.puzzleStarted("a", ctx); g.completed(5)
        assertTrue(sink.sent.isEmpty())
    }

    @Test fun sdkFailureNeverReachesGame() {
        sink.fail = true
        val g = ga(); g.openCategory(ctx); g.puzzleStarted("a", ctx); g.completed()
        assertTrue(problems.any { it.startsWith("sink failed") })
    }

    @Test fun validatorRejectsBadEvents() {
        assertNotNull(EventValidator.validate(AnalyticsEvent("firebase_x")))
        assertNotNull(EventValidator.validate(AnalyticsEvent("1abc")))
        assertNotNull(EventValidator.validate(AnalyticsEvent("a".repeat(41))))
        assertNotNull(EventValidator.validate(AnalyticsEvent("ok", mapOf("k" to "x".repeat(101)))))
        assertNotNull(EventValidator.validate(AnalyticsEvent("ok", mapOf("k" to listOf(1)))))
        assertNotNull(EventValidator.validate(AnalyticsEvent("ok", (1..26).associate { "k$it" to "v" })))
        assertNull(EventValidator.validate(AnalyticsEvent("ok", mapOf("k" to 1L))))
        tracker.log(AnalyticsEvent("bad name")); assertTrue(sink.sent.isEmpty() && problems.size == 1)
    }

    @Test fun twoDifferentContextsAreNotMerged() {
        val g = ga(); g.openCategory(ctx); g.puzzleStarted("a", ctx.copy(generatorVersion = "2", contentPackVersion = 4)); g.skipped()
        assertEquals(4L, sink.sent.last().second[P.CONTENT_PACK_VERSION]); assertEquals("2", sink.sent.last().second[P.GENERATOR_VERSION])
    }

    // ---- Реклама ----
    private var consentAds = true
    private var config = AppConfig()
    private val gw = FakeGateway()
    private fun ctl() = RewardedAdController(gw, AdAnalytics(tracker) { config }, { config }, { consentAds }, { now })

    private fun ready(c: RewardedAdController) { c.prepare("hint"); gw.load!!.onLoaded() }

    @Test fun rewardGrantedExactlyOnceDespiteRepeatedCallbacks() {
        val c = ctl(); ready(c); var grants = 0
        assertTrue(c.accept("hint", ctx) { grants++ })
        assertFalse(c.accept("hint", ctx) { grants += 10 })   // двойной тап
        val s = gw.show!!
        s.onOpened(); s.onImpression(); s.onImpression(); s.onRewardEarned(); s.onRewardEarned(); s.onDismissed(); s.onDismissed(); s.onRewardEarned()
        assertEquals(1, grants)
        assertEquals(1, sink.names().count { it == AdEv.REWARD_GRANTED })
        assertEquals(1, sink.names().count { it == AdEv.IMPRESSION })
        assertEquals(1, sink.names().count { it == AdEv.DISMISSED })
    }

    @Test fun dismissWithoutRewardGrantsNothing() {
        val c = ctl(); ready(c); var grants = 0
        c.accept("hint", ctx) { grants++ }; gw.show!!.onOpened(); gw.show!!.onDismissed()
        assertEquals(0, grants); assertFalse(sink.names().contains(AdEv.REWARD_EARNED))
        assertEquals(false, sink.sent.last { it.first == AdEv.DISMISSED }.second["rewarded"])
    }

    @Test fun requestIsNotImpressionAndLoadFailureIsHandled() {
        val c = ctl(); c.prepare("hint")
        assertEquals(listOf(AdEv.REQUEST), sink.names()); assertEquals(OfferState.LOADING, c.state)
        gw.load!!.onFailed(3)
        assertEquals(OfferState.UNAVAILABLE, c.state); assertFalse(c.accept("hint", ctx) {})
        assertEquals(3L, sink.sent.last().second["error_code"])
        c.prepare("hint"); assertEquals(1, gw.loads)            // повтор не раньше интервала
        now += 31_000; c.prepare("hint"); assertEquals(2, gw.loads)
    }

    @Test fun noConsentOrDisabledMeansNoRequestsAndNoOffers() {
        consentAds = false; val c = ctl(); c.prepare("hint"); assertEquals(0, gw.loads); assertFalse(c.offerAllowed())
        consentAds = true; config = AppConfig(rewardedEnabled = false); assertFalse(c.offerAllowed()); c.prepare("hint"); assertEquals(0, gw.loads)
        config = AppConfig(disabledFeatures = setOf("rewarded")); assertFalse(c.offerAllowed())
    }

    @Test fun offerFrequencyIsLimited() {
        config = AppConfig(offerCooldownSeconds = 60, maxOffersPerSession = 2); val c = ctl()
        assertTrue(c.offerAllowed()); c.offerShown("hint", ctx); assertFalse(c.offerAllowed())
        now += 61_000; assertTrue(c.offerAllowed()); c.offerShown("hint", ctx); now += 61_000; assertFalse(c.offerAllowed())
    }

    @Test fun showFailureDoesNotBlockGame() {
        val c = ctl(); ready(c); c.accept("hint", ctx) {}; gw.show!!.onFailedToShow(1)
        assertEquals(OfferState.UNAVAILABLE, c.state)
    }

    @Test fun revenueNotInventedAndNotDoubleCounted() {
        val c = ctl(); ready(c); c.accept("hint", ctx) {}; gw.show!!.onPaid(1_500_000, "USD", 3)
        assertFalse(sink.names().contains(AdEv.REVENUE))      // по умолчанию доход идёт через связку AdMob↔Firebase
        config = AppConfig(logAdRevenueInApp = true)
        val c2 = ctl(); gw.show = null; ready(c2); c2.accept("hint", ctx) {}
        gw.show!!.onPaid(0, "USD", 0); gw.show!!.onPaid(2_500_000, "EUR", 2)
        val rev = sink.sent.filter { it.first == AdEv.REVENUE }
        assertEquals(1, rev.size); assertEquals(2.5, rev[0].second["value"]); assertEquals("EUR", rev[0].second["currency"])
    }

    @Test fun interstitialOnlyAtNaturalBreaks() {
        val on = AppConfig(interstitialEnabled = true)
        assertFalse(InterstitialPolicy.canShow(AppConfig(), true, true, 10, 1_000_000))
        assertFalse(InterstitialPolicy.canShow(on, true, false, 10, 1_000_000))
        assertFalse(InterstitialPolicy.canShow(on, false, true, 10, 1_000_000))
        assertFalse(InterstitialPolicy.canShow(on, true, true, 1, 1_000_000))
        assertTrue(InterstitialPolicy.canShow(on, true, true, 3, 200_000))
    }

    // ---- Remote Config / согласие ----
    @Test fun remoteConfigFallsBackToSafeDefaults() {
        assertEquals(AppConfig.DEFAULT, AppConfig.sanitize(emptyMap()))
        val bad = AppConfig.sanitize(mapOf("ads_offer_cooldown_s" to "-5", "ads_max_offers_per_session" to "9999",
            "ads_rewarded_enabled" to "maybe", "free_hints_per_puzzle" to "x", "experiment_variant" to "Bad Value!"))
        assertEquals(AppConfig.DEFAULT, bad)
        val ok = AppConfig.sanitize(mapOf("ads_interstitial_enabled" to "true", "disabled_features" to "native, bad key,two_dots",
            "free_hints_per_puzzle" to "2", "experiment_variant" to "hint_b"))
        assertTrue(ok.interstitialEnabled); assertEquals(setOf("native", "two_dots"), ok.disabledFeatures)
        assertEquals(2, ok.freeHintsPerPuzzle); assertEquals("hint_b", ok.experimentVariant)
        assertEquals(AppConfig.UNCHANGED, AppConfig.DEFAULT.freeHintsPerPuzzle)
    }

    @Test fun consentDefaultsDeniedAndRefusalIsNormal() {
        val store = InMemoryConsentStore(); val seen = mutableListOf<ConsentState>()
        val c = ConsentController(store) { seen += it }
        assertFalse(c.analyticsAllowed); assertFalse(c.adsAllowed)
        c.setAnalytics(true); c.onUmpResult(canRequestAds = true, personalization = true)
        assertTrue(c.analyticsAllowed && c.adsAllowed && c.state.adPersonalization)
        c.onUmpResult(canRequestAds = false, personalization = true); assertFalse(c.state.adPersonalization)
        c.revokeAll(); assertFalse(c.analyticsAllowed); assertEquals(c.state, store.load())
        assertEquals(ConsentState(), ConsentController(InMemoryConsentStore()).state)
        val t = GuardedAnalyticsTracker(sink, { c.analyticsAllowed }); t.log(AnalyticsEvent("x1")); assertTrue(sink.sent.isEmpty())
    }
}
