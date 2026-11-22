package com.akynex.logicmix.domain.sudoku

private data class Snapshot(val values: List<Int>, val notes: List<Int>)

/**
 * Immutable Sudoku game state: value 0 = empty, notes are a bitmask over digits 1..9 per cell.
 * Placing a digit clears it from the notes of the cell's peers (automatic note update).
 */
class SudokuGame private constructor(
    val puzzle: SudokuPuzzle,
    val values: List<Int>,
    val notes: List<Int>,
    val hintsUsed: Int,
    private val undoStack: List<Snapshot>,
    private val redoStack: List<Snapshot>,
) {
    constructor(puzzle: SudokuPuzzle) : this(puzzle, puzzle.givens.toList(), List(81) { 0 }, 0, emptyList(), emptyList())

    fun isGiven(i: Int) = puzzle.givens[i] != 0
    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    /** Cells whose value repeats in its row, column or box. */
    val conflicts: Set<Int>
        get() = (0 until 81).filter { i -> values[i] != 0 && SudokuGeometry.peers[i].any { values[it] == values[i] } }.toSet()

    /** Solved means equal to the unique solution, not merely "all cells filled". */
    val solved: Boolean get() = values.indices.all { values[it] == puzzle.solution[it] }

    private fun snapshot() = Snapshot(values, notes)

    private fun changed(v: List<Int>, n: List<Int>, hints: Int = hintsUsed) =
        SudokuGame(puzzle, v, n, hints, (undoStack + snapshot()).takeLast(MAX_UNDO), emptyList())

    private fun withoutDigit(n: MutableList<Int>, cell: Int, digit: Int) {
        n[cell] = 0
        for (p in SudokuGeometry.peers[cell]) n[p] = n[p] and (1 shl digit).inv()
    }

    fun place(cell: Int, digit: Int): SudokuGame {
        if (isGiven(cell) || digit !in 1..9 || values[cell] == digit) return this
        val n = notes.toMutableList().also { withoutDigit(it, cell, digit) }
        return changed(values.toMutableList().also { it[cell] = digit }, n)
    }

    /** Clears the value, or the notes when the cell has no value. Returns this when nothing changes. */
    fun erase(cell: Int): SudokuGame {
        if (isGiven(cell)) return this
        return when {
            values[cell] != 0 -> changed(values.toMutableList().also { it[cell] = 0 }, notes)
            notes[cell] != 0 -> changed(values, notes.toMutableList().also { it[cell] = 0 })
            else -> this
        }
    }

    fun toggleNote(cell: Int, digit: Int): SudokuGame {
        if (isGiven(cell) || values[cell] != 0 || digit !in 1..9) return this
        return changed(values, notes.toMutableList().also { it[cell] = it[cell] xor (1 shl digit) })
    }

    /** The cell a hint would fill: the selected one when wrong/empty, else the first wrong/empty cell. */
    fun hintTarget(cell: Int?): Int? =
        cell?.takeIf { !isGiven(it) && values[it] != puzzle.solution[it] }
            ?: (0 until 81).firstOrNull { values[it] != puzzle.solution[it] }

    fun hint(cell: Int?): SudokuGame? {
        val target = hintTarget(cell) ?: return null
        val digit = puzzle.solution[target]
        val n = notes.toMutableList().also { withoutDigit(it, target, digit) }
        return changed(values.toMutableList().also { it[target] = digit }, n, hintsUsed + 1)
    }

    fun undo(): SudokuGame {
        val last = undoStack.lastOrNull() ?: return this
        return SudokuGame(puzzle, last.values, last.notes, hintsUsed, undoStack.dropLast(1), redoStack + snapshot())
    }

    fun redo(): SudokuGame {
        val next = redoStack.lastOrNull() ?: return this
        return SudokuGame(puzzle, next.values, next.notes, hintsUsed, undoStack + snapshot(), redoStack.dropLast(1))
    }

    fun reset(): SudokuGame = SudokuGame(puzzle, puzzle.givens.toList(), List(81) { 0 }, hintsUsed, emptyList(), emptyList())

    /** Compact persistence form: values;notes;hints. Undo history is intentionally not stored. */
    fun encode(): String = "${values.joinToString("")};${notes.joinToString(",")};$hintsUsed"

    companion object {
        private const val MAX_UNDO = 200

        fun restore(puzzle: SudokuPuzzle, encoded: String): SudokuGame? = try {
            val parts = encoded.split(";")
            val values = parts[0].map { it - '0' }
            val notes = parts[1].split(",").map { it.toInt() }
            val valid = values.size == 81 && notes.size == 81 && values.all { it in 0..9 } &&
                (0 until 81).all { puzzle.givens[it] == 0 || values[it] == puzzle.givens[it] }
            if (valid) SudokuGame(puzzle, values, notes, parts[2].toInt().coerceAtLeast(0), emptyList(), emptyList()) else null
        } catch (e: Exception) {
            null
        }
    }
}
