package io.github.chiefandrew5891max.logicmix.domain.visual

enum class ShapeKind { CIRCLE, SQUARE, TRIANGLE, DIAMOND, ARROW, LINE }

/**
 * One cell of a visual puzzle. [color] is a palette index; [rotation] is clockwise degrees (multiples of 90);
 * [count] is the number of copies; [slot] is -1 for the centre or 0..3 for the corners clockwise from top-left.
 */
data class Tile(
    val kind: ShapeKind = ShapeKind.CIRCLE,
    val color: Int = 0,
    val rotation: Int = 0,
    val count: Int = 1,
    val slot: Int = -1,
)

/** The pattern a puzzle is built on; the hint text is chosen by it, so it needs no per-puzzle translation. */
enum class VisualRule { SHAPE, COUNT, ROTATION, COLOR, POSITION, COMBINED, MATRIX }

data class VisualOption(val id: String, val tile: Tile)

data class VisualPuzzle(
    val id: String,
    val rule: VisualRule,
    val difficulty: Int,
    /** Row-major cells; the one at [gapIndex] is hidden and must be picked from [options]. */
    val tiles: List<Tile>,
    val gapIndex: Int,
    /** Cells per row: the sequence length for a strip, 2 or 3 for a matrix. */
    val columns: Int,
    val options: List<VisualOption>,
    val correctId: String,
) {
    val answer: Tile get() = tiles[gapIndex]

    fun problems(): List<String> {
        val out = mutableListOf<String>()
        if (gapIndex !in tiles.indices) out += "gap outside the grid"
        if (options.size != 4) out += "needs 4 options"
        if (options.map { it.tile }.toSet().size != options.size) out += "two options look identical"
        if (options.count { it.tile == answer } != 1) out += "exactly one option must equal the rule's answer"
        if (options.firstOrNull { it.id == correctId }?.tile != answer) out += "correct id does not point to the answer"
        if (tiles.size % columns != 0) out += "grid is not rectangular"
        return out
    }
}

data class VisualGame(
    val puzzle: VisualPuzzle,
    val wrong: Set<String> = emptySet(),
    val eliminated: Set<String> = emptySet(),
    val hintsUsed: Int = 0,
    val hintShown: Boolean = false,
    val solved: Boolean = false,
) {
    val blocked: Set<String> get() = wrong + eliminated

    fun choose(optionId: String): VisualGame {
        if (solved || optionId in blocked || puzzle.options.none { it.id == optionId }) return this
        return if (optionId == puzzle.correctId) copy(solved = true) else copy(wrong = wrong + optionId)
    }

    /** First hint names the kind of pattern to look for; later hints remove one wrong option each. */
    fun hint(): VisualGame {
        if (solved) return this
        if (!hintShown) return copy(hintShown = true, hintsUsed = hintsUsed + 1)
        val next = puzzle.options.firstOrNull { it.id != puzzle.correctId && it.id !in blocked } ?: return this
        return copy(eliminated = eliminated + next.id, hintsUsed = hintsUsed + 1)
    }

    /** Clears the attempt but keeps the hint counter, so Restart cannot be used to earn the no-hint bonus. */
    fun restart(): VisualGame = VisualGame(puzzle, hintsUsed = hintsUsed)

    fun encode(): String = listOf(
        hintsUsed.toString(), if (hintShown) "1" else "0", wrong.joinToString(","), eliminated.joinToString(","),
    ).joinToString(";")

    companion object {
        fun restore(puzzle: VisualPuzzle, raw: String?): VisualGame {
            val parts = raw?.split(";") ?: return VisualGame(puzzle)
            if (parts.size != 4) return VisualGame(puzzle)
            val ids = puzzle.options.map { it.id }.toSet()
            fun set(s: String) = s.split(",").filter { it in ids && it != puzzle.correctId }.toSet()
            return VisualGame(
                puzzle, wrong = set(parts[2]), eliminated = set(parts[3]),
                hintsUsed = parts[0].toIntOrNull()?.coerceAtLeast(0) ?: 0, hintShown = parts[1] == "1",
            )
        }
    }
}
