package io.github.chiefandrew5891max.logicmix.domain.visual.geo

import kotlin.random.Random

/**
 * Procedural generators for the 13 families. A generator may return null when a random draw is unusable;
 * [generate] retries a bounded number of times, checks every result with [GeoValidator] and falls back to a fixed
 * known-valid puzzle, so a broken puzzle is never shown.
 */
object GeoGenerators {
    private const val ATTEMPTS = 40

    fun generate(family: GeoFamily, difficulty: Int, seed: Long, id: String = ""): GeoPuzzle {
        val d = difficulty.coerceIn(1, 5)
        for (attempt in 0 until ATTEMPTS) {
            val rnd = Random(seed * 1_000_003L + attempt * 7_919L + family.ordinal)
            val p = build(family, d, rnd, seed)?.copy(id = id) ?: continue
            if (GeoValidator.problems(p).isEmpty()) return p
        }
        return fallback(id, d, seed)
    }

    /** A fixed rotation puzzle; the unit tests prove it valid. */
    fun fallback(id: String, difficulty: Int, seed: Long): GeoPuzzle {
        val fig = Figure(3, 3, listOf(1, 1, 1, 1, 0, 0, 0, 1, 0))
        return assemble(GeoFamily.ROTATION, difficulty, seed, listOf(fig), -1, 1, GeoRules.rotationToken(90), fig.rotate(90),
            listOf(fig.rotate(180), fig.rotate(270), fig.rotate(90).flipH()), Random(seed))!!.copy(id = id)
    }

    private fun build(f: GeoFamily, d: Int, rnd: Random, seed: Long): GeoPuzzle? = when (f) {
        GeoFamily.MATRIX -> matrix(d, rnd, seed)
        GeoFamily.ROTATION -> rotation(d, rnd, seed)
        GeoFamily.MIRROR -> mirror(d, rnd, seed)
        GeoFamily.SILHOUETTE -> silhouette(d, rnd, seed)
        GeoFamily.ABSTRACT -> abstract(d, rnd, seed)
        GeoFamily.KEYHOLE -> keyhole(d, rnd, seed)
        GeoFamily.FRAGMENT -> fragment(d, rnd, seed)
        GeoFamily.SPATIAL -> spatial(d, rnd, seed)
        GeoFamily.PAPER_FOLD -> paperFold(d, rnd, seed)
        GeoFamily.CUBE_NET -> cubeNet(d, rnd, seed)
        GeoFamily.COMPOSE -> compose(d, rnd, seed)
        GeoFamily.ODD_ONE -> oddOne(d, rnd, seed)
        GeoFamily.SEQUENCE -> sequence(d, rnd, seed)
    }

    // ---- shared helpers ----

    private fun assemble(
        family: GeoFamily, d: Int, seed: Long, stimulus: List<Figure>, gap: Int, columns: Int, badge: String,
        correct: Figure, wrong: List<Figure>, rnd: Random,
    ): GeoPuzzle? {
        val picked = wrong.filter { it != correct }.distinct().take(3)
        if (picked.size < 3) return null
        val options = (picked + correct).shuffled(rnd)
        return GeoPuzzle("", family, d, seed, stimulus, gap, columns, badge, options, options.indexOf(correct))
    }

    /** A connected random blob of [cells] cells inside an n x n box. */
    internal fun blob(rnd: Random, n: Int, cells: Int): Figure {
        val g = IntArray(n * n)
        g[rnd.nextInt(n * n)] = 1
        var filled = 1
        while (filled < cells) {
            val from = g.indices.filter { g[it] == 1 }.random(rnd)
            val x = from % n + listOf(1, -1, 0, 0).let { dx -> dx[rnd.nextInt(4)] }
            val y = from / n + listOf(0, 0, 1, -1).let { dy -> dy[rnd.nextInt(4)] }
            if (x !in 0 until n || y !in 0 until n || g[y * n + x] == 1) continue
            g[y * n + x] = 1
            filled++
        }
        return Figure(n, n, g.toList())
    }

