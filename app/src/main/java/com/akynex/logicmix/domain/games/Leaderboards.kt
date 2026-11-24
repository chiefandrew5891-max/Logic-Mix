package com.akynex.logicmix.domain.games

/** Every Google Play Games leaderboard Logic Mix uses. [key] is the stable name in leaderboards.properties. */
enum class LeaderboardKey(val key: String) {
    TOTAL_XP("total_xp"),
    ARCADE("arcade"),
    NUMBER_LOGIC("number_logic"),
    VISUAL_LOGIC("visual_logic"),
    MINI_CROSSWORD("mini_crossword"),
    WORD_SEARCH("word_search"),
    WORD_LOGIC("word_logic"),
    SUDOKU("sudoku"),
    MATCHSTICK("matchstick"),
    ORB_LINES("orb_lines"),
    TWO_DOTS("two_dots"),
}

/**
 * Leaderboard IDs created by the owner in Play Console (see docs/GOOGLE_PLAY_GAMES_SETUP.md). The repository ships none:
 * a blank value means "not configured", and that board is simply not offered.
 */
class LeaderboardIds(private val ids: Map<LeaderboardKey, String>) {
    fun idOf(key: LeaderboardKey): String? = ids[key]?.takeIf { it.isNotBlank() }
    val configured: List<LeaderboardKey> get() = LeaderboardKey.entries.filter { idOf(it) != null }

    companion object {
        const val PREFIX = "leaderboard_"

        /** Parses `leaderboard_<key>=<id>` lines; unknown keys, comments and malformed lines are ignored. */
        fun parse(text: String): LeaderboardIds {
            val raw = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
                .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            return LeaderboardIds(LeaderboardKey.entries.associateWith { raw[PREFIX + it.key].orEmpty() })
        }
    }
}

/**
 * Scores are cumulative or best-ever values that only grow, so submitting is idempotent: Play Games keeps the best score,
 * and re-sending after a failure can never double count. [plan] returns only what is new compared with what Google confirmed.
 */
object ScorePlanner {
    fun plan(current: Map<LeaderboardKey, Long>, confirmed: Map<LeaderboardKey, Long>, ids: LeaderboardIds): List<Pair<LeaderboardKey, Long>> =
        current.entries
            .filter { (k, v) -> v > 0 && ids.idOf(k) != null && v > (confirmed[k] ?: 0L) }
            .map { it.key to it.value }
}

/** Where a signed-in Play Games player stands on one board; absent values mean "no score yet". */
data class BoardStanding(val rank: String?, val score: String?)
data class BoardEntry(val rank: String, val name: String, val score: String)
