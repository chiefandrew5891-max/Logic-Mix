package io.github.chiefandrew5891max.logicmix.core.data

import androidx.room.withTransaction
import io.github.chiefandrew5891max.logicmix.core.database.AttemptEntity
import io.github.chiefandrew5891max.logicmix.core.database.LogicMixDatabase
import io.github.chiefandrew5891max.logicmix.core.database.ProgressEntity
import io.github.chiefandrew5891max.logicmix.core.database.SolvedPuzzleEntity
import io.github.chiefandrew5891max.logicmix.core.database.XpTransactionEntity
import io.github.chiefandrew5891max.logicmix.domain.game.AttemptOutcome
import io.github.chiefandrew5891max.logicmix.domain.game.GameMode
import io.github.chiefandrew5891max.logicmix.domain.progression.AttemptResult
import io.github.chiefandrew5891max.logicmix.domain.progression.PlayerProgress
import io.github.chiefandrew5891max.logicmix.domain.progression.Progression
import io.github.chiefandrew5891max.logicmix.domain.progression.XpReason
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

            val award = Progression.awardFor(hintsUsed)
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

    private fun xpTransactionId(profileId: String, puzzleId: String, reason: XpReason) =
        "$profileId:$puzzleId:${reason.name}"
}
