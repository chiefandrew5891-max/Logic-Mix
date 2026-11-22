package io.github.chiefandrew5891max.logicmix.domain.crossword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrosswordViewportTest {
    private val base = Viewport().withSizes(viewW = 400f, viewH = 300f, contentW = 800f, contentH = 800f)

    @Test fun fitShowsTheWholeGridAndResetIsNatural() {
        val fit = base.fit()
        assertEquals(300f / 800f, fit.scale, 1e-6f)
        assertFalse(fit.canPan.and(fit.contentH * fit.scale > fit.viewH + 1f))
        assertEquals(1f, base.reset().scale, 0f)
        assertTrue(base.reset().canPan)
    }

    @Test fun zoomKeepsFocusPointFixedAndIsLimited() {
        val v = base.reset().panBy(-100f, -100f)
        val cx = v.toContentX(200f)
        val cy = v.toContentY(150f)
        val z = v.zoomAt(200f, 150f, 1.5f)
        assertEquals(cx, z.toContentX(200f), 1e-3f)
        assertEquals(cy, z.toContentY(150f), 1e-3f)
        assertEquals(Viewport.MAX_SCALE, v.zoomAt(0f, 0f, 100f).scale, 0f)
        assertEquals(base.minScale, v.zoomAt(0f, 0f, 0.0001f).scale, 1e-6f)
    }

    @Test fun panIsClampedToContent() {
        val v = base.reset().panBy(10_000f, 10_000f)
        assertEquals(0f, v.tx, 0f)
        assertEquals(0f, v.ty, 0f)
        val w = base.reset().panBy(-10_000f, -10_000f)
        assertEquals(400f - 800f, w.tx, 0f)
        assertEquals(300f - 800f, w.ty, 0f)
    }

    @Test fun smallContentStaysCentredAndCannotPan() {
        val small = Viewport().withSizes(400f, 300f, 200f, 100f).reset()
        assertFalse(small.canPan)
        assertEquals(100f, small.tx, 0f)
        assertEquals(100f, small.ty, 0f)
    }

    @Test fun cellHitTestFollowsZoomAndPan() {
        // 5x5 grid, 40px cells, 4px gaps => pitch 44
        val v = Viewport().withSizes(200f, 200f, 220f, 220f).reset()
        assertEquals(0, v.cellAt(v.tx + 5f, v.ty + 5f, 40f, 4f, 5))
        assertNull(v.cellAt(v.tx + 41f, v.ty + 5f, 40f, 4f, 5)) // gap
        val z = v.zoomAt(100f, 100f, 2f)
        val px = z.tx + 2 * 44f * z.scale + 10f
        val py = z.ty + 3 * 44f * z.scale + 10f
        assertEquals(3 * 5 + 2, z.cellAt(px, py, 40f, 4f, 5))
        assertNull(z.cellAt(-5000f, 0f, 40f, 4f, 5))
    }

    @Test fun restoreRejectsGarbageAndClamps() {
        assertEquals(base, Viewport.restore(listOf(Float.NaN, 0f, 0f), base))
        assertEquals(base, Viewport.restore(null, base))
        val r = Viewport.restore(Viewport.encode(base.reset().panBy(-50f, -20f)), base)
        assertEquals(1f, r.scale, 0f)
        assertEquals(-250f, r.tx, 0f)
        assertEquals(3f, Viewport.restore(listOf(99f, 0f, 0f), base).scale, 0f)
    }
}
