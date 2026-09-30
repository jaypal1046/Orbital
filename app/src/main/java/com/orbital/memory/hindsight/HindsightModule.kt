package com.orbital.memory.hindsight

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
object HindsightModule {

    @Provides
    @Singleton
    fun provideHindsightDatabase(
        @ApplicationContext context: Context
    ): HindsightDatabase {
        return Room.databaseBuilder(
            context,
            HindsightDatabase::class.java,
            "orbital_hindsight_memories.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideHindsightDao(
        database: HindsightDatabase
    ): HindsightDao {
        return database.hindsightDao()
    }

    @Provides
    @Singleton
    fun provideTextEmbeddingProvider(): TextEmbeddingProvider {
        return LocalFastEmbeddingProvider()
    }

    @Provides
    @Singleton
    fun provideHindsightMemoryEngine(
        dao: HindsightDao,
        embeddingProvider: TextEmbeddingProvider
    ): HindsightMemoryEngine {
        return HindsightMemoryEngineImpl(
            dao = dao,
            embeddingProvider = embeddingProvider
        )
    }
}