    /** A blob whose silhouette has no rotational or mirror symmetry, so every transformation is distinguishable. */
    internal fun chiral(rnd: Random, n: Int, cells: Int): Figure? {
        repeat(120) {
            val b = blob(rnd, n, cells)
            if (b.mask().isChiral()) return b
        }
        return null
    }

    private fun sizeFor(d: Int) = when (d) { 1 -> 3; 2 -> 4; 3 -> 4; 4 -> 5; else -> 5 }
    private fun cellsFor(n: Int) = when (n) { 3 -> 5; 4 -> 7; 5 -> 10; else -> n * n / 2 }

    /** Colours each filled cell with one of [colors] palette colours (one colour when 1). */
    private fun paint(f: Figure, rnd: Random, colors: Int): Figure =
        if (colors <= 1) f.recolor(1 + rnd.nextInt(3)) else f.map { if (it == 0) 0 else 1 + rnd.nextInt(colors) }

    /** Moves one filled cell to a free neighbouring spot, keeping the cell count, so the figure looks very similar. */
    private fun nudge(f: Figure, rnd: Random): Figure? {
        repeat(30) {
            val filled = f.cells.indices.filter { f.cells[it] != 0 }.random(rnd)
            val free = f.cells.indices.filter { f.cells[it] == 0 }
            if (free.isEmpty()) return null
            val to = free.random(rnd)
            val c = f.cells.toMutableList()
            c[to] = c[filled]; c[filled] = 0
            return Figure(f.w, f.h, c)
        }
        return null
    }

    // ---- families ----

    private fun rotation(d: Int, rnd: Random, seed: Long): GeoPuzzle? {
        val n = sizeFor(d)
        val fig = paint(chiral(rnd, n, cellsFor(n)) ?: return null, rnd, if (d >= 3) 2 else 1)
        val angle = listOf(90, 180, 270).random(rnd)
        val answer = fig.rotate(angle)
        val others = listOf(90, 180, 270).filter { it != angle }.map { fig.rotate(it) }
        val pool = (others + listOf(answer.flipH(), fig)).shuffled(rnd)
        return assemble(GeoFamily.ROTATION, d, seed, listOf(fig), -1, 1, GeoRules.rotationToken(angle), answer, pool, rnd)
    }

    private fun mirror(d: Int, rnd: Random, seed: Long): GeoPuzzle? {
        val n = sizeFor(d)
        val fig = paint(chiral(rnd, n, cellsFor(n)) ?: return null, rnd, if (d >= 4) 2 else 1)
        val tokens = mutableListOf(listOf(GeoRules.FLIP_H, GeoRules.FLIP_V).random(rnd))
        if (d >= 3) tokens += GeoRules.rotationToken(listOf(90, 180, 270).random(rnd))
        val badge = tokens.joinToString(GeoRules.ARROW)
        val answer = GeoRules.applyBadge(fig, badge) ?: return null
        val others = fig.dihedral().filter { it != answer }
        val plain = listOf(fig, fig.flipH(), fig.flipV()).filter { it != answer }
        val pool = (plain + others.shuffled(rnd)).distinct()
        return assemble(GeoFamily.MIRROR, d, seed, listOf(fig), -1, 1, badge, answer, pool, rnd)
    }

    private fun silhouette(d: Int, rnd: Random, seed: Long): GeoPuzzle? {
        val n = if (d <= 2) 4 else 5
        val base = blob(rnd, n, cellsFor(n))
        val turn = if (d >= 3) listOf(90, 180, 270).random(rnd) else 0
        val exp = base.rotate(turn)
        val orbit = exp.rotations()
        val masks = mutableListOf<Figure>()
        repeat(12) {
            val m = nudge(exp, rnd)?.takeIf { cand -> orbit.none { it == cand } && cand !in masks }
            if (m != null) masks += m
        }
        if (masks.size < 3) return null
        fun toned(m: Figure) = m.map { if (it == 0) 0 else 1 + rnd.nextInt(2) }
        return assemble(
            GeoFamily.SILHOUETTE, d, seed, listOf(base.recolor(Figure.DARK)), -1, 1,
            if (turn == 0) "" else GeoRules.rotationToken(turn), toned(exp), masks.map(::toned), rnd,
        )
    }

