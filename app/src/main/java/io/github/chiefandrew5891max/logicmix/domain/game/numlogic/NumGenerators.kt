package io.github.chiefandrew5891max.logicmix.domain.game.numlogic

import io.github.chiefandrew5891max.logicmix.domain.game.NumFamily
import io.github.chiefandrew5891max.logicmix.domain.game.NumberSequencePuzzle
import kotlin.random.Random

/**
 * Deterministic generators for the Number Logic families. Each draw is checked by [NumValidator], which re-solves the
 * puzzle independently; failures retry a bounded number of times and then fall back to a fixed known-valid puzzle.
 */
object NumGenerators {
    private const val ATTEMPTS = 60
    private val CHOICES = listOf("+", "−", "×", "÷")
    private val GLYPHS = listOf("●", "■", "▲")

    fun generate(family: NumFamily, difficulty: Int, seed: Long, id: String = ""): NumberSequencePuzzle {
        val d = difficulty.coerceIn(1, 5)
        for (attempt in 0 until ATTEMPTS) {
            val rnd = Random(seed * 1_000_003L + attempt * 7_919L + family.ordinal)
            val raw = build(family, d, rnd)?.copy(id = id, difficulty = d, seed = seed, family = family) ?: continue
            val p = withTexts(raw) ?: continue
            if (NumValidator.problems(p).isEmpty()) return p
        }
        return fallback(id, d, seed)
    }

    /** Attaches the question and both hints derived from the puzzle's own data; no wording means no puzzle. */
    private fun withTexts(p: NumberSequencePuzzle): NumberSequencePuzzle? {
        val t = NumTexts.derive(p) ?: return null
        return p.copy(question = t.question.encode(), hints = listOf(t.hint1.encode(), t.hint2.encode()))
    }

    const val FALLBACK_RULE = "arith:2,2"

    fun fallback(id: String, d: Int, seed: Long): NumberSequencePuzzle = withTexts(
        NumberSequencePuzzle(id, d, "next", listOf(2, 4, 6, 8, 10), 12, "+2", emptyList(), NumFamily.SEQUENCE, emptyList(), emptyList(), FALLBACK_RULE, seed),
    )!!

    private fun build(f: NumFamily, d: Int, rnd: Random): NumberSequencePuzzle? = when (f) {
        NumFamily.SEQUENCE -> sequence(d, rnd)
        NumFamily.ORDER_OPS -> orderOps(d, rnd)
        NumFamily.PARENTHESES -> parentheses(d, rnd)
        NumFamily.MISSING_OP -> missingOp(d, rnd)
        NumFamily.PYRAMID -> pyramid(d, rnd)
        NumFamily.MATRIX -> matrix(d, rnd)
        NumFamily.BALANCE -> balance(d, rnd)
        NumFamily.HIDDEN_RELATION -> hiddenRelation(d, rnd)
        NumFamily.ATTENTION -> attention(d, rnd)
        NumFamily.MIXED -> mixed(d, rnd)
    }

    private fun puzzle(
        rows: List<List<String>>, answer: Int, explanation: String,
        choices: List<String> = emptyList(), sequence: List<Int> = emptyList(), rule: String = "",
    ) = NumberSequencePuzzle("", 1, "next", sequence, answer, explanation, emptyList(), NumFamily.SEQUENCE, rows, choices, rule, 0)

    private fun Random.between(a: Int, b: Int) = a + nextInt(b - a + 1)
    private fun Random.sign() = if (nextBoolean()) 1 else -1

    // ---- sequences ----

    private fun sequence(d: Int, rnd: Random): NumberSequencePuzzle? {
        val rules = listOf<() -> Pair<SeqRule, Int>>(
            { SeqRule.arithmetic(rnd.between(-5, 20).toLong(), (rnd.between(2, 4 + 2 * d) * rnd.sign()).toLong()) to 5 },
            { SeqRule.geometric(rnd.between(1, 4).toLong(), rnd.between(2, 3).toLong()) to 5 },
            { SeqRule.alternatingAdd(rnd.between(1, 20).toLong(), rnd.between(2, 9).toLong(), (-rnd.between(1, 6)).toLong()) to 7 },
            { SeqRule.secondOrder(rnd.between(0, 10).toLong(), rnd.between(1, 4).toLong(), rnd.between(1, 3).toLong()) to 6 },
            { SeqRule.interleaved(rnd.between(1, 15).toLong(), rnd.between(2, 6).toLong(), rnd.between(30, 60).toLong(), -rnd.between(2, 7).toLong()) to 8 },
            { SeqRule.affine(rnd.between(1, 4).toLong(), 2, rnd.between(-3, 5).toLong().takeIf { it != 0L } ?: 1L) to 6 },
            { SeqRule.fibonacci(rnd.between(1, 4).toLong(), rnd.between(1, 6).toLong()) to 7 },
            { SeqRule.mixed(rnd.between(1, 5).toLong(), rnd.between(2, 3).toLong(), rnd.between(1, 6).toLong()) to 7 },
        )
        val (rule, shown) = rules[rnd.nextInt(minOf(rules.size, 2 + 2 * d))]()
        val all = rule.terms(shown + 1) ?: return null
        val given = all.take(shown)
        return puzzle(emptyList(), all.last().toInt(), rule.text, sequence = given.map { it.toInt() }, rule = rule.code)
    }

