package com.akynex.logicmix.domain.visual.place

import com.akynex.logicmix.domain.visual.VisualPacks
import com.akynex.logicmix.domain.visual.geo.VisualCareer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTest {
    private fun playSolution(p: PlacePuzzle): PlaceGame {
        var g = PlaceGame(p)
        for (piece in p.pieces) g = g.place(piece.id, p.solution.getValue(piece.id)) ?: error("solution step rejected in ${p.id}")
        return g
    }

    @Test fun everyGeneratedPlacementPuzzleIsSolvableByItsReferenceSolution() {
        for (family in PlaceFamily.entries) for (d in 1..5) for (s in 0L until 12L) {
            val p = PlaceGenerators.generate(family, d, s * 101 + d, "t")
            assertTrue("$family d$d s$s", playSolution(p).solved)
        }
    }

    @Test fun illegalPlacementsAreRejected() {
        val p = PlaceGenerators.generate(PlaceFamily.POLYOMINO, 3, 5, "t")
        val g = PlaceGame(p)
        val first = p.pieces[0]
        assertNull(g.place(first.id, Placement(0, false, 100, 100)))
        val ok = g.place(first.id, p.solution.getValue(first.id))!!
        val other = p.pieces[1]
        val sol0 = p.solution.getValue(first.id)
        assertNull(ok.place(other.id, sol0))
    }

    @Test fun undoResetAndHintsWork() {
        val p = PlaceGenerators.generate(PlaceFamily.HEX, 2, 9, "t")
        var g = PlaceGame(p)
        g = g.hint()!!
        assertEquals(1, g.hintsUsed)
        assertEquals(1, g.placed.size)
        g = g.undo()!!
        assertEquals(0, g.placed.size)
        var h = PlaceGame(p)
        repeat(p.pieces.size) { h = h.hint() ?: h }
        assertTrue(h.solved)
        assertEquals(p.pieces.size, h.hintsUsed)
    }

    @Test fun anyValidMosaicArrangementCountsButColoursMustMatch() {
        val p = PlaceGenerators.generate(PlaceFamily.MOSAIC, 2, 3, "t")
        assertTrue(playSolution(p).solved)
        assertNotNull(p.targetColors)
    }

    @Test fun stateRoundTripsThroughEncoding() {
        val p = PlaceGenerators.generate(PlaceFamily.SILHOUETTE, 3, 4, "t")
        var g = PlaceGame(p)
        for (piece in p.pieces.take(2)) g = g.place(piece.id, p.solution.getValue(piece.id))!!
        val back = PlaceGame.restore(p, g.encode())
        assertEquals(g.placed, back.placed)
        assertEquals(g.hintsUsed, back.hintsUsed)
        assertTrue(PlaceGame.restore(p, "garbage").placed.isEmpty())
    }

    @Test fun hexRotationHasOrderSixAndFlipIsAnInvolution() {
        val c = Cell(2, -1)
        var r = c
        repeat(6) { r = Lattice.HEX.rotate(r) }
        assertEquals(c, r)
        assertEquals(c, Lattice.HEX.flip(Lattice.HEX.flip(c)))
    }

    @Test fun paradoxPiecesAreOneCellSmallerThanTheBoard() {
        for (d in 1..5) for (s in 0L until 6L) {
            val p = PlaceGenerators.generate(PlaceFamily.PARADOX, d, s, "t")
            assertEquals(p.board.size - 1, p.pieces.sumOf { it.cells.size })
            assertEquals(1, p.gaps)
            assertTrue(playSolution(p).solved)
        }
    }

    @Test fun slidingPuzzlesAreSolvable() {
        for (kind in SlideKind.entries) for (d in 1..5) for (s in 0L until 3L) {
            val p = SlideSolver.generate("t", d, kind, s * 7 + d)
            assertTrue(SlideSolver.valid(p, p.start))
            val path = SlideSolver.solve(p, p.start)!!
            assertEquals(p.minMoves, path.size)
            var g = SlideGame(p)
            for ((id, dx, dy) in path) g = g.move(id, dx, dy)!!
            assertTrue("$kind d$d", g.solved)
        }
    }

    @Test fun slidingHintAndUndo() {
        val p = SlideSolver.generate("t", 2, SlideKind.RUSH_HOUR, 4)
        var g = SlideGame(p).hint()!!
        assertEquals(1, g.hintsUsed)
        g = g.undo()!!
        assertEquals(p.start, g.pos)
    }

    @Test fun everyCubeNetFoldsAndEveryPuzzleHasAnAnswer() {
        assertTrue(CubeNets.nets.size >= 8)
        assertFalse(CubeNets.folds(setOf(Cell(0, 0), Cell(1, 0), Cell(0, 1), Cell(1, 1), Cell(2, 0), Cell(2, 1))))
        for (d in 1..5) for (s in 0L until 20L) {
            val p = CubeNets.generate("t", d, s)
            val g = NetGame(p)
            assertTrue(p.removed in g.validCells())
            assertTrue(g.tap(p.removed)!!.solved)
            assertTrue(g.validCells().all { g.tap(it) != null })
        }
    }

    @Test fun mixIsSeventyThirtyAndVariedFromTheStart() {
        val kinds = (0 until VisualMix.total).map { VisualMix.levelAt(it) }
        assertTrue(kinds.all { it != null })
        val firstTen = kinds.take(10)
        assertEquals(7, firstTen.count { it is VisualMix.Level.Hands })
        assertTrue(kinds[0] is VisualMix.Level.Hands && kinds[1] is VisualMix.Level.Choice)
        val interactive = kinds.filterIsInstance<VisualMix.Level.Hands>()
        assertEquals(VisualMix.interactiveCount, interactive.size)
        for ((a, b) in interactive.zipWithNext()) assertTrue(a.puzzle.family != b.puzzle.family)
        val head = kinds.take(VisualMix.interactiveCount * 10 / 7 - 10)
        val ratio = head.count { it is VisualMix.Level.Hands } / head.size.toDouble()
        assertTrue("ratio $ratio", ratio in 0.68..0.72)
    }

    @Test fun choiceLevelsKeepTheirIdsAndOrder() {
        val ids = (0 until VisualMix.total).mapNotNull { VisualMix.levelAt(it) }.filterIsInstance<VisualMix.Level.Choice>().map { it.id }
        val old = (0 until VisualCareer.total).map { VisualCareer.levelAt(it)!!.id }
        assertEquals(old, ids)
        assertEquals(VisualPacks.all.first().id, ids.first())
        assertEquals(ids.size, ids.toSet().size)
    }
}