    private fun abstract(d: Int, rnd: Random, seed: Long): GeoPuzzle? {
        val n = if (d <= 2) 4 else 5
        val fig = paint(chiral(rnd, n, cellsFor(n) + d / 2) ?: return null, rnd, if (d >= 3) 2 else 1)
        val answer = fig.rotate(listOf(90, 180, 270).random(rnd))
        val wrong = mutableListOf(fig.flipH().rotate(90 * rnd.nextInt(4)), fig.flipV().rotate(90 * rnd.nextInt(4)))
        repeat(8) { nudge(answer, rnd)?.let { wrong += it } }
        return assemble(GeoFamily.ABSTRACT, d, seed, listOf(fig), -1, 1, "", answer, wrong.filter { !fig.inRotationOrbit(it) }, rnd)
    }

    private fun keyhole(d: Int, rnd: Random, seed: Long): GeoPuzzle? {
        val n = sizeFor(d)
        val hole = chiral(rnd, n, cellsFor(n)) ?: return null
        val key = hole.rotate(90 * (1 + rnd.nextInt(3))).recolor(1)
        val wrong = mutableListOf<Figure>()
        wrong += key.flipH().rotate(90 * rnd.nextInt(4))
        repeat(10) { nudge(key, rnd)?.let { wrong += it.rotate(90 * rnd.nextInt(4)) } }
        if (d <= 2) wrong += key.flipV()
        val valid = wrong.filter { !GeoRules.fits(it, hole) }
        return assemble(GeoFamily.KEYHOLE, d, seed, listOf(hole.recolor(Figure.DARK)), -1, 1, "", key, valid, rnd)
    }

    private fun fragment(d: Int, rnd: Random, seed: Long): GeoPuzzle? {
        val q = if (d <= 2) 3 else 4
        val n = q * 2
        val quarter = List(q * q) { if (rnd.nextInt(100) < 55) 1 + rnd.nextInt(if (d >= 3) 3 else 2) else 0 }
        val full = Figure(n, n, List(n * n) { i ->
            val x = i % n; val y = i / n
            quarter[(if (y < q) y else n - 1 - y) * q + (if (x < q) x else n - 1 - x)]
        })
        val k = if (d <= 3) 2 else 3
        val x0 = rnd.nextInt(q - k + 1)
        val y0 = rnd.nextInt(q - k + 1)
        val patch = Figure(k, k, List(k * k) { full[x0 + it % k, y0 + it / k] })
        if (patch.cells.toSet().size < 2) return null
        val hidden = full.cells.toMutableList()
        for (py in 0 until k) for (px in 0 until k) hidden[(y0 + py) * n + x0 + px] = Figure.HIDDEN
        val wrong = (0 until 12).mapNotNull {
            val c = patch.cells.toMutableList()
            repeat(1 + rnd.nextInt(if (d >= 3) 1 else 2)) {
                val i = rnd.nextInt(c.size)
                c[i] = (0..(if (d >= 3) 3 else 2)).filter { v -> v != c[i] }.random(rnd)
            }
            Figure(k, k, c)
        }
        return assemble(GeoFamily.FRAGMENT, d, seed, listOf(Figure(n, n, hidden)), -1, 1, "", patch, wrong, rnd)
    }

