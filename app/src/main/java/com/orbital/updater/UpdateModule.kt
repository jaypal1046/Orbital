package com.orbital.updater

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideDistributionDetector(
        @ApplicationContext context: Context
    ): DistributionDetector {
        return DistributionDetector(context)
    }

    @Provides
    @Singleton
    fun provideDynamicOtaConfigStore(
        @ApplicationContext context: Context
    ): DynamicOtaConfigStore {
        return DynamicOtaConfigStore(context)
    }

    @Provides
    @Singleton
    fun provideGitHubUpdateEngine(
        @ApplicationContext context: Context,
        distributionDetector: DistributionDetector,
        otaConfigStore: DynamicOtaConfigStore
    ): GitHubUpdateEngine {
        return GitHubUpdateEngine(
            context = context,
            distributionDetector = distributionDetector,
            otaConfigStore = otaConfigStore
        )
    }
}
