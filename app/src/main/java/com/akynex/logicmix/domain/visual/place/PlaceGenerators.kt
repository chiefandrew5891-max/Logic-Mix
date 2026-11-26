package com.akynex.logicmix.domain.visual.place

import kotlin.random.Random

/**
 * Generators build every puzzle from a known tiling, so each one has at least one solution (kept in
 * [PlacePuzzle.solution] for hints). The player may find any other valid arrangement; validation never compares to it.
 */
object PlaceGenerators {
    fun generate(family: PlaceFamily, difficulty: Int, seed: Long, id: String): PlacePuzzle {
        val rng = Random(seed)
        val d = difficulty.coerceIn(1, 5)
        return when (family) {
            PlaceFamily.POLYOMINO -> polyomino(id, d, rng)
            PlaceFamily.SILHOUETTE -> assembled(id, PlaceFamily.SILHOUETTE, d, rng, flip = true)
            PlaceFamily.DISSECTION -> assembled(id, PlaceFamily.DISSECTION, d, rng, flip = false)
            PlaceFamily.MOSAIC -> mosaic(id, d, rng)
            PlaceFamily.HEX -> hex(id, d, rng)
            PlaceFamily.PARADOX -> paradox(id, d, rng)
        }
    }

    private fun rect(w: Int, h: Int): Set<Cell> = buildSet { for (y in 0 until h) for (x in 0 until w) add(Cell(x, y)) }

    private fun hexRegion(radius: Int): Set<Cell> = buildSet {
        for (q in -radius..radius) for (r in -radius..radius) if (kotlin.math.abs(q + r) <= radius) add(Cell(q, r))
    }

    /** Splits [board] into [k] connected pieces by growing regions from random seeds one cell at a time. */
    fun partition(board: Set<Cell>, lattice: Lattice, k: Int, rng: Random): List<Set<Cell>> {
        repeat(40) {
            val cells = board.toList()
            val seeds = cells.shuffled(rng).take(k)
            val owner = HashMap<Cell, Int>()
            seeds.forEachIndexed { i, c -> owner[c] = i }
            val sizes = IntArray(k) { 1 }
            while (owner.size < board.size) {
                val order = (0 until k).sortedBy { sizes[it] + rng.nextInt(2) }
                var grew = false
                for (i in order) {
                    val frontier = owner.filterValues { it == i }.keys.flatMap { lattice.neighbors(it) }.filter { it in board && it !in owner }
                    if (frontier.isNotEmpty()) {
                        owner[frontier.random(rng)] = i; sizes[i]++; grew = true; break
                    }
                }
                if (!grew) break
            }
            val parts = (0 until k).map { i -> owner.filterValues { it == i }.keys }
            if (owner.size == board.size && parts.all { it.size >= 2 }) return parts
        }
        // Fallback: strips are always valid pieces.
        val rows = board.groupBy { it.y }.values.map { it.toSet() }
        return rows
    }

    /** Finds the placement that puts [piece] exactly on [target], or null if its shape cannot be turned into it. */
    fun placementFor(lattice: Lattice, piece: Piece, target: Collection<Cell>): Placement? {
        val goal = normalize(target)
        val origin = Cell(target.minOf { it.x }, target.minOf { it.y })
        for (flip in if (piece.canFlip) listOf(false, true) else listOf(false)) {
            for (rot in 0 until (if (piece.canRotate) lattice.turns else 1)) {
                if (shapeOf(lattice, piece.cells, rot, flip) == goal) return Placement(rot, flip, origin.x, origin.y)
            }
        }
        return null
    }

    private fun scrambled(lattice: Lattice, cells: Set<Cell>, rng: Random, flip: Boolean): List<Cell> =
        shapeOf(lattice, cells.toList(), rng.nextInt(lattice.turns), flip && rng.nextBoolean())

    private fun polyomino(id: String, d: Int, rng: Random): PlacePuzzle {
        val (w, h) = listOf(4 to 3, 4 to 4, 5 to 4, 5 to 5, 6 to 5)[d - 1]
        return fromTiling(id, PlaceFamily.POLYOMINO, d, Lattice.SQUARE, rect(w, h), 3 + d, rng, rotate = d > 1, flip = d >= 4)
    }

