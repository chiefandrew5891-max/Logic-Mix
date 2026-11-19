package io.github.chiefandrew5891max.logicmix.feature.game

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.chiefandrew5891max.logicmix.core.data.GameSessionStore
import io.github.chiefandrew5891max.logicmix.domain.game.NumberSequenceEngine
import io.github.chiefandrew5891max.logicmix.domain.game.NumberSequencePack
import io.github.chiefandrew5891max.logicmix.domain.game.SolveResult
import io.github.chiefandrew5891max.logicmix.domain.game.ValidationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NumberSequencePackTest {
    @Test fun everyPuzzleIsValidAndUnique() {
        val ids = NumberSequencePack.puzzles.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        NumberSequencePack.puzzles.forEach {
            assertEquals(it.id, ValidationResult.Valid, NumberSequenceEngine.validateDefinition(it))
        }
    }

    @Test fun answersAreAcceptedAndNeighboursRejected() {
        NumberSequencePack.puzzles.forEach {
            assertEquals(SolveResult.Correct, NumberSequenceEngine.checkAnswer(it, it.answer.toString()))
            assertEquals(SolveResult.Incorrect, NumberSequenceEngine.checkAnswer(it, (it.answer + 1).toString()))
        }
    }

    @Test fun firstUnsolvedSkipsSolvedAndEndsWithNull() {
        val all = NumberSequencePack.puzzles.map { it.id }
        assertEquals(all[1], NumberSequencePack.firstUnsolved(listOf(all[0]))?.id)
        assertNull(NumberSequencePack.firstUnsolved(all))
    }
}

class GameUiStateTest {
    private val puzzle = NumberSequencePack.puzzles.first()

    @Test fun hintsAreLimitedToThePuzzleAndRevealedInOrder() {
        var s = GameUiState(loading = false, puzzle = puzzle)
        assertTrue(s.canUseHint)
        s = s.copy(hintsUsed = 1)
        assertEquals(puzzle.hints.take(1), s.shownHints)
        s = s.copy(hintsUsed = puzzle.hints.size)
        assertFalse(s.canUseHint)
    }

    @Test fun cannotSubmitBlankOrAfterSolving() {
        val base = GameUiState(loading = false, puzzle = puzzle)
        assertFalse(base.canSubmit)
        assertTrue(base.copy(input = "10").canSubmit)
        assertFalse(base.copy(input = "10", feedback = Feedback.CORRECT).canSubmit)
    }
}

class GameSessionStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() { scope.cancel() }

    private fun store() = GameSessionStore(
        PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("g.preferences_pb").also { it.delete() } },
    )

    @Test fun sessionKeepsHintsWhenReopenedAndResetsForAnotherPuzzle() = runBlocking {
        val s = store()
        assertFalse(s.hasActiveSession.first())
        s.begin("ns-001", 100L)
        s.setHintsUsed("ns-001", 2)
        assertTrue(s.hasActiveSession.first())
        assertEquals(2, s.begin("ns-001", 999L).hintsUsed)
        assertEquals(100L, s.session.first()?.startedAt)
        assertEquals(0, s.begin("ns-002", 200L).hintsUsed)
    }

    @Test fun clearEndsTheSession() = runBlocking {
        val s = store()
        s.begin("ns-001", 1L)
        s.clear()
        assertFalse(s.hasActiveSession.first())
        assertNull(s.session.first())
    }
}
