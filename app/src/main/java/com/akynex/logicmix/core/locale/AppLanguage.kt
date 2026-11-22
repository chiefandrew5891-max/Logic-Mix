package com.akynex.logicmix.core.locale

import java.util.Locale

enum class AppLanguage(val storedValue: String, val tag: String?) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    UKRAINIAN("uk", "uk"),
    RUSSIAN("ru", "ru"),
    ITALIAN("it", "it"),
    SPANISH("es", "es"),
    GERMAN("de", "de"),
    FRENCH("fr", "fr"),
    CHINESE_SIMPLIFIED("zh-CN", "zh-CN");

    companion object {
        val DEFAULT = ENGLISH

        fun fromStored(value: String?): AppLanguage = entries.firstOrNull { it.storedValue == value } ?: SYSTEM

        /** Supported language matching a system locale tag, or null if the tag is unsupported. */
        fun matchSystemTag(tag: String): AppLanguage? {
            val locale = Locale.forLanguageTag(tag)
            return when (locale.language) {
                "zh" -> CHINESE_SIMPLIFIED.takeIf { isSimplifiedChinese(locale) }
                else -> entries.firstOrNull { it != SYSTEM && it.tag == locale.language }
            }
        }

        private fun isSimplifiedChinese(locale: Locale): Boolean = when {
            locale.script.isNotEmpty() -> locale.script == "Hans"
            locale.country.isNotEmpty() -> locale.country in setOf("CN", "SG")
            else -> true
        }
    }
}

object LanguageResolver {
    /**
     * Concrete UI language. A manual choice wins; "system" takes the first supported language
     * from the system preference list and falls back to English.
     */
    fun effective(selection: AppLanguage, systemTags: List<String>): AppLanguage {
        if (selection != AppLanguage.SYSTEM) return selection
        return systemTags.firstNotNullOfOrNull(AppLanguage::matchSystemTag) ?: AppLanguage.DEFAULT
    }
}
