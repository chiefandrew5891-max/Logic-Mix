package com.akynex.logicmix.core.arcade

import com.akynex.logicmix.domain.arcade.ArcadeStage
import com.akynex.logicmix.domain.arcade.StageReport
import com.akynex.logicmix.domain.content.GameCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArcadeBridgeTest {
    private val stage = ArcadeStage(GameCategory.SUDOKU, 1, 7L, 0)

    @Test fun reportsOnlyForTheActiveCategory() {
        val bridge = ArcadeBridge()
        val got = mutableListOf<ArcadeStage>()
        bridge.stage = stage
        bridge.onCompleted = { s, _ -> got += s }
        bridge.report(StageReport(GameCategory.SUDOKU, 0, 0, 10))
        bridge.report(StageReport(GameCategory.WORD_LOGIC, 0, 0, 10))
        assertEquals(listOf(stage), got)
    }

    @Test fun nothingHappensOutsideArcade() {
        val bridge = ArcadeBridge()
        var calls = 0
        bridge.onCompleted = { _, _ -> calls++ }
        bridge.onExhausted = { calls++ }
        assertNull(bridge.activeFor(GameCategory.SUDOKU))
        bridge.report(StageReport(GameCategory.SUDOKU, 0, 0, 10))
        bridge.exhausted(GameCategory.SUDOKU)
        assertEquals(0, calls)
    }

    @Test fun exhaustedReachesTheHostOnlyForTheActiveCategory() {
        val bridge = ArcadeBridge()
        var calls = 0
        bridge.stage = stage
        bridge.onExhausted = { calls++ }
        bridge.exhausted(GameCategory.WORD_SEARCH)
        bridge.exhausted(GameCategory.SUDOKU)
        assertEquals(1, calls)
    }
}
