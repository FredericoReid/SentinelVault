package com.sentinelvault.vigilance

import android.graphics.Bitmap

/**
 * Abstraction over "the next silent camera frame". The production binding is
 * [com.sentinelvault.vigilance.camera.CameraXVerificationFrameSource] (Epic 9), wired by
 * [com.sentinelvault.di.VigilanceModule]. The state machine only needs a `suspend` call that
 * yields a Bitmap or `null` when no frame can be produced (camera permission missing,
 * service torn down, etc.).
 */
fun interface VerificationFrameSource {
    /** @return the next captured frame, or `null` when no frame can be produced. */
    suspend fun capture(): Bitmap?
}
