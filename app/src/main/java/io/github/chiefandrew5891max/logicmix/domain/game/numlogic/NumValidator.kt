package io.github.chiefandrew5891max.logicmix.domain.game.numlogic

import io.github.chiefandrew5891max.logicmix.domain.game.NumFamily
import io.github.chiefandrew5891max.logicmix.domain.game.NumberSequencePuzzle

/** Re-solves a generated puzzle from its visible data only and reports every inconsistency (empty list = valid). */
object NumValidator {
    private val CHOICES = listOf("+", "−", "×", "÷")

    fun problems(p: NumberSequencePuzzle): List<String> = buildList {
        if (p.family != NumFamily.SEQUENCE || p.rows.isNotEmpty()) {
            if (p.rows.isEmpty()) add("no rows")
        }
        if (p.choices.isNotEmpty() && p.choices.toSet().size != p.choices.size) add("duplicate choices")
        if (p.hints.isEmpty() || p.explanation.isBlank()) add("missing hints")
        if (isNotEmpty()) return@buildList
        val err = try { check(p) } catch (e: Exception) { "exception ${e.message}" }
        if (err != null) add(err)
    }

    private fun check(p: NumberSequencePuzzle): String? = when (p.family) {
        NumFamily.SEQUENCE -> if (p.rows.isNotEmpty()) "rows in sequence"
        else if (SeqSolver.candidates(p.sequence.map { it.toLong() }) == setOf(p.answer.toLong())) null else "sequence answer not unique"
        NumFamily.ORDER_OPS, NumFamily.PARENTHESES -> expr(p)
        NumFamily.MIXED -> expr(p)
        NumFamily.MISSING_OP -> missing(p)
        NumFamily.PYRAMID -> pyramid(p)
        NumFamily.MATRIX -> matrix(p)
        NumFamily.BALANCE -> balance(p)
        NumFamily.HIDDEN_RELATION -> hidden(p)
        NumFamily.ATTENTION -> attention(p)
    }

    private fun env(p: NumberSequencePuzzle): Map<String, Long> =
        if (p.family != NumFamily.MIXED) emptyMap() else p.rows[0][0].split(", ").associate { it.substringBefore(" = ") to it.substringAfter(" = ").toLong() }

    private fun expr(p: NumberSequencePuzzle): String? {
        val text = p.rows.last()[0]
        val e = Exprs.parse(text) ?: return "unparsable"
        val v = Exprs.eval(e, env(p)) ?: return "undefined"
        if (!v.isInt || v != Frac.of(p.answer.toLong())) return "wrong answer"
        if (Exprs.trace(e, env(p)) == null) return "inexact step"
        if (p.family == NumFamily.ORDER_OPS) {
            if (text.contains('(')) return "brackets in order-of-operations"
            if (Exprs.naiveLeftToRight(text) == v) return "precedence irrelevant"
        }
        if (p.family == NumFamily.PARENTHESES) {
            if (!text.contains('(')) return "no brackets"
            val flat = Exprs.parse(text.replace("(", "").replace(")", ""))?.let { Exprs.eval(it) }
            if (flat == null || flat == v) return "brackets irrelevant"
        }
        return null
    }

    private fun missing(p: NumberSequencePuzzle): String? {
        if (p.choices != CHOICES) return "bad choices"
        val line = p.rows[0][0]
        val target = line.substringAfter(" = ")
        val lhs = line.substringBefore(" = ")
        val good = CHOICES.indices.filter { i ->
            val e = Exprs.parse(lhs.replace("?", CHOICES[i])) ?: return@filter false
            Exprs.eval(e) == Frac.of(target.toLong())
        }
        return if (good == listOf(p.answer)) null else "operator not unique $good"
    }

    private fun pyramid(p: NumberSequencePuzzle): String? {
        val g = p.rows.map { row -> row.map { it.toIntOrNull() }.toMutableList<Int?>() }
        val n = g.size
        if (g.indices.any { g[it].size != it + 1 }) return "not a pyramid"
        var asked: Pair<Int, Int>? = null
        g.forEachIndexed { r, row -> row.indices.forEach { c -> if (p.rows[r][c] == "?") asked = r to c } }
        val (ar, ac) = asked ?: return "no asked cell"
        var changed = true
        while (changed) {
            changed = false
            for (r in 0 until n - 1) for (c in 0..r) {
                val top = g[r][c]; val l = g[r + 1][c]; val rt = g[r + 1][c + 1]
                val unknown = listOf(top, l, rt).count { it == null }
                if (unknown != 1) continue
                when {
                    top == null -> g[r][c] = l!! + rt!!
                    l == null -> g[r + 1][c] = top - rt!!
                    else -> g[r + 1][c + 1] = top - l
                }
                changed = true
            }
        }
        // "?" and "_" parse as null, so everything still null after propagation is undetermined
        if (g.any { row -> row.any { it == null } }) return "pyramid not determined"
        return if (g[ar][ac] == p.answer) null else "wrong pyramid answer"
    }

