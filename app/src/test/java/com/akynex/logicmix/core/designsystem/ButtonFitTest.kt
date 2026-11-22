package com.akynex.logicmix.core.designsystem

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

class LogoPlayerGeometryTest {
    private val g = LogoPlayerGeometry

    @Test fun buttonsAreSymmetricOnOneAxis() {
        assertEquals(0.5f, g.playPause.cx, 0f)
        assertEquals(g.playPause.cx - g.previous.cx, g.next.cx - g.playPause.cx, 1e-6f)
        assertTrue(g.previous.cy == g.playPause.cy && g.next.cy == g.playPause.cy)
        assertTrue(g.playPause.diameter > g.previous.diameter)
    }

    @Test fun fitRectKeepsAspectAndCentersInsideLetterboxedContainer() {
        val r = g.fit(1000f, 1000f)
        assertEquals(1000f, r.width, 0.01f)
        assertEquals(1000f / g.ASPECT, r.height, 0.01f)
        assertEquals((1000f - r.height) / 2f, r.top, 0.01f)
        val tall = g.fit(1000f, 200f)
        assertEquals(200f * g.ASPECT, tall.width, 0.01f)
        assertEquals(0f, tall.top, 0.01f)
    }

    @Test fun touchTargetsNeverOverlapAndReach48dpWhenThereIsRoom() {
        for (w in listOf(240f, 270f, 328f, 420f)) {
            val touch = g.touchSize(w, 0.165f * w / g.ASPECT)
            assertTrue(touch <= g.SIDE_DX * w + 0.01f || touch == 0.165f * w / g.ASPECT)
        }
        assertEquals(48f, g.touchSize(420f, 20f), 0f)
    }

    @Test fun buttonsStayInsidePlateAboveTheLights() {
        assertTrue(g.playPause.cy + g.playPause.diameter / 2 < 0.87f)
        assertTrue(g.previous.cx - g.previous.diameter / g.ASPECT / 2 > 0.2f)
    }
}
