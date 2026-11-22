package io.github.chiefandrew5891max.logicmix.domain.twodots

import kotlin.math.abs
import kotlin.random.Random

enum class TwoDotsMode { CLASSIC, ENDLESS }

/** [path] of a closed hint repeats the first cell at the end, like a finished loop gesture. */
data class TwoDotsHint(val path: List<Int>, val closed: Boolean)

/** Board after gravity: [drop] is how many rows each dot fell (fresh dots: how far above the board they start). */
class Collapse(val cells: List<Int>, val drop: List<Int>, val fresh: Set<Int>)

class TwoDotsMove(
    val game: TwoDotsGame,
    val removed: Set<Int>,
    val closed: Boolean,
    val gained: Int,
    val collapse: Collapse,
    val reshuffled: Boolean,
)

/** Pure rules of Two Dots. Cells are row-major, row 0 on top; colors are 0 until [COLORS]. */
object TwoDotsRules {
    const val SIZE = 6
    const val CELLS = SIZE * SIZE
    const val COLORS = 5
    const val MOVES = 20
    const val XP_SCORE = 300
    const val DOT_POINTS = 10
    const val LOOP_BONUS = 100

    fun lengthBonus(length: Int): Int = when {
        length >= 8 -> 50
        length >= 5 -> 20
        else -> 0
    }

    /** Loop: every removed dot scores, plus the flat loop bonus. Plain chain: dots plus the highest length bonus reached. */
    fun score(removed: Int, chainLength: Int, closed: Boolean): Int =
        removed * DOT_POINTS + if (closed) LOOP_BONUS else lengthBonus(chainLength)

    fun adjacent(a: Int, b: Int): Boolean {
        if (a !in 0 until CELLS || b !in 0 until CELLS) return false
        return abs(a / SIZE - b / SIZE) + abs(a % SIZE - b % SIZE) == 1
    }

    fun neighbors(cell: Int): List<Int> {
        val r = cell / SIZE
        val c = cell % SIZE
        return buildList {
            if (r > 0) add(cell - SIZE)
            if (r < SIZE - 1) add(cell + SIZE)
            if (c > 0) add(cell - 1)
            if (c < SIZE - 1) add(cell + 1)
        }
    }

    /** A loop is at least four distinct dots, the path returning to its first dot, all one color and orthogonally linked. */
    fun isClosed(cells: List<Int>, path: List<Int>): Boolean {
        if (path.size < 5 || path.first() != path.last()) return false
        val body = path.dropLast(1)
        return body.toSet().size == body.size && linked(cells, path)
    }

    private fun linked(cells: List<Int>, path: List<Int>): Boolean {
        if (path.any { it !in cells.indices }) return false
        val color = cells[path[0]]
        return path.all { cells[it] == color } && path.zipWithNext().all { (a, b) -> adjacent(a, b) }
    }

    fun isValidChain(cells: List<Int>, path: List<Int>): Boolean =
        path.size >= 2 && linked(cells, path) && (path.toSet().size == path.size || isClosed(cells, path))

    /** One finger step: add a neighbour of the same color, step back to undo, or close a loop. Anything else changes nothing. */
    fun extend(cells: List<Int>, path: List<Int>, cell: Int): List<Int> {
        if (cell !in cells.indices) return path
        if (path.isEmpty()) return listOf(cell)
        val last = path.last()
        if (cell == last) return path
        if (path.size >= 2 && cell == path[path.size - 2]) return path.dropLast(1)
        if (isClosed(cells, path)) return path
        if (!adjacent(last, cell) || cells[cell] != cells[last]) return path
        if (cell == path.first() && path.size >= 4) return path + cell
        if (cell in path) return path
        return path + cell
    }

    fun hasMove(cells: List<Int>): Boolean =
        cells.indices.any { i -> neighbors(i).any { cells[it] == cells[i] } }

    /** Repairs a board without any pair: bounded reshuffles keep the same dots, then one pair is forced. */
    fun ensureMove(cells: List<Int>, random: Random): List<Int> {
        if (hasMove(cells)) return cells
        var current = cells
        repeat(50) {
            current = current.shuffled(random)
            if (hasMove(current)) return current
        }
        return current.toMutableList().also { it[1] = it[0] }
    }

    fun generate(random: Random): List<Int> = ensureMove(List(CELLS) { random.nextInt(COLORS) }, random)

