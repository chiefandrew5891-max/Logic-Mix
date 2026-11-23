package com.akynex.logicmix.domain.progression

import com.akynex.logicmix.domain.content.GameCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelUpGateTest {
    private class Memory(override var celebratedLevel: Int? = null) : CelebratedLevelStorage

    @Test fun firstObservationOnlyRecordsTheLevelSoExistingPlayersAreNotCelebrated() {
        val m = Memory()
        assertNull(LevelUpGate(m).onLevel(7))
        assertEquals(7, m.celebratedLevel)
    }

    @Test fun crossingAThresholdCelebratesExactlyOnce() {
        val gate = LevelUpGate(Memory(1))
        assertNull(gate.onLevel(1))
        assertEquals(2, gate.onLevel(2))
        assertNull(gate.onLevel(2))
    }

    @Test fun restartDoesNotReplayAShownLevelAndSeveralLevelsGiveOneScene() {
        val m = Memory(1)
        assertEquals(4, LevelUpGate(m).onLevel(4))
        assertNull(LevelUpGate(m).onLevel(4)) // new gate = new process, same storage
        assertEquals(5, LevelUpGate(m).onLevel(5))
    }
}

class XpConfigTest {
    private fun total(category: GameCategory, tier: Int = 0, hints: Int = 0) = Progression.awardFor(category, tier, hints).total

    @Test fun roughlyFiveOrdinaryPuzzlesMoveFromLevelOneToTwo() {
        val ordinary = listOf(GameCategory.NUMBER_SEQUENCE, GameCategory.MATCHSTICK, GameCategory.WORD_LOGIC, GameCategory.VISUAL_LOGIC)
        val perPuzzle = ordinary.map { total(it, hints = 1) } // no no-hint bonus, easy tier
        val need = Progression.requiredXp(1)
        assertTrue(perPuzzle.all { need / it in 4..6 })
        assertEquals(20, total(GameCategory.MATCHSTICK, hints = 1))
        assertEquals(25, total(GameCategory.MATCHSTICK))
    }

    @Test fun difficultyAndCategoryRaiseTheAwardGently() {
        assertTrue(total(GameCategory.SUDOKU, tier = 2) > total(GameCategory.SUDOKU, tier = 1))
        assertTrue(total(GameCategory.ORB_LINES) < total(GameCategory.MATCHSTICK))
        val max = GameCategory.entries.maxOf { total(it, tier = 2) }
        assertTrue("a puzzle never pays more than a third of level 1->2 plus change", max <= 45)
        assertEquals(total(GameCategory.NUMBER_SEQUENCE, tier = 9), total(GameCategory.NUMBER_SEQUENCE, tier = 2))
    }

    @Test fun legacyAwardIsUnchanged() {
        assertEquals(25, Progression.awardFor(0).total)
        assertEquals(20, Progression.awardFor(2).total)
        assertEquals(Progression.awardFor(0).total, Progression.awardFor(null, 0, 0).total)
    }
}
