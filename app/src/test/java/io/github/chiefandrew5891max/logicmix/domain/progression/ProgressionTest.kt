package io.github.chiefandrew5891max.logicmix.domain.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    private val thresholds = (1 until Progression.MAX_LEVEL).map { Progression.requiredXp(it) }

    @Test
    fun thresholdsMatchSpecFormulaAtKnownPoints() {
        assertEquals(100, Progression.requiredXp(1))
        assertEquals(150, Progression.requiredXp(2))
        assertEquals(200, Progression.requiredXp(3))
        assertEquals(1000, Progression.requiredXp(12))
    }

    @Test
    fun thresholdsAreStrictlyIncreasingMultiplesOf50() {
        assertEquals(98, thresholds.size)
        assertTrue(thresholds.zipWithNext().all { (a, b) -> b > a })
        assertTrue(thresholds.all { it % 50 == 0 })
    }

    @Test
    fun noThresholdExistsOutsideLevels1To98() {
        listOf(0, 99, 100).forEach {
            val failed = runCatching { Progression.requiredXp(it) }.isFailure
            assertTrue("level $it", failed)
        }
    }

    @Test
    fun xpBelowThresholdKeepsLevel() {
        assertEquals(LevelProgress(1, 99), Progression.addXp(LevelProgress(1, 0), 99))
    }

    @Test
    fun exactThresholdLevelsUpWithZeroRemainder() {
        assertEquals(LevelProgress(2, 0), Progression.addXp(LevelProgress(1, 0), 100))
    }

    @Test
    fun remainderCarriesOverAndMultipleLevelsCanBeGainedAtOnce() {
        // 100 (L1) + 150 (L2) = 250, remainder 30 into level 3.
        assertEquals(LevelProgress(3, 30), Progression.addXp(LevelProgress(1, 0), 280))
    }

    @Test
    fun existingXpIntoLevelIsIncluded() {
        assertEquals(LevelProgress(2, 10), Progression.addXp(LevelProgress(1, 90), 20))
    }

    @Test
    fun levelNeverExceeds99AndXpKeepsAccumulating() {
        val total = thresholds.sum()
        assertEquals(LevelProgress(99, 0), Progression.addXp(LevelProgress(1, 0), total))
        assertEquals(LevelProgress(99, 500), Progression.addXp(LevelProgress(1, 0), total + 500))
    }

    @Test
    fun awardIncludesNoHintBonusOnlyWhenNoHintsUsed() {
        assertEquals(25, Progression.awardFor(0).total)
        assertEquals(20, Progression.awardFor(1).total)
    }

    @Test
    fun xpPerTaskStaysInSpecRange() {
        listOf(0, 1, 3).forEach { assertTrue(Progression.awardFor(it).total in 5..50) }
    }
}
