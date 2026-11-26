package com.akynex.logicmix.domain.visual.place

/** Interactive Visual Logic families. Each one is a hands-on puzzle, not a multiple-choice question. */
enum class PlaceFamily { POLYOMINO, SILHOUETTE, DISSECTION, MOSAIC, HEX, PARADOX }

data class Cell(val x: Int, val y: Int) {
    operator fun plus(o: Cell) = Cell(x + o.x, y + o.y)
    operator fun minus(o: Cell) = Cell(x - o.x, y - o.y)
}

/** Square grid (4 turns) or hexagonal grid in axial coordinates (6 turns). Both are exact integer maths. */
enum class Lattice(val turns: Int) {
    SQUARE(4), HEX(6);

    fun rotate(c: Cell): Cell = if (this == SQUARE) Cell(-c.y, c.x) else Cell(-c.y, c.x + c.y)

    /** Mirror image; for hexes the reflection across the q = r axis. */
    fun flip(c: Cell): Cell = if (this == SQUARE) Cell(-c.x, c.y) else Cell(c.y, c.x)

    fun neighbors(c: Cell): List<Cell> =
        if (this == SQUARE) listOf(Cell(c.x + 1, c.y), Cell(c.x - 1, c.y), Cell(c.x, c.y + 1), Cell(c.x, c.y - 1))
        else listOf(Cell(c.x + 1, c.y), Cell(c.x - 1, c.y), Cell(c.x, c.y + 1), Cell(c.x, c.y - 1), Cell(c.x + 1, c.y - 1), Cell(c.x - 1, c.y + 1))
}

fun normalize(cells: Collection<Cell>): List<Cell> {
    val mx = cells.minOf { it.x }
    val my = cells.minOf { it.y }
    return cells.map { Cell(it.x - mx, it.y - my) }.sortedWith(compareBy({ it.y }, { it.x }))
}

data class Piece(val id: Int, val cells: List<Cell>, val color: Int, val canRotate: Boolean, val canFlip: Boolean)

/** A piece laid on the board: [flip] is applied first, then [rot] turns, then the normalised shape is moved to ([ox], [oy]). */
data class Placement(val rot: Int, val flip: Boolean, val ox: Int, val oy: Int)

fun shapeOf(lattice: Lattice, cells: List<Cell>, rot: Int, flip: Boolean): List<Cell> {
    var out = cells
    if (flip) out = out.map { lattice.flip(it) }
    repeat(((rot % lattice.turns) + lattice.turns) % lattice.turns) { out = out.map { lattice.rotate(it) } }
    return normalize(out)
}

data class PlacePuzzle(
    val id: String,
    val family: PlaceFamily,
    val difficulty: Int,
    val lattice: Lattice,
    val board: Set<Cell>,
    val pieces: List<Piece>,
    /** Colour each board cell must receive (mosaics only); null means any piece may cover any cell. */
    val targetColors: Map<Cell, Int>? = null,
    /** Board cells that must stay empty (the paradox puzzles, where the pieces cover less than the whole board). */
    val gaps: Int = 0,
    val solution: Map<Int, Placement>,
    /** Index of the paradox variant, used to pick its explanation text. */
    val variant: Int = 0,
) {
    fun piece(id: Int): Piece = pieces.first { it.id == id }

    fun cellsOf(pieceId: Int, p: Placement): List<Cell> {
        val piece = piece(pieceId)
        return shapeOf(lattice, piece.cells, p.rot, p.flip).map { Cell(it.x + p.ox, it.y + p.oy) }
    }
}

/** Immutable game state; every action returns a new state, or null when the move is illegal (the piece goes back to the tray). */
data class PlaceGame(
    val puzzle: PlacePuzzle,
    val placed: Map<Int, Placement> = emptyMap(),
    val history: List<Map<Int, Placement>> = emptyList(),
    val hintsUsed: Int = 0,
) {
    val solved: Boolean get() = isSolved()

    fun occupied(except: Int? = null): Map<Cell, Int> {
        val out = HashMap<Cell, Int>()
        for ((id, p) in placed) if (id != except) for (c in puzzle.cellsOf(id, p)) out[c] = id
        return out
    }

    fun canPlace(pieceId: Int, p: Placement): Boolean {
        val piece = puzzle.piece(pieceId)
        if (!piece.canRotate && p.rot != 0) return false
        if (!piece.canFlip && p.flip) return false
        val busy = occupied(except = pieceId)
        return puzzle.cellsOf(pieceId, p).all { it in puzzle.board && it !in busy }
    }

    fun place(pieceId: Int, p: Placement): PlaceGame? {
        if (solved || !canPlace(pieceId, p)) return null
        return copy(placed = placed + (pieceId to p), history = history + listOf(placed))
    }

    fun remove(pieceId: Int): PlaceGame? =
        if (solved || pieceId !in placed) null else copy(placed = placed - pieceId, history = history + listOf(placed))

    fun undo(): PlaceGame? = if (solved || history.isEmpty()) null else copy(placed = history.last(), history = history.dropLast(1))

    fun reset(): PlaceGame? = if (solved || placed.isEmpty()) null else copy(placed = emptyMap(), history = history + listOf(placed))

    /** Puts one piece on its reference position, taking out whatever was in the way. */
    fun hint(): PlaceGame? {
        if (solved) return null
        val target = puzzle.pieces.firstOrNull { placed[it.id] != puzzle.solution[it.id] } ?: return null
        val sol = puzzle.solution[target.id] ?: return null
        val cells = puzzle.cellsOf(target.id, sol).toSet()
        val keep = placed.filter { (id, p) -> id != target.id && puzzle.cellsOf(id, p).none { it in cells } }
        return copy(placed = keep + (target.id to sol), history = history + listOf(placed), hintsUsed = hintsUsed + 1)
    }

    private fun isSolved(): Boolean {
        if (placed.size != puzzle.pieces.size) return false
        val busy = occupied()
        if (busy.size != puzzle.board.size - puzzle.gaps) return false
        val target = puzzle.targetColors ?: return true
        return busy.all { (cell, id) -> target[cell] == puzzle.piece(id).color }
    }

    fun encode(): String = "h$hintsUsed|" + placed.entries.sortedBy { it.key }.joinToString(";") { (id, p) -> "$id,${p.rot},${if (p.flip) 1 else 0},${p.ox},${p.oy}" }

    companion object {
        fun restore(puzzle: PlacePuzzle, raw: String?): PlaceGame {
            val fresh = PlaceGame(puzzle)
            if (raw == null) return fresh
            return runCatching {
                val hints = raw.substringBefore('|').removePrefix("h").toInt()
                var game = fresh.copy(hintsUsed = hints)
                raw.substringAfter('|').split(';').filter { it.isNotBlank() }.forEach { part ->
                    val f = part.split(',').map { it.toInt() }
                    game = game.place(f[0], Placement(f[1], f[2] == 1, f[3], f[4])) ?: return fresh
                }
                game.copy(history = emptyList())
            }.getOrDefault(fresh)
        }
    }
}
