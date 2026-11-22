package com.akynex.logicmix.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow

/** Source of the "can continue" state of the main button. Implemented by [GameSessionStore]. */
interface ActiveSessionProvider {
    val hasActiveSession: Flow<Boolean>
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds
    abstract fun bindActiveSessionProvider(impl: GameSessionStore): ActiveSessionProvider
}
