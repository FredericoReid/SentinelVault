package com.sentinelvault.di

import android.app.usage.UsageStatsManager
import android.content.Context
import android.hardware.SensorManager
import com.sentinelvault.triggers.SensitiveAppRegistry
import com.sentinelvault.triggers.DeskLiftHeuristic
import com.sentinelvault.triggers.SnatchHeuristic
import com.sentinelvault.triggers.TriggerClock
import com.sentinelvault.triggers.UsageStatsForegroundTracker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt graph for the Epic 4 trigger stack. The orchestrator, ContextTokenManager and
 * MotionTriggerDetector are auto-provided through their `@Inject` constructors; this module
 * only supplies the framework-side singletons that have no public constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
object TriggerModule {

    @Provides
    @Singleton
    fun provideTriggerClock(): TriggerClock = TriggerClock.SYSTEM

    @Provides
    @Singleton
    fun provideSensorManager(@ApplicationContext context: Context): SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Provides
    @Singleton
    fun provideUsageStatsManager(@ApplicationContext context: Context): UsageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    @Provides
    @Singleton
    fun provideUsageStatsForegroundTracker(
        usageStatsManager: UsageStatsManager
    ): UsageStatsForegroundTracker = UsageStatsForegroundTracker(usageStatsManager)

    @Provides
    @Singleton
    fun provideSensitiveAppRegistry(): SensitiveAppRegistry = SensitiveAppRegistry()

    @Provides
    @Singleton
    fun provideSnatchHeuristic(): SnatchHeuristic = SnatchHeuristic()

    @Provides
    @Singleton
    fun provideDeskLiftHeuristic(): DeskLiftHeuristic = DeskLiftHeuristic()
}