    private fun hex(id: String, d: Int, rng: Random): PlacePuzzle {
        val radius = if (d <= 3) 2 else 3
        return fromTiling(id, PlaceFamily.HEX, d, Lattice.HEX, hexRegion(radius), if (radius == 2) 3 + d else 3 + d, rng, rotate = true, flip = d >= 4)
    }

    private fun fromTiling(id: String, family: PlaceFamily, d: Int, lattice: Lattice, board: Set<Cell>, k: Int, rng: Random, rotate: Boolean, flip: Boolean): PlacePuzzle {
        val parts = partition(board, lattice, k, rng)
        val pieces = ArrayList<Piece>()
        val solution = HashMap<Int, Placement>()
        parts.forEachIndexed { i, part ->
            val shape = if (rotate) scrambled(lattice, part, rng, flip) else normalize(part)
            val piece = Piece(i, shape, i % 6, rotate, flip)
            pieces += piece
            solution[i] = placementFor(lattice, piece, part)!!
        }
        return PlacePuzzle(id, family, d, lattice, board, pieces.shuffled(rng), solution = solution)
    }

    /**
     * Silhouette / dissection: a rectangle is cut into pieces, then the same pieces are laid edge to edge into a new
     * outline that becomes the board. Dissection only turns pieces; the silhouette family may also flip them.
     */
    private fun assembled(id: String, family: PlaceFamily, d: Int, rng: Random, flip: Boolean): PlacePuzzle {
        val lattice = Lattice.SQUARE
        val (w, h) = listOf(4 to 3, 4 to 4, 6 to 3, 5 to 4, 6 to 4)[d - 1]
        val k = 3 + d
        repeat(60) {
            val parts = partition(rect(w, h), lattice, k, rng)
            val pieces = parts.mapIndexed { i, p -> Piece(i, normalize(p), i % 6, canRotate = true, canFlip = flip) }
            val laid = layOut(pieces, lattice, rng) ?: return@repeat
            val board = laid.values.flatten().toSet()
            val solution = laid.mapValues { (i, cells) -> placementFor(lattice, pieces[i], cells)!! }
            // Skip the trivial outline that just reproduces the source rectangle.
            if (normalize(board) == normalize(rect(w, h))) return@repeat
            return PlacePuzzle(id, family, d, lattice, board, pieces.shuffled(rng), solution = solution)
        }
        return polyomino(id, d, rng)
    }

    private fun layOut(pieces: List<Piece>, lattice: Lattice, rng: Random): Map<Int, List<Cell>>? {
        val laid = LinkedHashMap<Int, List<Cell>>()
        val union = HashSet<Cell>()
        for ((n, piece) in pieces.withIndex()) {
            val shape = shapeOf(lattice, piece.cells, if (piece.canRotate) rng.nextInt(lattice.turns) else 0, piece.canFlip && rng.nextBoolean())
            if (n == 0) { laid[piece.id] = shape; union += shape; continue }
            val options = HashMap<Cell, Int>()
            for (u in union) for (nb in lattice.neighbors(u)) if (nb !in union) for (c in shape) {
                val off = nb - c
                val moved = shape.map { it + off }
                if (moved.none { it in union }) options[off] = moved.sumOf { m -> lattice.neighbors(m).count { it in union } }
            }
            if (options.isEmpty()) return null
            val best = options.values.max()
            val pool = if (rng.nextInt(10) < 7) options.filterValues { it == best }.keys.toList() else options.keys.toList()
            val off = pool.random(rng)
            val moved = shape.map { it + off }
            laid[piece.id] = moved; union += moved
        }
        return laid
    }

