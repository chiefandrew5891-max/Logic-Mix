package com.akynex.logicmix.domain.progression

import kotlin.math.pow
import kotlin.math.roundToInt

data class LevelProgress(val level: Int, val xpIntoLevel: Int) {
    init {
        require(level in Progression.MIN_LEVEL..Progression.MAX_LEVEL) { "level out of range" }
        require(xpIntoLevel >= 0) { "xp must not be negative" }
    }
}

data class XpAward(val base: Int, val noHintBonus: Int) {
    val total: Int get() = base + noHintBonus
}

// Starting balance hypothesis from the spec (sections 6.2-6.3); not final.
object Progression {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 99
    const val BASE_XP = 20
    const val NO_HINT_BONUS = 5

    fun requiredXp(level: Int): Int {
        require(level in MIN_LEVEL until MAX_LEVEL) { "no threshold for level $level" }
        val raw = 100 + 35 * (level - 1).toDouble().pow(1.35)
        return (raw / 50.0).roundToInt() * 50
    }

    fun awardFor(hintsUsed: Int): XpAward =
        XpAward(BASE_XP, if (hintsUsed == 0) NO_HINT_BONUS else 0)

    // Carries the remainder over and may raise several levels at once; XP keeps accumulating at the cap.
    fun addXp(progress: LevelProgress, amount: Int): LevelProgress {
        require(amount >= 0) { "amount must not be negative" }
        var level = progress.level
        var xp = progress.xpIntoLevel + amount
        while (level < MAX_LEVEL && xp >= requiredXp(level)) {
            xp -= requiredXp(level)
            level++
        }
        return LevelProgress(level, xp)
    }
}
