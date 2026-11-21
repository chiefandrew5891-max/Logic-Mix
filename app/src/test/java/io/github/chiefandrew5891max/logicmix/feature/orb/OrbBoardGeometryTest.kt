package io.github.chiefandrew5891max.logicmix.feature.orb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbBoardGeometryTest {
    private val sides = listOf(280f, 360f, 411f, 1080f)

    @Test fun allCellsFitInsideSquareWithoutOverlap() {
        for (side in sides) {
            val cell = OrbBoardGeometry.cell(side)
            assertTrue(cell > 0f)
            for (i in 0 until 81) {
                val o = OrbBoardGeometry.origin(i, side)
                assertTrue(o.x >= 0f && o.y >= 0f)
                assertTrue(o.x + cell <= side + 0.01f && o.y + cell <= side + 0.01f)
            }
            val step = OrbBoardGeometry.origin(1, side).x - OrbBoardGeometry.origin(0, side).x
            assertTrue(step >= cell)
        }
    }

    @Test fun boardIsSymmetric() {
        val side = 400f
        val cell = OrbBoardGeometry.cell(side)
        val last = OrbBoardGeometry.origin(80, side)
        assertEquals(OrbBoardGeometry.origin(0, side).x, side - (last.x + cell), 0.01f)
    }

    @Test fun cellCentersResolveToTheirCell() {
        for (side in sides) for (i in 0 until 81) {
            val c = OrbBoardGeometry.center(i, side)
            assertEquals(i, OrbBoardGeometry.cellAt(c.x, c.y, side))
        }
    }

    @Test fun edgeTapsClampToBoard() {
        assertEquals(0, OrbBoardGeometry.cellAt(-5f, -5f, 300f))
        assertEquals(80, OrbBoardGeometry.cellAt(999f, 999f, 300f))
    }
}
