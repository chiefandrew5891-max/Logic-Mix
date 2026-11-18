package io.github.chiefandrew5891max.logicmix.core.data

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import io.github.chiefandrew5891max.logicmix.core.database.LogicMixDatabase
import io.github.chiefandrew5891max.logicmix.core.database.LogicMixMigrations
import io.github.chiefandrew5891max.logicmix.core.database.Profile
import io.github.chiefandrew5891max.logicmix.core.database.XpTransactionEntity
import io.github.chiefandrew5891max.logicmix.domain.game.AttemptOutcome
import io.github.chiefandrew5891max.logicmix.domain.game.GameMode
import io.github.chiefandrew5891max.logicmix.domain.progression.LevelProgress
import io.github.chiefandrew5891max.logicmix.domain.progression.PlayerProgress
import io.github.chiefandrew5891max.logicmix.domain.progression.Progression
import io.github.chiefandrew5891max.logicmix.domain.progression.XpReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgressRepositoryTest {
    private val pid = "local"
    private lateinit var context: Context
    private lateinit var db: LogicMixDatabase
    private lateinit var repo: ProgressRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, LogicMixDatabase::class.java).build()
        repo = ProgressRepository(db) { 1_000L }
        runBlocking { db.profileDao().insert(profile()) }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(FILE_DB)
        context.deleteDatabase(MIGRATION_DB)
    }

    private fun profile() = Profile(pid, "Player", "NORMAL", null, 0L)

    private suspend fun solve(
        puzzle: String,
        hints: Int = 0,
        mode: GameMode = GameMode.CAREER,
        outcome: AttemptOutcome = AttemptOutcome.CORRECT,
        repository: ProgressRepository = repo,
    ) = repository.recordAttempt(pid, puzzle, mode, outcome, 100L, 600L, hints)

    @Test
    fun missingProgressIsInitialAndNotWritten() = runBlocking {
        assertEquals(PlayerProgress.INITIAL, repo.getProgress(pid))
        assertEquals(PlayerProgress.INITIAL, repo.observeProgress(pid).first())
        assertNull(db.progressDao().get(pid))
        assertTrue(repo.solvedPuzzleIds(pid).isEmpty())
    }

    @Test
    fun ensureProgressCreatesRowOnceAndKeepsXp() = runBlocking {
        repo.ensureProgress(pid)
        solve("p1")
        repo.ensureProgress(pid)
        val row = db.progressDao().get(pid)!!
        assertEquals(25, row.totalXp)
        assertEquals(1, row.level)
    }

    @Test
    fun firstSolveWithoutHintsAwards25() = runBlocking {
        val result = solve("p1")
        assertTrue(result.firstSolve)
        assertEquals(25, result.xpAwarded)
        assertEquals(25, repo.getProgress(pid).totalXp)
        assertEquals(listOf("p1"), repo.solvedPuzzleIds(pid))
    }

    @Test
    fun firstSolveWithHintAwards20() = runBlocking {
        assertEquals(20, solve("p1", hints = 1).xpAwarded)
    }

    @Test
    fun repeatSolveAwardsNothingButRecordsAttempt() = runBlocking {
        solve("p1")
        val again = solve("p1")
        assertFalse(again.firstSolve)
        assertEquals(0, again.xpAwarded)
        assertEquals(25, repo.getProgress(pid).totalXp)
        assertEquals(1, db.solvedPuzzleDao().count(pid))
        assertEquals(1, db.xpTransactionDao().count(pid))
        assertEquals(2, db.attemptDao().count(pid))
    }

    @Test
    fun incorrectAnswerOnlyRecordsAttempt() = runBlocking {
        val result = solve("p1", outcome = AttemptOutcome.INCORRECT)
        assertEquals(0, result.xpAwarded)
        assertEquals(1, db.attemptDao().count(pid))
        assertEquals(0, db.solvedPuzzleDao().count(pid))
        assertEquals(0, repo.getProgress(pid).totalXp)
    }

    @Test
    fun nonCareerModesAwardNoXp() = runBlocking {
        solve("e1", mode = GameMode.ENDLESS)
        solve("d1", mode = GameMode.DAILY)
        assertEquals(0, repo.getProgress(pid).totalXp)
        assertEquals(0, db.solvedPuzzleDao().count(pid))
        assertEquals(2, db.attemptDao().count(pid))
    }

    @Test
    fun fourSolvesReachLevel2() = runBlocking {
        repeat(4) { solve("p$it") }
        val progress = repo.getProgress(pid)
        assertEquals(2, progress.level)
        assertEquals(0, progress.xpIntoLevel)
        assertEquals(2, db.progressDao().get(pid)!!.level)
    }

    @Test
    fun levelUpCarriesRemainder() = runBlocking {
        repeat(3) { solve("p$it") }
        solve("hinted", hints = 1)
        val result = solve("last")
        assertEquals(99, result.before.totalXp)
        assertEquals(1, result.levelsGained)
        assertEquals(2, result.after.level)
        assertEquals(24, result.after.xpIntoLevel)
        val expected = Progression.addXp(
            LevelProgress(1, 0),
            result.after.totalXp,
        )
        assertEquals(expected.level, result.after.level)
    }

    @Test
    fun totalXpEqualsSumOfTransactions() = runBlocking {
        solve("a")
        solve("b", hints = 2)
        solve("a")
        solve("c", outcome = AttemptOutcome.INCORRECT)
        assertEquals(db.xpTransactionDao().totalAmount(pid), repo.getProgress(pid).totalXp)
    }

    @Test
    fun failureRollsBackWholeTransaction() = runBlocking {
        db.xpTransactionDao().insert(
            XpTransactionEntity("$pid:z:FIRST_SOLVE", pid, "z", 1, XpReason.FIRST_SOLVE, 0L),
        )
        try {
            solve("z")
            fail("expected the duplicate XP transaction to fail")
        } catch (expected: Exception) {
            // rolled back below
        }
        assertEquals(0, db.attemptDao().count(pid))
        assertEquals(0, db.solvedPuzzleDao().count(pid))
        assertNull(db.progressDao().get(pid))
    }

    @Test
    fun concurrentSubmissionsAwardOnce() = runBlocking {
        val results = (1..20).map { async(Dispatchers.Default) { solve("same") } }.awaitAll()
        assertEquals(1, results.count { it.firstSolve })
        assertEquals(25, repo.getProgress(pid).totalXp)
        assertEquals(1, db.xpTransactionDao().count(pid))
        assertEquals(20, db.attemptDao().count(pid))
    }

    @Test
    fun progressSurvivesReopeningDatabase() = runBlocking {
        fun open() = Room.databaseBuilder(context, LogicMixDatabase::class.java, FILE_DB).build()
        val first = open()
        first.profileDao().insert(profile())
        val firstRepo = ProgressRepository(first) { 1_000L }
        repeat(4) { solve("p$it", repository = firstRepo) }
        first.close()

        val second = open()
        val secondRepo = ProgressRepository(second) { 2_000L }
        val progress = secondRepo.getProgress(pid)
        assertEquals(100, progress.totalXp)
        assertEquals(2, progress.level)
        assertEquals(4, secondRepo.solvedPuzzleIds(pid).size)
        assertEquals(0, solve("p0", repository = secondRepo).xpAwarded)
        second.close()
    }

    @Test
    fun migrationFrom1To2KeepsProfile() = runBlocking {
        val helper = object : SQLiteOpenHelper(context, MIGRATION_DB, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profiles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`difficulty` TEXT NOT NULL, `difficultyLockedAtLevel` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.insert(
                    "profiles",
                    null,
                    ContentValues().apply {
                        put("id", pid)
                        put("name", "Old")
                        put("difficulty", "EASY")
                        put("createdAt", 5L)
                    },
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.close()
        helper.close()

        val migrated = Room.databaseBuilder(context, LogicMixDatabase::class.java, MIGRATION_DB)
            .addMigrations(*LogicMixMigrations.ALL)
            .build()
        assertEquals("Old", migrated.profileDao().observe(pid).first()?.name)
        val migratedRepo = ProgressRepository(migrated) { 1_000L }
        assertEquals(25, solve("p1", repository = migratedRepo).xpAwarded)
        migrated.close()
    }

    private companion object {
        const val FILE_DB = "reopen-test.db"
        const val MIGRATION_DB = "migration-test.db"
    }
}
