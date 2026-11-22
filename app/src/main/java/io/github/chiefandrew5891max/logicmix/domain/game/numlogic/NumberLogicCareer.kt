package io.github.chiefandrew5891max.logicmix.domain.game.numlogic

import io.github.chiefandrew5891max.logicmix.domain.game.NumFamily
import io.github.chiefandrew5891max.logicmix.domain.game.NumberSequencePack
import io.github.chiefandrew5891max.logicmix.domain.game.NumberSequencePuzzle
import kotlin.random.Random

/** Deterministic Career: the 13 classic levels keep their ids, followed by generated `nl-NNN` levels. */
object NumberLogicCareer {
    const val BLOCKS = 10
    private val classic get() = NumberSequencePack.puzzles
    val generatedCount: Int = BLOCKS * NumFamily.entries.size
    val total: Int get() = classic.size + generatedCount

    private val order: List<NumFamily> by lazy {
        var prev: NumFamily? = null
        (0 until BLOCKS).flatMap { b ->
            val rnd = Random(9_001L + b)
            var block: List<NumFamily>
            do { block = NumFamily.entries.shuffled(rnd) } while (block.first() == prev)
            prev = block.last()
            block
        }
    }

    fun familyAt(g: Int) = order[g]
    fun difficultyAt(g: Int) = 1 + (g / NumFamily.entries.size) * 5 / BLOCKS
    fun seedAt(g: Int) = 70_000L + g
    fun idAt(g: Int) = "nl-%03d".format(g + 1)

    /** Index (0-based) of a level over classic + generated, or -1. */
    fun indexOf(id: String): Int {
        val c = classic.indexOfFirst { it.id == id }
        if (c >= 0) return c
        val g = id.removePrefix("nl-").toIntOrNull()?.takeIf { id.startsWith("nl-") } ?: return -1
        return if (g in 1..generatedCount) classic.size + g - 1 else -1
    }

    fun levelAt(i: Int): NumberSequencePuzzle? = when {
        i < 0 || i >= total -> null
        i < classic.size -> classic[i]
        else -> (i - classic.size).let { g -> NumGenerators.generate(familyAt(g), difficultyAt(g), seedAt(g), idAt(g)) }
    }

    fun byId(id: String) = indexOf(id).takeIf { it >= 0 }?.let(::levelAt)

    fun firstUnsolved(solved: Set<String>): NumberSequencePuzzle? {
        classic.firstOrNull { it.id !in solved }?.let { return it }
        return (0 until generatedCount).firstOrNull { idAt(it) !in solved }?.let { levelAt(classic.size + it) }
    }
}
