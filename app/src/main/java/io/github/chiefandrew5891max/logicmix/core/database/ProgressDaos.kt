package io.github.chiefandrew5891max.logicmix.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE profileId = :profileId LIMIT 1")
    suspend fun get(profileId: String): ProgressEntity?

    @Query("SELECT * FROM progress WHERE profileId = :profileId LIMIT 1")
    fun observe(profileId: String): Flow<ProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: ProgressEntity): Long

    @Upsert
    suspend fun upsert(entity: ProgressEntity)
}

@Dao
interface SolvedPuzzleDao {
    // Returns -1 when the puzzle was already solved in this profile.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: SolvedPuzzleEntity): Long

    @Query("SELECT puzzleId FROM solved_puzzles WHERE profileId = :profileId ORDER BY solvedAt, puzzleId")
    suspend fun puzzleIds(profileId: String): List<String>

    @Query("SELECT COUNT(*) FROM solved_puzzles WHERE profileId = :profileId")
    suspend fun count(profileId: String): Int
}

@Dao
interface AttemptDao {
    @Insert
    suspend fun insert(entity: AttemptEntity): Long

    @Query("SELECT * FROM attempts WHERE profileId = :profileId ORDER BY id DESC LIMIT :limit")
    suspend fun recent(profileId: String, limit: Int): List<AttemptEntity>

    @Query("SELECT COUNT(*) FROM attempts WHERE profileId = :profileId")
    suspend fun count(profileId: String): Int
}

@Dao
interface XpTransactionDao {
    // Default ABORT strategy: a duplicate transaction id is an inconsistency and must fail the whole transaction.
    @Insert
    suspend fun insert(entity: XpTransactionEntity): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_transactions WHERE profileId = :profileId")
    suspend fun totalAmount(profileId: String): Int

    @Query("SELECT COUNT(*) FROM xp_transactions WHERE profileId = :profileId")
    suspend fun count(profileId: String): Int
}
