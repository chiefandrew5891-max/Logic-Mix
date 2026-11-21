package io.github.chiefandrew5891max.logicmix.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonFitTest {
    // Fake text: every letter is 0.6 of the font size wide.
    private fun width(text: String) = { font: Float -> text.length * font * 0.6f }

    @Test fun roomyButtonKeepsOriginalLook() {
        val fit = ButtonFitter.fit(200f, 8f, 14f, 10.5f, textWidth = width("Hint"))
        assertEquals(ButtonFit(8f, 14f, false), fit)
    }

    @Test fun paddingShrinksFirstAndFontIsUntouched() {
        // "Подсказка" needs 75.6dp at 14sp: it does not fit with 8dp padding in 90dp, but fits with 7dp.
        val fit = ButtonFitter.fit(90f, 8f, 14f, 10.5f, textWidth = width("Подсказка"))
        assertEquals(14f, fit.fontSize, 0f)
        assertEquals(7f, fit.padding, 0.001f)
        assertFalse(fit.wrap)
    }

    @Test fun paddingNeverShrinksBelowTwentyPercent() {
        for (avail in 40..120) {
            val fit = ButtonFitter.fit(avail.toFloat(), 8f, 14f, 10f, textWidth = width("Подсказка"))
            assertTrue(fit.padding >= 8f * 0.8f - 0.001f)
        }
    }

    @Test fun fontShrinksAfterPaddingAndStopsAtMinimum() {
        val fit = ButtonFitter.fit(70f, 8f, 14f, 10.5f, textWidth = width("Подсказка"))
        assertEquals(6.4f, fit.padding, 0.001f)
        assertTrue(fit.fontSize < 14f && fit.fontSize >= 10.5f)
        assertFalse(fit.wrap)
        assertTrue(width("Подсказка")(fit.fontSize) <= 70f - 2 * fit.padding)
    }

    @Test fun whenNothingHelpsWrappingBetweenWordsIsRequested() {
        val fit = ButtonFitter.fit(30f, 8f, 14f, 10.5f, textWidth = width("Начать заново"))
        assertEquals(10.5f, fit.fontSize, 0f)
        assertTrue(fit.wrap)
    }

    @Test fun groupSharesTheTightestSettings() {
        val group = ButtonFitter.group(listOf(ButtonFit(8f, 14f, false), ButtonFit(6.4f, 12f, false), ButtonFit(7f, 13f, true)))
        assertEquals(ButtonFit(6.4f, 12f, true), group)
    }

    @Test fun everyShortLabelStaysOnOneLineWhenItFitsAtMinimum() {
        for (avail in 70..140) {
            val fit = ButtonFitter.fit(avail.toFloat(), 8f, 14f, 10.5f, textWidth = width("Подсказка"))
            if (width("Подсказка")(10.5f) <= avail - 2 * 6.4f) assertFalse(fit.wrap)
        }
    }
}
