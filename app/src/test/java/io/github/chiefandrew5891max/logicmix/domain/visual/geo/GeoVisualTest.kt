package io.github.chiefandrew5891max.logicmix.domain.visual.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoVisualTest {
    private val seeds = (1L..40L)

    @Test fun everyFamilyGeneratesValidPuzzlesAtEveryDifficulty() {
        for (family in GeoFamily.entries) for (d in 1..5) for (seed in seeds) {
            val p = GeoGenerators.generate(family, d, seed, "t")
            assertEquals("$family d=$d seed=$seed", emptyList<String>(), p.problems())
        }
    }

    @Test fun generationIsReproducibleAndSeedsVary() {
        for (family in GeoFamily.entries) {
            val a = GeoGenerators.generate(family, 3, 77, "x")
            assertEquals(family.name, a, GeoGenerators.generate(family, 3, 77, "x"))
            assertTrue(family.name, seeds.map { GeoGenerators.generate(family, 3, it).encode() }.toSet().size > 10)
        }
    }

    @Test fun generatorsDoNotFallBackInNormalOperation() {
        for (family in GeoFamily.entries) for (d in 1..5) {
            val fallbacks = seeds.count { GeoGenerators.generate(family, d, it).let { p -> p.family != family || p.stimulus == GeoGenerators.fallback("", d, it).stimulus } }
            assertEquals("$family d=$d", 0, fallbacks)
        }
    }

    @Test fun fallbackPuzzleIsValid() {
        assertEquals(emptyList<String>(), GeoGenerators.fallback("fb", 1, 1).problems())
        assertTrue(Figure(3, 3, listOf(1, 1, 1, 1, 0, 0, 0, 1, 0)).mask().isChiral())
    }

    @Test fun serializationRoundTrips() {
        for (family in GeoFamily.entries) {
            val p = GeoGenerators.generate(family, 4, 5, "rt")
            assertEquals(p, GeoPuzzle.decode(p.encode()))
        }
        assertEquals(null, GeoPuzzle.decode("garbage"))
        assertEquals(null, Figure.decode("2,2:11"))
    }

    @Test fun difficultyRaisesComplexity() {
        fun avg(family: GeoFamily, d: Int) = seeds.map { s -> GeoGenerators.generate(family, d, s).let { it.stimulus.sumOf { f -> f.w * f.h } + it.badge.length } }.average()
        for (f in listOf(GeoFamily.ROTATION, GeoFamily.SPATIAL, GeoFamily.KEYHOLE, GeoFamily.MATRIX)) assertTrue(f.name, avg(f, 5) > avg(f, 1))
        assertTrue(seeds.all { GeoGenerators.generate(GeoFamily.MATRIX, 3, it).columns == 3 })
        assertTrue(seeds.all { GeoGenerators.generate(GeoFamily.MATRIX, 1, it).columns == 2 })
    }

    @Test fun figureTransformsAreExact() {
        val f = Figure(2, 3, listOf(1, 0, 2, 3, 0, 1))
        assertEquals(f, f.rotate(360))
        assertEquals(f, f.rot90().rot90().rot90().rot90())
        assertEquals(f, f.flipH().flipH())
        assertEquals(f.flipV(), f.rotate(180).flipH())
        assertEquals(Figure(3, 2, listOf(0, 2, 1, 1, 3, 0)), f.rot90())
    }

    @Test fun keyFitsByRotationButNotReflection() {
        val hole = GeoGenerators.chiral(kotlin.random.Random(3), 4, 7)!!
        assertTrue(GeoRules.fits(hole.rotate(90), hole))
        assertFalse(GeoRules.fits(hole.flipH(), hole))
    }

    @Test fun cubeNetRecognitionMatchesTheElevenKnownNets() {
        val valid = Hexominoes.fixed.filter { GeoRules.isCubeNet(it) }
        assertEquals(216, Hexominoes.fixed.size)
        assertEquals(11, valid.map { Hexominoes.canonical(it) }.toSet().size)
        assertEquals(35, Hexominoes.fixed.map { Hexominoes.canonical(it) }.toSet().size)
        val cross = Figure(3, 4, listOf(0, 1, 0, 1, 1, 1, 0, 1, 0, 0, 1, 0))
        assertTrue(GeoRules.isCubeNet(cross))
        assertFalse(GeoRules.isCubeNet(Figure(3, 2, listOf(1, 1, 1, 1, 1, 1))))
    }

    @Test fun paperFoldUnfoldsByMirroring() {
        val sheet = Figure(3, 6, List(18) { if (it == 1) Figure.HOLE else 1 })
        val paper = GeoRules.unfold(sheet, true, false)
        assertEquals(6, paper.w)
        assertEquals(listOf(1, 4), (0 until 6).filter { paper[it, 0] == Figure.HOLE })
    }

    @Test fun mathOfBadgesMatchesPuzzles() {
        val f = GeoGenerators.chiral(kotlin.random.Random(1), 4, 7)!!
        assertEquals(f.rotate(90).flipH(), GeoRules.applyBadge(f, "↻90° → ⇆"))
        assertEquals(f.flipV(), GeoRules.applyBadge(f, "⇅"))
        assertEquals(null, GeoRules.applyBadge(f, "??"))
    }

    @Test fun validatorRejectsCorruptedPuzzles() {
        for (family in GeoFamily.entries) {
            val p = GeoGenerators.generate(family, 3, 9)
            val wrongIndex = (p.correct + 1) % 4
            assertNotEquals(family.name, emptyList<String>(), p.copy(correct = wrongIndex).problems())
            assertNotEquals(family.name, emptyList<String>(), p.copy(options = p.options.take(3) + p.options[0]).problems())
        }
    }

    @Test fun matrixAndSequenceAnswersAreUniqueUnderTheRuleSolver() {
        for (family in listOf(GeoFamily.MATRIX, GeoFamily.SEQUENCE)) for (d in 1..5) for (seed in seeds) {
            val p = GeoGenerators.generate(family, d, seed)
            val predicted = GeoValidator.predictions(p)
            assertEquals(1, predicted.size)
            assertEquals(p.options[p.correct], predicted.single())
        }
    }

    @Test fun oddOneOutHasExactlyOneMirroredOption() {
        for (d in 1..5) for (seed in seeds) {
            val p = GeoGenerators.generate(GeoFamily.ODD_ONE, d, seed)
            assertEquals(p.correct, GeoValidator.oddIndex(p.options))
        }
    }

    @Test fun gameStateCanBePlayedHintedAndRestored() {
        val p = GeoGenerators.generate(GeoFamily.KEYHOLE, 2, 4, "vg-001")
        var g = GeoGame(p)
        val wrong = p.optionIds.first { it != p.correctId }
        g = g.choose(wrong).hint()
        assertTrue(wrong in g.wrong && g.hintsUsed == 1)
        val restored = GeoGame.restore(p, g.encode())
        assertEquals(g, restored)
        assertEquals(GeoGame(p), GeoGame.restore(p, "garbage"))
        assertEquals(GeoGame(p), GeoGame.restore(p, "g9;0;;"))
        repeat(5) { g = g.hint() }
        assertFalse(p.correctId in g.eliminated)
        assertTrue(g.choose(p.correctId).solved)
        assertEquals(g.hintsUsed, g.restart().hintsUsed)
        assertNotNull(GeoPuzzle.decode(p.encode()))
    }

    @Test fun careerIsDeterministicNeverRepeatsAFamilyAndKeepsClassicLevelsFirst() {
        val classic = io.github.chiefandrew5891max.logicmix.domain.visual.VisualPacks.all
        assertEquals(classic.map { it.id }, (classic.indices).map { VisualCareer.levelAt(it)!!.id })
        val families = (0 until VisualCareer.generatedCount).map { VisualCareer.familyAt(it) }
        assertTrue(families.zipWithNext().all { (a, b) -> a != b })
        assertEquals(GeoFamily.entries.toSet(), families.toSet())
        val difficulties = (0 until VisualCareer.generatedCount).map { VisualCareer.difficultyAt(it) }
        assertEquals(difficulties.sorted(), difficulties)
        assertEquals(1, difficulties.first())
        assertEquals(5, difficulties.last())
        val last = VisualCareer.total - 1
        assertEquals(VisualCareer.levelAt(last), VisualCareer.levelAt(last))
        assertEquals(null, VisualCareer.levelAt(VisualCareer.total))
    }

    @Test fun everyCareerLevelIsValidAndIdsAreUnique() {
        val levels = (0 until VisualCareer.total).map { VisualCareer.levelAt(it)!! }
        assertEquals(levels.size, levels.map { it.id }.toSet().size)
        levels.filterIsInstance<VisualLevel.Generated>().forEach { assertEquals(it.id, emptyList<String>(), it.puzzle.problems()) }
    }
}
