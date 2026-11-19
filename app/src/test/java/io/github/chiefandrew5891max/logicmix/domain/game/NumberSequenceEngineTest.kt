package io.github.chiefandrew5891max.logicmix.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberSequenceEngineTest {
    private val puzzle = NumberSequencePuzzle(
        id = "number_en_000001",
        difficulty = 12,
        prompt = "What comes next?",
        sequence = listOf(2, 4, 6, 8),
        answer = 10,
        explanation = "Add 2 each time.",
        hints = listOf("Look at the difference between adjacent numbers."),
    )

    @Test
    fun validDefinitionPasses() {
        assertEquals(ValidationResult.Valid, NumberSequenceEngine.validateDefinition(puzzle))
    }

    @Test
    fun invalidDefinitionReportsAllProblems() {
        val broken = puzzle.copy(id = " ", difficulty = 0, sequence = listOf(1, 2), hints = listOf(""))
        val result = NumberSequenceEngine.validateDefinition(broken) as ValidationResult.Invalid
        assertEquals(4, result.errors.size)
    }

    @Test
    fun correctAndIncorrectAnswersAreDistinguished() {
        assertEquals(SolveResult.Correct, NumberSequenceEngine.checkAnswer(puzzle, "10"))
        assertEquals(SolveResult.Incorrect, NumberSequenceEngine.checkAnswer(puzzle, "12"))
    }

    @Test
    fun inputIsNormalized() {
        assertEquals(SolveResult.Correct, NumberSequenceEngine.checkAnswer(puzzle, "  +10 "))
        val negative = puzzle.copy(answer = -3)
        assertEquals(SolveResult.Correct, NumberSequenceEngine.checkAnswer(negative, "\u22123"))
        assertEquals(SolveResult.Correct, NumberSequenceEngine.checkAnswer(negative, "-3"))
    }

    @Test
    fun nonIntegerInputIsRejectedNotMarkedWrong() {
        listOf("", "  ", "abc", "1.5", "--3", "99999999999").forEach {
            assertEquals(it, SolveResult.InvalidInput, NumberSequenceEngine.checkAnswer(puzzle, it))
        }
    }

    @Test
    fun hintsAreRevealedInOrderThenRunOut() {
        assertTrue(NumberSequenceEngine.getHint(puzzle, 0)!!.isNotBlank())
        assertNull(NumberSequenceEngine.getHint(puzzle, 1))
    }
}
