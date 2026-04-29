package com.sentinelvault.di

import android.content.Context
import com.sentinelvault.security.MemorySanitizer
import com.sentinelvault.vault.BitmapFrameCandidateFactory
import com.sentinelvault.vault.BurstRecorder
import com.sentinelvault.vault.EvidenceRetentionPolicy
import com.sentinelvault.vault.EvidenceWriter
import com.sentinelvault.vault.FrameCandidateFactory
import com.sentinelvault.vault.LaplacianVarianceScorer
import com.sentinelvault.vault.SharpnessScorer
import com.sentinelvault.vault.WebpEvidenceWriter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt graph for the Epic 7 vault stack. `@Binds` covers the seams whose impls have a plain
 * `@Inject` constructor; `@Provides` covers the three classes that carry Kotlin default
 * arguments ([WebpEvidenceWriter.quality], [BurstRecorder.maxFrames],
 * [EvidenceRetentionPolicy.limitBytes]) — Hilt does not honour Kotlin defaults, so the
 * factory methods supply them explicitly. `TriggerClock` is reused from `TriggerModule`
 * (single source of truth for the time base).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VaultModule {

    @Binds
    @Singleton
    abstract fun bindSharpnessScorer(impl: LaplacianVarianceScorer): SharpnessScorer

    @Binds
    @Singleton
    abstract fun bindFrameCandidateFactory(impl: BitmapFrameCandidateFactory): FrameCandidateFactory

    companion object {

        @Provides
        @Singleton
        fun provideEvidenceWriter(@ApplicationContext context: Context): EvidenceWriter =
            WebpEvidenceWriter(context = context)

        @Provides
        @Singleton
        fun provideBurstRecorder(
            factory: FrameCandidateFactory,
            sanitizer: MemorySanitizer
        ): BurstRecorder = BurstRecorder(factory = factory, sanitizer = sanitizer)

        @Provides
        @Singleton
        fun provideEvidenceRetentionPolicy(): EvidenceRetentionPolicy = EvidenceRetentionPolicy()
    }
}
