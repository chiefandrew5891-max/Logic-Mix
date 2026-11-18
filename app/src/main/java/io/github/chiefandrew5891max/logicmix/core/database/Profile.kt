package io.github.chiefandrew5891max.logicmix.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey val id: String,
    val name: String,
    val difficulty: String,
    val difficultyLockedAtLevel: Int?,
    val createdAt: Long,
)

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(profile: Profile)

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<Profile?>
}

@Database(entities = [Profile::class], version = 1, exportSchema = true)
abstract class LogicMixDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}
