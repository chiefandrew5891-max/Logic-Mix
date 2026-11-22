package io.github.chiefandrew5891max.logicmix.core.consent

import android.content.Context
import android.content.SharedPreferences

class SharedPrefsConsentStore(context: Context) : ConsentStore {
    private val prefs: SharedPreferences = context.getSharedPreferences("consent", Context.MODE_PRIVATE)

    override fun load() = ConsentState(
        analytics = prefs.getBoolean("analytics", false),
        adPersonalization = prefs.getBoolean("ad_personalization", false),
        canRequestAds = prefs.getBoolean("can_request_ads", false),
        decided = prefs.getBoolean("decided", false),
    )

    override fun save(state: ConsentState) {
        prefs.edit()
            .putBoolean("analytics", state.analytics)
            .putBoolean("ad_personalization", state.adPersonalization)
            .putBoolean("can_request_ads", state.canRequestAds)
            .putBoolean("decided", state.decided)
            .apply()
    }
}
