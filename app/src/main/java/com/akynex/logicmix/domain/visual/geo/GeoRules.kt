package com.akynex.logicmix.domain.visual.geo

/** Geometry shared by generators and validators. Validators re-derive answers from the puzzle data, never from the stored index. */
object GeoRules {
    const val ARROW = " → "

    fun rotationToken(deg: Int) = "↻$deg°"
    const val FLIP_H = "⇆"
    const val FLIP_V = "⇅"

    /** Dihedral index for a badge token, or null when it is not a transformation glyph. */
    fun symOf(token: String): Int? = when (token) {
        rotationToken(90) -> 1
        rotationToken(180) -> 2
        rotationToken(270) -> 3
        FLIP_H -> 4
        FLIP_V -> 6
        else -> null
    }

    fun applyBadge(fig: Figure, badge: String): Figure? {
        var f = fig
        for (t in badge.split(ARROW)) f = f.symmetry(symOf(t.trim()) ?: return null)
        return f
    }

    /** A key fits a hole when some rotation (never a reflection) of the key has the hole's silhouette. */
    fun fits(key: Figure, hole: Figure): Boolean = key.mask().rotations().any { it == hole.mask() }

    fun combine(a: Figure, b: Figure, op: String): Figure? {
        if (a.w != b.w || a.h != b.h) return null
        val x = a.mask()
        val y = b.mask()
        return Figure(a.w, a.h, x.cells.indices.map { i ->
            val p = x.cells[i] == 1
            val q = y.cells[i] == 1
            val r = when (op) {
                "∪" -> p || q
                "∩" -> p && q
                "−" -> p && !q
                "Δ" -> p != q
                else -> return null
            }
            if (r) 1 else 0
        })
    }

    /** Paper with holes after unfolding: [sheet] sits in the bottom-right corner and is mirrored over every fold crease. */
    fun unfold(sheet: Figure, mirrorX: Boolean, mirrorY: Boolean): Figure {
        val pw = if (mirrorX) sheet.w * 2 else sheet.w
        val ph = if (mirrorY) sheet.h * 2 else sheet.h
        val out = IntArray(pw * ph) { 1 }
        for (sy in 0 until sheet.h) for (sx in 0 until sheet.w) {
            if (sheet[sx, sy] != Figure.HOLE) continue
            val bx = pw - sheet.w + sx
            val by = ph - sheet.h + sy
            val xs = if (mirrorX) listOf(bx, pw - 1 - bx) else listOf(bx)
            val ys = if (mirrorY) listOf(by, ph - 1 - by) else listOf(by)
            for (x in xs) for (y in ys) out[y * pw + x] = Figure.HOLE
        }
        return Figure(pw, ph, out.toList())
    }

    fun foldAxes(badge: String): Pair<Boolean, Boolean> = badge.contains("┃") to badge.contains("━")

    /** Valid cube net: six connected cells, no 2x2 block, and a die rolled over it shows each face on the bottom once. */
    fun isCubeNet(fig: Figure): Boolean {
        val cells = (0 until fig.w * fig.h).filter { fig.cells[it] != 0 }.map { it % fig.w to it / fig.w }.toSet()
        if (cells.size != 6) return false
        for ((x, y) in cells) {
            if ((x + 1 to y) in cells && (x to y + 1) in cells && (x + 1 to y + 1) in cells) return false
        }
        // Die faces: bottom, top, north, south, east, west.
        data class Die(val b: Int, val t: Int, val n: Int, val s: Int, val e: Int, val w: Int)
        val seen = mutableMapOf<Pair<Int, Int>, Die>()
        val start = cells.first()
        seen[start] = Die(0, 1, 2, 3, 4, 5)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            val d = seen.getValue(c)
            val moves = listOf(
                (1 to 0) to Die(d.e, d.w, d.n, d.s, d.t, d.b),
                (-1 to 0) to Die(d.w, d.e, d.n, d.s, d.b, d.t),
                (0 to -1) to Die(d.n, d.s, d.t, d.b, d.e, d.w),
                (0 to 1) to Die(d.s, d.n, d.b, d.t, d.e, d.w),
            )
            for ((delta, next) in moves) {
                val to = c.first + delta.first to c.second + delta.second
                if (to in cells && to !in seen) { seen[to] = next; queue.addLast(to) }
            }
        }
        return seen.size == 6 && seen.values.map { it.b }.toSet().size == 6
    }

    fun orbitKey(fig: Figure): String = fig.mask().rotations().minOf { it.encode() }
}

object GeoValidator {
    private const val MAX_DIM = 12

    fun problems(p: GeoPuzzle): List<String> {
        val out = mutableListOf<String>()
        if (p.options.size != 4) out += "needs 4 options"
        if (p.options.toSet().size != p.options.size) out += "duplicate options"
        if (p.correct !in p.options.indices) out += "correct index outside options"
        if (p.columns <= 0 || (p.stimulus.isNotEmpty() && p.stimulus.size % p.columns != 0)) out += "grid is not rectangular"
        if (p.gapIndex != -1 && p.gapIndex !in p.stimulus.indices) out += "gap outside the grid"
        if ((p.stimulus + p.options).any { it.count == 0 || it.w > MAX_DIM || it.h > MAX_DIM }) out += "malformed figure"
        if (p.difficulty !in 1..5) out += "difficulty out of range"
        if (out.isNotEmpty()) return out
        val right = p.options.indices.filter { isRight(p, it) }
        if (right != listOf(p.correct)) out += "right options are $right but correct is ${p.correct}"
        if (!ruleIsUnambiguous(p)) out += "rule is ambiguous"
        return out
    }

