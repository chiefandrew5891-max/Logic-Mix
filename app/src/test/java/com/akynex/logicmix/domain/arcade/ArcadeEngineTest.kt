package com.akynex.logicmix.domain.arcade

import com.akynex.logicmix.domain.content.GameCategory
import com.akynex.logicmix.domain.content.GameCategory.MATCHSTICK
import com.akynex.logicmix.domain.content.GameCategory.MINI_CROSSWORD
import com.akynex.logicmix.domain.content.GameCategory.SUDOKU
import com.akynex.logicmix.domain.content.GameCategory.WORD_SEARCH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcadeEngineTest {
    private fun report(c: GameCategory, hints: Int = 1, tier: Int? = null) = StageReport(c, hints, 0, 1000, tier)

    /** Plays [n] stages, solving each one, and returns the categories in play order. */
    private fun play(run: ArcadeRun, n: Int, stats: ArcadeStats = ArcadeStats()): Triple<ArcadeRun, ArcadeStats, List<GameCategory>> {
        var r = run
        var s = stats
        val seen = mutableListOf<GameCategory>()
        repeat(n) {
            val c = r.current.category
            seen += c
            val res = ArcadeEngine.complete(r, s, report(c))
            r = ArcadeEngine.advance(res.run); s = res.stats
        }
        return Triple(r, s, seen)
    }

    @Test fun emptySelectionCannotStart() = assertNull(ArcadeEngine.start(emptyList(), 1))

    @Test fun onlySelectedCategoriesAppearAndEachOnceInEveryCycle() {
        val chosen = listOf(MATCHSTICK, SUDOKU, WORD_SEARCH, MINI_CROSSWORD)
        for (seed in 1L..40L) {
            val seen = play(ArcadeEngine.start(chosen, seed)!!, 24).third
            assertTrue(seen.all { it in chosen })
            seen.chunked(4).forEach { assertEquals(chosen.toSet(), it.toSet()) }
            // Never the same category twice in a row, including the cycle boundary.
            seen.zipWithNext().forEach { (a, b) -> assertTrue("seed $seed", a != b) }
        }
    }

    @Test fun nineCategoriesAllRotate() {
        val seen = play(ArcadeEngine.start(GameCategory.entries, 7)!!, 18).third
        assertEquals(GameCategory.entries.toSet(), seen.take(9).toSet())
        assertEquals(GameCategory.entries.toSet(), seen.drop(9).toSet())
    }

    @Test fun singleCategoryRepeatsAndTwoCategoriesAlternate() {
        assertEquals(setOf(SUDOKU), play(ArcadeEngine.start(listOf(SUDOKU), 3)!!, 5).third.toSet())
        val two = play(ArcadeEngine.start(listOf(SUDOKU, MATCHSTICK), 3)!!, 6).third
        two.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
    }

    @Test fun rotationIsStableForTheSameSeed() {
        assertEquals(ArcadeEngine.start(GameCategory.entries, 5), ArcadeEngine.start(GameCategory.entries, 5))
    }

    @Test fun repeatedCompletionPaysOnlyOnce() {
        val run = ArcadeEngine.start(listOf(SUDOKU, MATCHSTICK), 1)!!
        val first = ArcadeEngine.complete(run, ArcadeStats(), report(run.current.category))
        val again = ArcadeEngine.complete(first.run, first.stats, report(run.current.category))
        assertTrue(first.accepted); assertFalse(again.accepted)
        assertEquals(first.run.score, again.run.score)
        assertEquals(1, again.stats.totalStages)
    }

    @Test fun reportFromAnotherCategoryIsRejected() {
        val run = ArcadeEngine.start(listOf(SUDOKU, MATCHSTICK), 1)!!
        val other = listOf(SUDOKU, MATCHSTICK).first { it != run.current.category }
        assertFalse(ArcadeEngine.complete(run, ArcadeStats(), report(other)).accepted)
    }

    @Test fun scoringFollowsTheTable() {
        val run = ArcadeEngine.start(listOf(SUDOKU), 1)!!
        val plain = ArcadeEngine.complete(run, ArcadeStats(), report(SUDOKU, hints = 2)).award!!
        assertEquals(100, plain.total)
        val clean = ArcadeEngine.complete(run, ArcadeStats(), report(SUDOKU, hints = 0, tier = 2)).award!!
        assertEquals(100 + 100 + 25, clean.total)
        assertEquals(150, ArcadeEngine.complete(run, ArcadeStats(), report(SUDOKU, hints = 1, tier = 1)).award!!.total)
        // Unknown difficulty never earns a difficulty bonus.
        assertEquals(0, ArcadeEngine.complete(run, ArcadeStats(), report(SUDOKU, 1, null)).award!!.difficultyBonus)
    }

    @Test fun streakBonusesAndAchievements() {
        var run = ArcadeEngine.start(listOf(SUDOKU), 1)!!
        var stats = ArcadeStats()
        val bonuses = mutableMapOf<Int, Int>()
        for (i in 1..10) {
            val res = ArcadeEngine.complete(run, stats, report(SUDOKU))
            bonuses[i] = res.award!!.streakBonus
            run = ArcadeEngine.advance(res.run); stats = res.stats
        }
        assertEquals(50, bonuses[3]); assertEquals(100, bonuses[5]); assertEquals(250, bonuses[10]); assertEquals(0, bonuses[4])
        assertTrue(stats.achievements.containsAll(listOf(ArcadeAchievement.STREAK_3, ArcadeAchievement.STREAK_5, ArcadeAchievement.STREAK_10)))
        assertEquals(10, run.bestStreak)
    }

    @Test fun skipEndsStreakWithoutPoints() {
        val run = ArcadeEngine.start(listOf(SUDOKU, MATCHSTICK), 1)!!
        val won = ArcadeEngine.advance(ArcadeEngine.complete(run, ArcadeStats(), report(run.current.category)).run)
        val skipped = ArcadeEngine.skip(won)
        assertEquals(0, skipped.streak); assertEquals(won.score, skipped.score)
        assertEquals(skipped, ArcadeEngine.skip(skipped))
    }

    @Test fun cycleAchievementsDependOnTheChosenSet() {
        val three = ArcadeEngine.start(listOf(SUDOKU, MATCHSTICK, WORD_SEARCH), 4)!!
        val (_, stats, _) = play(three, 3)
        assertTrue(ArcadeAchievement.FIRST_CYCLE in stats.achievements)
        assertFalse(ArcadeAchievement.FULL_SET_CYCLE in stats.achievements)
        assertTrue(ArcadeAchievement.FULL_SET_CYCLE in play(ArcadeEngine.start(GameCategory.entries, 4)!!, 9).second.achievements)
    }

    @Test fun skippedStageDoesNotCountAsCleanCycle() {
        var run = ArcadeEngine.start(listOf(SUDOKU, MATCHSTICK), 2)!!
        run = ArcadeEngine.advance(ArcadeEngine.skip(run))
        val res = ArcadeEngine.complete(run, ArcadeStats(), report(run.current.category))
        assertFalse(ArcadeAchievement.FIRST_CYCLE in res.stats.achievements)
    }

    @Test fun levelsRiseWithScoreAndRecordIsReportedOncePerRun() {
        assertEquals(1, ArcadeRules.levelOf(0)); assertEquals(2, ArcadeRules.levelOf(250)); assertEquals(3, ArcadeRules.levelOf(750))
        var run = ArcadeEngine.start(listOf(SUDOKU), 1)!!
        var stats = ArcadeStats(bestScore = 120)
        val first = ArcadeEngine.complete(run, stats, report(SUDOKU))
        assertFalse(first.achievements.contains(ArcadeAchievement.NEW_RECORD)) // 100 < 120
        run = ArcadeEngine.advance(first.run); stats = first.stats
        val second = ArcadeEngine.complete(run, stats, report(SUDOKU))
        assertTrue(second.achievements.contains(ArcadeAchievement.NEW_RECORD))
        run = ArcadeEngine.advance(second.run)
        assertFalse(ArcadeEngine.complete(run, second.stats, report(SUDOKU)).achievements.contains(ArcadeAchievement.NEW_RECORD))
        val levelUp = ArcadeEngine.complete(run.copy(score = 240), second.stats, report(SUDOKU))
        assertTrue(levelUp.levelUp)
    }

    @Test fun unavailableCategoriesAreDroppedWithoutReplacement() {
        val run = ArcadeEngine.start(listOf(SUDOKU, MINI_CROSSWORD, WORD_SEARCH), 9)!!
        val limited = ArcadeEngine.restrictTo(run, setOf(SUDOKU, WORD_SEARCH))!!
        assertEquals(setOf(SUDOKU, WORD_SEARCH), limited.categories.toSet())
        assertTrue(limited.queue.all { it in limited.categories })
        assertNull(ArcadeEngine.restrictTo(run, emptySet()))
    }

    @Test fun savedRunRoundTrips() {
        var run = ArcadeEngine.start(GameCategory.entries, 11)!!
        var stats = ArcadeStats()
        repeat(4) {
            val res = ArcadeEngine.complete(run, stats, report(run.current.category, tier = 1))
            run = ArcadeEngine.advance(res.run); stats = res.stats
        }
        run = ArcadeEngine.complete(run, stats, report(run.current.category)).run
        assertEquals(run, ArcadeCodec.decode(ArcadeCodec.encode(run)))
        assertEquals(stats, ArcadeCodec.decodeStats(ArcadeCodec.encode(stats)))
        assertNull(ArcadeCodec.decode("garbage"))
        assertNotNull(ArcadeCodec.decodeStats(null))
    }

    @Test fun stageSeedsAreStableAndDistinct() {
        val run = ArcadeEngine.start(GameCategory.entries, 5)!!
        assertEquals(run.current.seed, ArcadeEngine.start(GameCategory.entries, 5)!!.current.seed)
        assertTrue(run.current.seed != ArcadeEngine.advance(run.copy(outcome = StageOutcome.SKIPPED)).current.seed)
    }
}
