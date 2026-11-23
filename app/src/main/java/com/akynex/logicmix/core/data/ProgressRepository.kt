package com.akynex.logicmix.core.data

import androidx.room.withTransaction
import com.akynex.logicmix.core.database.AttemptEntity
import com.akynex.logicmix.core.database.LogicMixDatabase
import com.akynex.logicmix.core.database.ProgressEntity
import com.akynex.logicmix.core.database.SolvedPuzzleEntity
import com.akynex.logicmix.core.database.XpTransactionEntity
import com.akynex.logicmix.domain.arcade.ArcadeRules
import com.akynex.logicmix.domain.content.GameCategory
import com.akynex.logicmix.domain.game.AttemptOutcome
import com.akynex.logicmix.domain.game.GameMode
import com.akynex.logicmix.domain.progression.AttemptResult
import com.akynex.logicmix.domain.progression.PlayerProgress
import com.akynex.logicmix.domain.progression.Progression
import com.akynex.logicmix.domain.progression.XpConfig
import com.akynex.logicmix.domain.progression.XpReason
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProgressRepository internal constructor(
    private val database: LogicMixDatabase,
    private val now: () -> Long,
) {
    @Inject
    constructor(database: LogicMixDatabase) : this(database, System::currentTimeMillis)

    private val progressDao = database.progressDao()
    private val solvedDao = database.solvedPuzzleDao()
    private val attemptDao = database.attemptDao()
    private val xpDao = database.xpTransactionDao()

    // A missing row (first launch, profile without progress yet) is the initial progress, not an error.
    suspend fun getProgress(profileId: String): PlayerProgress =
        PlayerProgress.fromTotalXp(progressDao.get(profileId)?.totalXp ?: 0)

    fun observeProgress(profileId: String): Flow<PlayerProgress> =
        progressDao.observe(profileId).map { PlayerProgress.fromTotalXp(it?.totalXp ?: 0) }

    fun observeStreak(profileId: String): Flow<Int> = progressDao.observe(profileId).map { it?.streak ?: 0 }

    // The profile row must already exist (see LocalProfileRepository.ensureProfile).
    suspend fun ensureProgress(profileId: String) {
        progressDao.insertIfAbsent(
            ProgressEntity(profileId, Progression.MIN_LEVEL, totalXp = 0, streak = 0, updatedAt = now()),
        )
    }

    suspend fun solvedPuzzleIds(profileId: String): List<String> = solvedDao.puzzleIds(profileId)

    suspend fun recentAttempts(profileId: String, limit: Int = 50): List<AttemptEntity> =
        attemptDao.recent(profileId, limit)

    /**
     * Stores the attempt and, for the first correct CAREER solve of a puzzle, the XP award,
     * all in one transaction. Repeats, wrong answers and non-career modes never award XP
     * (spec 3.1, 3.2); everything is rolled back if any step fails.
     */
    suspend fun recordAttempt(
        profileId: String,
        puzzleId: String,
        mode: GameMode,
        outcome: AttemptOutcome,
        startedAt: Long,
        finishedAt: Long,
        hintsUsed: Int,
        category: GameCategory? = null,
        difficulty: String? = null,
    ): AttemptResult {
        require(profileId.isNotBlank()) { "profileId is blank" }
        require(puzzleId.isNotBlank()) { "puzzleId is blank" }
        require(hintsUsed >= 0) { "hintsUsed must not be negative" }

        return database.withTransaction {
            attemptDao.insert(
                AttemptEntity(
                    profileId = profileId,
                    puzzleId = puzzleId,
                    mode = mode,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    outcome = outcome,
                    durationMs = (finishedAt - startedAt).coerceAtLeast(0),
                ),
            )
            val existing = progressDao.get(profileId)
            val before = PlayerProgress.fromTotalXp(existing?.totalXp ?: 0)
            val noAward = AttemptResult(xpAwarded = 0, firstSolve = false, before = before, after = before)
            if (outcome != AttemptOutcome.CORRECT || mode != GameMode.CAREER) {
                return@withTransaction noAward
            }

            val tier = category?.let { ArcadeRules.tierOf(it, difficulty) } ?: 0
            val full = Progression.awardFor(category, tier, hintsUsed)
            // Endless games pay a limited number of confirmed results per rolling day; the solve itself is still recorded.
            val capped = XpConfig.isEndless(category) &&
                xpDao.countPaid(profileId, XpReason.FIRST_SOLVE, puzzleId.substringBefore('-') + "-", now() - XpConfig.DAY_MS) >= XpConfig.ENDLESS_DAILY_CAP
            val award = if (capped) com.akynex.logicmix.domain.progression.XpAward(0, 0) else full
            val inserted = solvedDao.insertIfAbsent(
                SolvedPuzzleEntity(profileId, puzzleId, solvedAt = finishedAt, hintsUsed = hintsUsed, firstSolveXp = award.total),
            )
            if (inserted == -1L) return@withTransaction noAward

            val timestamp = now()
            xpDao.insert(
                XpTransactionEntity(
                    id = xpTransactionId(profileId, puzzleId, XpReason.FIRST_SOLVE),
                    profileId = profileId,
                    puzzleId = puzzleId,
                    amount = award.total,
                    reason = XpReason.FIRST_SOLVE,
                    createdAt = timestamp,
                ),
            )
            val after = PlayerProgress.fromTotalXp(before.totalXp + award.total)
            progressDao.upsert(
                ProgressEntity(profileId, after.level, after.totalXp, streak = existing?.streak ?: 0, updatedAt = timestamp),
            )
            AttemptResult(award.total, firstSolve = true, before = before, after = after)
        }
    }

    /**
     * Awards [amount] XP for one extra word of a level, once per (profile, level, word); returns the XP
     * actually added (0 for a repeat, also after an app restart). The transaction id is the dedup key.
     */
    suspend fun awardExtraWord(profileId: String, levelId: String, word: String, amount: Int): Int {
        require(amount >= 0) { "amount must not be negative" }
        if (amount == 0) return 0
        return database.withTransaction {
            val timestamp = now()
            val paid = xpDao.sumFor(profileId, XpReason.EXTRA_WORD, levelId)
            val grant = minOf(amount, (XpConfig.EXTRA_WORD_LEVEL_CAP - paid).coerceAtLeast(0))
            if (grant == 0) return@withTransaction 0
            val inserted = xpDao.insertIfAbsent(
                XpTransactionEntity(
                    id = "$profileId:$levelId:${XpReason.EXTRA_WORD.name}:${word.uppercase()}",
                    profileId = profileId,
                    puzzleId = levelId,
                    amount = grant,
                    reason = XpReason.EXTRA_WORD,
                    createdAt = timestamp,
                ),
            )
            if (inserted == -1L) return@withTransaction 0
            val existing = progressDao.get(profileId)
            val after = PlayerProgress.fromTotalXp((existing?.totalXp ?: 0) + grant)
            progressDao.upsert(
                ProgressEntity(profileId, after.level, after.totalXp, streak = existing?.streak ?: 0, updatedAt = timestamp),
            )
            grant
        }
    }

    /**
     * Generic one-time XP grant (for example a rewarded ad). [key] is the dedup key: the same key never pays twice,
     * also after a restart, and goes through the same ledger and total as puzzle XP.
     */
    suspend fun awardOnce(profileId: String, key: String, amount: Int, reason: XpReason = XpReason.AD_REWARD): Int {
        require(amount >= 0) { "amount must not be negative" }
        require(key.isNotBlank()) { "key is blank" }
        if (amount == 0) return 0
        return database.withTransaction {
            val timestamp = now()
            val inserted = xpDao.insertIfAbsent(
                XpTransactionEntity("$profileId:$key:${reason.name}", profileId, key, amount, reason, timestamp),
            )
            if (inserted == -1L) return@withTransaction 0
            val existing = progressDao.get(profileId)
            val after = PlayerProgress.fromTotalXp((existing?.totalXp ?: 0) + amount)
            progressDao.upsert(
                ProgressEntity(profileId, after.level, after.totalXp, streak = existing?.streak ?: 0, updatedAt = timestamp),
            )
            amount
        }
    }

    private fun xpTransactionId(profileId: String, puzzleId: String, reason: XpReason) =
        "$profileId:$puzzleId:${reason.name}"
}
