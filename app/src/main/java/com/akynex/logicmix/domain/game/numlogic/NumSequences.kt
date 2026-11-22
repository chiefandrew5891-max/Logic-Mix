package com.akynex.logicmix.domain.game.numlogic

/** An explicit generating rule. [terms] returns null when a value leaves the safe range, so no overflow is possible. */
class SeqRule(val text: String, val code: String, val terms: (Int) -> List<Long>?) {
    companion object {
        const val LIMIT = 100_000L

        private fun build(n: Int, step: (List<Long>) -> Long, seed: List<Long>): List<Long>? {
            val out = seed.toMutableList()
            while (out.size < n) {
                val v = step(out)
                if (Math.abs(v) > LIMIT) return null
                out += v
            }
            return out.take(n)
        }

        fun arithmetic(a: Long, d: Long) = SeqRule(signed(d), "arith:$a,$d") { n -> build(n, { it.last() + d }, listOf(a)) }
        fun geometric(a: Long, r: Long) = SeqRule("×$r", "geom:$a,$r") { n -> build(n, { it.last() * r }, listOf(a)) }
        fun alternatingAdd(a: Long, p: Long, q: Long) =
            SeqRule("${signed(p)}, ${signed(q)}", "alt:$a,$p,$q") { n -> build(n, { it.last() + if (it.size % 2 == 1) p else q }, listOf(a)) }
        /** Differences themselves grow by [dd]: a second-order rule. */
        fun secondOrder(a: Long, d: Long, dd: Long) =
            SeqRule("${signed(d)}, ${signed(d + dd)}, …", "second:$a,$d,$dd") { n -> build(n, { it.last() + d + dd * (it.size - 1) }, listOf(a)) }
        fun interleaved(a: Long, da: Long, b: Long, db: Long) =
            SeqRule("${signed(da)} | ${signed(db)}", "inter:$a,$da,$b,$db") { n -> build(n, { if (it.size % 2 == 0) it[it.size - 2] + da else it[it.size - 2] + db }, listOf(a, b)) }
        fun fibonacci(a: Long, b: Long) = SeqRule("aₙ = aₙ₋₁ + aₙ₋₂", "fib:$a,$b") { n -> build(n, { it[it.size - 1] + it[it.size - 2] }, listOf(a, b)) }
        fun affine(a: Long, p: Long, q: Long) = SeqRule("×$p ${signed(q)}", "affine:$a,$p,$q") { n -> build(n, { it.last() * p + q }, listOf(a)) }
        fun mixed(a: Long, m: Long, k: Long) =
            SeqRule("×$m, ${signed(k)}", "mixed:$a,$m,$k") { n -> build(n, { if (it.size % 2 == 1) it.last() * m else it.last() + k }, listOf(a)) }

        /** Rebuilds a rule from its machine code, or null when the code is malformed. */
        fun fromCode(code: String): SeqRule? {
            val kind = code.substringBefore(":")
            val a = code.substringAfter(":", "").split(",").map { it.toLongOrNull() ?: return null }
            return when {
                kind == "arith" && a.size == 2 -> arithmetic(a[0], a[1])
                kind == "geom" && a.size == 2 -> geometric(a[0], a[1])
                kind == "alt" && a.size == 3 -> alternatingAdd(a[0], a[1], a[2])
                kind == "second" && a.size == 3 -> secondOrder(a[0], a[1], a[2])
                kind == "inter" && a.size == 4 -> interleaved(a[0], a[1], a[2], a[3])
                kind == "fib" && a.size == 2 -> fibonacci(a[0], a[1])
                kind == "affine" && a.size == 3 -> affine(a[0], a[1], a[2])
                kind == "mixed" && a.size == 3 -> mixed(a[0], a[1], a[2])
                else -> null
            }
        }

        fun signed(v: Long) = if (v >= 0) "+$v" else "−${-v}"
    }
}

/** Finds every value that any supported rule predicts as the next term, so uniqueness can be proved rather than assumed. */
object SeqSolver {
    fun candidates(t: List<Long>): Set<Long> {
        val out = mutableSetOf<Long>()
        val n = t.size
        if (n < 3) return out
        val diffs = (1 until n).map { t[it] - t[it - 1] }
        // first-order linear recurrence t[i+1] = p*t[i] + q covers arithmetic and geometric rules
        if (diffs[0] != 0L) {
            if (diffs[1] % diffs[0] == 0L) {
                val p = diffs[1] / diffs[0]
                val q = t[1] - p * t[0]
                if ((0 until n - 1).all { t[it + 1] == p * t[it] + q }) out += p * t[n - 1] + q
            }
        } else if (diffs.all { it == 0L }) out += t.last()
        if (n >= 3 && (0 until n - 2).all { t[it + 2] == t[it + 1] + t[it] }) out += t[n - 1] + t[n - 2]
        if (n >= 4) {
            val dd = (1 until diffs.size).map { diffs[it] - diffs[it - 1] }
            if (dd.all { it == dd[0] }) out += t.last() + diffs.last() + dd[0]
        }
        if (n >= 5 && (0 until diffs.size - 2).all { diffs[it] == diffs[it + 2] }) out += t.last() + diffs[diffs.size - 2]
        if (n >= 5) {
            val sub = t.filterIndexed { i, _ -> i % 2 == n % 2 }
            val other = t.filterIndexed { i, _ -> i % 2 != n % 2 }
            fun arith(l: List<Long>) = l.size >= 2 && (1 until l.size).all { l[it] - l[it - 1] == l[1] - l[0] }
            if (arith(sub) && arith(other)) out += sub.last() + (sub[1] - sub[0])
        }
        if (n >= 5 && t[0] != 0L && t[1] % t[0] == 0L) {
            val m = t[1] / t[0]
            val k = t[2] - t[1]
            if ((0 until n - 1).all { t[it + 1] == if (it % 2 == 0) t[it] * m else t[it] + k }) {
                out += if ((n - 1) % 2 == 0) t.last() * m else t.last() + k
            }
        }
        return out
    }
}
