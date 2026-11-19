package io.github.chiefandrew5891max.logicmix.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Source of the "can continue" state of the main button. Game engines will provide the real implementation. */
interface ActiveSessionProvider {
    val hasActiveSession: Flow<Boolean>
}

// No game session is persisted yet (puzzle engines are a later stage), so there is never one to resume.
@Singleton
class NoActiveSessionProvider @Inject constructor() : ActiveSessionProvider {
    override val hasActiveSession: Flow<Boolean> = flowOf(false)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds
    abstract fun bindActiveSessionProvider(impl: NoActiveSessionProvider): ActiveSessionProvider
}
