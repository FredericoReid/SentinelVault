package com.sentinelvault.vigilance

import android.graphics.Bitmap

/**
 * Abstraction over "the next silent camera frame". The default runtime implementation will
 * be backed by CameraX (Epic 6 will swap the [NoOpVerificationFrameSource] for a real
 * `ImageAnalysis`-driven implementation); the state machine only needs a `suspend` call that
 * yields a Bitmap or `null` when no frame can be produced (camera permission missing,
 * service torn down, etc.).
 */
fun interface VerificationFrameSource {
    /** @return the next captured frame, or `null` when no frame can be produced. */
    suspend fun capture(): Bitmap?
}

/**
 * Sentinel implementation used during development and on devices where the CameraX pipeline
 * has not yet been wired (Epic 6). Returning `null` makes the state machine treat every
 * pulse as a `NoFace` outcome — i.e. nothing changes — so the trigger plumbing can still be
 * exercised end-to-end without burning CPU on inference.
 */
object NoOpVerificationFrameSource : VerificationFrameSource {
    override suspend fun capture(): Bitmap? = null
}
