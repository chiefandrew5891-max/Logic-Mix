package com.akynex.logicmix.feature.matchstick

import com.akynex.logicmix.domain.matchstick.MatchSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    private val widths = listOf(280f, 320f, 360f, 411f, 540f, 1080f)

    @Test
    fun everySlotStaysInsideTheCanvasAtAnyWidth() {
        for (width in widths) {
            val l = Layout(width)
            val sticks = (0 until MatchSlots.COUNT).map { l.slot(it) } + l.equalsSticks()
            for (s in sticks) {
                for (scale in listOf(1f, 1.12f)) {
                    val (x, y) = l.extent(s, scale)
                    assertTrue("x $x outside $width", x.start >= 0f && x.endInclusive <= width)
                    assertTrue("y $y outside ${l.height}", y.start >= 0f && y.endInclusive <= l.height)
                }
            }
        }
    }

    @Test
    fun contentIsCenteredAndProportionsDoNotDependOnWidth() {
        val a = Layout(300f)
        val b = Layout(600f)
        assertEquals(a.thick / a.w, b.thick / b.w, 1e-4f)
        val xs = (0 until MatchSlots.COUNT).flatMap { val e = a.extent(a.slot(it)).first; listOf(e.start, e.endInclusive) }
        assertEquals(xs.min(), a.width - xs.max(), 0.5f)
    }

    @Test
    fun touchOnASlotCenterPicksThatSlotAfterScaling() {
        for (width in widths) {
            val l = Layout(width)
            val all = (0 until MatchSlots.COUNT).filter { it != MatchSlots.OP_V }
            for (i in all) assertEquals(i, l.nearest(l.slot(i).center, all))
        }
    }
}
