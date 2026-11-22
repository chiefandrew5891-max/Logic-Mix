package com.akynex.logicmix.feature.arcade

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VictoryVariantTest {
    private val res = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    @Test fun pngAndWavAreOnePairPerColour() {
        for (v in VictoryVariant.entries) {
            assertEquals("victory_fanfare_${v.key}.wav", v.sfx.file)
            assertEquals("victory_fanfare_${v.key}", v.drawableName)
            assertTrue(File(res, "drawable-nodpi/${v.drawableName}.png").isFile)
        }
        assertEquals(3, VictoryVariant.entries.map { it.sfx }.toSet().size)
    }

    @Test fun neverRepeatsTheSameVariantTwice() {
        val r = Random(7)
        var last: VictoryVariant? = null
        repeat(500) { val n = VictoryPicker.next(last, r); assertNotEquals(last, n); last = n }
    }

    @Test fun allVariantsAreReachable() {
        val r = Random(1)
        val seen = HashSet<VictoryVariant>()
        var last: VictoryVariant? = null
        repeat(100) { last = VictoryPicker.next(last, r); seen += last!! }
        assertEquals(3, seen.size)
    }

    @Test fun overlayGeometryStaysInsideTheArtwork() {
        for (v in VictoryVariant.entries) {
            val r = v.ribbon
            assertTrue(r.left in 0f..1f && r.right in 0f..1f && r.top < r.bottom && r.left < r.right && r.bottom <= 1f)
            assertTrue(v.leftHorn.x < 0.5f && v.rightHorn.x > 0.5f)
        }
    }

    @Test fun titleFontCoversEveryLanguage() {
        assertEquals(TitleFont.ROUNDED_BLACK, TitleFont.forLanguage("ru"))
        assertEquals(TitleFont.ROUNDED_BLACK, TitleFont.forLanguage("uk"))
        assertEquals(TitleFont.SYSTEM_CJK, TitleFont.forLanguage("zh"))
        for (l in listOf("en", "it", "de", "fr", "es")) assertEquals(TitleFont.TITAN, TitleFont.forLanguage(l))
    }
}
