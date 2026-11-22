package com.akynex.logicmix.domain.wordsearch

import com.akynex.logicmix.core.locale.AppLanguage
import com.akynex.logicmix.domain.content.CategoryAvailability
import com.akynex.logicmix.domain.content.ContentLanguage
import com.akynex.logicmix.domain.content.GameCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val WORD_LANGUAGES = ContentLanguage.entries.filter { it != ContentLanguage.ZH_CN }

class WordSearchGeneratorTest {
    @Test fun everyLanguageHasItsOwnDictionaryAndChineseHasNone() {
        WORD_LANGUAGES.forEach { assertNotNull(it.tag, WordDictionaries.load(it)) }
        assertNull(WordDictionaries.load(ContentLanguage.ZH_CN))
    }

    @Test fun dictionariesContainOnlyLettersOfTheirLanguage() {
        val cyrillic = Regex("[А-ЯЄІЇҐ]+")
        listOf(ContentLanguage.UK, ContentLanguage.RU).forEach { lang ->
            WordDictionaries.load(lang)!!.core.forEach { assertTrue("${lang.tag}: $it", cyrillic.matches(it)) }
        }
        listOf(ContentLanguage.EN, ContentLanguage.IT, ContentLanguage.FR).forEach { lang ->
            WordDictionaries.load(lang)!!.core.forEach { assertTrue("${lang.tag}: $it", Regex("[A-Z]+").matches(it)) }
        }
    }

    @Test fun everyLevelOfEveryLanguageIsGeneratedAndVerified() {
        WORD_LANGUAGES.forEach { lang ->
            val dictionary = WordDictionaries.load(lang)!!
            val ids = mutableSetOf<String>()
            for (index in 0 until WordSearchLevels.count) {
                val level = WordSearchLevels.build(lang, index, dictionary)
                val spec = WordSearchLevels.spec(index)
                assertTrue("${level.id}: ${WordSearchVerifier.errors(level)}", WordSearchVerifier.errors(level).isEmpty())
                assertEquals(spec.size * spec.size, level.letters.length)
                assertEquals(spec.wordCount, level.placements.size)
                assertTrue(ids.add(level.id))
                level.mainWords.forEach { assertTrue("${level.id}: $it", it in dictionary.core) }
            }
        }
    }

    @Test fun mainWordsAreSpelledAlongOrthogonalPathsWithoutReusedCells() {
        val dictionary = WordDictionaries.load(ContentLanguage.EN)!!
        val level = WordSearchLevels.build(ContentLanguage.EN, WordSearchLevels.count - 1, dictionary)
        val used = mutableSetOf<Cell>()
        level.placements.forEach { p ->
            assertEquals(p.word, level.wordOf(p.path))
            assertEquals(p.path.size, p.path.toSet().size)
            p.path.zipWithNext().forEach { (a, b) -> assertTrue(a.isAdjacentTo(b)) }
            p.path.forEach { assertTrue(used.add(it)) }
        }
    }

    @Test fun sameSeedGivesTheSameLevelAndLanguagesDiffer() {
        val en = WordDictionaries.load(ContentLanguage.EN)!!
        assertEquals(WordSearchLevels.build(ContentLanguage.EN, 4, en), WordSearchLevels.build(ContentLanguage.EN, 4, en))
        val itDictionary = WordDictionaries.load(ContentLanguage.IT)!!
        val a = WordSearchLevels.build(ContentLanguage.EN, 0, en)
        val b = WordSearchLevels.build(ContentLanguage.IT, 0, itDictionary)
        assertTrue(a.id != b.id && a.seed != b.seed && a.mainWords != b.mainWords)
    }
}

