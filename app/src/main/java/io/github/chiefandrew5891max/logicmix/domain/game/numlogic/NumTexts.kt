package io.github.chiefandrew5891max.logicmix.domain.game.numlogic

import io.github.chiefandrew5891max.logicmix.domain.game.NumFamily
import io.github.chiefandrew5891max.logicmix.domain.game.NumberSequencePuzzle

/**
 * Builds the task statement and the two hints of a generated puzzle from its visible data (rows, rule code, choices)
 * only, so the wording always follows the real mathematics. Returns null when no honest wording exists.
 */
object NumTexts {
    class Texts(val question: NumText, val hint1: NumText, val hint2: NumText)

    private fun t(m: NumMsg, vararg a: String) = NumText(m, a.toList())
    private fun sg(v: Long) = if (v >= 0) "+$v" else "−${-v}"
    private fun isLeaf(e: Expr) = e !is Expr.Bin

    private fun firstMulDiv(e: Expr): Expr.Bin? {
        if (e !is Expr.Bin) return null
        return firstMulDiv(e.l) ?: firstMulDiv(e.r) ?: e.takeIf { (it.op == Op.MUL || it.op == Op.DIV) && isLeaf(it.l) && isLeaf(it.r) }
    }

    fun derive(p: NumberSequencePuzzle): Texts? = try {
        when (p.family) {
            NumFamily.SEQUENCE -> sequence(p)
            NumFamily.ORDER_OPS -> order(p)
            NumFamily.PARENTHESES -> paren(p)
            NumFamily.MISSING_OP -> missing(p)
            NumFamily.PYRAMID -> pyramid(p)
            NumFamily.MATRIX -> matrix(p)
            NumFamily.BALANCE -> balance(p)
            NumFamily.HIDDEN_RELATION -> hidden(p)
            NumFamily.ATTENTION -> attention(p)
            NumFamily.MIXED -> mixed(p)
        }
    } catch (_: Exception) { null }

    private fun sequence(p: NumberSequencePuzzle): Texts? {
        val kind = p.rule.substringBefore(":")
        val a = p.rule.substringAfter(":", "").split(",").map { it.toLongOrNull() ?: return null }
        val q = t(NumMsg.Q_SEQ)
        return when {
            kind == "arith" && a.size == 2 -> Texts(q, t(NumMsg.H1_SEQ_ARITH), t(NumMsg.H2_SEQ_ARITH, sg(a[1])))
            kind == "geom" && a.size == 2 -> Texts(q, t(NumMsg.H1_SEQ_GEOM), t(NumMsg.H2_SEQ_GEOM, "${a[1]}"))
            kind == "alt" && a.size == 3 -> Texts(q, t(NumMsg.H1_SEQ_ALT), t(NumMsg.H2_SEQ_ALT, sg(a[1]), sg(a[2])))
            kind == "second" && a.size == 3 -> Texts(q, t(NumMsg.H1_SEQ_SECOND), t(NumMsg.H2_SEQ_SECOND, "${a[2]}"))
            kind == "inter" && a.size == 4 -> Texts(q, t(NumMsg.H1_SEQ_INTER), t(NumMsg.H2_SEQ_INTER, sg(a[1]), sg(a[3])))
            kind == "fib" && a.size == 2 -> Texts(q, t(NumMsg.H1_SEQ_FIB), t(NumMsg.H2_SEQ_FIB))
            kind == "affine" && a.size == 3 && a[2] != 0L ->
                Texts(q, t(NumMsg.H1_SEQ_AFFINE), t(if (a[2] > 0) NumMsg.H2_SEQ_AFFINE_ADD else NumMsg.H2_SEQ_AFFINE_SUB, "${a[1]}", "${Math.abs(a[2])}"))
            kind == "mixed" && a.size == 3 -> Texts(q, t(NumMsg.H1_SEQ_MIXED), t(NumMsg.H2_SEQ_MIXED, "${a[1]}", "${a[2]}"))
            else -> null
        }
    }

    private fun order(p: NumberSequencePuzzle): Texts? {
        val e = Exprs.parse(p.rows.last()[0]) ?: return null
        val sub = firstMulDiv(e) ?: return null
        return Texts(t(NumMsg.Q_ORDER), t(NumMsg.H1_ORDER), t(NumMsg.H2_ORDER, Exprs.render(sub)))
    }

    private fun paren(p: NumberSequencePuzzle): Texts? {
        val inner = Regex("\\(([^()]*)\\)").find(p.rows.last()[0])?.groupValues?.get(1) ?: return null
        return Texts(t(NumMsg.Q_PAREN), t(NumMsg.H1_PAREN), t(NumMsg.H2_PAREN, inner))
    }

    private fun missing(p: NumberSequencePuzzle): Texts? {
        val line = p.rows[0][0]
        val lhs = line.substringBefore(" = ")
        val target = line.substringAfter(" = ").toLong()
        for ((i, op) in p.choices.withIndex()) {
            if (i == p.answer) continue
            val v = Exprs.parse(lhs.replace("?", op))?.let { Exprs.eval(it) } ?: continue
            if (!v.isInt || v.n == target) continue
            return Texts(t(NumMsg.Q_MISSING_OP), t(NumMsg.H1_MISS), t(NumMsg.H2_MISS_TRY, op, "${v.n}", "$target"))
        }
        return Texts(t(NumMsg.Q_MISSING_OP), t(NumMsg.H1_MISS), t(NumMsg.H2_MISS_ORDER))
    }

