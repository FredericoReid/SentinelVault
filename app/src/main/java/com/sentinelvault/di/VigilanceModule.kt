package com.sentinelvault.di

import android.content.Context
import com.sentinelvault.vigilance.DaoOwnerTemplateProvider
import com.sentinelvault.vigilance.DefaultVerificationEngine
import com.sentinelvault.vigilance.FrameVerifier
import com.sentinelvault.vigilance.LivenessProbe
import com.sentinelvault.vigilance.OwnerTemplateProvider
import com.sentinelvault.vigilance.PulseScheduler
import com.sentinelvault.vigilance.VarianceLivenessProbe
import com.sentinelvault.vigilance.VerificationEngine
import com.sentinelvault.vigilance.VerificationFrameSource
import com.sentinelvault.vigilance.VigilanceConfig
import com.sentinelvault.ui.dashboard.VigilanceStatusViewModel
import com.sentinelvault.vigilance.camera.CameraProviderFactory
import com.sentinelvault.vigilance.camera.CameraXHeadlessCameraSession
import com.sentinelvault.vigilance.camera.CameraXVerificationFrameSource
import com.sentinelvault.vigilance.camera.HeadlessCameraSession
import com.sentinelvault.vigilance.orientation.OrientationTracker
import com.sentinelvault.service.SharedPreferencesVigilanceSettings
import com.sentinelvault.service.VigilanceServiceLauncher
import com.sentinelvault.service.VigilanceSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @Provides
    @Singleton
    fun provideFrameVerifierClock(): FrameVerifier.VerifierClock = FrameVerifier.VerifierClock.SYSTEM

    /**
     * Epic 9 / Task 9.3: Hilt does not honour Kotlin default constructor parameters on
     * `@Inject`-annotated classes, so the [CameraProviderFactory] used by
     * [CameraXHeadlessCameraSession] must be supplied explicitly. The unit tests inject the
     * `CameraXHeadlessCameraSession` directly and pass a fake factory, bypassing this binding.
     */
    @Provides
    @Singleton
    fun provideCameraProviderFactory(): CameraProviderFactory = CameraProviderFactory.DEFAULT

    @Provides
    @Singleton
    fun provideOrientationTracker(@ApplicationContext context: Context): OrientationTracker =
        OrientationTracker.create(context)

    /**
     * Epic 9 / Task 9.7: same default-arg caveat as [provideCameraProviderFactory] applies to
     * the dashboard's environment probes. The unit tests substitute their own implementation.
     */
    @Provides
    @Singleton
    fun provideVigilanceEnvironmentChecks(): VigilanceStatusViewModel.EnvironmentChecks =
        VigilanceStatusViewModel.EnvironmentChecks.DEFAULT

    /**
     * Epic 9 / Task 9.4: explicit provider for the SDK gate the launcher uses to detect
     * Android 12+. Same default-arg caveat as the other [VigilanceModule] providers.
     */
    @Provides
    @Singleton
    fun provideVigilanceSdkGate(): VigilanceServiceLauncher.SdkGate =
        VigilanceServiceLauncher.SdkGate.DEFAULT

    /**
     * Epic 9 / BUG-9.4: explicit provider for the runtime CAMERA permission gate the
     * launcher consults before invoking `startForeground(TYPE_CAMERA)`. Without this
     * binding the FGS start would crash on Android 14+ with a `SecurityException` when
     * the user has not yet completed the onboarding camera prompt.
     */
    @Provides
    @Singleton
    fun provideVigilancePermissionGate(): VigilanceServiceLauncher.PermissionGate =
        VigilanceServiceLauncher.PermissionGate.DEFAULT
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

    /**
     * Epic 9 / Task 9.3: replaces the previous `NoOpVerificationFrameSource` provider with the
     * real CameraX-backed implementation. The No-Op fixture is gone from production code on
     * purpose — silent fallbacks are what allowed BUG-9.1 to ship.
     */
    @Binds
    @Singleton
    abstract fun bindVerificationFrameSource(
        impl: CameraXVerificationFrameSource
    ): VerificationFrameSource

    @Binds
    @Singleton
    abstract fun bindHeadlessCameraSession(
        impl: CameraXHeadlessCameraSession
    ): HeadlessCameraSession

    @Binds
    @Singleton
    abstract fun bindVigilanceSettings(
        impl: SharedPreferencesVigilanceSettings
    ): VigilanceSettings
}
