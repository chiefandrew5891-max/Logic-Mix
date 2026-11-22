package io.github.chiefandrew5891max.logicmix.core.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import io.github.chiefandrew5891max.logicmix.core.consent.ConsentState

/**
 * Firebase-реализация. Без google-services.json FirebaseApp не инициализируется — тогда все вызовы тихо игнорируются,
 * и игра работает без аналитики.
 */
class FirebaseAnalyticsSink(private val context: Context) : AnalyticsSink {
    private val firebase: FirebaseAnalytics? by lazy {
        runCatching { if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAnalytics.getInstance(context) }.getOrNull()
    }

    override fun send(name: String, params: Map<String, Any>) {
        val fa = firebase ?: return
        val b = Bundle()
        for ((k, v) in params) when (v) {
            is String -> b.putString(k, v)
            is Long -> b.putLong(k, v)
            is Double -> b.putDouble(k, v)
            is Boolean -> b.putLong(k, if (v) 1L else 0L)   // Firebase не принимает Boolean
        }
        fa.logEvent(name, b)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        runCatching { firebase?.setAnalyticsCollectionEnabled(enabled) }
    }

    /**
     * Consent Mode: управляем только ANALYTICS_STORAGE по выбору пользователя. Рекламные сигналы (AD_STORAGE,
     * AD_USER_DATA, AD_PERSONALIZATION) остаются «denied» по умолчанию из манифеста; при согласии через Google UMP
     * (TCF) SDK получает их самостоятельно.
     */
    fun applyConsent(state: ConsentState) {
        runCatching {
            firebase?.setConsent(mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to
                    if (state.analytics) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
            ))
            firebase?.setAnalyticsCollectionEnabled(state.analytics)
        }
    }
}
