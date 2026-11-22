package com.akynex.logicmix.core.locale

import android.content.res.Resources
import com.akynex.logicmix.core.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

object SystemLocales {
    fun tags(): List<String> {
        val locales = Resources.getSystem().configuration.locales
        return (0 until locales.size()).map { locales[it].toLanguageTag() }
    }
}

/** The concrete UI language (never SYSTEM); emits again as soon as the user changes it. */
fun SettingsRepository.effectiveLanguage(): Flow<AppLanguage> =
    language.map { LanguageResolver.effective(it, SystemLocales.tags()) }.distinctUntilChanged()