    private fun mosaic(id: String, d: Int, rng: Random): PlacePuzzle {
        val (w, h) = listOf(4 to 3, 4 to 4, 5 to 4, 6 to 4, 6 to 5)[d - 1]
        val board = rect(w, h)
        val parts = partition(board, Lattice.SQUARE, 3 + d, rng)
        val colors = HashMap<Cell, Int>()
        val pieces = ArrayList<Piece>()
        val solution = HashMap<Int, Placement>()
        parts.forEachIndexed { i, part ->
            val color = i % 6
            part.forEach { colors[it] = color }
            val piece = Piece(i, normalize(part), color, canRotate = false, canFlip = false)
            pieces += piece
            solution[i] = placementFor(Lattice.SQUARE, piece, part)!!
        }
        return PlacePuzzle(id, PlaceFamily.MOSAIC, d, Lattice.SQUARE, board, pieces.shuffled(rng), targetColors = colors, solution = solution)
    }

    private data class Variant(val a: List<Cell>, val b: List<Cell>, val boardW: Int, val boardH: Int)

    private fun cells(vararg xy: Int): List<Cell> = (xy.indices step 2).map { Cell(xy[it], xy[it + 1]) }

    /** Two-piece area paradoxes found by exhaustive search: the pieces tile a W×H rectangle, yet fit a rectangle one cell larger with exactly one gap. */
    private val variants = listOf(
        Variant(cells(0, 0, 0, 1, 0, 2, 1, 0), cells(1, 1, 1, 2, 2, 0, 2, 1, 2, 2), 5, 2),
        Variant(cells(0, 0, 0, 1, 1, 0, 1, 1, 2, 0, 2, 1, 3, 0, 4, 0), cells(0, 2, 1, 2, 2, 2, 3, 1, 3, 2, 4, 1, 4, 2), 8, 2),
        Variant(
            cells(0, 0, 0, 1, 0, 2, 1, 0, 1, 1, 1, 2, 2, 0, 2, 1, 3, 0, 4, 0),
            cells(0, 3, 1, 3, 2, 2, 2, 3, 3, 1, 3, 2, 3, 3, 4, 1, 4, 2, 4, 3), 7, 3,
        ),
        Variant(
            cells(0, 0, 0, 1, 1, 0, 1, 1, 2, 0, 2, 1, 3, 0, 4, 0, 5, 0, 6, 0),
            cells(0, 2, 1, 2, 2, 2, 3, 1, 3, 2, 4, 1, 4, 2, 5, 1, 5, 2, 6, 1, 6, 2), 11, 2,
        ),
    )

    val paradoxVariants: Int get() = variants.size

    private fun paradox(id: String, d: Int, rng: Random): PlacePuzzle {
        val index = (d - 1).coerceAtMost(variants.size - 1)
        val v = variants[(index + rng.nextInt(2)).coerceAtMost(variants.size - 1)]
        val i = variants.indexOf(v)
        val board = rect(v.boardW, v.boardH)
        val pieces = listOf(Piece(0, normalize(v.a), 0, canRotate = true, canFlip = true), Piece(1, normalize(v.b), 1, canRotate = true, canFlip = true))
        val sol = solveParadox(board, pieces) ?: error("paradox variant $i has no solution")
        return PlacePuzzle(id, PlaceFamily.PARADOX, d, Lattice.SQUARE, board, pieces, gaps = 1, solution = sol, variant = i)
    }

    /** Brute-force placement of the two pieces into the board; also used by tests. */
    fun solveParadox(board: Set<Cell>, pieces: List<Piece>): Map<Int, Placement>? {
        val lat = Lattice.SQUARE
        fun options(p: Piece) = buildList {
            for (flip in listOf(false, true)) for (rot in 0 until 4) {
                val shape = shapeOf(lat, p.cells, rot, flip)
                for (oy in 0..board.maxOf { it.y }) for (ox in 0..board.maxOf { it.x }) {
                    val c = shape.map { Cell(it.x + ox, it.y + oy) }
                    if (c.all { it in board }) add(Placement(rot, flip, ox, oy) to c.toSet())
                }
            }
        }
        for ((p0, c0) in options(pieces[0])) for ((p1, c1) in options(pieces[1])) if (c0.none { it in c1 }) return mapOf(0 to p0, 1 to p1)
        return null
    }
}
