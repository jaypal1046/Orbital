package com.orbital.bridge

import android.content.Context
import com.orbital.action.DeviceActionExecutor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BridgeModule {

    @Provides
    @Singleton
    fun provideBridgeActionDispatcher(
        @ApplicationContext context: Context,
        actionExecutor: DeviceActionExecutor
    ): BridgeActionDispatcher {
        return BridgeActionDispatcher(context, actionExecutor)
    }

    @Provides
    @Singleton
    fun provideOrbitalBridgeClient(
        @ApplicationContext context: Context,
        actionDispatcher: BridgeActionDispatcher
    ): OrbitalBridgeClient {
        return OrbitalBridgeClient(context, actionDispatcher)
    }
}