    private fun spatial(d: Int, rnd: Random, seed: Long): GeoPuzzle? {
        val n = sizeFor(d)
        val fig = paint(chiral(rnd, n, cellsFor(n)) ?: return null, rnd, if (d >= 4) 2 else 1)
        val tokens = List(if (d <= 2) 2 else if (d == 3) 3 else 4) {
            listOf(GeoRules.rotationToken(90), GeoRules.rotationToken(180), GeoRules.rotationToken(270), GeoRules.FLIP_H, GeoRules.FLIP_V).random(rnd)
        }
        val badge = tokens.joinToString(GeoRules.ARROW)
        val answer = GeoRules.applyBadge(fig, badge) ?: return null
        if (answer == fig) return null
        fun run(ts: List<String>) = GeoRules.applyBadge(fig, ts.joinToString(GeoRules.ARROW))
        val near = buildList {
            add(run(tokens.reversed()))
            tokens.indices.forEach { i -> add(run(tokens.filterIndexed { j, _ -> j != i })) }
            add(fig)
        }.filterNotNull() + fig.dihedral().shuffled(rnd)
        return assemble(GeoFamily.SPATIAL, d, seed, listOf(fig), -1, 1, badge, answer, near.filter { it != answer }, rnd)
    }

    private fun paperFold(d: Int, rnd: Random, seed: Long): GeoPuzzle? {
        val twoFolds = d >= 3
        val mirrorX = if (twoFolds) true else rnd.nextBoolean()
        val mirrorY = if (twoFolds) true else !mirrorX
        val sw = if (mirrorX) 3 else 6
        val sh = if (mirrorY) 3 else 6
        val holes = (0 until sw * sh).shuffled(rnd).take(if (d <= 1) 1 else if (d <= 3) 2 else 3)
        val sheet = Figure(sw, sh, List(sw * sh) { if (it in holes) Figure.HOLE else 1 })
        val answer = GeoRules.unfold(sheet, mirrorX, mirrorY)
        val badge = buildList { if (mirrorX) add("┃"); if (mirrorY) add("━") }.let { if (rnd.nextBoolean()) it else it.reversed() }.joinToString(" ")
        val wrong = listOf(false to false, true to false, false to true, true to true)
            .filter { it != (mirrorX to mirrorY) }
            .map { (x, y) -> placeSheet(sheet, answer.w, answer.h, x, y) }.toMutableList()
        val moved = holes.first().let { h -> (0 until sw * sh).filter { it !in holes }.random(rnd).let { to -> holes.map { if (it == h) to else it } } }
        wrong += GeoRules.unfold(Figure(sw, sh, List(sw * sh) { if (it in moved) Figure.HOLE else 1 }), mirrorX, mirrorY)
        return assemble(GeoFamily.PAPER_FOLD, d, seed, listOf(sheet), -1, 1, badge, answer, wrong.shuffled(rnd), rnd)
    }

    /** A wrong unfolding: the sheet's holes mirrored over the wrong creases (or none) on a paper of the correct size. */
    private fun placeSheet(sheet: Figure, pw: Int, ph: Int, mx: Boolean, my: Boolean): Figure {
        val out = IntArray(pw * ph) { 1 }
        for (sy in 0 until sheet.h) for (sx in 0 until sheet.w) {
            if (sheet[sx, sy] != Figure.HOLE) continue
            val bx = pw - sheet.w + sx
            val by = ph - sheet.h + sy
            for (x in if (mx) listOf(bx, pw - 1 - bx) else listOf(bx)) for (y in if (my) listOf(by, ph - 1 - by) else listOf(by)) out[y * pw + x] = Figure.HOLE
        }
        return Figure(pw, ph, out.toList())
    }

