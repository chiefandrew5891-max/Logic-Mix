package com.akynex.logicmix.domain.visual.place

import com.akynex.logicmix.domain.visual.geo.VisualCareer
import com.akynex.logicmix.domain.visual.geo.VisualLevel
import kotlin.random.Random

enum class InteractiveFamily(val place: PlaceFamily?) {
    POLYOMINO(PlaceFamily.POLYOMINO), SILHOUETTE(PlaceFamily.SILHOUETTE), DISSECTION(PlaceFamily.DISSECTION), MOSAIC(PlaceFamily.MOSAIC),
    HEX(PlaceFamily.HEX), PARADOX(PlaceFamily.PARADOX), RUSH_HOUR(null), KLOTSKI(null), CUBE_NET(null);
}

sealed interface Interactive {
    val id: String
    val family: InteractiveFamily
    val difficulty: Int
    data class Place(val puzzle: PlacePuzzle, override val family: InteractiveFamily) : Interactive { override val id get() = puzzle.id; override val difficulty get() = puzzle.difficulty }
    data class Slide(val puzzle: SlidePuzzle, override val family: InteractiveFamily) : Interactive { override val id get() = puzzle.id; override val difficulty get() = puzzle.difficulty }
    data class Net(val puzzle: NetPuzzle) : Interactive { override val id get() = puzzle.id; override val family get() = InteractiveFamily.CUBE_NET; override val difficulty get() = puzzle.difficulty }
}

/**
 * The Visual Logic level sequence: about 70% interactive puzzles and 30% classic or generated choice puzzles.
 * The first levels already alternate, interactive families rotate in shuffled rounds without repeating back to back,
 * and difficulty grows with the round. Choice levels keep their old ids (vl-*, vg-*); interactive ones are vi-###.
 */
object VisualMix {
    const val ROUNDS = 30
    private val families = InteractiveFamily.entries
    private const val PATTERN = "ICIICIIICI" // 7 interactive, 3 choice per ten levels

    private val interactiveOrder: List<InteractiveFamily> by lazy {
        val out = mutableListOf<InteractiveFamily>()
        for (r in 0 until ROUNDS) {
            var round = families.shuffled(Random(0x1A7E_0000L + r))
            if (out.isNotEmpty() && round.first() == out.last()) round = round.drop(1) + round.first()
            out += round
        }
        out
    }

    val interactiveCount: Int get() = interactiveOrder.size

    /** Slot i is either an interactive ordinal (>= 0) or a choice index encoded as -(choiceIndex + 1). */
    private val slots: IntArray by lazy {
        val out = ArrayList<Int>()
        var inter = 0
        var choice = 0
        var i = 0
        while (inter < interactiveCount) {
            if (PATTERN[i % PATTERN.length] == 'I') out += inter++ else out += -(choice++ + 1)
            i++
        }
        while (choice < VisualCareer.total) out += -(choice++ + 1)
        out.toIntArray()
    }

    val total: Int get() = slots.size

    fun interactiveFamilyAt(ordinal: Int) = interactiveOrder[ordinal]

    fun difficultyOf(ordinal: Int): Int = 1 + ordinal / families.size * 4 / (ROUNDS - 1)

    fun idOf(ordinal: Int) = "vi-%03d".format(ordinal + 1)

    fun levelId(index: Int): String? {
        val s = slots.getOrNull(index) ?: return null
        return if (s >= 0) idOf(s) else VisualCareer.levelAt(-s - 1)?.id
    }

    fun interactiveAt(ordinal: Int): Interactive {
        val family = interactiveOrder[ordinal]
        val d = difficultyOf(ordinal)
        val seed = 0xB10C_0000L + ordinal * 37L
        val id = idOf(ordinal)
        return when (family) {
            InteractiveFamily.RUSH_HOUR -> Interactive.Slide(SlideSolver.generate(id, d, SlideKind.RUSH_HOUR, seed), family)
            InteractiveFamily.KLOTSKI -> Interactive.Slide(SlideSolver.generate(id, d, SlideKind.KLOTSKI, seed), family)
            InteractiveFamily.CUBE_NET -> Interactive.Net(CubeNets.generate(id, d, seed))
            else -> Interactive.Place(PlaceGenerators.generate(family.place!!, d, seed, id), family)
        }
    }

    sealed interface Level {
        val id: String
        data class Choice(val level: VisualLevel) : Level { override val id get() = level.id }
        data class Hands(val puzzle: Interactive) : Level { override val id get() = puzzle.id }
    }

    fun levelAt(index: Int): Level? {
        val s = slots.getOrNull(index) ?: return null
        return if (s >= 0) Level.Hands(interactiveAt(s)) else VisualCareer.levelAt(-s - 1)?.let { Level.Choice(it) }
    }
}
