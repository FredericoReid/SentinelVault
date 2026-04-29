package com.sentinelvault.vigilance

import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Run a single pulse" facade for the state machine. Owns the camera-to-verdict pipeline
 * (frame source → [FrameVerifier]) so [VigilanceStateMachine] can stay free of Android
 * graphics types and remain pure-Kotlin testable.
 */
fun interface VerificationEngine {
    suspend fun verifyOnce(nowMs: Long): VerificationOutcome
}

/**
 * Default engine: pulls a frame from the [VerificationFrameSource] and pushes it through
 * the [FrameVerifier]. A `null` frame collapses to [VerificationOutcome.NoFace] so the
 * state machine sees a benign signal instead of a crash.
 */
@Singleton
class DefaultVerificationEngine @Inject constructor(
    private val frameSource: VerificationFrameSource,
    private val frameVerifier: FrameVerifier
) : VerificationEngine {

    override suspend fun verifyOnce(nowMs: Long): VerificationOutcome {
        val bitmap = frameSource.capture() ?: return VerificationOutcome.NoFace(nowMs)
        return frameVerifier.verify(bitmap)
    }
}
