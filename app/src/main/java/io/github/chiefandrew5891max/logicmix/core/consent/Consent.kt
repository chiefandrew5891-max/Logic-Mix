package io.github.chiefandrew5891max.logicmix.core.consent

/**
 * Согласия пользователя. Значения по умолчанию — «не разрешено»: до выбора аналитика выключена, реклама не
 * персонализируется. Отказ — штатное состояние, игра работает полностью.
 */
data class ConsentState(
    val analytics: Boolean = false,
    val adPersonalization: Boolean = false,
    /** Результат Google UMP: можно ли вообще запрашивать рекламу. */
    val canRequestAds: Boolean = false,
    val decided: Boolean = false,
)

interface ConsentStore {
    fun load(): ConsentState
    fun save(state: ConsentState)
}

class InMemoryConsentStore(var state: ConsentState = ConsentState()) : ConsentStore {
    override fun load() = state
    override fun save(state: ConsentState) { this.state = state }
}

class ConsentController(private val store: ConsentStore, private val onChange: (ConsentState) -> Unit = {}) {
    @Volatile var state: ConsentState = runCatching { store.load() }.getOrDefault(ConsentState())
        private set

    val analyticsAllowed: Boolean get() = state.analytics
    val adsAllowed: Boolean get() = state.canRequestAds

    private fun update(f: (ConsentState) -> ConsentState) {
        state = f(state)
        runCatching { store.save(state) }
        onChange(state)
    }

    fun setAnalytics(granted: Boolean) = update { it.copy(analytics = granted, decided = true) }
    fun setAdPersonalization(granted: Boolean) = update { it.copy(adPersonalization = granted, decided = true) }

    /** Вызывается после UMP; без согласия реклама не запрашивается, это не ошибка. */
    fun onUmpResult(canRequestAds: Boolean, personalization: Boolean) =
        update { it.copy(canRequestAds = canRequestAds, adPersonalization = personalization && canRequestAds) }

    fun revokeAll() = update { ConsentState(decided = true, canRequestAds = it.canRequestAds) }
}