    private fun cubeNet(d: Int, rnd: Random, seed: Long): GeoPuzzle? {
        val valid = Hexominoes.fixed.filter { GeoRules.isCubeNet(pad(it)) }
        val invalid = Hexominoes.fixed.filter { !GeoRules.isCubeNet(pad(it)) }
        val net = valid.random(rnd)
        fun place(shape: Figure) = pad(shape.symmetry(rnd.nextInt(8)).let { trim(it) })
        val answer = place(net)
        val netClass = Hexominoes.canonical(net)
        val seenClasses = mutableSetOf(netClass)
        val picks = mutableListOf<Figure>()
        val near = if (d >= 3) invalid.filter { cand -> near(net, cand) } else emptyList()
        for (cand in near.shuffled(rnd) + invalid.shuffled(rnd)) {
            if (picks.size == 3) break
            if (seenClasses.add(Hexominoes.canonical(cand))) picks += place(cand)
        }
        return assemble(GeoFamily.CUBE_NET, d, seed, emptyList(), -1, 1, "", answer, picks, rnd)
    }

    /** True when some orientation of [b] differs from [a] by exactly one moved cell. */
    private fun near(a: Figure, b: Figure): Boolean {
        val ca = cellSet(a)
        return Hexominoes.variants(b).any { v -> (cellSet(v) - ca).size == 1 }
    }

    private fun cellSet(f: Figure) = f.cells.indices.filter { f.cells[it] != 0 }.map { it % f.w to it / f.w }.toSet()

    private fun trim(f: Figure): Figure = Hexominoes.normalize(cellSet(f))

    private fun pad(f: Figure): Figure {
        val out = IntArray(36)
        for (y in 0 until f.h) for (x in 0 until f.w) if (x < 6 && y < 6) out[y * 6 + x] = f[x, y]
        return Figure(6, 6, out.toList())
    }

    private fun compose(d: Int, rnd: Random, seed: Long): GeoPuzzle? {
        val n = if (d <= 2) 4 else 5
        val cells = n * n * (30 + 4 * d) / 100
        val a = blob(rnd, n, cells).recolor(1)
        val b = blob(rnd, n, cells).recolor(2)
        val ops = listOf("∪", "∩", "−", "Δ")
        val op = ops.random(rnd)
        val results = ops.associateWith { (GeoRules.combine(a, b, it) ?: return null).recolor(3) }
        if (results.values.any { it.count == 0 }) return null
        val wrong = (ops - op).map { results.getValue(it) } + listOfNotNull(nudge(results.getValue(op), rnd), nudge(results.getValue(op), rnd))
        return assemble(GeoFamily.COMPOSE, d, seed, listOf(a, b), -1, 2, "A $op B", results.getValue(op), if (d >= 3) wrong.reversed() else wrong, rnd)
    }

    private fun oddOne(d: Int, rnd: Random, seed: Long): GeoPuzzle? {
        val n = sizeFor(d)
        val base = chiral(rnd, n, cellsFor(n)) ?: return null
        val turns = (0..3).shuffled(rnd).take(4)
        fun coloured(f: Figure) = if (d >= 3) f.recolor(1 + rnd.nextInt(3)) else f.recolor(1)
        val same = turns.take(3).map { coloured(base.rotate(90 * it)) }
        val odd = coloured(base.flipH().rotate(90 * turns[3]))
        return assemble(GeoFamily.ODD_ONE, d, seed, emptyList(), -1, 1, "", odd, same, rnd)
    }

    private fun randomXf(rnd: Random, d: Int): Xf = when {
        d <= 1 -> Xf(1 + rnd.nextInt(3), 0)
        d == 2 -> Xf(listOf(1, 2, 3, 4, 6).random(rnd), 0)
        d == 3 -> if (rnd.nextBoolean()) Xf(rnd.nextInt(8), 1 + rnd.nextInt(2)) else Xf(1 + rnd.nextInt(7), 0)
        else -> Xf(1 + rnd.nextInt(7), 1 + rnd.nextInt(2))
    }

