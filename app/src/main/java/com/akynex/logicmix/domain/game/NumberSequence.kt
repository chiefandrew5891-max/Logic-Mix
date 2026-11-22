package com.akynex.logicmix.domain.game

data class NumberSequencePuzzle(
    val id: String,
    val difficulty: Int,
    val prompt: String,
    val sequence: List<Int>,
    val answer: Int,
    val explanation: String,
    val hints: List<String> = emptyList(),
)

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val errors: List<String>) : ValidationResult
}

sealed interface SolveResult {
    data object Correct : SolveResult
    data object Incorrect : SolveResult
    data object InvalidInput : SolveResult
}

object NumberSequenceEngine {
    const val MIN_SEQUENCE_LENGTH = 3
    const val MIN_DIFFICULTY = 1
    const val MAX_DIFFICULTY = 100

    fun validateDefinition(puzzle: NumberSequencePuzzle): ValidationResult {
        val errors = buildList {
            if (puzzle.id.isBlank()) add("id is blank")
            if (puzzle.difficulty !in MIN_DIFFICULTY..MAX_DIFFICULTY) add("difficulty out of range")
            if (puzzle.prompt.isBlank()) add("prompt is blank")
            if (puzzle.explanation.isBlank()) add("explanation is blank")
            if (puzzle.sequence.size < MIN_SEQUENCE_LENGTH) add("sequence is too short")
            if (puzzle.hints.any { it.isBlank() }) add("hint is blank")
        }
        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    fun checkAnswer(puzzle: NumberSequencePuzzle, input: String): SolveResult {
        val value = parseInteger(input) ?: return SolveResult.InvalidInput
        return if (value == puzzle.answer) SolveResult.Correct else SolveResult.Incorrect
    }

    // Accepts surrounding spaces, a leading "+", and the Unicode minus sign.
    fun parseInteger(input: String): Int? {
        val normalized = input.trim().replace('\u2212', '-').removePrefix("+")
        if (normalized.isEmpty()) return null
        return normalized.toIntOrNull()
    }

    fun getHint(puzzle: NumberSequencePuzzle, hintsUsed: Int): String? =
        puzzle.hints.getOrNull(hintsUsed)
}
