package com.orbital.foreman

import com.orbital.decision.jev.JevDecisionEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ForemanModule {

    @Provides
    @Singleton
    fun provideForemanWatchdog(): ForemanWatchdog {
        return ForemanWatchdog()
    }

    @Provides
    @Singleton
    fun providePlanExecutionTracker(): PlanExecutionTracker {
        return PlanExecutionTracker()
    }

    @Provides
    @Singleton
    fun provideForemanSupervisor(
        decisionEngine: JevDecisionEngine,
        watchdog: ForemanWatchdog,
        tracker: PlanExecutionTracker
    ): ForemanSupervisor {
        return ForemanSupervisor(
            decisionEngine = decisionEngine,
            watchdog = watchdog,
            tracker = tracker
        )
    }
}