    // ---- expressions ----

    /** Random tree over [leaves] numbers merged pairwise in order; every step must be an exact integer within bounds. */
    internal fun randomExpr(rnd: Random, leaves: Int, ops: List<Op>, maxLeaf: Int, allowNegative: Boolean, vars: List<String> = emptyList(), env: Map<String, Long> = emptyMap()): Expr? {
        val nodes = MutableList(leaves) { if (vars.isNotEmpty() && rnd.nextInt(3) > 0) Expr.Var(vars.random(rnd)) else Expr.Num(rnd.between(2, maxLeaf).toLong()) as Expr }
        var guard = 0
        while (nodes.size > 1) {
            if (guard++ > 80) return null
            val i = rnd.nextInt(nodes.size - 1)
            val op = ops.random(rnd)
            var r = nodes[i + 1]
            val lv = Exprs.eval(nodes[i], env) ?: return null
            if (op == Op.DIV && r is Expr.Num) {
                val divisors = (2..maxLeaf).filter { lv.isInt && lv.n != 0L && lv.n % it == 0L }
                if (divisors.isEmpty()) continue
                r = Expr.Num(divisors.random(rnd).toLong())
            }
            val e = Expr.Bin(op, nodes[i], r)
            val v = Exprs.eval(e, env) ?: continue
            if (!v.isInt || Math.abs(v.n) > 500 || (!allowNegative && v.n < 0) || (op == Op.MUL && v.n == 0L)) continue
            nodes[i] = e
            nodes.removeAt(i + 1)
        }
        return nodes[0].takeIf { Exprs.trace(it, env) != null }
    }

    private fun exprPuzzle(e: Expr, extra: List<List<String>> = emptyList(), env: Map<String, Long> = emptyMap()): NumberSequencePuzzle? {
        val v = Exprs.eval(e, env) ?: return null
        val trace = Exprs.trace(e, env) ?: return null
        val text = Exprs.render(e)
        return puzzle(extra + listOf(listOf(text, "=", "?")), v.n.toInt(), trace.joinToString(" → "))
    }

    private fun orderOps(d: Int, rnd: Random): NumberSequencePuzzle? {
        val ops = if (d <= 1) listOf(Op.ADD, Op.SUB, Op.MUL) else Op.entries
        val e = randomExpr(rnd, 2 + minOf(d, 3) + (if (d >= 5) 1 else 0), ops, 6 + 2 * d, d >= 3) ?: return null
        if (Exprs.hasParens(e) || Exprs.leaves(e) < 3) return null
        val text = Exprs.render(e)
        // The point of the puzzle is that precedence matters, so the careless left-to-right reading must differ.
        if (Exprs.naiveLeftToRight(text) == Exprs.eval(e)) return null
        return exprPuzzle(e)
    }

    private fun parentheses(d: Int, rnd: Random): NumberSequencePuzzle? {
        val ops = if (d <= 1) listOf(Op.ADD, Op.SUB, Op.MUL) else Op.entries
        val e = randomExpr(rnd, 3 + (if (d >= 3) 1 else 0) + (if (d >= 5) 1 else 0), ops, 5 + 2 * d, d >= 2) ?: return null
        val text = Exprs.render(e)
        if (!text.contains('(')) return null
        // Dropping the brackets must change the value, otherwise they teach nothing.
        val flat = Exprs.parse(text.replace("(", "").replace(")", ""))?.let { Exprs.eval(it) }
        if (flat == null || flat == Exprs.eval(e)) return null
        return exprPuzzle(e)
    }

