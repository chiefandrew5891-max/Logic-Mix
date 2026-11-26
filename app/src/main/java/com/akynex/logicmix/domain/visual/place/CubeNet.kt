package com.akynex.logicmix.domain.visual.place

import kotlin.random.Random

/**
 * Interactive cube net: five squares of a cube net are given and the player taps the grid to add the sixth square
 * so the whole shape folds into a cube. Every cell that really folds is accepted, not just the one the generator removed.
 */
data class NetPuzzle(val id: String, val difficulty: Int, val w: Int, val h: Int, val given: Set<Cell>, val removed: Cell)

data class NetGame(val puzzle: NetPuzzle, val chosen: Cell? = null, val attempts: Int = 0, val hintsUsed: Int = 0) {
    val solved: Boolean get() = chosen != null

    /** Returns the solved state if [cell] completes a foldable net; otherwise null and the caller gives feedback. */
    fun tap(cell: Cell): NetGame? =
        if (solved || cell in puzzle.given || cell.x !in 0 until puzzle.w || cell.y !in 0 until puzzle.h) null
        else if (CubeNets.folds(puzzle.given + cell)) copy(chosen = cell) else null

    fun failedTap(): NetGame = copy(attempts = attempts + 1)

    /** Every cell that completes the net. */
    fun validCells(): List<Cell> = CubeNets.completions(puzzle.given, puzzle.w, puzzle.h)

    fun hint(): NetGame? = if (solved) null else copy(chosen = null, hintsUsed = hintsUsed + 1)

    fun encode() = "h$hintsUsed|a$attempts"

    companion object {
        fun restore(p: NetPuzzle, raw: String?): NetGame = runCatching {
            if (raw == null) return NetGame(p)
            NetGame(p, null, raw.substringAfter("|a").toInt(), raw.substringBefore('|').removePrefix("h").toInt())
        }.getOrDefault(NetGame(p))
    }
}

object CubeNets {
    private val base = listOf(
        ".X..\nXXXX\n.X..", "X...\nXXXX\n.X..", "X...\nXXXX\n..X.", "X...\nXXXX\nX...", ".X..\nXXXX\n...X",
        "XX...\n.XXX.\n.X...", "XX...\n.XXX.\n..X..", "XX...\n.XXX.\n...X.",
        "XX..\n.XX.\n..XX", "XXX..\n..XXX",
    )

    private fun parse(s: String): List<Cell> = s.lines().flatMapIndexed { y, row -> row.indices.filter { row[it] == 'X' }.map { Cell(it, y) } }

    /** True when the six edge-connected squares fold into a cube (rolling a cube over them touches six different faces). */
    fun folds(cells: Set<Cell>): Boolean {
        if (cells.size != 6) return false
        // faces: 0 top,1 bottom,2 north,3 south,4 east,5 west
        val start = cells.first()
        val seen = HashSet<Cell>()
        val bottoms = HashSet<Int>()
        val stack = ArrayDeque<Pair<Cell, IntArray>>()
        stack.add(start to intArrayOf(0, 1, 2, 3, 4, 5))
        seen.add(start)
        while (stack.isNotEmpty()) {
            val (c, f) = stack.removeLast()
            if (!bottoms.add(f[1])) return false
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val n = Cell(c.x + dx, c.y + dy)
                if (n !in cells || !seen.add(n)) continue
                val t = f[0]; val b = f[1]; val no = f[2]; val so = f[3]; val e = f[4]; val w = f[5]
                val g = when {
                    dx == 1 -> intArrayOf(w, e, no, so, t, b)
                    dx == -1 -> intArrayOf(e, w, no, so, b, t)
                    dy == 1 -> intArrayOf(no, so, b, t, e, w)
                    else -> intArrayOf(so, no, t, b, e, w)
                }
                stack.add(n to g)
            }
        }
        return seen.size == 6 && bottoms.size == 6
    }

    fun completions(given: Set<Cell>, w: Int, h: Int): List<Cell> =
        (0 until h).flatMap { y -> (0 until w).map { Cell(it, y) } }.filter { it !in given && folds(given + it) }

    val nets: List<List<Cell>> by lazy { base.map { parse(it) }.filter { folds(it.toSet()) } }

    fun generate(id: String, difficulty: Int, seed: Long): NetPuzzle {
        val rng = Random(seed)
        val d = difficulty.coerceIn(1, 5)
        val pool = if (d <= 2) nets.take(5) else nets
        val net = pool.random(rng)
        val shape = shapeOf(Lattice.SQUARE, net, rng.nextInt(4), rng.nextBoolean())
        val removable = shape.filter { c -> connected(shape.toSet() - c) }
        // Low levels remove an end square; harder ones may remove a middle square.
        val leaves = removable.filter { c -> Lattice.SQUARE.neighbors(c).count { it in shape } == 1 }
        val removed = (if (d <= 2 && leaves.isNotEmpty()) leaves else removable).random(rng)
        val given = (shape.toSet() - removed).map { Cell(it.x + 1, it.y + 1) }.toSet()
        val w = shape.maxOf { it.x } + 3
        val h = shape.maxOf { it.y } + 3
        return NetPuzzle(id, d, w, h, given, Cell(removed.x + 1, removed.y + 1))
    }

    private fun connected(cells: Set<Cell>): Boolean {
        val seen = HashSet<Cell>()
        val stack = ArrayDeque(listOf(cells.first()))
        seen.add(cells.first())
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            for (n in Lattice.SQUARE.neighbors(c)) if (n in cells && seen.add(n)) stack.add(n)
        }
        return seen.size == cells.size
    }
}
