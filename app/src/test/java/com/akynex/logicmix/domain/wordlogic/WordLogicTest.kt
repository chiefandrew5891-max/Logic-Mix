package com.akynex.logicmix.domain.wordlogic

import com.akynex.logicmix.domain.content.ContentLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordLogicTest {
    private val all = WordLogicPacks.all()

    @Test fun everyPuzzleIsWellFormedAndIdsAreUnique() {
        all.forEach { assertEquals(it.id, emptyList<String>(), it.problems()) }
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test fun eachSupportedLanguageHasTenPuzzlesCoveringAllFiveTypes() {
        WordLogicPacks.supported.forEach { lang ->
            val pack = all.filter { it.language == lang }
            assertTrue(pack.size >= 10)
            assertEquals(WordLogicType.entries.toSet(), pack.map { it.type }.toSet())
        }
    }

    @Test fun unsupportedLanguageFallsBackToEnglishWithFlag() {
        val r = WordLogicPacks.resolve(ContentLanguage.ZH_CN)
        assertTrue(r.isFallback)
        assertEquals(ContentLanguage.EN, r.language)
        assertFalse(WordLogicPacks.resolve(ContentLanguage.RU).isFallback)
    }

    @Test fun answerIsCheckedByIdNotText() {
        val puzzle = WordLogicPacks.resolve(ContentLanguage.RU).puzzles.first()
        val wrongId = puzzle.options.first { it.id != puzzle.correctId }.id
        val afterWrong = WordLogicGame(puzzle).choose(wrongId)
        assertFalse(afterWrong.solved)
        assertEquals(setOf(wrongId), afterWrong.wrong)
        assertTrue(afterWrong.choose(puzzle.correctId).solved)
    }

    @Test fun hintsExplainFirstThenEliminateOnlyWrongOptions() {
        val puzzle = all.first()
        var g = WordLogicGame(puzzle).hint()
        assertTrue(g.hintShown)
        assertTrue(g.eliminated.isEmpty())
        repeat(5) { g = g.hint() }
        assertEquals(3, g.eliminated.size)
        assertFalse(puzzle.correctId in g.eliminated)
        assertTrue(g.choose(puzzle.correctId).solved)
    }

    @Test fun restartClearsAttemptButKeepsHintCounter() {
        val puzzle = all.first()
        val g = WordLogicGame(puzzle).hint().hint().choose(puzzle.options.first { it.id != puzzle.correctId && it.id !in WordLogicGame(puzzle).hint().hint().eliminated }.id)
        val r = g.restart()
        assertTrue(r.wrong.isEmpty() && r.eliminated.isEmpty() && !r.hintShown)
        assertEquals(g.hintsUsed, r.hintsUsed)
    }

    @Test fun savedStateRoundTripsAndIgnoresGarbage() {
        val puzzle = all.first()
        val g = WordLogicGame(puzzle).hint().hint().choose(puzzle.options.last { it.id != puzzle.correctId }.id)
        assertEquals(g, WordLogicGame.restore(puzzle, g.encode()))
        assertEquals(WordLogicGame(puzzle), WordLogicGame.restore(puzzle, "broken"))
    }
}
