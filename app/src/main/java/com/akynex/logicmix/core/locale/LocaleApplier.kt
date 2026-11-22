package com.akynex.logicmix.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleApplier {
    // "System" clears the override, so Android uses the system language or the default resources (English).
    fun apply(language: AppLanguage) {
        val wanted = language.tag.orEmpty()
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == wanted) return
        AppCompatDelegate.setApplicationLocales(
            if (wanted.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(wanted),
        )
    }
}
