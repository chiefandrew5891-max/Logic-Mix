package io.github.chiefandrew5891max.logicmix.domain.sudoku

import kotlin.random.Random

enum class SudokuDifficulty(val clues: Int, val needsSingles: Boolean) {
    EASY(42, true), MEDIUM(34, true), HARD(28, false), EXPERT(24, false);

    companion object {
        fun fromStored(name: String?) = entries.firstOrNull { it.name == name } ?: EASY
    }
}

object SudokuGeometry {
    const val CELLS = 81
    fun row(i: Int) = i / 9
    fun col(i: Int) = i % 9
    fun box(i: Int) = (i / 27) * 3 + (i % 9) / 3

    /** The 20 cells sharing a row, column or box with each cell. */
    val peers: Array<IntArray> = Array(CELLS) { i ->
        (0 until CELLS).filter { it != i && (row(it) == row(i) || col(it) == col(i) || box(it) == box(i)) }.toIntArray()
    }
}

/** Backtracking solver with bitmask candidates and fewest-candidates-first cell choice. */
object SudokuSolver {
    private const val ALL = 0x3FE // bits 1..9

    private fun candidates(g: IntArray, i: Int): Int {
        var used = 0
        for (p in SudokuGeometry.peers[i]) used = used or (1 shl g[p])
        return ALL and used.inv()
    }

    /** Counts solutions, stopping at [limit]. Returns 0 for a grid that already breaks the rules. */
    fun countSolutions(grid: IntArray, limit: Int = 2): Int {
        val g = grid.copyOf()
        if (!consistent(g)) return 0
        return search(g, limit, null, null)
    }

    fun solve(grid: IntArray, random: Random? = null): IntArray? {
        val g = grid.copyOf()
        if (!consistent(g)) return null
        val out = arrayOfNulls<IntArray>(1)
        search(g, 1, random, out)
        return out[0]
    }

    fun consistent(g: IntArray): Boolean = g.indices.all { i ->
        g[i] == 0 || SudokuGeometry.peers[i].none { g[it] == g[i] }
    }

    private fun search(g: IntArray, limit: Int, random: Random?, out: Array<IntArray?>?): Int {
        var best = -1
        var bestMask = 0
        var bestCount = 10
        for (i in 0 until SudokuGeometry.CELLS) {
            if (g[i] != 0) continue
            val m = candidates(g, i)
            val c = Integer.bitCount(m)
            if (c == 0) return 0
            if (c < bestCount) {
                best = i; bestMask = m; bestCount = c
                if (c == 1) break
            }
        }
        if (best == -1) {
            if (out != null && out[0] == null) out[0] = g.copyOf()
            return 1
        }
        val digits = (1..9).filter { bestMask and (1 shl it) != 0 }.let { if (random != null) it.shuffled(random) else it }
        var found = 0
        for (d in digits) {
            g[best] = d
            found += search(g, limit - found, random, out)
            g[best] = 0
            if (found >= limit) break
        }
        return found
    }

    /** True when naked and hidden singles alone solve the puzzle (no guessing needed). */
    fun solvableBySingles(grid: IntArray): Boolean {
        val g = grid.copyOf()
        var progress = true
        while (progress) {
            progress = false
            for (i in 0 until SudokuGeometry.CELLS) {
                if (g[i] != 0) continue
                val m = candidates(g, i)
                if (Integer.bitCount(m) == 1) {
                    g[i] = Integer.numberOfTrailingZeros(m)
                    progress = true
                } else if (hiddenSingle(g, i, m)) {
                    progress = true
                }
            }
        }
        return g.all { it != 0 }
    }

    private fun hiddenSingle(g: IntArray, i: Int, m: Int): Boolean {
        val groups = listOf(
            (0 until 81).filter { SudokuGeometry.row(it) == SudokuGeometry.row(i) },
            (0 until 81).filter { SudokuGeometry.col(it) == SudokuGeometry.col(i) },
            (0 until 81).filter { SudokuGeometry.box(it) == SudokuGeometry.box(i) },
        )
        for (d in 1..9) {
            if (m and (1 shl d) == 0) continue
            for (group in groups) {
                if (group.none { it != i && g[it] == 0 && candidates(g, it) and (1 shl d) != 0 }) {
                    g[i] = d
                    return true
                }
            }
        }
        return false
    }
}

class SudokuPuzzle(val givens: IntArray, val solution: IntArray, val difficulty: SudokuDifficulty, val seed: Long) {
    val id: String get() = "sudoku-${difficulty.name.lowercase()}-$seed"
}

object SudokuGenerator {
    /** Deterministic for a given (difficulty, seed); the result always has exactly one solution. */
    fun generate(difficulty: SudokuDifficulty, seed: Long): SudokuPuzzle {
        var attempt = 0
        var last: SudokuPuzzle
        do {
            last = build(difficulty, seed, attempt)
            attempt++
            if (SudokuSolver.solvableBySingles(last.givens) == difficulty.needsSingles) return last
        } while (attempt < MAX_ATTEMPTS)
        return last
    }

    private fun build(difficulty: SudokuDifficulty, seed: Long, attempt: Int): SudokuPuzzle {
        val random = Random(seed * 31 + attempt)
        val solution = SudokuSolver.solve(IntArray(SudokuGeometry.CELLS), random)!!
        val givens = solution.copyOf()
        var clues = SudokuGeometry.CELLS
        for (i in (0 until SudokuGeometry.CELLS).shuffled(random)) {
            if (clues <= difficulty.clues) break
            val keep = givens[i]
            givens[i] = 0
            if (SudokuSolver.countSolutions(givens, 2) == 1) clues-- else givens[i] = keep
        }
        return SudokuPuzzle(givens, solution, difficulty, seed)
    }

    private const val MAX_ATTEMPTS = 12
}
