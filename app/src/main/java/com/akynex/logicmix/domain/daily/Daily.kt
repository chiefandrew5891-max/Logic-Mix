package com.akynex.logicmix.domain.daily

import java.time.Instant
import java.time.ZoneOffset

/** Mirrors backend `daily.DAILY_CONFIG` (not final; owner has not approved the ranges). */
object DailyConfig {
    const val PUZZLES_PER_DAY = 5
    val tierLevelRanges: Map<Int, IntRange> = mapOf(1 to 1..5, 2 to 6..15, 3 to 16..30, 4 to 31..50, 5 to 51..99)

    fun tierFor(level: Int): Int =
        tierLevelRanges.entries.firstOrNull { level in it.value }?.key ?: if (level < 1) 1 else 5
}

/** Server day = UTC date; the device time zone and clock-setting tricks never choose the plan, only the displayed countdown. */
object DailyClock {
    fun dayOf(nowMillis: Long): String = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()

    fun millisUntilNextDay(nowMillis: Long): Long {
        val next = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC)
        return next.toInstant().toEpochMilli() - nowMillis
    }
}

/** The tier chosen the first time the player opens a day; levelling up or down later the same day never changes it. */
data class DayTierLock(val day: String, val tier: Int)

object DailyTierPolicy {
    fun resolve(lock: DayTierLock?, today: String, level: Int): DayTierLock =
        if (lock != null && lock.day == today) lock else DayTierLock(today, DailyConfig.tierFor(level))
}

/** Per-day progress. A puzzle id awards XP once; the set is the only place that decides it, so restarts cannot repeat it. */
data class DailyProgress(val dailyId: String, val solved: Set<String> = emptySet()) {
    val count: Int get() = solved.size
    val complete: Boolean get() = count >= DailyConfig.PUZZLES_PER_DAY

    /** Returns the new progress and whether this call is the first completion of [puzzleId] (the only time XP may be paid). */
    fun complete(puzzleId: String): Pair<DailyProgress, Boolean> =
        if (puzzleId in solved) this to false else copy(solved = solved + puzzleId) to true
}
