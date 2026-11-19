package io.github.chiefandrew5891max.logicmix.domain.game

/**
 * Bundled offline pack for the number-sequence engine. Content is language-neutral
 * (numbers and formulas), so nothing here needs translating. Hints go from the vaguer
 * (differences or ratios) to the explicit rule; the last one doubles as the explanation.
 */
object NumberSequencePack {
    private fun puzzle(id: Int, difficulty: Int, sequence: List<Int>, answer: Int, vararg hints: String) =
        NumberSequencePuzzle(
            id = "ns-%03d".format(id),
            difficulty = difficulty,
            prompt = "next",
            sequence = sequence,
            answer = answer,
            explanation = hints.last(),
            hints = hints.toList(),
        )

    val puzzles: List<NumberSequencePuzzle> = listOf(
        puzzle(1, 1, listOf(2, 4, 6, 8), 10, "2 · 2 · 2", "+2"),
        puzzle(2, 1, listOf(5, 10, 15, 20), 25, "5 · 5 · 5", "+5"),
        puzzle(3, 1, listOf(1, 3, 5, 7, 9), 11, "2 · 2 · 2 · 2", "+2"),
        puzzle(4, 2, listOf(3, 6, 12, 24), 48, "3 → 6 → 12 → 24", "×2"),
        puzzle(5, 2, listOf(1, 4, 9, 16), 25, "3 · 5 · 7", "n²"),
        puzzle(6, 2, listOf(100, 90, 80, 70), 60, "-10 · -10 · -10", "−10"),
        puzzle(7, 2, listOf(1, 2, 4, 7, 11), 16, "1 · 2 · 3 · 4", "+1, +2, +3, +4, +5"),
        puzzle(8, 3, listOf(2, 6, 18, 54), 162, "2 → 6 → 18 → 54", "×3"),
        puzzle(9, 3, listOf(1, 1, 2, 3, 5, 8), 13, "0 · 1 · 1 · 2 · 3", "a + b"),
        puzzle(10, 3, listOf(1, 8, 27, 64), 125, "7 · 19 · 37", "n³"),
        puzzle(11, 4, listOf(1, 3, 6, 10, 15), 21, "2 · 3 · 4 · 5", "n(n+1)/2"),
        puzzle(12, 4, listOf(0, 1, 3, 7, 15), 31, "1 · 2 · 4 · 8", "2ⁿ − 1"),
        puzzle(13, 5, listOf(2, 3, 5, 7, 11), 13, "1 · 2 · 2 · 4", "2, 3, 5, 7, 11, 13"),
    )

    fun firstUnsolved(solvedIds: Collection<String>): NumberSequencePuzzle? =
        puzzles.firstOrNull { it.id !in solvedIds }

    fun byId(id: String): NumberSequencePuzzle? = puzzles.firstOrNull { it.id == id }
}
