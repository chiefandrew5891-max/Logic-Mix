package com.akynex.logicmix.domain.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualLogicTest {
    private val all = VisualPacks.all

    @Test fun everyPuzzleHasExactlyOneCorrectDistinctOption() {
        all.forEach { assertEquals(it.id, emptyList<String>(), it.problems()) }
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test fun packCoversEveryRuleWithTwoPuzzlesPerGroupAndRisingDifficulty() {
        assertTrue(all.size >= 10)
        listOf(VisualRule.SHAPE, VisualRule.COUNT, VisualRule.ROTATION, VisualRule.COMBINED, VisualRule.MATRIX)
            .forEach { rule -> assertTrue(rule.name, all.count { it.rule == rule } >= 2) }
        assertTrue(all.count { it.rule == VisualRule.COLOR || it.rule == VisualRule.POSITION } >= 2)
        assertEquals(1, all.first().difficulty)
        assertTrue(all.last().difficulty > all.first().difficulty)
    }

    @Test fun correctOptionIsNotAlwaysInTheSameSlot() {
        val positions = all.map { p -> p.options.indexOfFirst { it.id == p.correctId } }.toSet()
        assertTrue(positions.size >= 3)
    }

    @Test fun rotationAnswerFollowsTheTurn() {
        val p = all.first { it.rule == VisualRule.ROTATION }
        val rotations = p.tiles.map { it.rotation }
        assertEquals((rotations[p.gapIndex - 1] + 90) % 360, rotations[p.gapIndex])
    }

    @Test fun wrongChoiceIsMarkedAndCorrectSolves() {
        val p = all.first()
        val wrong = p.options.first { it.id != p.correctId }.id
        val g = VisualGame(p).choose(wrong)
        assertFalse(g.solved)
        assertTrue(wrong in g.wrong)
        assertTrue(g.choose(wrong) === g)
        assertTrue(g.choose(p.correctId).solved)
    }

    @Test fun hintNeverRemovesCorrectOptionAndRestartKeepsCounter() {
        val p = all.first()
        var g = VisualGame(p)
        repeat(6) { g = g.hint() }
        assertEquals(3, g.eliminated.size)
        assertFalse(p.correctId in g.eliminated)
        val r = g.restart()
        assertTrue(r.eliminated.isEmpty() && !r.hintShown)
        assertEquals(g.hintsUsed, r.hintsUsed)
        assertEquals(g.copy(), VisualGame.restore(p, g.encode()))
    }
}
