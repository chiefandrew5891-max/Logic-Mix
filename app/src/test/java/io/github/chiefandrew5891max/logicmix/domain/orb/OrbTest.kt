package io.github.chiefandrew5891max.logicmix.domain.orb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbTest {
    private fun board(vararg balls: Pair<Int, Int>): MutableList<Int> =
        MutableList(OrbRules.CELLS) { 0 }.also { b -> balls.forEach { (cell, color) -> b[cell] = color } }

    private fun idx(r: Int, c: Int) = r * 9 + c

    @Test
    fun newGameIsNineByNineWithFiveBallsAndThreeUpcoming() {
        val g = OrbGame.new(42)
        assertEquals(81, g.cells.size)
        assertEquals(5, g.cells.count { it != 0 })
        assertEquals(3, g.next.size)
        assertTrue(g.cells.all { it in 0..7 } && g.next.all { it in 1..7 })
        assertTrue(OrbLines.find(g.cells).isEmpty())
        assertEquals(g.encode(), OrbGame.new(42).encode())
    }

    @Test
    fun pathFindsOrthogonalRouteAroundBalls() {
        val b = board(idx(0, 0) to 1, idx(0, 1) to 2, idx(1, 0) to 2)
        assertNull(OrbPath.find(b, idx(0, 0), idx(8, 8)))
        val open = board(idx(0, 0) to 1, idx(0, 1) to 2)
        val path = OrbPath.find(open, idx(0, 0), idx(0, 2))!!
        assertEquals(idx(0, 0), path.first())
        assertEquals(idx(0, 2), path.last())
        assertTrue(path.none { it == idx(0, 1) })
        path.zipWithNext().forEach { (a, c) ->
            val d = kotlin.math.abs(a / 9 - c / 9) + kotlin.math.abs(a % 9 - c % 9)
            assertEquals(1, d)
        }
    }

    @Test
    fun cannotMoveThroughOrOntoOccupiedCellsOrDiagonally() {
        val walled = board(idx(0, 0) to 1, idx(0, 1) to 2, idx(1, 0) to 3, idx(1, 1) to 4)
        assertNull(OrbPath.find(walled, idx(0, 0), idx(5, 5)))
        assertNull(OrbPath.find(walled, idx(2, 2), idx(0, 0)))
        assertNull(OrbPath.find(walled, idx(0, 0), idx(0, 1)))
        val diag = board(idx(0, 0) to 1, idx(0, 1) to 2, idx(1, 0) to 2)
        assertNull(OrbPath.find(diag, idx(0, 0), idx(1, 1)))
    }

    @Test
    fun linesDetectedInAllFourDirectionsAndAtLeastFive() {
        val h = board().also { b -> (0 until 5).forEach { b[idx(3, it)] = 1 } }
        assertEquals(5, OrbLines.find(h).size)
        val v = board().also { b -> (0 until 5).forEach { b[idx(it, 2)] = 2 } }
        assertEquals(5, OrbLines.find(v).size)
        val d1 = board().also { b -> (0 until 6).forEach { b[idx(it, it)] = 3 } }
        assertEquals(6, OrbLines.find(d1).size)
        val d2 = board().also { b -> (0 until 5).forEach { b[idx(it, 8 - it)] = 4 } }
        assertEquals(5, OrbLines.find(d2).size)
        val four = board().also { b -> (0 until 4).forEach { b[idx(3, it)] = 1 } }
        assertTrue(OrbLines.find(four).isEmpty())
        val mixed = board().also { b -> (0 until 5).forEach { b[idx(3, it)] = if (it == 2) 2 else 1 } }
        assertTrue(OrbLines.find(mixed).isEmpty())
    }

    @Test
    fun crossingLinesAreRemovedTogetherWithoutDoubleCounting() {
        val b = board()
        (0 until 5).forEach { b[idx(4, it)] = 1 }
        (0 until 5).forEach { b[idx(it, 2)] = 1 }
        assertEquals(9, OrbLines.find(b).size)
        assertEquals(54, OrbRules.scoreFor(9))
    }

    @Test
    fun completingALineRemovesBallsScoresAndSpawnsNothing() {
        val b = board()
        (0 until 4).forEach { b[idx(0, it)] = 1 }
        b[idx(5, 4)] = 1
        b[idx(8, 8)] = 2
        val g = OrbGame.of(b, listOf(3, 3, 3))
        val m = g.move(idx(5, 4), idx(0, 4))!!
        assertEquals(5, m.clearedByMove.size)
        assertEquals(10, m.game.score)
        assertTrue(m.spawned.isEmpty())
        assertEquals(1, m.game.cells.count { it != 0 })
        assertEquals(listOf(3, 3, 3), m.game.next)
    }

    @Test
    fun noLineSpawnsThreeBallsOfTheAnnouncedColors() {
        val g = OrbGame.of(board(idx(0, 0) to 1), listOf(5, 6, 7))
        val m = g.move(idx(0, 0), idx(4, 4))!!
        assertEquals(3, m.spawned.size)
        assertEquals(listOf(5, 6, 7), m.spawned.map { m.game.cells[it] })
        assertEquals(1 + 3, m.game.cells.count { it != 0 })
        assertEquals(3, m.game.next.size)
    }

    @Test
    fun fewerThanThreeFreeCellsOnlyPlacesWhatFits() {
        val b = MutableList(OrbRules.CELLS) { (it * 3 + it / 9 * 2) % 7 + 1 }
        // Break any accidental lines by using a pattern that never repeats 5 in a row.
        assertTrue(OrbLines.find(b).isEmpty())
        b[0] = 0
        b[1] = 0
        val g = OrbGame.of(b, listOf(1, 2, 3))
        val m = g.move(idx(0, 2), idx(0, 0))!!
        assertTrue(m.spawned.size <= 2)
    }

    @Test
    fun fullBoardEndsTheGameAndRejectsMoves() {
        val b = MutableList(OrbRules.CELLS) { (it * 3 + it / 9 * 2) % 7 + 1 }
        val g = OrbGame.of(b, listOf(1, 2, 3))
        assertTrue(g.over)
        assertNull(g.move(0, 1))
    }

    @Test
    fun undoRestoresPreviousPosition() {
        val g = OrbGame.new(7)
        val from = g.cells.indexOfFirst { it != 0 }
        val to = g.cells.indexOfFirst { it == 0 }
        val m = g.move(from, to)!!
        assertTrue(m.game.canUndo)
        val back = m.game.undo()
        assertEquals(g.encode(), back.encode())
        assertFalse(back.canUndo)
    }

    @Test
    fun saveAndRestoreRoundTripAndRejectGarbage() {
        val g = OrbGame.new(99)
        val r = OrbGame.restore(g.encode())!!
        assertEquals(g.encode(), r.encode())
        val from = g.cells.indexOfFirst { it != 0 }
        val to = g.cells.indexOfFirst { it == 0 }
        assertEquals(g.move(from, to)!!.game.encode(), r.move(from, to)!!.game.encode())
        assertNull(OrbGame.restore("nope"))
        assertNull(OrbGame.restore("1;2;3"))
    }

    @Test
    fun hintOnlyReturnsMovesThatReallyCompleteALine() {
        val b = board()
        (0 until 4).forEach { b[idx(0, it)] = 1 }
        b[idx(5, 4)] = 1
        val g = OrbGame.of(b, listOf(2, 2, 2))
        val (from, to) = g.hintMove()!!
        assertNotNull(g.move(from, to))
        assertTrue(g.move(from, to)!!.clearedByMove.isNotEmpty())
        assertNull(OrbGame.of(board(idx(0, 0) to 1), listOf(2, 2, 2)).hintMove())
    }

    @Test
    fun scoreGrowsWithLineLength() {
        assertEquals(0, OrbRules.scoreFor(4))
        assertTrue(OrbRules.scoreFor(6) > OrbRules.scoreFor(5))
    }
}
