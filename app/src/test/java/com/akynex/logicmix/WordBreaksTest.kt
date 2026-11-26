package com.akynex.logicmix

import com.akynex.logicmix.core.designsystem.breaksInsideWord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordBreaksTest {
    @Test fun breakInsideWordIsDetected() {
        assertTrue(breaksInsideWord("Ежедневное", listOf(8)))
        assertTrue(breaksInsideWord("Herausforderung", listOf(7)))
    }

    @Test fun breakAtSpaceIsFine() {
        assertFalse(breaksInsideWord("Ежедневное испытание", listOf(11)))
        assertFalse(breaksInsideWord("Ежедневное испытание", listOf(10)))
    }

    @Test fun chineseMayBreakAnywhere() {
        assertFalse(breaksInsideWord("每日挑战", listOf(2)))
    }
}
