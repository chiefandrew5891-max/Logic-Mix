package com.akynex.logicmix.domain.progression

enum class XpReason { FIRST_SOLVE, EXTRA_WORD }

// totalXp is the source of truth; level and xpIntoLevel are always derived from it with the B1 rules.
data class PlayerProgress(val totalXp: Int, val level: Int, val xpIntoLevel: Int) {
    companion object {
        val INITIAL = fromTotalXp(0)

        fun fromTotalXp(totalXp: Int): PlayerProgress {
            require(totalXp >= 0) { "totalXp must not be negative" }
            val derived = Progression.addXp(LevelProgress(Progression.MIN_LEVEL, 0), totalXp)
            return PlayerProgress(totalXp, derived.level, derived.xpIntoLevel)
        }
    }
}

data class AttemptResult(
    val xpAwarded: Int,
    val firstSolve: Boolean,
    val before: PlayerProgress,
    val after: PlayerProgress,
) {
    val levelsGained: Int get() = after.level - before.level
}
