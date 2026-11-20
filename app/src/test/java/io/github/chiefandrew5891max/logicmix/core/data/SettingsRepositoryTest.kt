package io.github.chiefandrew5891max.logicmix.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.chiefandrew5891max.logicmix.core.designsystem.ThemeMode
import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage
import io.github.chiefandrew5891max.logicmix.domain.content.GameCategory
import io.github.chiefandrew5891max.logicmix.domain.content.GameStyle
import io.github.chiefandrew5891max.logicmix.domain.content.GameStylePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var repo: SettingsRepository

    @Before fun setUp() {
        store = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("s.preferences_pb") .also { it.delete() } }
        repo = SettingsRepository(store)
    }

    @After fun tearDown() { scope.cancel() }

    @Test fun defaults() = runBlocking {
        assertEquals(AudioSettings(), repo.audio.first())
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
        assertEquals(AppLanguage.SYSTEM, repo.language.first())
    }

    @Test fun legacySoundOffMigratesToMusicAndEffects() = runBlocking {
        store.edit { it[booleanPreferencesKey("sound_enabled")] = false }
        val a = repo.audio.first()
        assertFalse(a.musicEnabled)
        assertFalse(a.effectsEnabled)
        repo.setMusicEnabled(true)
        val b = repo.audio.first()
        assertTrue(b.musicEnabled)
        assertFalse(b.effectsEnabled)
    }

    @Test fun themeAndLanguageRoundTrip() = runBlocking {
        repo.setThemeMode(ThemeMode.DARK)
        repo.setLanguage(AppLanguage.CHINESE_SIMPLIFIED)
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, repo.language.first())
    }

    @Test fun volumeIsClamped() = runBlocking {
        repo.setMusicVolume(3f)
        repo.setEffectsVolume(-1f)
        val a = repo.audio.first()
        assertEquals(1f, a.musicVolume, 0f)
        assertEquals(0f, a.effectsVolume, 0f)
    }

    @Test fun unknownStoredValuesFallBackToDefaults() = runBlocking {
        store.edit {
            it[stringPreferencesKey("theme_mode")] = "bogus"
            it[stringPreferencesKey("language")] = "xx"
        }
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
        assertEquals(AppLanguage.SYSTEM, repo.language.first())
    }

    @Test fun gameStyleDefaultsToNotOnboarded() = runBlocking {
        assertEquals(GameStylePrefs(), repo.gameStyle.first())
    }

    @Test fun confirmedGameStylePersistsWithStableIds() = runBlocking {
        repo.confirmGameStyle(GameStyle.TRAINER, trainer = setOf(GameCategory.SUDOKU, GameCategory.WORD_LOGIC))
        repo.confirmGameStyle(GameStyle.CATEGORIES, preferred = GameCategory.NUMBER_SEQUENCE)
        val saved = repo.gameStyle.first()
        assertTrue(saved.onboardingDone)
        assertEquals(GameStyle.CATEGORIES, saved.style)
        // The trainer selection is kept when only another part of the choice changes.
        assertEquals(setOf(GameCategory.SUDOKU, GameCategory.WORD_LOGIC), saved.trainerCategories)
        assertEquals(GameCategory.NUMBER_SEQUENCE, saved.preferredCategory)
        assertEquals(AppLanguage.SYSTEM, repo.language.first())
    }
}

class LanguageOptionsTest {
    @Test fun allEightLanguagesPlusSystemRoundTripThroughStorage() {
        assertEquals(9, AppLanguage.entries.size)
        assertEquals(9, AppLanguage.entries.map { it.storedValue }.toSet().size)
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromStored(it.storedValue)) }
    }
}