    private fun pyramid(p: NumberSequencePuzzle): Texts? {
        val rows = p.rows
        var r = -1; var c = -1
        rows.forEachIndexed { i, row -> row.forEachIndexed { j, v -> if (v == "?") { r = i; c = j } } }
        if (r < 0) return null
        val q = t(if (rows.any { row -> row.any { it == "_" } }) NumMsg.Q_PYRAMID_BLANKS else NumMsg.Q_PYRAMID)
        if (r == rows.size - 1) return Texts(q, t(NumMsg.H1_PYR_BOTTOM), t(NumMsg.H2_PYR_BOTTOM))
        val known = rows[r + 1][c].toIntOrNull() != null && rows[r + 1][c + 1].toIntOrNull() != null
        return Texts(q, t(NumMsg.H1_PYR_BELOW), t(if (known) NumMsg.H2_PYR_BELOW_KNOWN else NumMsg.H2_PYR_BELOW_HIDDEN))
    }

    private fun matrix(p: NumberSequencePuzzle): Texts? {
        val a = p.rule.split(",")
        if (a.size != 3) return null
        val sym = a[0]; val k = a[1].toLongOrNull() ?: return null
        val both = a[2] == "1"
        val q = t(if (both) NumMsg.Q_MATRIX_BOTH else NumMsg.Q_MATRIX_ROWS)
        val h1 = t(if (both) NumMsg.H1_MATRIX_BOTH else NumMsg.H1_MATRIX_ROWS)
        val h2 = when {
            k > 0 -> t(NumMsg.H2_MATRIX_PLUS, sym, "$k")
            k < 0 -> t(NumMsg.H2_MATRIX_MINUS, sym, "${-k}")
            else -> t(NumMsg.H2_MATRIX, sym)
        }
        return Texts(q, h1, h2)
    }

    private fun balance(p: NumberSequencePuzzle): Texts? {
        val glyphs = p.rows.flatMap { it[0].filter { c -> c in "●■▲" }.toList() }.distinct()
        if (glyphs.isEmpty()) return null
        val one = glyphs.size == 1
        return Texts(t(NumMsg.Q_BALANCE), t(if (one) NumMsg.H1_BAL_ONE else NumMsg.H1_BAL_MANY), t(if (one) NumMsg.H2_BAL_ONE else NumMsg.H2_BAL_MANY))
    }

    private fun hidden(p: NumberSequencePuzzle): Texts? {
        val a = p.rule.split(",")
        val q = t(NumMsg.Q_HIDDEN)
        return when (a[0]) {
            "lin" -> Texts(q, t(NumMsg.H1_HID_STEP), t(NumMsg.H2_HID_STEP, a[1]))
            "sq" -> Texts(q, t(NumMsg.H1_HID_UNEQUAL), t(NumMsg.H2_HID_SQUARE))
            "near" -> Texts(q, t(NumMsg.H1_HID_UNEQUAL), t(NumMsg.H2_HID_NEAR))
            else -> null
        }
    }

    private fun attention(p: NumberSequencePuzzle): Texts? {
        val a = p.rule.split(",")
        val k = a.getOrNull(1)
        return when (a[0]) {
            "sumdiv" -> k?.let { Texts(t(NumMsg.Q_ATT_SUM_DIV, it), t(NumMsg.H1_ATT_MARK_DIV, it), t(NumMsg.H2_ATT_SUM_DIV, it)) }
            "cntdiv" -> k?.let { Texts(t(NumMsg.Q_ATT_COUNT_DIV, it), t(NumMsg.H1_ATT_MARK_DIV, it), t(NumMsg.H2_ATT_COUNT_DIV, it)) }
            "cntgt" -> k?.let { Texts(t(NumMsg.Q_ATT_COUNT_GT, it), t(NumMsg.H1_ATT_GT, it), t(NumMsg.H2_ATT_GT)) }
            "range" -> Texts(t(NumMsg.Q_ATT_RANGE), t(NumMsg.H1_ATT_RANGE), t(NumMsg.H2_ATT_RANGE))
            "digits" -> Texts(t(NumMsg.Q_ATT_DIGITS), t(NumMsg.H1_ATT_DIGITS), t(NumMsg.H2_ATT_DIGITS))
            "second" -> Texts(t(NumMsg.Q_ATT_SECOND), t(NumMsg.H1_ATT_SECOND), t(NumMsg.H2_ATT_SECOND))
            else -> null
        }
    }

    private fun mixed(p: NumberSequencePuzzle): Texts? {
        val br = p.rows.last()[0].contains('(')
        return Texts(t(NumMsg.Q_MIXED), t(NumMsg.H1_MIXED), t(if (br) NumMsg.H2_MIXED_BR else NumMsg.H2_MIXED))
    }
}
