package io.github.chiefandrew5891max.logicmix.core.data

import io.github.chiefandrew5891max.logicmix.core.database.Profile
import io.github.chiefandrew5891max.logicmix.core.database.ProfileDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalProfileRepositoryTest {
    @Test
    fun offlineProfileIsCreatedOnlyOnceAndExistingDataIsPreserved() = runBlocking {
        val dao = InMemoryProfileDao()
        val repository = LocalProfileRepository(dao)

        repository.ensureProfile()
        val created = dao.state.value
        assertNotNull(created)
        assertEquals("CLASSIC", created?.difficulty)
        repository.ensureProfile()

        assertEquals(created, dao.state.value)
    }

    @Test
    fun existingLocalProfileIsNotReplacedOnStartup() = runBlocking {
        val dao = InMemoryProfileDao()
        val existing = Profile(
            id = "local",
            name = "Existing",
            difficulty = "EXPERT",
            difficultyLockedAtLevel = 10,
            createdAt = 123L,
        )
        dao.state.value = existing

        LocalProfileRepository(dao).ensureProfile()

        assertEquals(existing, dao.state.value)
    }

    private class InMemoryProfileDao : ProfileDao {
        val state = MutableStateFlow<Profile?>(null)

        override suspend fun insert(profile: Profile) {
            if (state.value == null) state.value = profile
        }

        override fun observe(id: String): Flow<Profile?> = state
    }
}
