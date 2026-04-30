package com.sentinelvault.vigilance.camera

import android.graphics.Bitmap
import com.sentinelvault.vigilance.VerificationFrameSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [VerificationFrameSource] implementation that pulls one fresh frame from the
 * [HeadlessCameraSession] per call (Task 9.3). Replaces the placeholder
 * `NoOpVerificationFrameSource` in [com.sentinelvault.di.VigilanceModule], which used to
 * silently swallow every verification request (BUG-9.1).
 *
 * The bitmap returned here is *owned by the caller* — `FrameVerifier.verify` already recycles
 * it through `MemorySanitizer.recycle` regardless of outcome.
 */
@Singleton
class CameraXVerificationFrameSource @Inject constructor(
    private val session: HeadlessCameraSession
) : VerificationFrameSource {
    override suspend fun capture(): Bitmap? = session.next()
}
