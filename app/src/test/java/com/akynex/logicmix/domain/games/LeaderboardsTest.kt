package com.akynex.logicmix.domain.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardsTest {
    private val ids = LeaderboardIds.parse(
        """
        # comment
        leaderboard_total_xp=CgkiTOTAL
        leaderboard_arcade=
        leaderboard_sudoku = CgkiSUD
        leaderboard_unknown=zzz
        garbage line
        """.trimIndent(),
    )

    @Test fun blankAndUnknownIdsAreNotConfigured() {
        assertEquals("CgkiTOTAL", ids.idOf(LeaderboardKey.TOTAL_XP))
        assertEquals("CgkiSUD", ids.idOf(LeaderboardKey.SUDOKU))
        assertNull(ids.idOf(LeaderboardKey.ARCADE))
        assertNull(ids.idOf(LeaderboardKey.TWO_DOTS))
        assertEquals(listOf(LeaderboardKey.TOTAL_XP, LeaderboardKey.SUDOKU), ids.configured)
    }

    @Test fun emptyConfigConfiguresNothing() {
        assertTrue(LeaderboardIds.parse("").configured.isEmpty())
    }

    @Test fun plannerSendsOnlyNewPositiveScoresForConfiguredBoards() {
        val current = mapOf(LeaderboardKey.TOTAL_XP to 100L, LeaderboardKey.SUDOKU to 40L, LeaderboardKey.ARCADE to 9L, LeaderboardKey.MATCHSTICK to 0L)
        val confirmed = mapOf(LeaderboardKey.SUDOKU to 40L)
        assertEquals(listOf(LeaderboardKey.TOTAL_XP to 100L), ScorePlanner.plan(current, confirmed, ids))
    }

    @Test fun plannerResendsAfterFailureAndNeverLowers() {
        val current = mapOf(LeaderboardKey.TOTAL_XP to 100L)
        assertEquals(1, ScorePlanner.plan(current, emptyMap(), ids).size)
        assertTrue(ScorePlanner.plan(current, mapOf(LeaderboardKey.TOTAL_XP to 120L), ids).isEmpty())
    }
}
