package com.akynex.logicmix.domain.visual.geo

import com.akynex.logicmix.domain.visual.VisualPacks
import com.akynex.logicmix.domain.visual.VisualPuzzle
import kotlin.random.Random

/** One Career level: the original hand-built puzzles first (ids and saves unchanged), then generated ones. */
sealed interface VisualLevel {
    val id: String
    data class Classic(val puzzle: VisualPuzzle) : VisualLevel { override val id get() = puzzle.id }
    data class Generated(val puzzle: GeoPuzzle) : VisualLevel { override val id get() = puzzle.id }
}

/**
 * Deterministic family scheduler. Levels are dealt in blocks of all 13 families in a seeded shuffle, and a block
 * never starts with the family that ended the previous one, so the same family never appears twice in a row.
 * Level i always yields the same puzzle. [familyAt] and [difficultyAt] also serve future Endless and Daily modes.
 */
object VisualCareer {
    const val BLOCKS = 12
    private val families = GeoFamily.entries
    val generatedCount = BLOCKS * families.size
    val total: Int get() = VisualPacks.all.size + generatedCount

    private val order: List<GeoFamily> by lazy {
        val out = mutableListOf<GeoFamily>()
        for (b in 0 until BLOCKS) {
            var block = families.shuffled(Random(0x5EED_0000L + b))
            if (out.isNotEmpty() && block.first() == out.last()) block = block.drop(1) + block.first()
            out += block
        }
        out
    }

    fun familyAt(generatedIndex: Int): GeoFamily = order[generatedIndex]

    fun difficultyAt(generatedIndex: Int): Int = 1 + (generatedIndex / families.size) * 5 / BLOCKS

    fun seedAt(generatedIndex: Int): Long = 0xA11CE000L + generatedIndex * 31L

    fun idAt(generatedIndex: Int) = "vg-%03d".format(generatedIndex + 1)

    fun levelAt(index: Int): VisualLevel? {
        val classic = VisualPacks.all
        if (index < 0) return null
        if (index < classic.size) return VisualLevel.Classic(classic[index])
        val g = index - classic.size
        if (g >= generatedCount) return null
        return VisualLevel.Generated(GeoGenerators.generate(familyAt(g), difficultyAt(g), seedAt(g), idAt(g)))
    }
}
