package com.sentinelvault.di

import com.sentinelvault.data.auth.PinRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun providePinRepositoryClock(): PinRepository.Clock = PinRepository.Clock.SYSTEM
}
