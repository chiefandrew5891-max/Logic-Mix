package io.github.chiefandrew5891max.logicmix.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.chiefandrew5891max.logicmix.domain.game.AttemptOutcome
import io.github.chiefandrew5891max.logicmix.domain.game.GameMode
import io.github.chiefandrew5891max.logicmix.domain.progression.XpReason

private const val CASCADE = ForeignKey.CASCADE

@Entity(
    tableName = "progress",
    foreignKeys = [ForeignKey(Profile::class, ["id"], ["profileId"], onDelete = CASCADE)],
)
data class ProgressEntity(
    @PrimaryKey val profileId: String,
    val level: Int,
    val totalXp: Int,
    // Reserved by the spec's schema; the streak rules are not defined yet, so it stays 0.
    val streak: Int,
    val updatedAt: Long,
)

@Entity(
    tableName = "solved_puzzles",
    primaryKeys = ["profileId", "puzzleId"],
    foreignKeys = [ForeignKey(Profile::class, ["id"], ["profileId"], onDelete = CASCADE)],
)
data class SolvedPuzzleEntity(
    val profileId: String,
    val puzzleId: String,
    val solvedAt: Long,
    val hintsUsed: Int,
    val firstSolveXp: Int,
)

@Entity(
    tableName = "attempts",
    foreignKeys = [ForeignKey(Profile::class, ["id"], ["profileId"], onDelete = CASCADE)],
    indices = [Index("profileId")],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val puzzleId: String,
    val mode: GameMode,
    val startedAt: Long,
    val finishedAt: Long,
    val outcome: AttemptOutcome,
    val durationMs: Long,
)

@Entity(
    tableName = "xp_transactions",
    foreignKeys = [ForeignKey(Profile::class, ["id"], ["profileId"], onDelete = CASCADE)],
    indices = [Index("profileId")],
)
data class XpTransactionEntity(
    // Deterministic per (profile, puzzle, reason): a second award for the same solve cannot be stored.
    @PrimaryKey val id: String,
    val profileId: String,
    val puzzleId: String,
    val amount: Int,
    val reason: XpReason,
    val createdAt: Long,
)
