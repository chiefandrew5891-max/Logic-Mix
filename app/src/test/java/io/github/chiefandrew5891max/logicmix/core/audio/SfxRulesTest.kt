package io.github.chiefandrew5891max.logicmix.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SfxRulesTest {
    @Test fun thereAre38UniqueWavFiles() {
        assertEquals(38, Sfx.entries.size)
        assertEquals(38, Sfx.entries.map { it.file }.toSet().size)
        assertTrue(Sfx.entries.all { it.file.startsWith("sfx_") && it.file.endsWith(".wav") })
    }

    @Test fun traceRisesThenStaysCapped() {
        assertEquals(1f, SfxPitch.traceRate(0), 0.001f)
        assertTrue(SfxPitch.traceRate(1) > SfxPitch.traceRate(0))
        assertTrue(SfxPitch.traceRate(5) > SfxPitch.traceRate(4))
        assertEquals(SfxPitch.traceRate(7), SfxPitch.traceRate(40), 0f)
        assertTrue(SfxPitch.traceRate(40) <= SfxPitch.MAX_RATE)
    }

    @Test fun comboSemitonesFollowTheTable() {
        val expected = mapOf(2 to 0, 3 to 2, 4 to 4, 5 to 5, 6 to 7, 7 to 9, 12 to 9, 1 to 0)
        expected.forEach { (m, s) -> assertEquals(s, SfxPitch.comboSemitones(m)) }
        assertEquals(1.4983f, SfxPitch.comboRate(6), 0.001f)
        assertEquals(1f, SfxPitch.comboRate(2), 0.001f)
    }

    @Test fun doubleTriggerIsDroppedButRapidDifferentLettersAreNot() {
        val gate = SfxGate()
        assertTrue(gate.allow(Sfx.LETTER_INPUT, 1000))
        assertFalse(gate.allow(Sfx.LETTER_INPUT, 1005))
        assertTrue(gate.allow(Sfx.LETTER_INPUT, 1040))
        assertTrue(gate.allow(Sfx.LETTER_DELETE, 1041))
    }

    @Test fun moveSoundsAreRateLimited() {
        val gate = SfxGate()
        assertTrue(gate.allow(Sfx.MATCHSTICK_MOVE, 0))
        assertFalse(gate.allow(Sfx.MATCHSTICK_MOVE, 50))
        assertTrue(gate.allow(Sfx.MATCHSTICK_MOVE, 130))
    }

    @Test fun uiClicksYieldToMajorRewardsAndToEachOther() {
        val gate = SfxGate()
        assertTrue(gate.allow(Sfx.BUTTON_TAP, 0))
        assertFalse(gate.allow(Sfx.CARD_SELECT, 50))
        assertTrue(gate.allow(Sfx.LEVEL_COMPLETE, 60))
        assertFalse(gate.allow(Sfx.BUTTON_TAP, 200))
        assertTrue(gate.allow(Sfx.BUTTON_TAP, 400))
    }
}