class WordSearchRulesTest {
    // C A T
    // X O X
    // D O G
    private val level = WordSearchLevel(
        id = "ws-en-test", language = ContentLanguage.EN, index = 0, size = 3, seed = 1L, difficulty = 1,
        letters = "CATXOXDOG",
        placements = listOf(
            Placement("CAT", listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2))),
            Placement("DOG", listOf(Cell(2, 0), Cell(2, 1), Cell(2, 2))),
        ),
    )
    private val dictionary = WordDictionary(ContentLanguage.EN, listOf("CAT", "DOG"), listOf("TAO"))

    private fun check(vararg cells: Cell, main: Set<String> = emptySet(), extra: Set<String> = emptySet()) =
        WordSearchChecker.check(level, dictionary, cells.toList(), main, extra)

    @Test fun diagonalAndRepeatedCellsAreInvalidPaths() {
        assertFalse(WordSearchRules.isValidPath(listOf(Cell(0, 0), Cell(1, 1)), 3))
        assertFalse(WordSearchRules.isValidPath(listOf(Cell(0, 0), Cell(0, 1), Cell(0, 0)), 3))
        assertFalse(WordSearchRules.isValidPath(listOf(Cell(0, 2), Cell(0, 3)), 3))
        assertTrue(WordSearchRules.isValidPath(listOf(Cell(0, 2), Cell(0, 1), Cell(1, 1)), 3))
    }

    @Test fun selectionIgnoresDiagonalsAndRevisitsAndAllowsBacktracking() {
        var path = listOf(Cell(0, 0))
        path = WordSearchSelection.extend(path, Cell(1, 1))
        assertEquals(listOf(Cell(0, 0)), path)
        path = WordSearchSelection.extend(path, Cell(0, 1))
        path = WordSearchSelection.extend(path, Cell(1, 1))
        assertEquals(3, path.size)
        assertEquals(path, WordSearchSelection.extend(path, null))
        assertEquals(path, WordSearchSelection.extend(path, Cell(1, 1)))
        path = WordSearchSelection.extend(path, Cell(0, 1))
        assertEquals(listOf(Cell(0, 0), Cell(0, 1)), path)
    }

    @Test fun mainWordIsAcceptedOnceAndExtraWordFromDictionaryOnce() {
        assertEquals(WordCheck.Main("CAT"), check(Cell(0, 0), Cell(0, 1), Cell(0, 2)))
        assertEquals(WordCheck.Duplicate("CAT"), check(Cell(0, 0), Cell(0, 1), Cell(0, 2), main = setOf("CAT")))
        assertEquals(WordCheck.Extra("TAO"), check(Cell(0, 2), Cell(0, 1), Cell(1, 1)))
        assertEquals(WordCheck.Duplicate("TAO"), check(Cell(0, 2), Cell(0, 1), Cell(1, 1), extra = setOf("TAO")))
    }

    @Test fun sameCellsMayFormDifferentWordsIncludingReversedOnes() {
        // GOD is not a placed word; reading DOG backwards along the same cells gives GOD.
        val withGod = WordDictionary(ContentLanguage.EN, listOf("CAT", "DOG"), listOf("GOD"))
        val reversed = listOf(Cell(2, 2), Cell(2, 1), Cell(2, 0))
        assertEquals(WordCheck.Main("DOG"), check(Cell(2, 0), Cell(2, 1), Cell(2, 2), main = emptySet()))
        // Cells of an already found main word stay selectable for another word.
        assertEquals(
            WordCheck.Extra("GOD"),
            WordSearchChecker.check(level, withGod, reversed, setOf("DOG"), emptySet()),
        )
        assertEquals(
            WordCheck.Duplicate("GOD"),
            WordSearchChecker.check(level, withGod, reversed, setOf("DOG"), setOf("GOD")),
        )
        // A cell may be shared by two different routes, just not twice inside one route.
        assertTrue(WordSearchRules.isValidPath(listOf(Cell(0, 1), Cell(1, 1), Cell(2, 1)), 3))
        assertFalse(WordSearchRules.isValidPath(listOf(Cell(0, 1), Cell(1, 1), Cell(0, 1)), 3))
    }

    @Test fun unknownShortAndInvalidInputsAreRejected() {
        assertEquals(WordCheck.Unknown("TAC"), check(Cell(0, 2), Cell(0, 1), Cell(0, 0)))
        assertEquals(WordCheck.TooShort, check(Cell(0, 0), Cell(0, 1)))
        assertEquals(WordCheck.InvalidPath, check(Cell(0, 0), Cell(1, 1), Cell(2, 2)))
    }

    @Test fun verifierRejectsBrokenLevels() {
        val broken = level.copy(placements = listOf(Placement("CAT", listOf(Cell(0, 0), Cell(1, 1), Cell(0, 2)))))
        assertFalse(WordSearchVerifier.errors(broken).isEmpty())
    }
}

class CategoryAvailabilityTest {
    @Test fun wordSearchIsHiddenOnlyForChinese() {
        assertFalse(CategoryAvailability.isAvailable(GameCategory.WORD_SEARCH, AppLanguage.CHINESE_SIMPLIFIED))
        AppLanguage.entries.filter { it != AppLanguage.CHINESE_SIMPLIFIED }.forEach {
            assertTrue(it.name, CategoryAvailability.isAvailable(GameCategory.WORD_SEARCH, it))
        }
    }

    @Test fun categoryReturnsWhenSwitchingBackFromChinese() {
        assertTrue(GameCategory.WORD_SEARCH in CategoryAvailability.available(AppLanguage.ITALIAN))
        assertTrue(GameCategory.WORD_SEARCH !in CategoryAvailability.available(AppLanguage.CHINESE_SIMPLIFIED))
        assertTrue(GameCategory.WORD_SEARCH in CategoryAvailability.available(AppLanguage.ENGLISH))
    }
}
