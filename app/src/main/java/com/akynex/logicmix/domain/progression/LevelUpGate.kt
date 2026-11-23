package com.akynex.logicmix.domain.progression

/** Persistent "highest level whose celebration has already been started". */
interface CelebratedLevelStorage {
    var celebratedLevel: Int?
}

/**
 * Decides when the Player Level Up fanfare may play. The level is a pure function of total XP, so the only extra state is
 * the last celebrated level: it is written before the scene starts, which makes the fanfare play exactly once per
 * level (also across restarts and when several levels are gained at once). The first observation only records the current
 * level, so existing players are not celebrated retroactively.
 */
class LevelUpGate(private val storage: CelebratedLevelStorage) {
    @Synchronized
    fun onLevel(level: Int): Int? {
        val seen = storage.celebratedLevel
        if (seen == null || level <= seen) {
            if (seen == null) storage.celebratedLevel = level
            return null
        }
        storage.celebratedLevel = level
        return level
    }
}
