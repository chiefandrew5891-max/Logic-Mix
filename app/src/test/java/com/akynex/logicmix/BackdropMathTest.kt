package com.akynex.logicmix

import com.akynex.logicmix.core.designsystem.backdropOffsetY
import com.akynex.logicmix.core.designsystem.backdropProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class BackdropMathTest {
    @Test fun halfwayContentMovesBackgroundHalfItsTravel() {
        // content 4000, viewport 2000 -> max scroll 2000; background 3000 -> travel 1000
        val p = backdropProgress(1000, 2000)
        assertEquals(0.5f, p, 0f)
        assertEquals(-500f, backdropOffsetY(3000f, 2000f, p), 0f)
    }

    @Test fun endOfPageAlignsBackgroundBottomWithViewport() {
        assertEquals(-1000f, backdropOffsetY(3000f, 2000f, backdropProgress(2000, 2000)), 0f)
    }

    @Test fun shortPageStaysStill() {
        assertEquals(0f, backdropProgress(0, 0), 0f)
        assertEquals(0f, backdropOffsetY(3000f, 2000f, backdropProgress(0, 0)), 0f)
    }

    @Test fun neverRevealsAnEdge() {
        assertEquals(0f, backdropOffsetY(1500f, 2000f, 1f), 0f)
        assertEquals(-1000f, backdropOffsetY(3000f, 2000f, backdropProgress(5000, 2000)), 0f)
    }
}