    private fun missingOp(d: Int, rnd: Random): NumberSequencePuzzle? {
        val count = if (d <= 2) 2 else 3
        val ops = List(count) { Op.entries.random(rnd) }
        val nums = List(count + 1) { rnd.between(2, 6 + 2 * d) }
        val text = buildString { append(nums[0]); ops.forEachIndexed { i, o -> append(" ${o.sym} ${nums[i + 1]}") } }
        val e = Exprs.parse(text) ?: return null
        val target = Exprs.eval(e) ?: return null
        if (!target.isInt || Exprs.trace(e) == null || Math.abs(target.n) > 500) return null
        val hide = rnd.nextInt(count)
        val matching = CHOICES.indices.filter { c ->
            val t = buildString { append(nums[0]); ops.forEachIndexed { i, o -> append(" ${if (i == hide) CHOICES[c] else o.sym} ${nums[i + 1]}") } }
            Exprs.parse(t)?.let { Exprs.eval(it) } == target
        }
        if (matching.size != 1) return null
        val shown = buildString { append(nums[0]); ops.forEachIndexed { i, o -> append(" ${if (i == hide) "?" else o.sym} ${nums[i + 1]}") } } + " = ${target.n}"
        return puzzle(listOf(listOf(shown)), matching[0], "$text = ${target.n}", CHOICES)
    }

    // ---- pyramids ----

    private fun pyramid(d: Int, rnd: Random): NumberSequencePuzzle? {
        val n = when (d) { 1, 2 -> 3; 3, 4 -> 4; else -> 5 }
        val base = List(n) { rnd.between(1, 3 + 3 * d) }
        val full = mutableListOf(base)
        repeat(n - 1) { full.add(0, full[0].zipWithNext { a, b -> a + b }) }
        val cells = full.flatMapIndexed { r, row -> row.indices.map { r to it } }
        val asked = cells.random(rnd)
        val blanks = (cells - asked).shuffled(rnd).take(d - 1)
        val rows = full.mapIndexed { r, row ->
            row.mapIndexed { c, v -> if (r to c == asked) "?" else if (r to c in blanks) "_" else v.toString() }
        }
        return puzzle(rows, full[asked.first][asked.second], describeRelation(full, asked))
    }

    private fun describeRelation(full: List<List<Int>>, cell: Pair<Int, Int>): String {
        val (r, c) = cell
        if (r < full.size - 1) {
            val a = full[r + 1][c]; val b = full[r + 1][c + 1]
            return "$a + $b = ${full[r][c]}"
        }
        val above = full[r - 1]
        val left = if (c > 0) full[r][c - 1] else null
        val text = if (left != null) "${above[c - 1]} − $left = ${full[r][c]}" else "${above[0]} − ${full[r][1]} = ${full[r][0]}"
        return text
    }

    // ---- matrices ----

    private fun matrix(d: Int, rnd: Random): NumberSequencePuzzle? {
        val both = d >= 4
        val op = if (both) listOf(Op.ADD, Op.MUL).random(rnd) else listOf(Op.ADD, Op.SUB, Op.MUL).random(rnd)
        val k = if (d >= 3 && !both) rnd.between(-4, 4) else 0
        fun f(a: Long, b: Long) = (op.apply(Frac.of(a), Frac.of(b))!!.n + k)
        val grid = Array(3) { LongArray(3) }
        for (r in 0..1) for (c in 0..1) grid[r][c] = rnd.between(1, 3 + 2 * d).toLong()
        for (r in 0..1) grid[r][2] = f(grid[r][0], grid[r][1])
        for (c in 0..1) grid[2][c] = if (both) f(grid[0][c], grid[1][c]) else rnd.between(1, 3 + 2 * d).toLong()
        grid[2][2] = f(grid[2][0], grid[2][1])
        if (both && grid[2][2] != f(grid[0][2], grid[1][2])) return null
        if (grid.any { row -> row.any { Math.abs(it) > 999 } }) return null
        val rows = grid.mapIndexed { r, row -> row.mapIndexed { c, v -> if (r == 2 && c == 2) "?" else v.toString() } }
        val sym = op.sym
        val example = "${grid[0][0]} $sym ${grid[0][1]}${if (k != 0) " ${if (k > 0) "+" else "−"} ${Math.abs(k)}" else ""} = ${grid[0][2]}"
        return puzzle(rows, grid[2][2].toInt(), example, rule = "$sym,$k,${if (both) 1 else 0}")
    }

    // ---- balance ----

