package com.akynex.logicmix.domain.progression

import com.akynex.logicmix.domain.content.GameCategory

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

    /**
     * First-solve XP for any category: category base + difficulty bonus (tier 0..2 as in ArcadeRules.tierOf) + no-hint bonus.
     * All numbers live in [XpConfig].
     */
    fun awardFor(category: GameCategory?, tier: Int, hintsUsed: Int): XpAward = XpAward(
        base = XpConfig.base(category) + XpConfig.tierBonus(tier),
        noHintBonus = if (hintsUsed == 0) XpConfig.NO_HINT_BONUS else 0,
    )

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

/**
 * The single place to tune rewards. Level thresholds stay in [Progression.requiredXp] (level 1 -> 2 needs 100 XP, i.e. about
 * five ordinary puzzles at 20-25 XP). A puzzle pays XP once per puzzle id, so Arcade and normal modes share one ledger.
 */
object XpConfig {
    const val DEFAULT_BASE = 20
    const val NO_HINT_BONUS = Progression.NO_HINT_BONUS

    /** Extra XP for medium / hard puzzles (tier 0 = easy). */
    val TIER_BONUS = intArrayOf(0, 5, 10)

    private val CATEGORY_BASE = mapOf(
        GameCategory.SUDOKU to 25,
        GameCategory.MINI_CROSSWORD to 25,
        GameCategory.WORD_SEARCH to 25,
        // Endless games: a confirmed result or Arcade goal is worth a little less and is capped per day.
        GameCategory.ORB_LINES to 15,
        GameCategory.TWO_DOTS to 15,
    )

    /** Endless games (Lines, Two Dots) pay XP for at most this many confirmed results per rolling 24 hours. */
    const val ENDLESS_DAILY_CAP = 8
    const val DAY_MS = 24L * 60 * 60 * 1000

    /** Fillwords bonus words: XP per new word and the most one level can pay in bonuses. */
    const val EXTRA_WORD_XP = 2
    const val EXTRA_WORD_LEVEL_CAP = 10

    fun base(category: GameCategory?): Int = CATEGORY_BASE[category] ?: DEFAULT_BASE
    fun tierBonus(tier: Int): Int = TIER_BONUS[tier.coerceIn(0, TIER_BONUS.lastIndex)]
    fun isEndless(category: GameCategory?): Boolean = category == GameCategory.ORB_LINES || category == GameCategory.TWO_DOTS
}
