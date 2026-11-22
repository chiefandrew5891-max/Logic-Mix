package com.akynex.logicmix.domain.visual

import com.akynex.logicmix.domain.visual.ShapeKind.*
import com.akynex.logicmix.domain.visual.VisualRule.*

/**
 * Language-neutral puzzles. Each one is generated from a rule function (cell index -> tile), so the
 * answer follows from the rule and not from a hand-picked option index. Distractors vary the attribute
 * the rule is about; [answerPos] only decides where the correct option is displayed.
 */
object VisualPacks {
    val all: List<VisualPuzzle> by lazy { build() }

    private fun make(
        n: Int, rule: VisualRule, difficulty: Int, length: Int, gap: Int, columns: Int, answerPos: Int,
        tileAt: (Int) -> Tile, distractors: (Tile) -> List<Tile>,
    ): VisualPuzzle {
        val tiles = List(length, tileAt)
        val answer = tiles[gap]
        val wrong = distractors(answer).filter { it != answer }.distinct().take(3)
        val ordered = wrong.toMutableList().apply { add(answerPos.coerceIn(0, size), answer) }
        val options = ordered.mapIndexed { i, t -> VisualOption("v${i + 1}", t) }
        return VisualPuzzle(
            "vl-%02d".format(n), rule, difficulty, tiles, gap, columns, options,
            correctId = options.first { it.tile == answer }.id,
        )
    }

    private val kinds4 = listOf(CIRCLE, SQUARE, TRIANGLE, DIAMOND)

    private fun byKind(t: Tile) = kinds4.filter { it != t.kind }.map { t.copy(kind = it) }
    private fun byCount(t: Tile) = (1..4).filter { it != t.count }.map { t.copy(count = it) }
    private fun byRotation(t: Tile) = listOf(0, 90, 180, 270).filter { it != t.rotation }.map { t.copy(rotation = it) }
    private fun byColor(t: Tile) = (0..3).filter { it != t.color }.map { t.copy(color = it) }
    private fun bySlot(t: Tile) = (0..3).filter { it != t.slot }.map { t.copy(slot = it) }

    private fun build(): List<VisualPuzzle> = listOf(
        make(1, SHAPE, 1, 5, 4, 5, 1, { Tile(if (it % 2 == 0) CIRCLE else SQUARE) }, ::byKind),
        make(2, SHAPE, 2, 7, 3, 7, 2, { Tile(listOf(CIRCLE, TRIANGLE, SQUARE)[it % 3]) }, ::byKind),
        make(3, COUNT, 1, 4, 3, 4, 3, { Tile(CIRCLE, count = it + 1) }, ::byCount),
        make(4, COUNT, 2, 4, 3, 4, 0, { Tile(SQUARE, color = 1, count = 4 - it) }, ::byCount),
        make(5, ROTATION, 1, 4, 3, 4, 2, { Tile(ARROW, rotation = it * 90 % 360) }, ::byRotation),
        make(6, ROTATION, 2, 5, 2, 5, 1, { Tile(ARROW, color = 2, rotation = (360 - it * 90) % 360) }, ::byRotation),
        make(7, COLOR, 2, 6, 5, 6, 3, { Tile(SQUARE, color = it % 3) }, ::byColor),
        make(8, POSITION, 2, 5, 4, 5, 0, { Tile(CIRCLE, color = 1, slot = it % 4) }, ::bySlot),
        make(9, COMBINED, 3, 4, 3, 4, 2, { Tile(if (it % 2 == 0) CIRCLE else SQUARE, count = it + 1) }) { a ->
            listOf(a.copy(kind = CIRCLE), a.copy(count = 3), a.copy(kind = CIRCLE, count = 3))
        },
        make(10, COMBINED, 3, 5, 4, 5, 1, { Tile(ARROW, color = it % 2, rotation = it * 90 % 360) }) { a ->
            listOf(a.copy(color = 1), a.copy(rotation = 90), a.copy(color = 1, rotation = 90))
        },
        // 2x2: shape by column, colour by row.
        make(11, MATRIX, 3, 4, 3, 2, 3, { Tile(if (it % 2 == 0) CIRCLE else SQUARE, color = it / 2) }) { a ->
            listOf(a.copy(kind = CIRCLE), a.copy(color = 0), a.copy(kind = CIRCLE, color = 0))
        },
        // 3x3: shape by column, count by row.
        make(12, MATRIX, 3, 9, 8, 3, 0, { Tile(listOf(CIRCLE, SQUARE, TRIANGLE)[it % 3], count = it / 3 + 1) }) { a ->
            listOf(a.copy(count = 2), a.copy(kind = SQUARE), a.copy(kind = SQUARE, count = 2))
        },
    )
}
