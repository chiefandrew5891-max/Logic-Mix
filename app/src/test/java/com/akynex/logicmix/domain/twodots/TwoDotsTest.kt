package com.akynex.logicmix.domain.twodots

import com.akynex.logicmix.feature.twodots.TwoDotsGeometry
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoDotsTest {
    /** (row + col) % 5: orthogonal neighbours always differ, so this board has no pair at all. */
    private fun base(vararg same: Pair<Int, Int>): List<Int> =
        List(36) { (it / 6 + it % 6) % 5 }.toMutableList().also { b -> same.forEach { (i, c) -> b[i] = c } }

    private fun game(cells: List<Int>, mode: TwoDotsMode = TwoDotsMode.CLASSIC) = TwoDotsGame(mode, 1L, 7L, cells)

    @Test fun generatesSixBySixWithFiveColorsAndAMoveForEverySeed() {
        for (seed in 0L..200L) {
            val cells = TwoDotsRules.generate(Random(seed))
            assertEquals(36, cells.size)
            assertTrue(cells.all { it in 0..4 })
            assertTrue(TwoDotsRules.hasMove(cells))
        }
        assertEquals(TwoDotsRules.generate(Random(5)), TwoDotsRules.generate(Random(5)))
        assertEquals(TwoDotsGame.new(TwoDotsMode.CLASSIC, 9L), TwoDotsGame.new(TwoDotsMode.CLASSIC, 9L))
    }

    @Test fun connectsAdjacentSameColorOnly() {
        val cells = base(1 to 0)
        assertEquals(listOf(0, 1), TwoDotsRules.extend(cells, listOf(0), 1))
        assertEquals(listOf(0), TwoDotsRules.extend(base(), listOf(0), 1))
    }

    @Test fun diagonalIsRejected() {
        val cells = base(7 to 0)
        assertEquals(listOf(0), TwoDotsRules.extend(cells, listOf(0), 7))
        assertFalse(TwoDotsRules.isValidChain(cells, listOf(0, 7)))
    }

    @Test fun dotCannotBeReusedExceptToCloseTheLoop() {
        val cells = base(0 to 0, 1 to 0, 2 to 0, 6 to 0, 7 to 0, 8 to 0)
        assertEquals(listOf(0, 1, 2, 8, 7), TwoDotsRules.extend(cells, listOf(0, 1, 2, 8), 7))
        assertEquals(listOf(0, 1, 2, 8, 7), TwoDotsRules.extend(cells, listOf(0, 1, 2, 8, 7), 1))
        assertFalse(TwoDotsRules.isValidChain(cells, listOf(0, 1, 2, 1)))
    }

    @Test fun stepBackUndoesTheLastSegment() {
        val cells = base(0 to 0, 1 to 0, 2 to 0)
        assertEquals(listOf(0, 1), TwoDotsRules.extend(cells, listOf(0, 1, 2), 1))
        assertEquals(listOf(0), TwoDotsRules.extend(cells, listOf(0, 1), 0))
    }

    @Test fun squareLoopIsClosedAndBrokenOnesAreNot() {
        val cells = base(0 to 0, 1 to 0, 6 to 0, 7 to 0)
        val path = TwoDotsRules.extend(cells, listOf(0, 1, 7, 6), 0)
        assertEquals(listOf(0, 1, 7, 6, 0), path)
        assertTrue(TwoDotsRules.isClosed(cells, path))
        assertTrue(TwoDotsRules.isValidChain(cells, path))
        assertEquals(path, TwoDotsRules.extend(cells, path, 1))
        assertFalse(TwoDotsRules.isClosed(base(0 to 0, 1 to 0, 7 to 0), listOf(0, 1, 7, 6, 0)))
        assertFalse(TwoDotsRules.isClosed(cells, listOf(0, 1, 0)))
        assertFalse(TwoDotsRules.isClosed(cells, listOf(0, 1, 7, 6)))
    }

    @Test fun playRemovesOnlySelectedDotsAndSpendsOneMove() {
        val g = game(base(1 to 0))
        val move = g.play(listOf(0, 1))!!
        assertEquals(setOf(0, 1), move.removed)
        assertFalse(move.closed)
        assertEquals(19, move.game.movesLeft)
        assertEquals(1, move.game.moves)
        assertEquals(20, move.game.score)
        assertEquals(36, move.game.cells.size)
    }

    @Test fun invalidGesturesSpendNothing() {
        val g = game(base(1 to 0))
        assertNull(g.play(listOf(0)))
        assertNull(g.play(emptyList()))
        assertNull(g.play(listOf(0, 7)))
        assertNull(g.play(listOf(0, 2)))
    }

    @Test fun loopClearsEveryDotOfThatColor() {
        val cells = base(0 to 0, 1 to 0, 6 to 0, 7 to 0)
        val all = cells.indices.filter { cells[it] == 0 }.toSet()
        val move = game(cells).play(listOf(0, 1, 7, 6, 0))!!
        assertTrue(move.closed)
        assertEquals(all, move.removed)
        assertEquals(all.size * 10 + 100, move.gained)
        assertEquals(19, move.game.movesLeft)
    }

    @Test fun gravityKeepsOrderAndFillsFromTheTop() {
        val cells = base()
        val result = TwoDotsRules.collapse(cells, setOf(12, 24), Random(1))
        val column = (0 until 6).map { result.cells[it * 6] }
        assertEquals(listOf(0, 1, 3, 0), column.drop(2))
        assertEquals(listOf(2, 2, 1, 0), (2..5).map { result.drop[it * 6] })
        assertEquals(setOf(0, 6), result.fresh)
        for (c in 1 until 6) for (r in 0 until 6) assertEquals(cells[r * 6 + c], result.cells[r * 6 + c])
    }

    @Test fun scoring() {
        assertEquals(20, TwoDotsRules.score(2, 2, false))
        assertEquals(40, TwoDotsRules.score(4, 4, false))
        assertEquals(70, TwoDotsRules.score(5, 5, false))
        assertEquals(130, TwoDotsRules.score(8, 8, false))
        assertEquals(20, TwoDotsRules.lengthBonus(7))
        assertEquals(50, TwoDotsRules.lengthBonus(12))
        assertEquals(240, TwoDotsRules.score(14, 5, true))
    }

    @Test fun classicEndsAfterTwentyMovesAndEndlessNever() {
        var g = TwoDotsGame.new(TwoDotsMode.CLASSIC, 3L)
        repeat(20) {
            assertFalse(g.over)
            val hint = TwoDotsRules.findHint(g.cells)!!
            g = g.play(hint.path)!!.game
        }
        assertTrue(g.over)
        assertEquals(0, g.movesLeft)
        assertNull(g.play(TwoDotsRules.findHint(g.cells)!!.path))

        var e = TwoDotsGame.new(TwoDotsMode.ENDLESS, 3L)
        repeat(40) { e = e.play(TwoDotsRules.findHint(e.cells)!!.path)!!.game }
        assertFalse(e.over)
        assertEquals(20, e.movesLeft)
        assertEquals(40, e.moves)
    }

    @Test fun restartGivesAFreshGameAndKeepsMode() {
        var g = TwoDotsGame.new(TwoDotsMode.CLASSIC, 3L).withHint()
        g = g.play(TwoDotsRules.findHint(g.cells)!!.path)!!.game
        val fresh = TwoDotsGame.new(g.mode, 99L)
        assertEquals(0, fresh.score)
        assertEquals(20, fresh.movesLeft)
        assertEquals(0, fresh.hintsUsed)
        assertEquals(TwoDotsMode.CLASSIC, fresh.mode)
    }

    @Test fun saveAndRestoreContinuesIdentically() {
        var g = TwoDotsGame.new(TwoDotsMode.CLASSIC, 11L).withHint()
        g = g.play(TwoDotsRules.findHint(g.cells)!!.path)!!.game
        val back = TwoDotsGame.restore(g.encode())!!
        assertEquals(g, back)
        val path = TwoDotsRules.findHint(g.cells)!!.path
        assertEquals(g.play(path)!!.game, back.play(path)!!.game)
        assertNull(TwoDotsGame.restore("garbage"))
        assertNull(TwoDotsGame.restore(g.encode().replace("td1", "td0")))
        assertNull(TwoDotsGame.restore(g.encode().dropLast(1)))
    }

    @Test fun hintPrefersLoopThenLongestChain() {
        val loop = TwoDotsRules.findHint(base(0 to 0, 1 to 0, 6 to 0, 7 to 0))!!
        assertTrue(loop.closed)
        assertTrue(TwoDotsRules.isValidChain(base(0 to 0, 1 to 0, 6 to 0, 7 to 0), loop.path))

        val cells = base(0 to 0, 1 to 0, 2 to 0, 3 to 0, 35 to 4, 34 to 4)
        val hint = TwoDotsRules.findHint(cells)!!
        assertFalse(hint.closed)
        assertEquals(setOf(0, 1, 2, 3), hint.path.toSet())
        assertTrue(TwoDotsRules.isValidChain(cells, hint.path))
        assertNull(TwoDotsRules.findHint(base()))
        assertEquals(2, TwoDotsRules.findHint(base(1 to 0))!!.path.size)
    }

    @Test fun xpIsOncePerGameAndOnlyForClassic() {
        val classic = game(base()).copy(score = 300)
        assertTrue(classic.xpReady)
        assertFalse(game(base()).copy(score = 290).xpReady)
        assertFalse(game(base(), TwoDotsMode.ENDLESS).copy(score = 5000).xpReady)
        val restored = TwoDotsGame.restore(classic.encode())!!
        assertEquals(classic.xpId, restored.xpId)
    }

    @Test fun boardWithoutMovesIsRepairedWithoutSpendingAnything() {
        val dead = base()
        assertFalse(TwoDotsRules.hasMove(dead))
        val fixed = TwoDotsRules.ensureMove(dead, Random(4))
        assertTrue(TwoDotsRules.hasMove(fixed))
        assertEquals(36, fixed.size)
        val playable = base(1 to 0)
        assertEquals(playable, TwoDotsRules.ensureMove(playable, Random(4)))
    }

    @Test fun hitTestingUsesTheInscribedCircle() {
        val side = 600f
        for (i in 0 until 36) {
            val c = TwoDotsGeometry.center(i, side)
            assertEquals(i, TwoDotsGeometry.cellAt(c.x, c.y, side))
        }
        val o = TwoDotsGeometry.origin(7, side)
        assertNull(TwoDotsGeometry.cellAt(o.x + 1f, o.y + 1f, side))
        assertNull(TwoDotsGeometry.cellAt(-5f, 10f, side))
    }
}
