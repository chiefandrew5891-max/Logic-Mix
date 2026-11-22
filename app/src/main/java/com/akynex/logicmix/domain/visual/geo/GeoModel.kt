package com.akynex.logicmix.domain.visual.geo

/** The 13 procedural puzzle families. Each one has a generator in [GeoGenerators] and a rule check in [GeoValidator]. */
enum class GeoFamily {
    MATRIX, ROTATION, MIRROR, SILHOUETTE, ABSTRACT, KEYHOLE, FRAGMENT, SPATIAL, PAPER_FOLD, CUBE_NET, COMPOSE, ODD_ONE, SEQUENCE;

    val key: String get() = name.lowercase()

    companion object {
        fun fromKey(key: String): GeoFamily? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A language-neutral puzzle: [stimulus] panels (row-major, [columns] per row; the panel at [gapIndex] holds the
 * answer and stays hidden until solved), an optional [badge] of universal glyphs such as "↻90°" or "A ∪ B",
 * and four [options] of which exactly [correct] is right. Everything is derived from [seed], so it is reproducible.
 */
data class GeoPuzzle(
    val id: String,
    val family: GeoFamily,
    val difficulty: Int,
    val seed: Long,
    val stimulus: List<Figure>,
    val gapIndex: Int,
    val columns: Int,
    val badge: String,
    val options: List<Figure>,
    val correct: Int,
) {
    val optionIds: List<String> get() = options.indices.map { "v${it + 1}" }
    val correctId: String get() = "v${correct + 1}"

    fun problems(): List<String> = GeoValidator.problems(this)

    fun encode(): String = listOf(
        "g1", id, family.key, difficulty.toString(), seed.toString(), columns.toString(), gapIndex.toString(), badge,
        correct.toString(), stimulus.joinToString(";") { it.encode() }, options.joinToString(";") { it.encode() },
    ).joinToString("|")

    companion object {
        fun decode(raw: String): GeoPuzzle? {
            val p = raw.split("|")
            if (p.size != 11 || p[0] != "g1") return null
            val family = GeoFamily.fromKey(p[2]) ?: return null
            val stim = p[9].split(";").filter { it.isNotEmpty() }.map { Figure.decode(it) ?: return null }
            val opts = p[10].split(";").filter { it.isNotEmpty() }.map { Figure.decode(it) ?: return null }
            return GeoPuzzle(
                p[1], family, p[3].toIntOrNull() ?: return null, p[4].toLongOrNull() ?: return null, stim,
                p[6].toIntOrNull() ?: return null, p[5].toIntOrNull() ?: return null, p[7], opts,
                p[8].toIntOrNull() ?: return null,
            )
        }
    }
}

data class GeoGame(
    val puzzle: GeoPuzzle,
    val wrong: Set<String> = emptySet(),
    val eliminated: Set<String> = emptySet(),
    val hintsUsed: Int = 0,
    val solved: Boolean = false,
) {
    val blocked: Set<String> get() = wrong + eliminated

    fun choose(optionId: String): GeoGame {
        if (solved || optionId in blocked || optionId !in puzzle.optionIds) return this
        return if (optionId == puzzle.correctId) copy(solved = true) else copy(wrong = wrong + optionId)
    }

    /** Every hint removes one wrong option. */
    fun hint(): GeoGame {
        if (solved) return this
        val next = puzzle.optionIds.firstOrNull { it != puzzle.correctId && it !in blocked } ?: return this
        return copy(eliminated = eliminated + next, hintsUsed = hintsUsed + 1)
    }

    /** Keeps the hint counter so Restart cannot be used to earn the no-hint bonus. */
    fun restart(): GeoGame = GeoGame(puzzle, hintsUsed = hintsUsed)

    fun encode(): String = listOf(VERSION, hintsUsed.toString(), wrong.joinToString(","), eliminated.joinToString(",")).joinToString(";")

    companion object {
        const val VERSION = "g1"

        fun restore(puzzle: GeoPuzzle, raw: String?): GeoGame {
            val parts = raw?.split(";") ?: return GeoGame(puzzle)
            if (parts.size != 4 || parts[0] != VERSION) return GeoGame(puzzle)
            val ids = puzzle.optionIds.toSet()
            fun set(s: String) = s.split(",").filter { it in ids && it != puzzle.correctId }.toSet()
            return GeoGame(puzzle, set(parts[2]), set(parts[3]), parts[1].toIntOrNull()?.coerceAtLeast(0) ?: 0)
        }
    }
}
