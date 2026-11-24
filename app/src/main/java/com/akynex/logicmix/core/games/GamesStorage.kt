package com.akynex.logicmix.core.games

import android.content.Context
import com.akynex.logicmix.domain.games.LeaderboardKey

/**
 * Local, Google-independent bookkeeping for Play Games: per-category XP (a monotonic leaderboard metric) and the highest score
 * Google has confirmed per board. Kept in its own SharedPreferences file; Room, XP and Firebase are never touched.
 */
class GamesStorage(context: Context) {
    private val prefs = context.getSharedPreferences("play_games", Context.MODE_PRIVATE)

    @Synchronized
    fun addCategoryXp(categoryKey: String, xp: Int) {
        if (xp <= 0) return
        prefs.edit().putLong("xp_$categoryKey", categoryXp(categoryKey) + xp).apply()
    }

    fun categoryXp(categoryKey: String): Long = prefs.getLong("xp_$categoryKey", 0L)

    fun confirmed(): Map<LeaderboardKey, Long> =
        LeaderboardKey.entries.associateWith { prefs.getLong("sent_${it.key}", 0L) }

    @Synchronized
    fun markConfirmed(key: LeaderboardKey, score: Long) {
        if (score > prefs.getLong("sent_${key.key}", 0L)) prefs.edit().putLong("sent_${key.key}", score).apply()
    }
}
