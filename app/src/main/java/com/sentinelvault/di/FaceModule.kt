package com.sentinelvault.di

import android.content.Context
import com.sentinelvault.face.EnrollmentRepository
import com.sentinelvault.face.FaceDetector
import com.sentinelvault.face.FaceEmbedder
import com.sentinelvault.face.MultiRotationFaceDetector
import com.sentinelvault.face.NoOpFaceDetector
import com.sentinelvault.face.NoOpFaceEmbedder
import com.sentinelvault.face.TfLiteFaceDetector
import com.sentinelvault.face.TfLiteFaceEmbedder
import com.sentinelvault.security.MemorySanitizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Hilt graph for the Edge-AI face stack.
 *
 * Both providers attempt to memory-map their TFLite asset (BlazeFace / MobileFaceNet) and
 * fall back to the NoOp implementation when the model file is absent. This keeps the
 * application bootable on developer machines that don't ship the binary models, while still
 * letting [com.sentinelvault.face.EnrollmentRepository] surface a meaningful "model missing"
 * UI state.
 */
@Module
@InstallIn(SingletonComponent::class)
object FaceModule {

    @Provides
    @Singleton
    @Named("baseFaceDetector")
    fun provideBaseFaceDetector(@ApplicationContext context: Context): FaceDetector =
        TfLiteFaceDetector.tryCreate(context) ?: NoOpFaceDetector

    @Provides
    @Singleton
    @Named("enrollmentFaceDetector")
    fun provideEnrollmentFaceDetector(
        @Named("baseFaceDetector") detector: FaceDetector
    ): FaceDetector = detector

    @Provides
    @Singleton
    @Named("vigilanceFaceDetector")
    fun provideVigilanceFaceDetector(
        @Named("baseFaceDetector") detector: FaceDetector,
        sanitizer: MemorySanitizer
    ): FaceDetector = MultiRotationFaceDetector(delegate = detector, sanitizer = sanitizer)

    @Provides
    @Singleton
    fun provideFaceEmbedder(@ApplicationContext context: Context): FaceEmbedder =
        TfLiteFaceEmbedder.tryCreate(context) ?: NoOpFaceEmbedder

    @Provides
    @Singleton
    fun provideEnrollmentClock(): EnrollmentRepository.Clock = EnrollmentRepository.Clock.SYSTEM

    @Provides
    @Singleton
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
