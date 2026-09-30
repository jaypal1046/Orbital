package com.orbital.di

import android.content.Context
import androidx.room.Room
import com.orbital.action.AppCapabilityManager
import com.orbital.action.DeviceActionExecutor
import com.orbital.chat.ChatEngine
import com.orbital.chat.DefaultChatEngine
import com.orbital.data.LlmRepository
import com.orbital.data.SecureStorage
import com.orbital.data.db.ChatDao
import com.orbital.data.db.ChatEncryptionHelper
import com.orbital.data.db.ChatHistoryRepository
import com.orbital.data.db.OrbitalDatabase
import com.orbital.voice.VoiceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSecureStorage(@ApplicationContext context: Context): SecureStorage {
        return SecureStorage(context)
    }

    @Provides
    @Singleton
    fun provideAppCapabilityManager(@ApplicationContext context: Context): AppCapabilityManager {
        return AppCapabilityManager(context)
    }

    @Provides
    @Singleton
    fun provideDeviceActionExecutor(
        @ApplicationContext context: Context
    ): DeviceActionExecutor {
        return DeviceActionExecutor(context)
    }

    @Provides
    @Singleton
    fun provideVoiceManager(@ApplicationContext context: Context): VoiceManager {
        return VoiceManager(context)
    }

    @Provides
    @Singleton
    fun provideLlmRepository(secureStorage: SecureStorage): LlmRepository {
        return LlmRepository(secureStorage)
    }

    @Provides
    @Singleton
    fun provideOrbitalDatabase(@ApplicationContext context: Context): OrbitalDatabase {
        return Room.databaseBuilder(
            context,
            OrbitalDatabase::class.java,
            "orbital_secure.db"
        )
            .addMigrations(OrbitalDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: OrbitalDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    @Singleton
    fun provideChatHistoryRepository(
        chatDao: ChatDao,
        encryptionHelper: ChatEncryptionHelper
    ): ChatHistoryRepository {
        return ChatHistoryRepository(chatDao, encryptionHelper)
    }

    @Provides
    @Singleton
    fun provideChatEngine(
        @ApplicationContext context: Context,
        llmRepository: LlmRepository,
        actionExecutor: DeviceActionExecutor,
        voiceManager: VoiceManager,
        chatHistoryRepository: ChatHistoryRepository,
        hindsightMemoryEngine: com.orbital.memory.hindsight.HindsightMemoryEngine,
        foremanSupervisor: com.orbital.foreman.ForemanSupervisor,
        documentPipeline: com.orbital.media.parser.HybridDocumentPipeline,
        dynamicOtaConfigStore: com.orbital.updater.DynamicOtaConfigStore
    ): ChatEngine {
        return DefaultChatEngine(
            context = context,
            llmRepository = llmRepository,
            voiceManager = voiceManager,
            deviceActionExecutor = actionExecutor,
            chatHistoryRepository = chatHistoryRepository,
            hindsightMemoryEngine = hindsightMemoryEngine,
            foremanSupervisor = foremanSupervisor,
            documentPipeline = documentPipeline,
            dynamicOtaConfigStore = dynamicOtaConfigStore
        )
    }
}

@Module
@InstallIn(ServiceComponent::class)
object OverlayServiceModule {

    @Provides
    @ServiceScoped
    fun provideOverlayServiceScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.Main + SupervisorJob())
    }
}