    private fun balance(d: Int, rnd: Random): NumberSequencePuzzle? {
        val vars = GLYPHS.take(if (d <= 2) 1 else if (d <= 4) 2 else 3)
        val env = vars.associateWith { rnd.between(1, 6 + d).toLong() }
        fun sideExpr(): Expr? {
            val terms = List(rnd.between(2, 3 + (d + 1) / 2)) {
                if (rnd.nextInt(4) == 0) Expr.Num(rnd.between(1, 9).toLong()) else Expr.Var(vars.random(rnd))
            }
            if (terms.none { it is Expr.Var }) return null
            return terms.reduce { a, b -> Expr.Bin(Op.ADD, a, b) }
        }
        val equations = mutableListOf<Expr>()
        repeat(vars.size) { equations += sideExpr() ?: return null }
        val question = sideExpr() ?: return null
        val rows = equations.map { listOf(Exprs.render(it), "=", Exprs.eval(it, env)!!.n.toString()) } + listOf(listOf(Exprs.render(question), "=", "?"))
        val answer = Exprs.eval(question, env)!!.n.toInt()
        return puzzle(rows, answer, env.entries.joinToString(", ") { "${it.key} = ${it.value}" })
    }

    // ---- hidden relation ----

    private fun hiddenRelation(d: Int, rnd: Random): NumberSequencePuzzle? {
        val p = rnd.between(2, 4 + d).toLong()
        val q = rnd.between(-6, 9).toLong()
        val kind = rnd.nextInt(if (d <= 2) 1 else if (d <= 3) 2 else 3)
        if (kind == 2 && q % 4 == 0L) return null
        val f: (Long) -> Long = when (kind) { 0 -> { n -> p * n + q }; 1 -> { n -> n * n + q }; else -> { n -> n * (n + (q % 4)) } }
        val code = when (kind) { 0 -> "lin,$p,$q"; 1 -> "sq,$q"; else -> "near,${q % 4}" }
        val text = when (kind) { 0 -> "n × $p ${if (q >= 0) "+ $q" else "− ${-q}"}"; 1 -> "n² ${if (q >= 0) "+ $q" else "− ${-q}"}"; else -> "n × (n ${if (q % 4 >= 0) "+" else "−"} ${Math.abs(q % 4)})" }
        val inputs = (1..9).shuffled(rnd).take(5)
        val rows = inputs.take(4).map { listOf(it.toString(), "→", f(it.toLong()).toString()) } + listOf(listOf(inputs[4].toString(), "→", "?"))
        return puzzle(rows, f(inputs[4].toLong()).toInt(), text, rule = code)
    }

    // ---- attention ----

    private fun attention(d: Int, rnd: Random): NumberSequencePuzzle? {
        val count = 6 + d
        val nums = List(count) { rnd.between(if (d <= 2) 5 else 10, if (d <= 2) 40 else 99) }.distinct()
        if (nums.size < count - 1) return null
        val k = rnd.between(2, 5)
        val t = rnd.between(15, 60)
        val codes = listOf("sumdiv,$k", "cntdiv,$k", "range", "cntgt,$t", "digits", "second")
        val code = codes[rnd.nextInt(minOf(codes.size, 2 + d))]
        val answer = NumValidator.attentionAnswer(code, nums) ?: return null
        if (answer == 0 || answer > 999) return null
        return puzzle(listOf(nums.map { it.toString() }), answer, attentionExplanation(code, nums, answer), rule = code)
    }

    internal fun attentionExplanation(code: String, nums: List<Int>, answer: Int): String {
        val k = code.substringAfter(",", "").toIntOrNull()
        val sorted = nums.sortedDescending()
        return when (code.substringBefore(",")) {
            "sumdiv" -> nums.filter { it % k!! == 0 }.joinToString(" + ") + " = $answer"
            "cntdiv" -> nums.filter { it % k!! == 0 }.joinToString(", ") + " → $answer"
            "cntgt" -> nums.filter { it > k!! }.joinToString(", ") + " → $answer"
            "range" -> "${nums.max()} − ${nums.min()} = $answer"
            "digits" -> nums.joinToString(" + ") { n -> n.toString().toList().joinToString(" + ") } + " = $answer"
            else -> "${sorted[0]} − ${sorted[1]} = $answer"
        }
    }

    // ---- mixed ----

    private fun mixed(d: Int, rnd: Random): NumberSequencePuzzle? {
        val names = listOf("a", "b", "c")
        val env = names.associateWith { rnd.between(2, 9).toLong() }
        val e = randomExpr(rnd, 3 + (d + 1) / 2, Op.entries, 7, true, names, env) ?: return null
        if (Exprs.leaves(e) < 4 || e.let { Exprs.render(it) }.none { it in "abc" }) return null
        return exprPuzzle(e, listOf(listOf(env.entries.joinToString(", ") { "${it.key} = ${it.value}" })), env)
    }
}
