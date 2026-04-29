package com.sentinelvault.di

import com.sentinelvault.lockdown.DefaultDevicePolicyController
import com.sentinelvault.lockdown.DefaultLockdownAction
import com.sentinelvault.lockdown.DevicePolicyController
import com.sentinelvault.lockdown.LockdownAction
import com.sentinelvault.lockdown.SoftLockOverlayController
import com.sentinelvault.lockdown.WindowManagerOverlayController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt graph for the Epic 6 lockdown stack. Only `@Binds` are required: every implementation
 * has an `@Inject` constructor and is annotated `@Singleton`, matching the lifetime of the
 * [com.sentinelvault.vigilance.VigilanceStateMachine] consumer.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LockdownModule {

    @Binds
    @Singleton
    abstract fun bindDevicePolicyController(
        impl: DefaultDevicePolicyController
    ): DevicePolicyController

    @Binds
    @Singleton
    abstract fun bindLockdownAction(
        impl: DefaultLockdownAction
    ): LockdownAction

    @Binds
    @Singleton
    abstract fun bindSoftLockOverlayController(
        impl: WindowManagerOverlayController
    ): SoftLockOverlayController
}
