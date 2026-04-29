package com.sentinelvault.di

import com.sentinelvault.vigilance.DaoOwnerTemplateProvider
import com.sentinelvault.vigilance.DefaultVerificationEngine
import com.sentinelvault.vigilance.FrameVerifier
import com.sentinelvault.vigilance.LivenessProbe
import com.sentinelvault.vigilance.NoOpVerificationFrameSource
import com.sentinelvault.vigilance.OwnerTemplateProvider
import com.sentinelvault.vigilance.PulseScheduler
import com.sentinelvault.vigilance.VarianceLivenessProbe
import com.sentinelvault.vigilance.VerificationEngine
import com.sentinelvault.vigilance.VerificationFrameSource
import com.sentinelvault.vigilance.VigilanceConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt graph for the Epic 5 vigilance stack. The state machine itself is auto-provided via
 * its `@Inject` constructor; this module supplies the small interfaces it depends on and
 * pins safe defaults so the application boots even before Epic 6 wires the real CameraX
 * frame source.
 */
@Module
@InstallIn(SingletonComponent::class)
object VigilanceModule {

    @Provides
    @Singleton
    fun provideVigilanceConfig(): VigilanceConfig = VigilanceConfig()

    @Provides
    @Singleton
    fun providePulseScheduler(): PulseScheduler = PulseScheduler.DEFAULT

    @Provides
    @Singleton
    fun provideLivenessProbe(): LivenessProbe = VarianceLivenessProbe()

    /**
     * Until Epic 6 wires CameraX into the verification path, fall back to a no-op source
     * that produces no frames. The state machine treats every pulse as `NoFace` and never
     * escalates, so the trigger plumbing can still be exercised end-to-end.
     */
    @Provides
    @Singleton
    fun provideVerificationFrameSource(): VerificationFrameSource = NoOpVerificationFrameSource

    @Provides
    @Singleton
    fun provideFrameVerifierClock(): FrameVerifier.VerifierClock = FrameVerifier.VerifierClock.SYSTEM
}

/**
 * Interface bindings live in their own `@Module` because `@Binds` cannot share a class with
 * `@Provides`. Both bindings are `@Singleton` to match the lifetime of the consuming
 * [com.sentinelvault.vigilance.VigilanceStateMachine].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VigilanceBindingsModule {

    @Binds
    @Singleton
    abstract fun bindOwnerTemplateProvider(
        impl: DaoOwnerTemplateProvider
    ): OwnerTemplateProvider

    @Binds
    @Singleton
    abstract fun bindVerificationEngine(
        impl: DefaultVerificationEngine
    ): VerificationEngine
}