    private fun matrix(p: NumberSequencePuzzle): String? {
        val g = p.rows.map { r -> r.map { it.toLongOrNull() } }
        if (g.size != 3 || g.any { it.size != 3 } || g[2][2] != null) return "bad matrix"
        val ops = listOf(Op.ADD, Op.SUB, Op.MUL)
        fun f(op: Op, k: Long, a: Long, b: Long) = op.apply(Frac.of(a), Frac.of(b))!!.n + k
        val predictions = mutableSetOf<Long>()
        for (op in ops) for (k in -4L..4L) {
            if ((0..1).all { r -> f(op, k, g[r][0]!!, g[r][1]!!) == g[r][2] }) predictions += f(op, k, g[2][0] ?: return "gap", g[2][1] ?: return "gap")
            if ((0..1).all { c -> f(op, k, g[0][c]!!, g[1][c]!!) == g[2][c] }) predictions += f(op, k, g[0][2]!!, g[1][2]!!)
        }
        return if (predictions == setOf(p.answer.toLong())) null else "matrix not unique $predictions"
    }

    private fun balance(p: NumberSequencePuzzle): String? {
        val names = p.rows.flatMap { it[0].filter { c -> c in "●■▲" }.toList() }.distinct().map { it.toString() }
        val eqs = p.rows.dropLast(1).map { Exprs.parse(it[0])!! to it[2].toLong() }
        val q = Exprs.parse(p.rows.last()[0])!!
        val answers = mutableSetOf<Long>()
        fun rec(i: Int, env: Map<String, Long>) {
            if (i == names.size) {
                if (eqs.all { (e, t) -> Exprs.eval(e, env) == Frac.of(t) }) Exprs.eval(q, env)?.let { answers += it.n }
                return
            }
            for (v in 1L..20L) rec(i + 1, env + (names[i] to v))
        }
        rec(0, emptyMap())
        return if (answers == setOf(p.answer.toLong())) null else "balance not unique $answers"
    }

    private fun hidden(p: NumberSequencePuzzle): String? {
        val pairs = p.rows.dropLast(1).map { it[0].toLong() to it[2].toLong() }
        val x = p.rows.last()[0].toLong()
        val fs = mutableListOf<(Long) -> Long>()
        for (a in 1L..12L) for (b in -9L..12L) fs += { n -> a * n + b }
        for (b in -9L..12L) fs += { n -> n * n + b }
        for (b in -3L..3L) fs += { n -> n * (n + b) }
        val out = fs.filter { f -> pairs.all { f(it.first) == it.second } }.map { it(x) }.toSet()
        return if (out == setOf(p.answer.toLong())) null else "relation not unique $out"
    }

    fun attentionAnswer(query: String, nums: List<Int>): Int? {
        val k = Regex("(\\d+) ∣ n").find(query)?.groupValues?.get(1)?.toInt()
        val t = Regex("n > (\\d+)").find(query)?.groupValues?.get(1)?.toInt()
        return when {
            query.startsWith("Σ{") && k != null -> nums.filter { it % k == 0 }.sum()
            query.startsWith("#{") && k != null -> nums.count { it % k == 0 }
            query.startsWith("#{") && t != null -> nums.count { it > t }
            query == "max − min" -> nums.max() - nums.min()
            query == "Σ S(n)" -> nums.sumOf { n -> n.toString().sumOf { it - '0' } }
            query == "max₁ − max₂" -> nums.sortedDescending().let { it[0] - it[1] }
            else -> null
        }
    }

    private fun attention(p: NumberSequencePuzzle): String? {
        val nums = p.rows[1].map { it.toInt() }
        if (nums.toSet().size != nums.size) return "duplicate numbers"
        return if (attentionAnswer(p.rows[0][0], nums) == p.answer) null else "wrong attention answer"
    }
}
