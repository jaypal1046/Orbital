package com.orbital.di

import android.content.Context
import com.orbital.action.AppCapabilityManager
import com.orbital.action.DeviceActionExecutor
import com.orbital.chat.ChatEngine
import com.orbital.chat.DefaultChatEngine
import com.orbital.data.LlmRepository
import com.orbital.data.SecureStorage
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
    fun provideChatEngine(
        llmRepository: LlmRepository,
        actionExecutor: DeviceActionExecutor,
        voiceManager: VoiceManager
    ): ChatEngine {
        return DefaultChatEngine(llmRepository, voiceManager, actionExecutor)
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