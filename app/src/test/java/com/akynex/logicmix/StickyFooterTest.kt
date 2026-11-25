package com.akynex.logicmix

import com.akynex.logicmix.core.designsystem.stickyFooterPositions
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class StickyFooterTest {
    @Test fun shortPageDocksFooterToBottom() {
        assertArrayEquals(intArrayOf(0, 110, 900), stickyFooterPositions(1000, intArrayOf(100, 200, 100), 10))
    }

    @Test fun longPageKeepsFooterAfterContent() {
        assertArrayEquals(intArrayOf(0, 110, 330), stickyFooterPositions(430, intArrayOf(100, 200, 100), 10))
    }
}
