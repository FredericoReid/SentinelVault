package com.sentinelvault.di

import com.sentinelvault.security.PinHasher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.SecureRandom
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecureRandom(): SecureRandom = SecureRandom()

    @Provides
    @Singleton
    fun providePinHasher(random: SecureRandom): PinHasher = PinHasher(random)
}
