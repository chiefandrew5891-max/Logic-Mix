package io.github.chiefandrew5891max.logicmix.domain.matchstick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchstickTest {
    @Test fun digitEncodingRoundTripsAndEquationsEvaluate() {
        val s = MatchSlots.encode(6, true, 4, 4)
        assertEquals("6 + 4 = 4", MatchEquation.text(s))
        assertFalse(MatchEquation.isCorrect(s))
        assertTrue(MatchEquation.isCorrect(MatchSlots.encode(6, false, 4, 2)))
        assertFalse(MatchEquation.wellFormed(1 shl 7 or MatchSlots.digitMasks[1]))
    }

    @Test fun everyPuzzleIsWellFormedWrongAndSolvableInExactlyItsMoveCount() {
        for (i in 0 until MatchPuzzles.COUNT) {
            val p = MatchPuzzles.build(i)
            assertTrue(MatchEquation.wellFormed(p.start))
            assertFalse(MatchEquation.isCorrect(p.start))
            assertEquals("level $i", p.maxMoves, MatchSolver.minMoves(p.start))
            val sol = p.solution
            assertEquals(p.maxMoves, sol.size)
            var g = MatchGame(p)
            for (m in sol) g = g.move(m.from, m.to)
            assertTrue("level $i solution works", g.solved)
        }
    }

    @Test fun difficultyRisesFromOneToTwoMoves() {
        assertEquals(1, MatchPuzzles.build(0).maxMoves)
        assertEquals(2, MatchPuzzles.build(MatchPuzzles.COUNT - 1).maxMoves)
    }

    @Test fun puzzlesAreDeterministicAndFoundById() {
        assertEquals(MatchPuzzles.build(5).start, MatchPuzzles.build(5).start)
        assertEquals(MatchPuzzles.build(5).start, MatchPuzzles.byId("matchstick-6")!!.start)
        assertNull(MatchPuzzles.byId("matchstick-999"))
        assertNull(MatchPuzzles.firstUnsolved((1..MatchPuzzles.COUNT).map { "matchstick-$it" }.toSet()))
        assertEquals(0, MatchPuzzles.firstUnsolved(emptySet())!!.index)
    }

    @Test fun movesAreValidatedAndLimited() {
        val p = MatchPuzzles.build(0)
        val g = MatchGame(p)
        val from = (0 until MatchSlots.COUNT).first { p.start shr it and 1 == 1 }
        val empty = (0 until MatchSlots.COUNT).first { p.start shr it and 1 == 0 }
        assertSame(g, g.move(empty, from))
        assertSame(g, g.move(from, from))
        val wrongTarget = (0 until MatchSlots.COUNT).first { it != from && p.start shr it and 1 == 0 && !MatchEquation.isCorrect((p.start and (1 shl from).inv()) or (1 shl it)) }
        val moved = g.move(from, wrongTarget)
        assertEquals(1, moved.movesUsed)
        assertTrue(moved.failed)
        assertEquals(0, moved.movesLeft)
        assertSame(moved, moved.move(wrongTarget, from))
    }

    @Test fun undoAndResetRestoreTheStart() {
        val p = MatchPuzzles.build(40)
        var g = MatchGame(p)
        val m = p.solution
        g = g.move(m[0].from, m[0].to)
        assertTrue(g.canUndo)
        assertEquals(p.start, g.undo().current)
        g = g.move(m[1].from, m[1].to)
        assertTrue(g.solved)
        assertEquals(p.start, g.reset().current)
        assertEquals(0, g.reset().movesUsed)
    }

    @Test fun hintPointsAtAMoveThatKeepsTheLevelSolvable() {
        val p = MatchPuzzles.build(35)
        val g = MatchGame(p)
        val hint = g.hintMove()
        assertNotNull(hint)
        val after = g.move(hint!!.from, hint.to)
        assertEquals(1, after.movesLeft)
        assertEquals(1, MatchSolver.minMoves(after.current))
    }
}
