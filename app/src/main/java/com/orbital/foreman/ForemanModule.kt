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

    @Provides
    @Singleton
    fun provideObstacleClearanceEngine(): com.orbital.automation.ObstacleClearanceEngine {
        return com.orbital.automation.ObstacleClearanceEngine()
    }

    @Provides
    @Singleton
    fun provideStateVerificationEngine(): StateVerificationEngine {
        return StateVerificationEngine()
    }

    @Provides
    @Singleton
    fun provideReActExecutor(
        supervisor: ForemanSupervisor,
        obstacleEngine: com.orbital.automation.ObstacleClearanceEngine,
        verificationEngine: StateVerificationEngine
    ): ReActExecutor {
        return ReActExecutor(
            supervisor = supervisor,
            obstacleEngine = obstacleEngine,
            verificationEngine = verificationEngine
        )
    }
}
