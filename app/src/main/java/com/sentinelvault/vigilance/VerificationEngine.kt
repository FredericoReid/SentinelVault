package com.sentinelvault.vigilance

import com.sentinelvault.vault.BurstRecorder
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
 * Default engine: pulls a frame from the [VerificationFrameSource], offers it to the
 * Epic 7 [BurstRecorder] (no-op outside an investigation window) and pushes the original
 * through the [FrameVerifier]. A `null` frame collapses to [VerificationOutcome.NoFace]
 * so the state machine sees a benign signal instead of a crash.
 *
 * The recorder clones the bitmap before this call returns, so the verifier remains free
 * to recycle the source frame as part of its sanitisation pass.
 */
@Singleton
class DefaultVerificationEngine @Inject constructor(
    private val frameSource: VerificationFrameSource,
    private val frameVerifier: FrameVerifier,
    private val burstRecorder: BurstRecorder
) : VerificationEngine {

    override suspend fun verifyOnce(nowMs: Long): VerificationOutcome {
        val bitmap = frameSource.capture() ?: return VerificationOutcome.NoFace(nowMs)
        burstRecorder.offer(bitmap, nowMs)
        return frameVerifier.verify(bitmap)
    }
}