    private fun sequence(d: Int, rnd: Random, seed: Long): GeoPuzzle? {
        val n = sizeFor(d).coerceAtMost(4)
        val base = paint(chiral(rnd, n, cellsFor(n)) ?: return null, rnd, if (d >= 3) 2 else 1)
        val t = randomXf(rnd, d)
        val shown = if (d >= 4) 5 else 4
        val panels = mutableListOf(base)
        repeat(shown) { panels += t.apply(panels.last()) }
        val answer = panels.last()
        val wrong = Xf.all.filter { it != t }.shuffled(rnd).map { it.apply(panels[shown - 1]) } + listOf(panels[shown - 1], panels[shown - 2])
        val near = Xf.all.filter { it.sym == t.sym && it != t || it.shift == t.shift && it != t }.map { it.apply(panels[shown - 1]) }
        return assemble(GeoFamily.SEQUENCE, d, seed, panels, shown, shown + 1, "", answer, near.shuffled(rnd) + wrong, rnd)
    }

    private fun matrix(d: Int, rnd: Random, seed: Long): GeoPuzzle? {
        val size = if (d <= 2) 2 else 3
        val n = sizeFor(d).coerceAtMost(4)
        val base = paint(chiral(rnd, n, cellsFor(n)) ?: return null, rnd, 1)
        val tr: Xf
        val tc: Xf
        when (d) {
            1 -> { tr = Xf(1 + rnd.nextInt(3), 0); tc = Xf(0, 1 + rnd.nextInt(2)) }
            2 -> { tr = Xf(listOf(2, 4, 6).random(rnd), 0); tc = Xf(0, 1 + rnd.nextInt(2)) }
            else -> { tr = randomXf(rnd, d); tc = randomXf(rnd, d) }
        }
        fun pow(t: Xf, f: Figure, times: Int): Figure { var r = f; repeat(times) { r = t.apply(r) }; return r }
        fun cell(r: Int, c: Int) = pow(tr, pow(tc, base, c), r)
        if (pow(tc, pow(tr, base, size - 1), size - 1) != cell(size - 1, size - 1)) return null
        val panels = List(size * size) { cell(it / size, it % size) }
        val answer = panels.last()
        val wrong = listOf(
            cell(size - 2, size - 1), cell(size - 1, size - 2), pow(tr, pow(tc, base, size), size - 1),
            pow(tr, pow(tc, base, size - 1), size), cell(0, 0),
        ) + Xf.all.shuffled(rnd).map { it.apply(answer) }
        return assemble(GeoFamily.MATRIX, d, seed, panels, size * size - 1, size, "", answer, wrong, rnd)
    }
}

/** All 216 fixed hexominoes, enumerated once by growing polyominoes cell by cell. */
internal object Hexominoes {
    val fixed: List<Figure> by lazy {
        var level: Set<Set<Pair<Int, Int>>> = setOf(setOf(0 to 0))
        repeat(5) {
            level = level.flatMap { shape ->
                shape.flatMap { (x, y) -> listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1) }
                    .filter { it !in shape }.map { norm(shape + it) }
            }.toSet()
        }
        level.map { normalize(it) }.sortedBy { it.encode() }
    }

    private fun norm(cells: Set<Pair<Int, Int>>): Set<Pair<Int, Int>> {
        val mx = cells.minOf { it.first }
        val my = cells.minOf { it.second }
        return cells.map { it.first - mx to it.second - my }.toSet()
    }

    fun normalize(cells: Set<Pair<Int, Int>>): Figure {
        val c = norm(cells)
        val w = c.maxOf { it.first } + 1
        val h = c.maxOf { it.second } + 1
        return Figure(w, h, List(w * h) { if ((it % w to it / w) in c) 1 else 0 })
    }

    private fun cells(f: Figure) = f.cells.indices.filter { f.cells[it] != 0 }.map { it % f.w to it / f.w }.toSet()

    fun variants(f: Figure): List<Figure> = f.dihedral().map { normalize(cells(it)) }

    /** Orientation-independent identity of a hexomino. */
    fun canonical(f: Figure): String = variants(f).minOf { it.encode() }
}
