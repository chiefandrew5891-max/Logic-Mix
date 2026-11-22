package com.akynex.logicmix.domain.orb

object OrbRules {
    const val SIZE = 9
    const val CELLS = SIZE * SIZE
    const val COLORS = 7
    const val MIN_LINE = 5
    const val SPAWN = 3
    const val START_BALLS = 5

    /** Score for removing [n] balls at once: 5 -> 10, 6 -> 18, 7 -> 28 ... */
    fun scoreFor(n: Int): Int = if (n < MIN_LINE) 0 else n * (n - 3)

    /** Finished games with at least this score count as a solved puzzle for XP. */
    const val XP_SCORE = 100
}

object OrbPath {
    private fun neighbours(i: Int): List<Int> {
        val r = i / OrbRules.SIZE
        val c = i % OrbRules.SIZE
        return buildList {
            if (r > 0) add(i - OrbRules.SIZE)
            if (r < OrbRules.SIZE - 1) add(i + OrbRules.SIZE)
            if (c > 0) add(i - 1)
            if (c < OrbRules.SIZE - 1) add(i + 1)
        }
    }

    /** Shortest orthogonal route through empty cells, including both ends; null when blocked. */
    fun find(cells: List<Int>, from: Int, to: Int): List<Int>? {
        if (from !in cells.indices || to !in cells.indices || from == to) return null
        if (cells[from] == 0 || cells[to] != 0) return null
        val prev = IntArray(cells.size) { -1 }
        val seen = BooleanArray(cells.size)
        val queue = ArrayDeque<Int>()
        queue.add(from)
        seen[from] = true
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur == to) {
                val path = ArrayList<Int>()
                var n = to
                while (n != -1) { path.add(n); n = prev[n] }
                return path.reversed()
            }
            for (nb in neighbours(cur)) {
                if (seen[nb] || cells[nb] != 0) continue
                seen[nb] = true
                prev[nb] = cur
                queue.add(nb)
            }
        }
        return null
    }

    fun reachable(cells: List<Int>, from: Int): Set<Int> {
        val seen = HashSet<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(from)
        while (queue.isNotEmpty()) {
            for (nb in neighbours(queue.removeFirst())) {
                if (cells[nb] == 0 && seen.add(nb)) queue.add(nb)
            }
        }
        return seen
    }
}

object OrbLines {
    private val dirs = arrayOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)

    /** Every cell that belongs to a run of [OrbRules.MIN_LINE]+ same-colored balls in any of the 4 directions. */
    fun find(cells: List<Int>): Set<Int> {
        val result = HashSet<Int>()
        val n = OrbRules.SIZE
        for (r in 0 until n) for (c in 0 until n) {
            val color = cells[r * n + c]
            if (color == 0) continue
            for ((dr, dc) in dirs) {
                val pr = r - dr
                val pc = c - dc
                if (pr in 0 until n && pc in 0 until n && cells[pr * n + pc] == color) continue
                val run = ArrayList<Int>()
                var rr = r
                var cc = c
                while (rr in 0 until n && cc in 0 until n && cells[rr * n + cc] == color) {
                    run.add(rr * n + cc)
                    rr += dr
                    cc += dc
                }
                if (run.size >= OrbRules.MIN_LINE) result.addAll(run)
            }
        }
        return result
    }
}

private data class OrbSnapshot(val cells: List<Int>, val next: List<Int>, val score: Int, val rng: Long)

data class OrbMove(
    val game: OrbGame,
    val path: List<Int>,
    val clearedByMove: Set<Int>,
    val spawned: List<Int>,
    val clearedBySpawn: Set<Int>,
)

private class Roll(var state: Long) {
    fun nextInt(bound: Int): Int {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        return ((z ushr 1) % bound).toInt()
    }

    fun colors(count: Int) = List(count) { nextInt(OrbRules.COLORS) + 1 }

    fun place(cells: MutableList<Int>, colors: List<Int>): List<Int> {
        val placed = ArrayList<Int>()
        for (color in colors) {
            val empty = cells.indices.filter { cells[it] == 0 }
            if (empty.isEmpty()) break
            val spot = empty[nextInt(empty.size)]
            cells[spot] = color
            placed.add(spot)
        }
        return placed
    }
}

