package io.github.chiefandrew5891max.logicmix.domain.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuTest {
    private fun validSolution(s: IntArray) =
        SudokuSolver.consistent(s) && s.all { it in 1..9 }

    @Test fun generatedPuzzlesHaveExactlyOneSolutionForEveryDifficulty() {
        for (d in SudokuDifficulty.entries) {
            val p = SudokuGenerator.generate(d, 7L)
            assertEquals(d.name, 1, SudokuSolver.countSolutions(p.givens, 5))
            assertTrue(validSolution(p.solution))
            assertTrue(p.givens.indices.all { p.givens[it] == 0 || p.givens[it] == p.solution[it] })
            assertTrue(p.givens.count { it != 0 } in d.clues..(d.clues + 8))
        }
    }

    @Test fun generationIsDeterministicAndDifficultyOrdersClues() {
        val a = SudokuGenerator.generate(SudokuDifficulty.MEDIUM, 3L)
        val b = SudokuGenerator.generate(SudokuDifficulty.MEDIUM, 3L)
        assertTrue(a.givens.contentEquals(b.givens))
        val easy = SudokuGenerator.generate(SudokuDifficulty.EASY, 3L).givens.count { it != 0 }
        val expert = SudokuGenerator.generate(SudokuDifficulty.EXPERT, 3L).givens.count { it != 0 }
        assertTrue(easy > expert)
        assertTrue(SudokuSolver.solvableBySingles(SudokuGenerator.generate(SudokuDifficulty.EASY, 3L).givens))
    }

    @Test fun solverDetectsAmbiguousAndBrokenGrids() {
        assertTrue(SudokuSolver.countSolutions(IntArray(81), 2) == 2)
        val broken = IntArray(81).also { it[0] = 5; it[1] = 5 }
        assertEquals(0, SudokuSolver.countSolutions(broken))
        assertNull(SudokuSolver.solve(broken))
    }

    private fun game() = SudokuGame(SudokuGenerator.generate(SudokuDifficulty.EASY, 11L))
    private fun SudokuGame.firstEmpty() = values.indices.first { values[it] == 0 }

    @Test fun placeEraseAndGivensAreProtected() {
        val g = game()
        val given = g.values.indices.first { g.isGiven(it) }
        assertSame(g, g.place(given, (g.values[given] % 9) + 1))
        val c = g.firstEmpty()
        val placed = g.place(c, 5)
        assertEquals(5, placed.values[c])
        assertEquals(0, placed.erase(c).values[c])
        assertSame(g, g.erase(c))
    }

    @Test fun conflictsAreDetectedInRowColumnAndBox() {
        val g = game()
        val c = g.firstEmpty()
        val peer = SudokuGeometry.peers[c].first { g.values[it] != 0 }
        val clash = g.place(c, g.values[peer])
        assertTrue(c in clash.conflicts)
        assertFalse(clash.solved)
    }

    @Test fun filledButWrongIsNotSolvedAndCorrectIsSolved() {
        var g = game()
        val wrong = g.values.indices.fold(g) { acc, i -> if (acc.isGiven(i)) acc else acc.place(i, g.puzzle.solution[i] % 9 + 1) }
        assertTrue(wrong.values.all { it != 0 })
        assertFalse(wrong.solved)
        g = g.values.indices.fold(g) { acc, i -> if (acc.isGiven(i)) acc else acc.place(i, g.puzzle.solution[i]) }
        assertTrue(g.solved)
        assertTrue(g.conflicts.isEmpty())
    }

    @Test fun notesToggleAndAutoClearOnPlace() {
        val g = game()
        val c = g.firstEmpty()
        val peer = SudokuGeometry.peers[c].first { g.values[it] == 0 }
        val noted = g.toggleNote(c, 4).toggleNote(peer, 4).toggleNote(peer, 6)
        assertEquals(1 shl 4, noted.notes[c])
        val after = noted.place(c, 4)
        assertEquals(0, after.notes[c])
        assertEquals(1 shl 6, after.notes[peer])
        assertEquals(0, noted.toggleNote(c, 4).notes[c])
    }

    @Test fun undoRedoAndReset() {
        val g = game()
        val c = g.firstEmpty()
        val a = g.place(c, 3)
        assertTrue(a.canUndo)
        val u = a.undo()
        assertEquals(0, u.values[c])
        assertEquals(3, u.redo().values[c])
        assertFalse(a.undo().place(c, 4).canRedo)
        assertTrue(a.reset().values == g.values)
        assertSame(g, g.undo())
    }

    @Test fun hintFillsSolutionDigitAndCounts() {
        val g = game()
        val c = g.firstEmpty()
        val h = g.hint(c)!!
        assertEquals(g.puzzle.solution[c], h.values[c])
        assertEquals(1, h.hintsUsed)
    }

    @Test fun encodeAndRestoreRoundTrip() {
        val g = game()
        val c = g.firstEmpty()
        val played = g.place(c, 2).toggleNote(g.values.indices.last { g.values[it] == 0 }, 7)
        val restored = SudokuGame.restore(g.puzzle, played.encode())
        assertNotNull(restored)
        assertEquals(played.values, restored!!.values)
        assertEquals(played.notes, restored.notes)
        assertNull(SudokuGame.restore(g.puzzle, "garbage"))
    }
}
