package io.github.chiefandrew5891max.logicmix.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LogicMixDatabase =
        Room.databaseBuilder(context, LogicMixDatabase::class.java, "logic_mix.db").build()

    @Provides
    fun provideProfileDao(database: LogicMixDatabase): ProfileDao = database.profileDao()
}
