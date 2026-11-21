package io.github.chiefandrew5891max.logicmix.feature.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardGeometryTest {
    @Test
    fun cellsAreEqualAndTapsMapToAll81Cells() {
        val side = 900f
        assertEquals(100f, SudokuBoardGeometry.cell(side), 0f)
        val hit = HashSet<Int>()
        for (r in 0 until 9) for (c in 0 until 9) {
            val cell = SudokuBoardGeometry.cellAt(c * 100f + 50f, r * 100f + 50f, side)
            assertEquals(r * 9 + c, cell)
            hit += cell
        }
        assertEquals(81, hit.size)
    }

    @Test
    fun edgeTapsStayOnBoard() {
        assertEquals(0, SudokuBoardGeometry.cellAt(0f, 0f, 900f))
        assertEquals(80, SudokuBoardGeometry.cellAt(900f, 900f, 900f))
        assertEquals(0, SudokuBoardGeometry.cellAt(-5f, -5f, 900f))
    }

    @Test
    fun frameFitsInsideTheCornerRadius() {
        assertTrue(SudokuBoardGeometry.FRAME.value / 2 < SudokuBoardGeometry.CORNER.value)
        assertTrue(SudokuBoardGeometry.FRAME.value > SudokuBoardGeometry.BLOCK_LINE.value)
    }
}
