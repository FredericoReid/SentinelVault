package com.sentinelvault.vault

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [FrameCandidateFactory]. Copies the source [Bitmap] into an independent
 * `ARGB_8888` buffer (the verification pipeline recycles the original right after
 * [com.sentinelvault.vigilance.FrameVerifier.verify]) and derives the luminance plane in a
 * single pass via the BT.601 weights — Y = 0.299·R + 0.587·G + 0.114·B — using fixed-point
 * arithmetic to avoid per-pixel floating-point work.
 *
 * Allocates exactly two scratch buffers per call: an `IntArray(width * height)` for the
 * pixel readback and the returned `ByteArray(width * height)` luma plane. The cloned bitmap
 * is the third (and largest) allocation and is owned by the returned [FrameCandidate].
 */
@Singleton
class BitmapFrameCandidateFactory @Inject constructor() : FrameCandidateFactory {

    override fun snapshot(bitmap: Bitmap, capturedAtMs: Long): FrameCandidate? {
        if (bitmap.isRecycled) return null
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val clone = try {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } catch (_: Throwable) {
            return null
        }
        val pixels = IntArray(width * height)
        try {
            clone.getPixels(pixels, 0, width, 0, 0, width, height)
        } catch (_: Throwable) {
            clone.recycle()
            return null
        }
        val luma = ByteArray(width * height)
        var i = 0
        while (i < pixels.size) {
            val argb = pixels[i]
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            // Fixed-point BT.601 weights scaled by 1024 (299, 587, 114 → 306, 601, 117).
            val y = (306 * r + 601 * g + 117 * b + 512) ushr 10
            luma[i] = (if (y > 255) 255 else y).toByte()
            i++
        }
        return FrameCandidate(
            bitmap = clone,
            capturedAtMs = capturedAtMs,
            luma = luma,
            width = width,
            height = height
        )
    }
}
