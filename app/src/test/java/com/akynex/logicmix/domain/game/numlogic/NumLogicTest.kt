package com.akynex.logicmix.domain.game.numlogic

import com.akynex.logicmix.domain.game.NumFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumLogicTest {
    private fun ev(s: String) = Exprs.eval(Exprs.parse(s)!!)

    @Test fun precedence() {
        assertEquals(Frac.of(4), ev("2 + 2 × 2 − 2"))
        assertEquals(Frac.of(8), ev("(2 + 2) × 2"))
        assertEquals(Frac.of(-3), ev("2 − 5"))
        assertEquals(Frac.of(3), ev("12 ÷ 4 × 1"))
    }

    @Test fun divisionSafety() {
        assertNull(ev("5 ÷ (3 − 3)"))
        assertEquals(Frac.of(5, 2), ev("5 ÷ 2"))
        assertNull(Exprs.parse("2 + "))
        assertNull(Exprs.parse("(2 + 3"))
    }

    @Test fun overflowIsNullNotWrap() {
        assertNull(Frac.of(Long.MAX_VALUE) * Frac.of(2))
        assertNull(Frac.of(Long.MAX_VALUE) + Frac.of(1))
    }

    @Test fun renderRoundTrip() {
        for (s in 0L until 100) for (f in listOf(NumFamily.ORDER_OPS, NumFamily.PARENTHESES)) {
            val p = NumGenerators.generate(f, 1 + (s % 5).toInt(), s)
            val text = p.rows.last()[0]
            assertEquals(text, Exprs.render(Exprs.parse(text)!!))
        }
    }

    @Test fun massGenerationValidReproducibleNoFallback() {
        for (f in NumFamily.entries) for (d in 1..5) for (s in 0L until 40) {
            val p = NumGenerators.generate(f, d, s, "x")
            assertEquals("$f d$d s$s", emptyList<String>(), NumValidator.problems(p))
            assertEquals("$f d$d s$s fell back", f.takeIf { it == NumFamily.SEQUENCE } ?: f, p.family)
            assertTrue("$f d$d s$s fallback rule", p.family != NumFamily.SEQUENCE || p.rule != NumGenerators.FALLBACK_RULE)
            assertEquals(p, NumGenerators.generate(f, d, s, "x"))
        }
    }

    @Test fun missingOperatorUnique() {
        for (s in 0L until 100) {
            val p = NumGenerators.generate(NumFamily.MISSING_OP, 1 + (s % 5).toInt(), s)
            val line = p.rows[0][0]
            val good = p.choices.indices.count { i ->
                Exprs.eval(Exprs.parse(line.substringBefore(" = ").replace("?", p.choices[i]))!!) == Frac.of(line.substringAfter(" = ").toLong())
            }
            assertEquals(1, good)
        }
    }

    @Test fun orderOpsDiffersFromNaive() {
        val t = NumGenerators.generate(NumFamily.ORDER_OPS, 2, 5).rows.last()[0]
        assertNotEquals(Exprs.naiveLeftToRight(t), ev(t))
    }

    @Test fun sequenceSolver() {
        assertEquals(setOf(10L), SeqSolver.candidates(listOf(2, 4, 6, 8)))
        assertEquals(setOf(5L), SeqSolver.candidates(listOf(1, 1, 2, 3)))
        assertNull(SeqRule.geometric(3, 3).terms(30))
    }

    @Test fun careerShape() {
        assertEquals(113, NumberLogicCareer.total)
        for (i in 1 until NumberLogicCareer.generatedCount) assertNotEquals(NumberLogicCareer.familyAt(i - 1), NumberLogicCareer.familyAt(i))
        assertEquals("ns-001", NumberLogicCareer.firstUnsolved(emptySet())!!.id)
        assertEquals("nl-001", NumberLogicCareer.firstUnsolved((1..13).map { "ns-%03d".format(it) }.toSet())!!.id)
        assertEquals(14, NumberLogicCareer.indexOf("nl-001") + 1)
        assertEquals(NumberLogicCareer.byId("nl-007"), NumberLogicCareer.byId("nl-007"))
        assertNull(NumberLogicCareer.byId("nl-101"))
    }
}
