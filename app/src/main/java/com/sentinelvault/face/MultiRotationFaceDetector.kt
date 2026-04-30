package com.sentinelvault.face

import android.graphics.Bitmap
import android.graphics.Matrix
import com.sentinelvault.security.MemorySanitizer

/**
 * Decorator that retries face detection across quarter-turn rotations before giving up.
 *
 * The original bitmap is tried first; only on miss do we rotate by 90°, 180° and 270°.
 * Every intermediate bitmap is recycled through [MemorySanitizer] immediately after the
 * attempt so the vigilance pipeline keeps the same memory-hygiene guarantees as Epic 5.
 */
class MultiRotationFaceDetector(
    private val delegate: FaceDetector,
    private val sanitizer: MemorySanitizer,
    private val clock: Clock = Clock.SYSTEM,
    private val budgetMs: Long = DEFAULT_BUDGET_MS,
    private val rotator: (Bitmap, Float) -> Bitmap = ::rotateBitmap
) : FaceDetector {

    override fun detect(bitmap: Bitmap): FaceBox? {
        val start = clock.nowMs()
        delegate.detect(bitmap)?.let { return it }
        for (degrees in SWEEP_DEGREES) {
            if (clock.nowMs() - start >= budgetMs) return null
            val rotated = runCatching { rotator(bitmap, degrees) }.getOrNull() ?: continue
            try {
                delegate.detect(rotated)?.let { return it }
            } finally {
                if (rotated !== bitmap) sanitizer.recycle(rotated)
            }
        }
        return null
    }

    fun interface Clock {
        fun nowMs(): Long

        companion object {
            val SYSTEM: Clock = Clock { System.currentTimeMillis() }
        }
    }

    companion object {
        private const val DEFAULT_BUDGET_MS: Long = 50L
        private val SWEEP_DEGREES = listOf(90f, 180f, 270f)

        private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
            val matrix = Matrix().apply { postRotate(degrees) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}