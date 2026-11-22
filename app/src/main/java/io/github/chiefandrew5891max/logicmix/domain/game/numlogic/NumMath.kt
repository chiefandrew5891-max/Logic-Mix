package io.github.chiefandrew5891max.logicmix.domain.game.numlogic

/** Exact rational number on Long. Every operation is overflow-checked and reports failure as null instead of wrapping. */
class Frac private constructor(val n: Long, val d: Long) {
    val isInt: Boolean get() = d == 1L
    override fun equals(other: Any?) = other is Frac && n == other.n && d == other.d
    override fun hashCode() = 31 * n.hashCode() + d.hashCode()
    override fun toString() = if (isInt) "$n" else "$n/$d"

    operator fun plus(o: Frac): Frac? = guard { of(Math.addExact(Math.multiplyExact(n, o.d), Math.multiplyExact(o.n, d)), Math.multiplyExact(d, o.d)) }
    operator fun minus(o: Frac): Frac? = guard { of(Math.subtractExact(Math.multiplyExact(n, o.d), Math.multiplyExact(o.n, d)), Math.multiplyExact(d, o.d)) }
    operator fun times(o: Frac): Frac? = guard { of(Math.multiplyExact(n, o.n), Math.multiplyExact(d, o.d)) }
    operator fun div(o: Frac): Frac? = if (o.n == 0L) null else guard { of(Math.multiplyExact(n, o.d), Math.multiplyExact(d, o.n)) }

    companion object {
        fun of(n: Long, d: Long = 1): Frac {
            require(d != 0L)
            val g = gcd(Math.abs(n), Math.abs(d)).coerceAtLeast(1)
            val s = if (d < 0) -1 else 1
            return Frac(s * n / g, s * d / g)
        }

        private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
        private inline fun guard(f: () -> Frac): Frac? = try { f() } catch (_: ArithmeticException) { null }
    }
}

enum class Op(val sym: String, val prec: Int) {
    ADD("+", 1), SUB("−", 1), MUL("×", 2), DIV("÷", 2);

    fun apply(a: Frac, b: Frac): Frac? = when (this) { ADD -> a + b; SUB -> a - b; MUL -> a * b; DIV -> a / b }
}

/** Expression tree; evaluation never goes through string evaluation, only through this model. */
sealed interface Expr {
    data class Num(val v: Long) : Expr
    data class Var(val name: String) : Expr
    data class Bin(val op: Op, val l: Expr, val r: Expr) : Expr
}

object Exprs {
    fun eval(e: Expr, env: Map<String, Long> = emptyMap()): Frac? = when (e) {
        is Expr.Num -> Frac.of(e.v)
        is Expr.Var -> env[e.name]?.let { Frac.of(it) }
        is Expr.Bin -> eval(e.l, env)?.let { l -> eval(e.r, env)?.let { r -> e.op.apply(l, r) } }
    }

    /** Standard notation with the fewest parentheses; mixed × and ÷ at one level are always bracketed so no reading is ambiguous. */
    fun render(e: Expr): String = when (e) {
        is Expr.Num -> e.v.toString()
        is Expr.Var -> e.name
        is Expr.Bin -> "${wrap(e.l, e, false)} ${e.op.sym} ${wrap(e.r, e, true)}"
    }

    private fun wrap(child: Expr, parent: Expr.Bin, right: Boolean): String {
        val text = render(child)
        if (child !is Expr.Bin) return text
        val mixedLevel = parent.op.prec == 2 && child.op.prec == 2 && parent.op != child.op
        val needs = child.op.prec < parent.op.prec ||
            (right && child.op.prec == parent.op.prec && (parent.op == Op.SUB || parent.op == Op.DIV)) || mixedLevel
        return if (needs) "($text)" else text
    }

    fun hasParens(e: Expr) = render(e).contains('(')

    /** Every operation in evaluation order, e.g. "2 × 2 = 4", or null when any step is not an exact whole number. */
    fun trace(e: Expr, env: Map<String, Long> = emptyMap()): List<String>? {
        val out = mutableListOf<String>()
        fun walk(x: Expr): Frac? = when (x) {
            is Expr.Num -> Frac.of(x.v)
            is Expr.Var -> env[x.name]?.let { Frac.of(it) }
            is Expr.Bin -> {
                val l = walk(x.l)
                val r = walk(x.r)
                val v = if (l != null && r != null) x.op.apply(l, r) else null
                if (v != null && v.isInt) out += "${l} ${x.op.sym} ${r} = $v"
                v?.takeIf { it.isInt }
            }
        }
        return if (walk(e) != null) out else null
    }

    fun leaves(e: Expr): Int = if (e is Expr.Bin) leaves(e.l) + leaves(e.r) else 1

    fun operators(e: Expr): Int = if (e is Expr.Bin) 1 + operators(e.l) + operators(e.r) else 0

    fun depth(e: Expr): Int = if (e is Expr.Bin) 1 + maxOf(depth(e.l), depth(e.r)) else 0

    /** Same text evaluated strictly left to right, ignoring precedence: the wrong answer a careless player gives. */
    fun naiveLeftToRight(text: String): Frac? {
        val t = tokenize(text) ?: return null
        if (t.isEmpty() || t.any { it == "(" || it == ")" }) return null
        var acc = Frac.of(t[0].toLongOrNull() ?: return null)
        var i = 1
        while (i + 1 < t.size) {
            val op = Op.entries.firstOrNull { it.sym == t[i] } ?: return null
            acc = op.apply(acc, Frac.of(t[i + 1].toLongOrNull() ?: return null)) ?: return null
            i += 2
        }
        return acc
    }

    fun tokenize(text: String): List<String>? {
        val out = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() -> { var j = i; while (j < text.length && text[j].isDigit()) j++; out += text.substring(i, j); i = j }
                c == '-' -> { out += Op.SUB.sym; i++ }
                c == '*' -> { out += Op.MUL.sym; i++ }
                c == '/' -> { out += Op.DIV.sym; i++ }
                else -> { out += c.toString(); i++ }
            }
        }
        return out
    }

    /** Recursive-descent parser with standard precedence; returns null for malformed input. */
    fun parse(text: String): Expr? {
        val p = Parser(tokenize(text) ?: return null)
        val r = p.expr()
        return if (r != null && p.done) r else null
    }

    private class Parser(val t: List<String>) {
        var pos = 0
        val done: Boolean get() = pos == t.size

        fun expr(): Expr? {
            var l = term() ?: return null
            while (pos < t.size && (t[pos] == Op.ADD.sym || t[pos] == Op.SUB.sym)) {
                val op = if (t[pos++] == Op.ADD.sym) Op.ADD else Op.SUB
                l = Expr.Bin(op, l, term() ?: return null)
            }
            return l
        }

        private fun term(): Expr? {
            var l = factor() ?: return null
            while (pos < t.size && (t[pos] == Op.MUL.sym || t[pos] == Op.DIV.sym)) {
                val op = if (t[pos++] == Op.MUL.sym) Op.MUL else Op.DIV
                l = Expr.Bin(op, l, factor() ?: return null)
            }
            return l
        }

        private fun factor(): Expr? {
            if (pos >= t.size) return null
            val tok = t[pos++]
            return when {
                tok == "(" -> expr()?.takeIf { pos < t.size && t[pos++] == ")" }
                tok[0].isDigit() -> tok.toLongOrNull()?.let { Expr.Num(it) }
                tok.length == 1 && tok[0] !in "+−×÷)=,?" -> Expr.Var(tok)
                else -> null
            }
        }
    }
}
