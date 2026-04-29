package com.sentinelvault.vigilance

import android.graphics.Bitmap

/**
 * First-line presentation-attack defence. A printed photo, a flat phone screen or a static
 * mask produces a frame with very low luminance variance compared to a real face under
 * ambient lighting. The probe rejects anything below a configurable variance threshold.
 *
 * This is intentionally a cheap statistical heuristic (no extra model on the hot path); the
 * heavier challenge-response liveness check is on the Epic 6+ roadmap. The threshold is
 * tuned for 8-bit grayscale luminance: typical living-face pulses sit in the 400-1500 range,
 * printed photos under the same lighting collapse to single digits.
 */
interface LivenessProbe {
    /** @return [Result.Live] when the frame passes, [Result.Flat] otherwise. */
    fun evaluate(bitmap: Bitmap): Result

    sealed interface Result {
        val variance: Float
        data class Live(override val variance: Float) : Result
        data class Flat(override val variance: Float) : Result
    }
}

/**
 * Default implementation: luminance-variance probe.
 *
 *  1. Down-sample by reading every Nth pixel — a 64-stride keeps the cost in microseconds
 *     even on a 1920×1080 frame and is more than enough to characterise a Gaussian-ish
 *     luminance distribution.
 *  2. Convert each ARGB pixel to ITU-R BT.601 luma (`0.299·R + 0.587·G + 0.114·B`).
 *  3. Compute population variance with a single-pass online algorithm (Welford's).
 *
 * The probe never mutates [bitmap]; recycling is the caller's responsibility (see
 * [com.sentinelvault.security.MemorySanitizer]).
 */
class VarianceLivenessProbe(
    private val minimumVariance: Float = DEFAULT_MIN_VARIANCE,
    private val pixelStride: Int = DEFAULT_PIXEL_STRIDE
) : LivenessProbe {

    init {
        require(pixelStride >= 1) { "pixelStride must be ≥ 1" }
        require(minimumVariance >= 0f) { "minimumVariance must be ≥ 0" }
    }

    override fun evaluate(bitmap: Bitmap): LivenessProbe.Result {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return LivenessProbe.Result.Flat(0f)

        val total = w * h
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var count = 0
        var mean = 0f
        var m2 = 0f
        var i = 0
        while (i < total) {
            val argb = pixels[i]
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            count += 1
            val delta = luma - mean
            mean += delta / count
            m2 += delta * (luma - mean)
            i += pixelStride
        }
        val variance = if (count < 2) 0f else m2 / count
        return if (variance >= minimumVariance) LivenessProbe.Result.Live(variance)
        else LivenessProbe.Result.Flat(variance)
    }

    companion object {
        /** Empirically chosen against printed-photo and screen-replay corpora. */
        const val DEFAULT_MIN_VARIANCE: Float = 60f
        /** Read every 64th pixel — ≈ 32 k samples on a 1080p frame. */
        const val DEFAULT_PIXEL_STRIDE: Int = 64
    }
}
