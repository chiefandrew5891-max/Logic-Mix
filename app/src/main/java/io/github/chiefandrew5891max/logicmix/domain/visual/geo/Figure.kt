package io.github.chiefandrew5891max.logicmix.domain.visual.geo

/**
 * A pixel figure: [cells] is row-major, 0 = empty and 1..3 = palette colours. Larger values are reserved markers
 * (see [HIDDEN], [HOLE], [DARK]). All geometry is exact integer arithmetic, so symmetry and equality checks are reliable.
 */
data class Figure(val w: Int, val h: Int, val cells: List<Int>) {
    init { require(w > 0 && h > 0 && cells.size == w * h) { "bad figure size" } }

    operator fun get(x: Int, y: Int): Int = cells[y * w + x]

    val count: Int get() = cells.count { it != 0 }

    /** Clockwise quarter turn: (x, y) goes to (h - 1 - y, x). */
    fun rot90(): Figure {
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) out[x * h + (h - 1 - y)] = this[x, y]
        return Figure(h, w, out.toList())
    }

    fun rotate(degrees: Int): Figure {
        var f = this
        repeat((((degrees / 90) % 4) + 4) % 4) { f = f.rot90() }
        return f
    }

    fun flipH(): Figure = Figure(w, h, List(w * h) { this[w - 1 - it % w, it / w] })

    fun flipV(): Figure = Figure(w, h, List(w * h) { this[it % w, h - 1 - it / w] })

    fun map(f: (Int) -> Int): Figure = Figure(w, h, cells.map(f))

    fun recolor(color: Int): Figure = map { if (it == 0) 0 else color }

    /** Same silhouette with every filled cell set to 1. */
    fun mask(): Figure = map { if (it == 0) 0 else 1 }

    /** Shifts colours 1..3 cyclically by [shift]; other values are untouched. */
    fun shiftColor(shift: Int): Figure = map { if (it in 1..3) ((it - 1 + shift) % 3 + 3) % 3 + 1 else it }

    /** Dihedral image 0..7: index 0..3 = rotations, 4..7 = horizontal flip followed by rotation. */
    fun symmetry(index: Int): Figure = (if (index >= 4) flipH() else this).rotate(90 * (index % 4))

    fun rotations(): List<Figure> = (0..3).map { rotate(90 * it) }

    fun dihedral(): List<Figure> = (0..7).map { symmetry(it) }

    /** True when all 8 images differ, so no rotation or reflection can be mistaken for another. */
    fun isChiral(): Boolean = dihedral().toSet().size == 8

    fun inRotationOrbit(other: Figure): Boolean = rotations().any { it == other }

    fun encode(): String = "$w,$h:" + cells.joinToString("")

    companion object {
        const val HIDDEN = 9
        const val HOLE = 2
        const val DARK = 4

        fun decode(raw: String): Figure? {
            val head = raw.substringBefore(':', "").split(',')
            val body = raw.substringAfter(':', "")
            val w = head.getOrNull(0)?.toIntOrNull() ?: return null
            val h = head.getOrNull(1)?.toIntOrNull() ?: return null
            if (w <= 0 || h <= 0 || body.length != w * h || body.any { !it.isDigit() }) return null
            return Figure(w, h, body.map { it - '0' })
        }

        fun empty(w: Int, h: Int) = Figure(w, h, List(w * h) { 0 })
    }
}

/** A colour-shift plus dihedral transformation; the unit rule for sequences and matrices. */
data class Xf(val sym: Int, val shift: Int) {
    fun apply(f: Figure): Figure = f.symmetry(sym).shiftColor(shift)

    companion object {
        val all: List<Xf> = (0..7).flatMap { s -> (0..2).map { Xf(s, it) } }
    }
}
