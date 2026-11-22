package io.github.chiefandrew5891max.logicmix.domain.game.numlogic

import io.github.chiefandrew5891max.logicmix.domain.game.NumFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NumTextsTest {
    private val langs = listOf("values", "values-ru", "values-uk", "values-it", "values-de", "values-fr", "values-es", "values-zh-rCN")

    private fun resDir(): File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private fun strings(dir: String): Map<String, String> {
        val xml = File(resDir(), "$dir/strings.xml").readText()
        return Regex("<string name=\"(nl_[a-z0-9_]+)\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun all(): List<io.github.chiefandrew5891max.logicmix.domain.game.NumberSequencePuzzle> =
        NumFamily.entries.flatMap { f -> (1..5).flatMap { d -> (1L..25L).map { s -> NumGenerators.generate(f, d, s * 31 + d) } } }

    @Test fun everyMessageIsLocalizedInAllEightLanguages() {
        for (lang in langs) {
            val s = strings(lang)
            for (m in NumMsg.entries) {
                val text = s[m.resName]
                assertNotNull("$lang missing ${m.resName}", text)
                val idx = Regex("%(\\d)\\$" + "s").findAll(text!!).map { it.groupValues[1].toInt() }.toSet()
                assertEquals("$lang ${m.resName} placeholders", (1..m.arity).toSet(), idx)
                val letters = text.replace(Regex("%\\d\\$" + "s"), "")
                val latin = letters.any { it in 'a'..'z' || it in 'A'..'Z' }
                when (lang) {
                    "values-ru", "values-uk", "values-zh-rCN" -> assertFalse("$lang ${m.resName} has Latin text", latin)
                    else -> assertFalse("$lang ${m.resName} has Cyrillic", letters.any { it in 'Ѐ'..'ӿ' })
                }
                if (lang != "values-zh-rCN") assertFalse("$lang ${m.resName} has CJK", letters.any { it in '一'..'鿿' })
                for (bad in listOf("Σ", "∈", "{", "Δ")) assertFalse("$lang ${m.resName} contains $bad", text.contains(bad))
                assertFalse("$lang ${m.resName} has max/min", Regex("\\b(max|min)\\b").containsMatchIn(text))
            }
        }
    }

    @Test fun generatedTextsAreCompleteAndMatchThePuzzle() {
        for (p in all()) {
            val q = NumText.decode(p.question)
            assertNotNull("${p.family} question", q)
            assertEquals(2, p.hints.size)
            val h = p.hints.map { NumText.decode(it) }
            assertTrue(h.all { it != null })
            assertEquals(NumTexts.derive(p)!!.hint2, h[1])
            assertTrue("${p.family} problems ${NumValidator.problems(p)}", NumValidator.problems(p).isEmpty())
            assertTrue(p.question.startsWith("~Q_"))
            assertTrue(p.hints[0].startsWith("~H1_") && p.hints[1].startsWith("~H2_"))
        }
    }

    @Test fun wordingFollowsTheRealOperation() {
        for (p in all()) {
            val q = NumText.decode(p.question)!!
            val h2 = NumText.decode(p.hints[1])!!
            when (p.family) {
                NumFamily.SEQUENCE -> {
                    val kind = p.rule.substringBefore(":")
                    val expected = mapOf("arith" to NumMsg.H2_SEQ_ARITH, "geom" to NumMsg.H2_SEQ_GEOM, "alt" to NumMsg.H2_SEQ_ALT, "second" to NumMsg.H2_SEQ_SECOND,
                        "inter" to NumMsg.H2_SEQ_INTER, "fib" to NumMsg.H2_SEQ_FIB, "mixed" to NumMsg.H2_SEQ_MIXED)
                    if (kind == "affine") assertTrue(h2.msg == NumMsg.H2_SEQ_AFFINE_ADD || h2.msg == NumMsg.H2_SEQ_AFFINE_SUB)
                    else assertEquals(expected[kind], h2.msg)
                    if (h2.msg == NumMsg.H2_SEQ_AFFINE_SUB) assertTrue(p.rule.substringAfterLast(",").toLong() < 0)
                    if (h2.msg == NumMsg.H2_SEQ_AFFINE_ADD) assertTrue(p.rule.substringAfterLast(",").toLong() > 0)
                }
                NumFamily.PYRAMID -> assertEquals(p.rows.any { r -> r.contains("_") }, q.msg == NumMsg.Q_PYRAMID_BLANKS)
                NumFamily.ATTENTION -> {
                    val nums = p.rows[0].map { it.toInt() }
                    when (q.msg) {
                        NumMsg.Q_ATT_SUM_DIV -> assertEquals(nums.filter { it % q.args[0].toInt() == 0 }.sum(), p.answer)
                        NumMsg.Q_ATT_COUNT_DIV -> assertEquals(nums.count { it % q.args[0].toInt() == 0 }, p.answer)
                        NumMsg.Q_ATT_COUNT_GT -> assertEquals(nums.count { it > q.args[0].toInt() }, p.answer)
                        NumMsg.Q_ATT_RANGE -> assertEquals(nums.max() - nums.min(), p.answer)
                        NumMsg.Q_ATT_DIGITS -> assertEquals(nums.sumOf { n -> n.toString().sumOf { it - '0' } }, p.answer)
                        NumMsg.Q_ATT_SECOND -> assertEquals(nums.sorted().let { it[it.size - 1] - it[it.size - 2] }, p.answer)
                        else -> error("unexpected ${q.msg}")
                    }
                }
                NumFamily.MISSING_OP -> assertTrue(h2.msg == NumMsg.H2_MISS_TRY || h2.msg == NumMsg.H2_MISS_ORDER)
                NumFamily.PARENTHESES -> assertTrue(p.rows.last()[0].contains(h2.args[0]))
                NumFamily.ORDER_OPS -> assertTrue(h2.args[0].contains("×") || h2.args[0].contains("÷"))
                else -> {}
            }
        }
    }

    @Test fun answerNeverAppearsInHintArguments() {
        for (p in all()) {
            val answer = if (p.choices.isNotEmpty()) p.choices[p.answer] else p.answer.toString()
            for (raw in p.hints + p.question) {
                NumText.decode(raw)!!.args.forEach { assertFalse("$answer leaked ${p.family}", it.replace("−", "-").removePrefix("+") == answer) }
            }
        }
    }

    @Test fun answersAreIndependentOfWording() {
        for (p in all()) {
            val stripped = p.copy(question = "", hints = emptyList())
            // The solver-side checks (uniqueness, arithmetic) never read the texts, so they must still pass on the data alone.
            assertTrue(NumTexts.derive(stripped) != null)
            assertEquals(p.answer, NumGenerators.generate(p.family, p.difficulty, p.seed, p.id).answer)
        }
    }

    @Test fun tamperedTextsAreRejected() {
        val p = NumGenerators.generate(NumFamily.PYRAMID, 2, 9)
        assertTrue(NumValidator.problems(p.copy(question = "")).isNotEmpty())
        assertTrue(NumValidator.problems(p.copy(hints = p.hints.take(1))).isNotEmpty())
        assertTrue(NumValidator.problems(p.copy(question = NumText(NumMsg.Q_SEQ).encode())).isNotEmpty())
        assertTrue(NumValidator.problems(p.copy(hints = listOf(p.hints[0], NumText(NumMsg.H2_PYR_BOTTOM).encode()))).isNotEmpty() || p.hints[1] == NumText(NumMsg.H2_PYR_BOTTOM).encode())
    }

    @Test fun progressIdentifiersAreUnchanged() {
        assertEquals(113, NumberLogicCareer.total)
        assertEquals("nl-001", NumberLogicCareer.firstUnsolved((1..13).map { "ns-%03d".format(it) }.toSet())!!.id)
        assertEquals("ns-001", NumberLogicCareer.byId("ns-001")!!.id)
        assertEquals("", NumberLogicCareer.byId("ns-001")!!.question)
        assertTrue(NumberLogicCareer.byId("nl-050")!!.question.startsWith("~Q_"))
    }
}
