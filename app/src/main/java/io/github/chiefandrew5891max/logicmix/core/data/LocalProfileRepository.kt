package io.github.chiefandrew5891max.logicmix.core.data

import io.github.chiefandrew5891max.logicmix.core.database.Profile
import io.github.chiefandrew5891max.logicmix.core.database.ProfileDao
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class LocalProfileRepository @Inject constructor(private val dao: ProfileDao) {
    val profile: Flow<Profile?> = dao.observe(LOCAL_PROFILE_ID)

    suspend fun ensureProfile() {
        dao.insert(
            Profile(
                id = LOCAL_PROFILE_ID,
                name = "Local profile",
                difficulty = "CLASSIC",
                difficultyLockedAtLevel = null,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        const val LOCAL_PROFILE_ID = "local"
    }
}
