package com.akynex.logicmix.domain.visual.place

import java.util.ArrayDeque
import kotlin.random.Random

enum class SlideKind { RUSH_HOUR, KLOTSKI }

/** A rectangular block. [axis]: 0 = moves in any direction, 1 = horizontal only, 2 = vertical only. Block 0 is the target. */
data class SlideBlock(val id: Int, val w: Int, val h: Int, val axis: Int)

data class SlidePuzzle(
    val id: String,
    val difficulty: Int,
    val kind: SlideKind,
    val w: Int,
    val h: Int,
    val blocks: List<SlideBlock>,
    val start: List<Cell>,
    /** Where the top-left cell of block 0 has to end up. */
    val goal: Cell,
    val minMoves: Int,
)

/** Single-step sliding. Every step is one undo entry; solved as soon as the target block reaches [SlidePuzzle.goal]. */
data class SlideGame(
    val puzzle: SlidePuzzle,
    val pos: List<Cell> = puzzle.start,
    val history: List<List<Cell>> = emptyList(),
    val hintsUsed: Int = 0,
) {
    val moves: Int get() = history.size
    val solved: Boolean get() = pos[0] == puzzle.goal

    fun canMove(id: Int, dx: Int, dy: Int): Boolean = SlideSolver.canMove(puzzle, pos, id, dx, dy)

    fun move(id: Int, dx: Int, dy: Int): SlideGame? =
        if (solved || !canMove(id, dx, dy)) null
        else copy(pos = pos.toMutableList().also { it[id] = Cell(it[id].x + dx, it[id].y + dy) }, history = history + listOf(pos))

    fun undo(): SlideGame? = if (solved || history.isEmpty()) null else copy(pos = history.last(), history = history.dropLast(1))

    fun reset(): SlideGame? = if (solved || history.isEmpty()) null else copy(pos = puzzle.start, history = history + listOf(pos))

    /** Plays the first step of a shortest solution from the current position. */
    fun hint(): SlideGame? {
        if (solved) return null
        val (id, dx, dy) = SlideSolver.firstStep(puzzle, pos) ?: return null
        return move(id, dx, dy)?.copy(hintsUsed = hintsUsed + 1)
    }

    fun encode(): String = "h$hintsUsed|" + pos.joinToString(";") { "${it.x},${it.y}" }

    companion object {
        fun restore(puzzle: SlidePuzzle, raw: String?): SlideGame {
            if (raw == null) return SlideGame(puzzle)
            return runCatching {
                val hints = raw.substringBefore('|').removePrefix("h").toInt()
                val cells = raw.substringAfter('|').split(';').map { p -> p.split(',').let { Cell(it[0].toInt(), it[1].toInt()) } }
                if (cells.size != puzzle.blocks.size || !SlideSolver.valid(puzzle, cells)) SlideGame(puzzle) else SlideGame(puzzle, cells, emptyList(), hints)
            }.getOrDefault(SlideGame(puzzle))
        }
    }
}

object SlideSolver {
    private val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    fun valid(p: SlidePuzzle, pos: List<Cell>): Boolean {
        val seen = HashSet<Cell>()
        for ((i, b) in p.blocks.withIndex()) for (y in 0 until b.h) for (x in 0 until b.w) {
            val c = Cell(pos[i].x + x, pos[i].y + y)
            if (c.x !in 0 until p.w || c.y !in 0 until p.h || !seen.add(c)) return false
        }
        return true
    }