/** Immutable Orb Lines position. All randomness comes from [rng], so a saved game replays identically. */
class OrbGame private constructor(
    val cells: List<Int>,
    val next: List<Int>,
    val score: Int,
    val rng: Long,
    val hintsUsed: Int,
    private val history: List<OrbSnapshot>,
) {
    val over: Boolean get() = cells.none { it == 0 }
    val canUndo: Boolean get() = history.isNotEmpty()

    private fun clear(board: MutableList<Int>, lines: Set<Int>): Int {
        lines.forEach { board[it] = 0 }
        return OrbRules.scoreFor(lines.size)
    }

    /** Null when there is no free route, so callers can reject the move without side effects. */
    fun move(from: Int, to: Int): OrbMove? {
        if (over) return null
        val path = OrbPath.find(cells, from, to) ?: return null
        val board = cells.toMutableList()
        board[to] = board[from]
        board[from] = 0
        var gained = 0
        val lines = OrbLines.find(board)
        val roll = Roll(rng)
        var spawned = emptyList<Int>()
        var afterSpawn = emptySet<Int>()
        var upcoming = next
        if (lines.isNotEmpty()) {
            gained += clear(board, lines)
        } else {
            spawned = roll.place(board, next)
            afterSpawn = OrbLines.find(board)
            if (afterSpawn.isNotEmpty()) gained += clear(board, afterSpawn)
            upcoming = roll.colors(OrbRules.SPAWN)
        }
        val snap = OrbSnapshot(cells, next, score, rng)
        val game = OrbGame(board, upcoming, score + gained, roll.state, hintsUsed, (history + snap).takeLast(MAX_UNDO))
        return OrbMove(game, path, lines, spawned, afterSpawn)
    }

    fun undo(): OrbGame {
        val last = history.lastOrNull() ?: return this
        return OrbGame(last.cells, last.next, last.score, last.rng, hintsUsed, history.dropLast(1))
    }

    /** A real hint: a reachable move that completes a line right now, or null when none exists. */
    fun hintMove(): Pair<Int, Int>? {
        for (from in cells.indices) {
            if (cells[from] == 0) continue
            for (to in OrbPath.reachable(cells, from)) {
                val trial = cells.toMutableList()
                trial[to] = trial[from]
                trial[from] = 0
                if (OrbLines.find(trial).isNotEmpty()) return from to to
            }
        }
        return null
    }

    fun withHint(): OrbGame = OrbGame(cells, next, score, rng, hintsUsed + 1, history)

    fun encode(): String = "${cells.joinToString("")};${next.joinToString("")};$score;$rng;$hintsUsed"

    companion object {
        private const val MAX_UNDO = 20

        fun new(seed: Long): OrbGame {
            val roll = Roll(seed)
            val board = MutableList(OrbRules.CELLS) { 0 }
            roll.place(board, roll.colors(OrbRules.START_BALLS))
            // Re-roll the rare start where five random balls already form a line.
            if (OrbLines.find(board).isNotEmpty()) return new(seed + 1)
            return OrbGame(board, roll.colors(OrbRules.SPAWN), 0, roll.state, 0, emptyList())
        }

        /** Test and restore entry point: any valid board with the given upcoming colors. */
        fun of(cells: List<Int>, next: List<Int>, score: Int = 0, rng: Long = 1L): OrbGame {
            require(cells.size == OrbRules.CELLS && next.size == OrbRules.SPAWN)
            return OrbGame(cells, next, score, rng, 0, emptyList())
        }

        fun restore(encoded: String): OrbGame? = try {
            val p = encoded.split(";")
            val cells = p[0].map { it - '0' }
            val next = p[1].map { it - '0' }
            val ok = cells.size == OrbRules.CELLS && cells.all { it in 0..OrbRules.COLORS } &&
                next.size == OrbRules.SPAWN && next.all { it in 1..OrbRules.COLORS }
            if (ok) OrbGame(cells, next, p[2].toInt().coerceAtLeast(0), p[3].toLong(), p[4].toInt().coerceAtLeast(0), emptyList()) else null
        } catch (e: Exception) {
            null
        }
    }
}
