package io.github.chiefandrew5891max.logicmix.domain.crossword

import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage
import io.github.chiefandrew5891max.logicmix.domain.crossword.Direction.ACROSS
import io.github.chiefandrew5891max.logicmix.domain.crossword.Direction.DOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrosswordTest {
    private val ru = CrosswordPacks.resolve(ContentLanguage.RU).puzzles

    @Test fun everyShippedGridIsValid() {
        CrosswordPacks.all().forEach { assertEquals(it.id, emptyList<String>(), it.problems()) }
        assertEquals(CrosswordPacks.all().size, CrosswordPacks.all().map { it.id }.toSet().size)
        CrosswordPacks.supported.forEach { lang -> assertTrue(CrosswordPacks.all().count { it.language == lang } >= 5) }
    }

    @Test fun validatorRejectsBrokenGrids() {
        fun p(vararg e: CrosswordEntry) = CrosswordPuzzle("x", ContentLanguage.EN, 5, 1, e.toList())
        assertTrue(p(CrosswordEntry(0, 0, ACROSS, "CAT", "c"), CrosswordEntry(0, 1, DOWN, "OAK", "c")).problems().isNotEmpty()) // conflict
        assertTrue(p(CrosswordEntry(0, 3, ACROSS, "CAT", "c")).problems().isNotEmpty()) // out of bounds
        // CAT across, TOE down from its T, plus a parallel word making an accidental two-letter run
        assertTrue(
            p(
                CrosswordEntry(0, 0, ACROSS, "CAT", "c"), CrosswordEntry(0, 2, DOWN, "TOE", "c"),
                CrosswordEntry(1, 0, ACROSS, "AB", "c"),
            ).problems().isNotEmpty(),
        )
    }

    @Test fun wordsSharingAStartCellShareOneNumber() {
        val p = ru.first { it.id == "cw-ru-04" }
        val across = p.entries.first { it.answer == "ЛЕС" }
        val down = p.entries.first { it.answer == "ЛАМПА" }
        assertEquals(p.numberOf(across), p.numberOf(down))
    }

    @Test fun typingMovesCursorAndSharedCellHoldsOneValue() {
        val p = ru.first()
        val start = p.cells(p.entries.first { it.answer == "СЛОН" }).first()
        var g = CrosswordGame(p).select(start).input('С')
        assertEquals(start + 1, g.selected)
        g = g.input('Л')
        // ЛЕТО crosses СЛОН at the shared Л, so typing there fills both words' cell
        val shared = p.cells(p.entries.first { it.answer == "ЛЕТО" }).first()
        assertEquals('Л', g.letters[shared])
    }

    @Test fun tappingCrossingCellTogglesDirection() {
        val p = ru.first()
        val crossing = p.cells(p.entries.first { it.answer == "СЛОН" })[1]
        val g = CrosswordGame(p).select(crossing)
        val flipped = g.select(crossing)
        assertTrue(g.direction != flipped.direction)
    }

    @Test fun deleteClearsOrStepsBack() {
        val p = ru.first()
        val start = p.cells(p.entries.first { it.answer == "СЛОН" }).first()
        val g = CrosswordGame(p).select(start).input('С').input('Л')
        val back = g.delete() // cursor is on an empty cell: step back and clear
        assertEquals(start + 1, back.selected)
        assertEquals(null, back.letters[start + 1])
    }

    @Test fun checkMarksOnlyWrongLettersAndNeverReveals() {
        val p = ru.first()
        val start = p.cells(p.entries.first { it.answer == "СЛОН" }).first()
        val g = CrosswordGame(p).select(start).input('Я').checkWord()
        assertEquals(setOf(start), g.wrong)
        assertEquals(1, g.letters.size)
    }

    @Test fun solvingEveryWordCompletesTheLevel() {
        CrosswordPacks.all().forEach { p ->
            var g = CrosswordGame(p)
            p.solution.forEach { (cell, ch) -> g = g.copy(letters = g.letters + (cell to ch)) }
            assertTrue(p.id, g.solved)
        }
    }

    @Test fun hintsRevealCorrectLettersAndSurviveSaveButNotRestart() {
        val p = ru.first()
        val g = CrosswordGame(p).hintLetter()
        val cell = g.revealed.single()
        assertEquals(p.solution[cell], g.letters[cell])
        assertEquals(g, CrosswordGame.restore(p, g.encode()).copy(selected = g.selected, direction = g.direction))
        val r = g.restart()
        assertTrue(r.letters.isEmpty() && r.revealed.isEmpty())
        assertEquals(1, r.hintsUsed)
        assertNotNull(CrosswordGame(p).hintWord().selected)
    }

    @Test fun revealedLetterCannotBeOverwritten() {
        val p = ru.first()
        val g = CrosswordGame(p).hintLetter()
        val cell = g.revealed.single()
        val typed = g.copy(selected = cell).input('Я')
        assertEquals(p.solution[cell], typed.letters[cell])
        assertFalse(typed.letters.values.contains('Я'))
    }
}
