package com.orbital.decision.jev

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DecisionEngineModule {

    @Provides
    @Singleton
    fun provideLocalFallbackEngine(): JevLocalFallbackEngine {
        return JevLocalFallbackEngine()
    }

    @Provides
    @Singleton
    fun provideJevDecisionEngine(
        @ApplicationContext context: Context,
        localFallback: JevLocalFallbackEngine
    ): JevDecisionEngine {
        val prefs = context.getSharedPreferences("orbital_settings", Context.MODE_PRIVATE)
        val remoteUrl = prefs.getString("openjev_base_url", null)
        val apiKey = prefs.getString("openjev_api_key", null)

        return JevRemoteClient(
            baseUrl = remoteUrl,
            apiKey = apiKey,
            localFallback = localFallback
        )
    }

    @Provides
    @Singleton
    fun provideAccessibilityNodeRanker(
        decisionEngine: JevDecisionEngine
    ): AccessibilityNodeRanker {
        return AccessibilityNodeRanker(decisionEngine)
    }
}
