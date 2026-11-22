package io.github.chiefandrew5891max.logicmix.core.consent

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Google User Messaging Platform. Форма показывается только там, где она нужна (например, ЕЭЗ/Великобритания).
 * Любая ошибка или отказ = реклама не запрашивается, игра продолжает работать.
 */
class UmpConsentManager {
    fun gather(activity: Activity, onResult: (canRequestAds: Boolean, personalized: Boolean) -> Unit) {
        runCatching {
            val info = UserMessagingPlatform.getConsentInformation(activity)
            info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    onResult(info.canRequestAds(), personalizedAllowed(activity))
                }
            }, { _ -> onResult(info.canRequestAds(), false) })
        }.onFailure { onResult(false, false) }
    }

    fun privacyOptionsRequired(activity: Activity): Boolean = runCatching {
        UserMessagingPlatform.getConsentInformation(activity).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }.getOrDefault(false)

    fun showPrivacyOptions(activity: Activity, onResult: (canRequestAds: Boolean, personalized: Boolean) -> Unit) {
        runCatching {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
                onResult(UserMessagingPlatform.getConsentInformation(activity).canRequestAds(), personalizedAllowed(activity))
            }
        }
    }

    // Персонализация разрешена, только если в TCF-строке есть согласие по целям 1–4 (purpose consents).
    private fun personalizedAllowed(activity: Activity): Boolean = runCatching {
        val p = activity.getSharedPreferences(activity.packageName + "_preferences", 0)
            .getString("IABTCF_PurposeConsents", "") ?: ""
        p.length >= 4 && p.take(4).all { it == '1' }
    }.getOrDefault(false)
}
