package com.akynex.logicmix.domain.arcade

import com.akynex.logicmix.domain.content.GameCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcadeGoalsTest {
    private fun stage(ordinal: Int, seed: Long) = ArcadeStage(GameCategory.ORB_LINES, ordinal + 1, seed, ordinal)

    @Test fun firstStageIsTheEasiestAndGoalsStaySmall() {
        assertEquals(2, ArcadeGoals.orbLines(stage(0, 5)))
        assertEquals(150, ArcadeGoals.twoDotsScore(stage(0, 5)))
        for (seed in 1L..200L) for (n in 1..6) {
            assertTrue(ArcadeGoals.orbLines(stage(n, seed)) in 2..3)
            assertTrue(ArcadeGoals.twoDotsScore(stage(n, seed)) in 150..250)
        }
    }

    @Test fun goalsAreDeterministicAndVaryBetweenStages() {
        assertEquals(ArcadeGoals.orbLines(stage(3, 9)), ArcadeGoals.orbLines(stage(3, 9)))
        assertTrue((1L..50L).map { ArcadeGoals.twoDotsScore(stage(2, it)) }.toSet().size > 1)
    }
}