    private fun known(p: GeoPuzzle): List<Figure?> = p.stimulus.mapIndexed { i, f -> if (i == p.gapIndex) null else f }

    private fun isRight(p: GeoPuzzle, i: Int): Boolean {
        val opt = p.options[i]
        val s = p.stimulus
        return when (p.family) {
            GeoFamily.ROTATION, GeoFamily.MIRROR, GeoFamily.SPATIAL ->
                s.size == 1 && GeoRules.applyBadge(s[0], p.badge) == opt
            GeoFamily.ABSTRACT -> s.size == 1 && s[0].inRotationOrbit(opt)
            GeoFamily.SILHOUETTE -> s.size == 1 && expectedSilhouette(p)?.let { opt.mask() == it } == true
            GeoFamily.KEYHOLE -> s.size == 1 && GeoRules.fits(opt, s[0])
            GeoFamily.FRAGMENT -> s.size == 1 && fragmentFits(s[0], opt)
            GeoFamily.PAPER_FOLD -> s.size == 1 && GeoRules.foldAxes(p.badge).let { (x, y) -> GeoRules.unfold(s[0], x, y) == opt }
            GeoFamily.CUBE_NET -> s.isEmpty() && GeoRules.isCubeNet(opt)
            GeoFamily.COMPOSE -> s.size == 2 && GeoRules.combine(s[0], s[1], p.badge.removePrefix("A ").removeSuffix(" B").trim()) == opt.mask()
            GeoFamily.ODD_ONE -> s.isEmpty() && oddIndex(p.options) == i
            GeoFamily.SEQUENCE, GeoFamily.MATRIX -> predictions(p).let { it.size == 1 && opt in it }
        }
    }

    private fun ruleIsUnambiguous(p: GeoPuzzle): Boolean = when (p.family) {
        GeoFamily.SEQUENCE, GeoFamily.MATRIX -> predictions(p).size == 1
        GeoFamily.ROTATION, GeoFamily.MIRROR, GeoFamily.SPATIAL, GeoFamily.ABSTRACT, GeoFamily.KEYHOLE ->
            p.stimulus.firstOrNull()?.mask()?.isChiral() == true
        GeoFamily.ODD_ONE -> p.options.map { GeoRules.orbitKey(it) }.toSet().size == 2
        else -> true
    }

    private fun expectedSilhouette(p: GeoPuzzle): Figure? {
        val base = p.stimulus.firstOrNull()?.mask() ?: return null
        return if (p.badge.isEmpty()) base else GeoRules.applyBadge(base, p.badge)
    }

    private fun fragmentFits(pattern: Figure, patch: Figure): Boolean {
        val hidden = (0 until pattern.w * pattern.h).filter { pattern.cells[it] == Figure.HIDDEN }
        if (hidden.isEmpty()) return false
        val xs = hidden.map { it % pattern.w }
        val ys = hidden.map { it / pattern.w }
        val x0 = xs.min(); val y0 = ys.min()
        if (xs.max() - x0 + 1 != patch.w || ys.max() - y0 + 1 != patch.h || hidden.size != patch.w * patch.h) return false
        val filled = pattern.cells.toMutableList()
        for (py in 0 until patch.h) for (px in 0 until patch.w) filled[(y0 + py) * pattern.w + x0 + px] = patch[px, py]
        val full = Figure(pattern.w, pattern.h, filled)
        return full == full.flipH() && full == full.flipV()
    }

    /** The option that is alone in its rotation class while the other three share one; -1 when there is no such option. */
    internal fun oddIndex(options: List<Figure>): Int {
        val groups = options.indices.groupBy { GeoRules.orbitKey(options[it]) }
        if (groups.size != 2) return -1
        val single = groups.values.firstOrNull { it.size == 1 } ?: return -1
        return if (groups.values.any { it.size == 3 }) single[0] else -1
    }

    /** All figures that fit the visible panels under any supported rule, so unique means the answer cannot be disputed. */
    internal fun predictions(p: GeoPuzzle): Set<Figure> {
        val k = known(p)
        val out = mutableSetOf<Figure>()
        if (p.gapIndex < 0) return out
        if (p.family == GeoFamily.SEQUENCE) {
            if (p.gapIndex != p.stimulus.size - 1 || p.gapIndex < 2) return out
            val panels = k.take(p.gapIndex).map { it ?: return out }
            for (t in Xf.all) if ((0 until panels.size - 1).all { t.apply(panels[it]) == panels[it + 1] }) out += t.apply(panels.last())
            return out
        }
        val n = p.columns
        if (p.stimulus.size != n * n || p.gapIndex != n * n - 1) return out
        val base = k[0] ?: return out
        fun pow(t: Xf, f: Figure, times: Int): Figure { var r = f; repeat(times) { r = t.apply(r) }; return r }
        for (tr in Xf.all) for (tc in Xf.all) {
            val ok = k.indices.all { i ->
                val panel = k[i] ?: return@all true
                pow(tr, pow(tc, base, i % n), i / n) == panel
            }
            if (!ok) continue
            val r = p.gapIndex / n
            val c = p.gapIndex % n
            out += pow(tr, pow(tc, base, c), r)
            out += pow(tc, pow(tr, base, r), c)
        }
        return out
    }
}
