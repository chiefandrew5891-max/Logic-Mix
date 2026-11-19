package io.github.chiefandrew5891max.logicmix.core

import io.github.chiefandrew5891max.logicmix.core.designsystem.ThemeMode
import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage
import io.github.chiefandrew5891max.logicmix.core.locale.LanguageResolver
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageAndThemeTest {
    @Test
    fun systemUsesFirstSupportedSystemLanguage() {
        assertEquals(AppLanguage.UKRAINIAN, LanguageResolver.effective(AppLanguage.SYSTEM, listOf("xx-YY", "uk-UA", "en")))
    }

    @Test
    fun unsupportedSystemLanguageFallsBackToEnglish() {
        assertEquals(AppLanguage.ENGLISH, LanguageResolver.effective(AppLanguage.SYSTEM, listOf("ja-JP", "ko")))
        assertEquals(AppLanguage.ENGLISH, LanguageResolver.effective(AppLanguage.SYSTEM, emptyList()))
    }

    @Test
    fun manualChoiceWinsOverSystem() {
        assertEquals(AppLanguage.GERMAN, LanguageResolver.effective(AppLanguage.GERMAN, listOf("fr")))
    }

    @Test
    fun chineseMatchesOnlySimplified() {
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.matchSystemTag("zh-CN"))
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.matchSystemTag("zh-Hans-SG"))
        assertNull(AppLanguage.matchSystemTag("zh-TW"))
        assertNull(AppLanguage.matchSystemTag("zh-Hant"))
    }

    @Test
    fun allEightLanguagesRoundTripThroughStorage() {
        val real = AppLanguage.entries.filter { it != AppLanguage.SYSTEM }
        assertEquals(8, real.size)
        real.forEach { assertEquals(it, AppLanguage.fromStored(it.storedValue)) }
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStored(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStored("garbage"))
    }

    @Test
    fun contentLanguageFollowsInterfaceWithEnglishFallback() {
        assertEquals(ContentLanguage.RU, ContentLanguage.forInterface(AppLanguage.RUSSIAN))
        assertEquals(ContentLanguage.ZH_CN, ContentLanguage.forInterface(AppLanguage.CHINESE_SIMPLIFIED))
        assertEquals(ContentLanguage.EN, ContentLanguage.forInterface(AppLanguage.SYSTEM))
    }

    @Test
    fun themeModeResolvesDarkness() {
        assertTrue(ThemeMode.SYSTEM.isDark(true))
        assertFalse(ThemeMode.SYSTEM.isDark(false))
        assertFalse(ThemeMode.LIGHT.isDark(true))
        assertTrue(ThemeMode.DARK.isDark(false))
    }

    @Test
    fun themeModeStorageDefaultsToSystem() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStored(it.storedValue)) }
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("x"))
    }
}
