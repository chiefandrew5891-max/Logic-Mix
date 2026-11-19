package io.github.chiefandrew5891max.logicmix.feature.home

import io.github.chiefandrew5891max.logicmix.domain.progression.PlayerProgress
import io.github.chiefandrew5891max.logicmix.domain.progression.Progression
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateTest {
    @Test
    fun primaryButtonStartsWithoutSessionAndContinuesWithOne() {
        assertEquals(PrimaryAction.START, HomeStateMapper.primaryAction(false))
        assertEquals(PrimaryAction.CONTINUE, HomeStateMapper.primaryAction(true))
    }

    @Test
    fun newPlayerStateIsRealInitialProgress() {
        val state = HomeStateMapper.map(PlayerProgress.INITIAL, streak = 0, hasActiveSession = false)
        assertEquals(1, state.level)
        assertEquals(0, state.xpIntoLevel)
        assertEquals(Progression.requiredXp(1), state.xpForNextLevel)
        assertEquals(0f, state.xpFraction, 0f)
        assertEquals(PrimaryAction.START, state.primaryAction)
    }

    @Test
    fun midLevelProgressUsesB1Thresholds() {
        val progress = PlayerProgress.fromTotalXp(120)
        val state = HomeStateMapper.map(progress, streak = 3, hasActiveSession = true)
        assertEquals(progress.level, state.level)
        assertEquals(progress.xpIntoLevel, state.xpIntoLevel)
        assertEquals(Progression.requiredXp(progress.level), state.xpForNextLevel)
        assertEquals(progress.xpIntoLevel.toFloat() / Progression.requiredXp(progress.level), state.xpFraction, 0.0001f)
        assertEquals(3, state.streak)
        assertEquals(PrimaryAction.CONTINUE, state.primaryAction)
    }

    @Test
    fun maxLevelHasNoNextThresholdAndFullBar() {
        val state = HomeStateMapper.map(PlayerProgress(Int.MAX_VALUE, Progression.MAX_LEVEL, 0), 0, false)
        assertNull(state.xpForNextLevel)
        assertEquals(1f, state.xpFraction, 0f)
    }
}

class StringResourceParityTest {
    private fun keys(file: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { (nodes.item(it) as org.w3c.dom.Element).getAttribute("name") }.toSet()
    }

    @Test
    fun everyLanguageTranslatesEveryString() {
        val res = File("src/main/res")
        val base = keys(File(res, "values/strings.xml"))
        assertTrue(base.isNotEmpty())
        listOf("uk", "ru", "it", "es", "de", "fr", "zh-rCN").forEach {
            assertEquals("values-$it", base, keys(File(res, "values-$it/strings.xml")))
        }
    }
}