    fun canMove(p: SlidePuzzle, pos: List<Cell>, id: Int, dx: Int, dy: Int): Boolean {
        val b = p.blocks[id]
        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) != 1) return false
        if (b.axis == 1 && dy != 0) return false
        if (b.axis == 2 && dx != 0) return false
        val grid = HashSet<Cell>()
        for ((i, o) in p.blocks.withIndex()) if (i != id) for (y in 0 until o.h) for (x in 0 until o.w) grid.add(Cell(pos[i].x + x, pos[i].y + y))
        val nx = pos[id].x + dx
        val ny = pos[id].y + dy
        for (y in 0 until b.h) for (x in 0 until b.w) {
            val c = Cell(nx + x, ny + y)
            if (c.x !in 0 until p.w || c.y !in 0 until p.h || c in grid) return false
        }
        return true
    }

    private fun key(pos: List<Cell>) = pos.joinToString(";") { "${it.x},${it.y}" }

    /** Breadth-first search; returns the sequence of (block, dx, dy) steps of a shortest solution, or null. */
    fun solve(p: SlidePuzzle, from: List<Cell>, limit: Int = 400_000): List<Triple<Int, Int, Int>>? {
        if (from[0] == p.goal) return emptyList()
        val parent = HashMap<String, Pair<String, Triple<Int, Int, Int>>>()
        val states = HashMap<String, List<Cell>>()
        val start = key(from)
        states[start] = from
        val queue = ArrayDeque<String>()
        queue.add(start)
        val seen = HashSet<String>().also { it.add(start) }
        while (queue.isNotEmpty() && seen.size < limit) {
            val k = queue.poll()
            val pos = states[k]!!
            for (id in p.blocks.indices) for ((dx, dy) in dirs) {
                if (!canMove(p, pos, id, dx, dy)) continue
                val next = pos.toMutableList().also { it[id] = Cell(it[id].x + dx, it[id].y + dy) }
                val nk = key(next)
                if (!seen.add(nk)) continue
                parent[nk] = k to Triple(id, dx, dy)
                states[nk] = next
                if (next[0] == p.goal) {
                    val path = ArrayList<Triple<Int, Int, Int>>()
                    var cur = nk
                    while (cur != start) { val (pk, step) = parent[cur]!!; path += step; cur = pk }
                    return path.reversed()
                }
                queue.add(nk)
            }
        }
        return null
    }

    fun firstStep(p: SlidePuzzle, from: List<Cell>) = solve(p, from)?.firstOrNull()

    /** Builds a layout, then scrambles it with legal moves from the solved position, so the result is always solvable. */
    fun generate(id: String, difficulty: Int, kind: SlideKind, seed: Long): SlidePuzzle {
        val rng = Random(seed)
        val d = difficulty.coerceIn(1, 5)
        var best: SlidePuzzle? = null
        repeat(12) {
            val layout = if (kind == SlideKind.RUSH_HOUR) rushLayout(d, rng) else klotskiLayout(d, rng)
            val (blocks, solved, goal, w, h) = layout
            val shell = SlidePuzzle(id, d, kind, w, h, blocks, solved, goal, 0)
            var pos = solved
            val steps = 80 + d * 60
            var lastId = -1
            repeat(steps) {
                val moves = blocks.indices.flatMap { b -> dirs.filter { (dx, dy) -> canMove(shell, pos, b, dx, dy) }.map { (dx, dy) -> Triple(b, dx, dy) } }
                val pick = moves.filter { it.first != lastId }.ifEmpty { moves }.randomOrNull(rng) ?: return@repeat
                pos = pos.toMutableList().also { it[pick.first] = Cell(it[pick.first].x + pick.second, it[pick.first].y + pick.third) }
                lastId = pick.first
            }
            if (pos[0] == goal) return@repeat
            val path = solve(shell, pos) ?: return@repeat
            val candidate = shell.copy(start = pos, minMoves = path.size)
            if (best == null || candidate.minMoves > best!!.minMoves) best = candidate
            if (candidate.minMoves >= 4 + d * 3) return candidate
        }
        return best ?: fallback(id, d, kind)
    }

    private fun fallback(id: String, d: Int, kind: SlideKind) =
        SlidePuzzle(id, d, kind, 6, 6, listOf(SlideBlock(0, 2, 1, 1)), listOf(Cell(0, 2)), Cell(4, 2), 4)

    private data class Layout(val blocks: List<SlideBlock>, val pos: List<Cell>, val goal: Cell, val w: Int, val h: Int)


    /** 6×6 board, target car on the exit row (row 2), exit on the right edge. */
    private fun rushLayout(d: Int, rng: Random): Layout {
        val w = 6; val h = 6
        val blocks = arrayListOf(SlideBlock(0, 2, 1, 1))
        val pos = arrayListOf(Cell(w - 2, 2))
        val busy = HashSet<Cell>().apply { add(Cell(4, 2)); add(Cell(5, 2)) }
        val want = 5 + d * 2
        var tries = 0
        while (blocks.size < want && tries++ < 400) {
            val horizontal = rng.nextBoolean()
            val len = if (rng.nextInt(4) == 0) 3 else 2
            val bw = if (horizontal) len else 1
            val bh = if (horizontal) 1 else len
            val x = rng.nextInt(w - bw + 1)
            val y = rng.nextInt(h - bh + 1)
            val cs = (0 until bh).flatMap { yy -> (0 until bw).map { xx -> Cell(x + xx, y + yy) } }
            if (cs.any { it in busy } || (horizontal && y == 2)) continue
            busy += cs
            blocks += SlideBlock(blocks.size, bw, bh, if (horizontal) 1 else 2)
            pos += Cell(x, y)
        }
        return Layout(blocks, pos, Cell(w - 2, 2), w, h)
    }

    /** 4×5 board: the 2×2 target belongs at the bottom centre; pieces are packed around it, leaving two empty cells. */
    private fun klotskiLayout(d: Int, rng: Random): Layout {
        val w = 4; val h = 5
        val blocks = arrayListOf(SlideBlock(0, 2, 2, 0))
        val pos = arrayListOf(Cell(1, 3))
        val busy = HashSet<Cell>().apply { addAll(listOf(Cell(1, 3), Cell(2, 3), Cell(1, 4), Cell(2, 4))) }
        val free = (0 until w * h).map { Cell(it % w, it / w) }.filter { it !in busy }.toMutableList()
        val shapes = listOf(1 to 2, 2 to 1, 1 to 2, 2 to 1, 1 to 1)
        var tries = 0
        val empties = 2
        while (free.size > empties && tries++ < 800) {
            val (bw, bh) = shapes.random(rng)
            val c = free.random(rng)
            val cs = (0 until bh).flatMap { yy -> (0 until bw).map { xx -> Cell(c.x + xx, c.y + yy) } }
            if (cs.any { it !in free } || free.size - cs.size < empties) continue
            free.removeAll(cs.toSet())
            blocks += SlideBlock(blocks.size, bw, bh, 0)
            pos += c
        }
        return Layout(blocks, pos, Cell(1, 3), w, h)
    }
}
