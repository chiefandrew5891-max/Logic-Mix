package io.github.chiefandrew5891max.logicmix.domain.crossword

import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrosswordGeneratorTest {
    private val langs get() = CrosswordPacks.supported

    @Test fun neverMoreThanTenWordsAndAlwaysValid() {
        for (lang in langs) {
            val all = CrosswordCareer.forLanguage(lang)!!
            all.forEach {
                assertEquals(it.id, emptyList<String>(), it.problems())
                assertTrue("${it.id} has ${it.entries.size}", it.entries.size in 3..CrosswordPuzzle.MAX_WORDS)
            }
            assertEquals(all.size, all.map { it.id }.toSet().size)
            assertEquals(CrosswordPacks.forLanguage(lang)!!.size + CrosswordCareer.GENERATED, all.size)
        }
    }

    @Test fun wordCountsVaryAndAreNotARamp() {
        for (lang in langs) {
            val counts = CrosswordCareer.forLanguage(lang)!!.drop(CrosswordPacks.forLanguage(lang)!!.size).map { it.entries.size }
            assertTrue("$lang: ${counts.toSet()}", counts.toSet().size >= 4)
            assertTrue("$lang never exceeds 10", counts.max() <= 10)
            val rises = counts.zipWithNext().count { (a, b) -> b > a }
            val falls = counts.zipWithNext().count { (a, b) -> b < a }
            assertTrue("$lang rises=$rises falls=$falls", rises > 2 && falls > 2)
        }
    }

    @Test fun mostGeneratedLevelsReachTheirTarget() {
        for (lang in langs) {
            val classic = CrosswordPacks.forLanguage(lang)!!.size
            val generated = CrosswordCareer.forLanguage(lang)!!.drop(classic)
            val real = generated.count { it.seed != 0L }
            assertTrue("$lang generated $real of ${generated.size}", real >= generated.size - 3)
        }
    }

    @Test fun seedIsReproducible() {
        val a = CrosswordGenerator.generate(ContentLanguage.EN, "t", 2, 99L, 6)
        val b = CrosswordGenerator.generate(ContentLanguage.EN, "t", 2, 99L, 6)
        assertEquals(a, b)
        assertNotEquals(a, CrosswordGenerator.generate(ContentLanguage.EN, "t", 2, 100L, 6))
    }

    @Test fun requestsAboveTheCapAreClamped() {
        val p = CrosswordGenerator.generate(ContentLanguage.RU, "t", 5, 7L, 50)
        assertTrue(p == null || p.entries.size <= 10)
    }

    @Test fun targetWordsStayInRange() {
        for (s in 0L until 50) for (i in 0 until 25) assertTrue(CrosswordGenerator.targetWords(i, 25, s) in 3..10)
    }

    @Test fun noLanguageSubstitution() {
        assertNull(CrosswordCareer.forLanguage(ContentLanguage.ZH_CN))
        for (lang in langs) {
            val letters = CrosswordPacks.forLanguage(lang)!!.flatMap { it.entries }.flatMap { it.answer.toList() }.toSet()
            CrosswordCareer.forLanguage(lang)!!.flatMap { it.entries }.forEach { e -> assertTrue("$lang ${e.answer}", e.answer.all { it in letters }) }
        }
    }

    @Test fun validatorRejectsElevenWords() {
        val base = CrosswordPacks.forLanguage(ContentLanguage.EN)!!.first()
        val many = base.copy(entries = List(11) { base.entries[it % base.entries.size] })
        assertFalse(many.problems().isEmpty())
    }

    @Test fun savedGameSurvivesRestoreAndDictionaryChange() {
        val p = CrosswordCareer.forLanguage(ContentLanguage.EN)!!.last()
        val g = CrosswordGame(p).select(p.solution.keys.first()).input('A')
        val restored = CrosswordGame.restore(p, g.encode())
        assertEquals(g.letters, restored.letters)
        assertTrue(CrosswordGame.restore(p.copy(dictionary = "builtin-2"), g.encode()).letters.isEmpty())
        assertEquals(g.letters, CrosswordGame.restore(p, g.encode().substringBeforeLast(";")).letters)
    }

    @Test fun regeneratingGivesTheSamePuzzleAsSaved() {
        val a = CrosswordCareer.forLanguage(ContentLanguage.DE)!!.last()
        val b = CrosswordGenerator.generate(ContentLanguage.DE, a.id, a.difficulty, a.seed, a.entries.size)
        if (a.seed != 0L) assertEquals(a.entries, b!!.entries)
    }
}