    fun collapse(cells: List<Int>, removed: Set<Int>, random: Random): Collapse {
        val out = IntArray(CELLS)
        val drop = IntArray(CELLS)
        val fresh = HashSet<Int>()
        for (c in 0 until SIZE) {
            val kept = (SIZE - 1 downTo 0).map { it * SIZE + c }.filter { it !in removed }
            kept.forEachIndexed { i, old ->
                val idx = (SIZE - 1 - i) * SIZE + c
                out[idx] = cells[old]
                drop[idx] = (SIZE - 1 - i) - old / SIZE
            }
            val gap = SIZE - kept.size
            for (row in 0 until gap) {
                val idx = row * SIZE + c
                out[idx] = random.nextInt(COLORS)
                drop[idx] = gap
                fresh += idx
            }
        }
        return Collapse(out.toList(), drop.toList(), fresh)
    }

    /** Looks for a loop first, then the longest chain, then any pair. The search is bounded, so it never hangs. */
    fun findHint(cells: List<Int>): TwoDotsHint? {
        var closed: List<Int>? = null
        var best: List<Int> = emptyList()
        for (start in cells.indices) {
            val visited = BooleanArray(CELLS)
            val path = ArrayList<Int>()
            var budget = 4000
            fun dfs(v: Int) {
                if (budget-- <= 0 || closed != null) return
                visited[v] = true
                path += v
                if (path.size > best.size) best = path.toList()
                for (n in neighbors(v)) {
                    if (cells[n] != cells[start]) continue
                    if (n == start && path.size >= 4) {
                        closed = path.toList() + start
                        return
                    }
                    if (!visited[n]) dfs(n)
                    if (closed != null) return
                }
                path.removeAt(path.lastIndex)
                visited[v] = false
            }
            dfs(start)
            closed?.let { return TwoDotsHint(it, true) }
        }
        return best.takeIf { it.size >= 2 }?.let { TwoDotsHint(it, false) }
    }
}

/** Immutable game state; every random draw is derived from [seed], so saved games continue deterministically. */
data class TwoDotsGame(
    val mode: TwoDotsMode,
    val id: Long,
    val seed: Long,
    val cells: List<Int>,
    val score: Int = 0,
    val movesLeft: Int = TwoDotsRules.MOVES,
    val moves: Int = 0,
    val hintsUsed: Int = 0,
) {
    val over: Boolean get() = mode == TwoDotsMode.CLASSIC && movesLeft <= 0

    /** Stable per game (survives restore), so the XP of one game can be recorded only once. */
    val xpId: String get() = "twodots-${mode.name.lowercase()}-$id"

    /** XP is paid by classic games only; endless follows the app rule that only career attempts award XP. */
    val xpReady: Boolean get() = mode == TwoDotsMode.CLASSIC && score >= TwoDotsRules.XP_SCORE

    /** Plays a finished gesture. Returns null (no move spent) for anything that is not a valid chain. */
    fun play(path: List<Int>): TwoDotsMove? {
        if (over || !TwoDotsRules.isValidChain(cells, path)) return null
        val closed = TwoDotsRules.isClosed(cells, path)
        val color = cells[path[0]]
        val removed = if (closed) cells.indices.filter { cells[it] == color }.toSet() else path.toSet()
        val gained = TwoDotsRules.score(removed.size, path.size, closed)
        val random = Random(seed)
        val collapse = TwoDotsRules.collapse(cells, removed, random)
        val fixed = TwoDotsRules.ensureMove(collapse.cells, random)
        val next = copy(
            seed = random.nextLong(),
            cells = fixed,
            score = score + gained,
            movesLeft = if (mode == TwoDotsMode.CLASSIC) movesLeft - 1 else movesLeft,
            moves = moves + 1,
        )
        return TwoDotsMove(next, removed, closed, gained, collapse, fixed != collapse.cells)
    }

    fun withHint(): TwoDotsGame = copy(hintsUsed = hintsUsed + 1)

    fun encode(): String =
        listOf("td1", mode.name, id, seed, score, movesLeft, moves, hintsUsed, cells.joinToString("")).joinToString("|")

    companion object {
        fun new(mode: TwoDotsMode, seed: Long): TwoDotsGame {
            val random = Random(seed)
            return TwoDotsGame(mode, seed, random.nextLong(), TwoDotsRules.generate(random))
        }

        fun restore(raw: String?): TwoDotsGame? {
            val p = raw?.split("|") ?: return null
            if (p.size != 9 || p[0] != "td1") return null
            return try {
                val mode = TwoDotsMode.valueOf(p[1])
                val cells = p[8].map { it - '0' }
                if (cells.size != TwoDotsRules.CELLS || cells.any { it !in 0 until TwoDotsRules.COLORS }) return null
                val game = TwoDotsGame(mode, p[2].toLong(), p[3].toLong(), cells, p[4].toInt(), p[5].toInt(), p[6].toInt(), p[7].toInt())
                game.takeIf { it.score >= 0 && it.movesLeft in 0..TwoDotsRules.MOVES && it.moves >= 0 && it.hintsUsed >= 0 }
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
