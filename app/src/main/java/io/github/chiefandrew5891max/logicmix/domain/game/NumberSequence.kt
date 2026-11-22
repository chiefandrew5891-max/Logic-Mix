package io.github.chiefandrew5891max.logicmix.domain.game

/** Number Logic puzzle families. The persisted category stays NUMBER_SEQUENCE for backward compatibility. */
enum class NumFamily {
    SEQUENCE, ORDER_OPS, PARENTHESES, MISSING_OP, PYRAMID, MATRIX, BALANCE, HIDDEN_RELATION, ATTENTION, MIXED;

    val key: String get() = name.lowercase()
}

/**
 * Classic puzzles use [sequence] only. Generated ones show [rows] of cells instead ("?" is the asked cell, "_" a
 * blank that must be deduced). When [choices] is not empty, [answer] is the index of the right choice.
 */
data class NumberSequencePuzzle(
    val id: String,
    val difficulty: Int,
    val prompt: String,
    val sequence: List<Int>,
    val answer: Int,
    val explanation: String,
    val hints: List<String> = emptyList(),
    val family: NumFamily = NumFamily.SEQUENCE,
    val rows: List<List<String>> = emptyList(),
    val choices: List<String> = emptyList(),
    val rule: String = "",
    val seed: Long = 0L,
    /** Encoded localizable task statement (see NumText); empty for classic puzzles. */
    val question: String = "",
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
            if (puzzle.rows.isEmpty() && puzzle.sequence.size < MIN_SEQUENCE_LENGTH) add("sequence is too short")
            if (puzzle.choices.isNotEmpty() && puzzle.answer !in puzzle.choices.indices) add("choice index out of range")
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
