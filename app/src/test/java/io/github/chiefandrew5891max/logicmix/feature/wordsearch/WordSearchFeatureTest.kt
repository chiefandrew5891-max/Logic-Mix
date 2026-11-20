package io.github.chiefandrew5891max.logicmix.feature.wordsearch

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import io.github.chiefandrew5891max.logicmix.core.data.ProgressRepository
import io.github.chiefandrew5891max.logicmix.core.data.WordSearchStore
import io.github.chiefandrew5891max.logicmix.core.database.LogicMixDatabase
import io.github.chiefandrew5891max.logicmix.core.database.Profile
import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage
import io.github.chiefandrew5891max.logicmix.domain.content.GameCategory
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage
import io.github.chiefandrew5891max.logicmix.domain.game.AttemptOutcome
import io.github.chiefandrew5891max.logicmix.domain.game.GameMode
import io.github.chiefandrew5891max.logicmix.domain.progression.PlayerProgress
import io.github.chiefandrew5891max.logicmix.domain.progression.Progression
import io.github.chiefandrew5891max.logicmix.domain.wordsearch.WordDictionaries
import io.github.chiefandrew5891max.logicmix.domain.wordsearch.WordSearchLevels
import io.github.chiefandrew5891max.logicmix.domain.wordsearch.WordSearchRewards
import io.github.chiefandrew5891max.logicmix.feature.home.HomeStateMapper
import io.github.chiefandrew5891max.logicmix.feature.settings.languageOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class WordSearchStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() { scope.cancel() }

    private fun store() = WordSearchStore(
        PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("w.preferences_pb").also { it.delete() } },
    )

    private fun level(language: ContentLanguage, index: Int = 0) =
        WordSearchLevels.build(language, index, WordDictionaries.load(language)!!)

    @Test fun progressIsKeptSeparatelyForEachLanguage() = runBlocking {
        val s = store()
        val en = level(ContentLanguage.EN)
        val it = level(ContentLanguage.IT)
        s.begin(en, 10L)
        s.begin(it, 20L)
        s.addWord(en, en.mainWords.first(), main = true)
        s.addWord(en, "SEA", main = false)
        s.setHintsUsed(en, 2)
        s.setCurrentIndex(ContentLanguage.EN, 1)

        val english = s.session(ContentLanguage.EN, 0)!!
        assertEquals(setOf(en.mainWords.first()), english.mainFound)
        assertEquals(setOf("SEA"), english.extraFound)
        assertEquals(2, english.hintsUsed)
        val italian = s.session(ContentLanguage.IT, 0)!!
        assertTrue(italian.mainFound.isEmpty() && italian.extraFound.isEmpty())
        assertEquals(0, italian.hintsUsed)
        assertEquals(0, s.currentIndex(ContentLanguage.IT))
        assertEquals(1, s.currentIndex(ContentLanguage.EN))
    }

    @Test fun reopeningALevelRestoresTheSessionWithoutResettingIt() = runBlocking {
        val s = store()
        val level = level(ContentLanguage.DE)
        assertNull(s.session(ContentLanguage.DE, 0))
        s.begin(level, 100L)
        s.addWord(level, level.mainWords.first(), main = true)
        s.complete(level, 25)
        val restored = s.begin(level, 999L)
        assertEquals(100L, restored.startedAt)
        assertEquals(level.id, restored.levelId)
        assertEquals(level.seed, restored.seed)
        assertTrue(restored.completed)
        assertEquals(25, restored.xpAwarded)
        assertEquals(1, restored.mainFound.size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WordSearchRewardTest {
    private val pid = "local"

    @Test fun levelRewardIsPaidOnceAndEachLanguageHasItsOwnLevel() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, LogicMixDatabase::class.java).build()
        try {
            val repo = ProgressRepository(db) { 1_000L }
            db.profileDao().insert(Profile(pid, "Player", "NORMAL", null, 0L))
            repo.ensureProgress(pid)
            suspend fun solve(id: String, hints: Int) =
                repo.recordAttempt(pid, id, GameMode.CAREER, AttemptOutcome.CORRECT, 0L, 10L, hints)

            val first = solve(WordSearchLevels.levelId(ContentLanguage.EN, 0), 0)
            val reopened = solve(WordSearchLevels.levelId(ContentLanguage.EN, 0), 0)
            val italian = solve(WordSearchLevels.levelId(ContentLanguage.IT, 0), 1)

            assertEquals(Progression.awardFor(0).total, first.xpAwarded)
            assertEquals(0, reopened.xpAwarded)
            assertEquals(Progression.awardFor(1).total, italian.xpAwarded)
            assertEquals(
                PlayerProgress.fromTotalXp(first.xpAwarded + italian.xpAwarded).totalXp,
                repo.getProgress(pid).totalXp,
            )

            val before = repo.getProgress(pid).totalXp
            val levelId = WordSearchLevels.levelId(ContentLanguage.EN, 0)
            val xp = WordSearchRewards.EXTRA_WORD_XP
            assertEquals(2, xp)
            assertEquals(xp, repo.awardExtraWord(pid, levelId, "SEA", xp))
            assertEquals(0, repo.awardExtraWord(pid, levelId, "SEA", xp))
            assertEquals(0, repo.awardExtraWord(pid, levelId, "sea", xp))
            assertEquals(xp, repo.awardExtraWord(pid, levelId, "SKY", xp))
            assertEquals(xp, repo.awardExtraWord(pid, WordSearchLevels.levelId(ContentLanguage.EN, 1), "SEA", xp))
            assertEquals(before + 3 * xp, repo.getProgress(pid).totalXp)
        } finally {
            db.close()
        }
    }
}

class WordSearchUiLogicTest {
    private val level = WordSearchLevels.build(ContentLanguage.EN, 0, WordDictionaries.load(ContentLanguage.EN)!!)

    @Test fun restoredStateHighlightsOnlyMainWordsAndNeverExtraRoutes() {
        val main = level.mainWords.first()
        val restored = WordSearchUiState(
            loading = false, level = level, mainFound = setOf(main), extraFound = setOf("SEA"),
        )
        assertEquals(listOf(main), restored.foundPlacements.map { it.word })
        assertTrue(restored.flash.isEmpty())
    }

    @Test fun languageListNeverRepeatsTheSelectedLanguageAndKeepsOrder() {
        AppLanguage.entries.forEach { selected ->
            val options = languageOptions(selected)
            assertFalse(selected in options)
            assertEquals(AppLanguage.entries.size - 1, options.size)
            assertEquals(AppLanguage.entries.filter { it != selected }, options)
        }
    }

    @Test fun previousLanguageReturnsToItsPlaceAfterChoosingAnother() {
        assertTrue(AppLanguage.ITALIAN in languageOptions(AppLanguage.SYSTEM))
        assertFalse(AppLanguage.ITALIAN in languageOptions(AppLanguage.ITALIAN))
        assertEquals(languageOptions(AppLanguage.SYSTEM), AppLanguage.entries.drop(1))
    }

    @Test fun homeStateReflectsCategoryAvailability() {
        val hidden = HomeStateMapper.map(PlayerProgress.INITIAL, 0, false, emptySet())
        assertFalse(GameCategory.WORD_SEARCH in hidden.categories)
        val shown = HomeStateMapper.map(PlayerProgress.INITIAL, 0, false)
        assertTrue(GameCategory.WORD_SEARCH in shown.categories)
    }
}
