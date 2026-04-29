package com.sentinelvault.vault

/**
 * Computes a scalar "sharpness" score for a single-plane (luminance) frame. Higher means
 * sharper. Implementations MUST be pure: no Android imports, no allocations beyond the
 * unavoidable per-call scratch buffer.
 *
 * The contract is intentionally narrow so the Epic 6 investigation pipeline can score every
 * pulsed sample on the camera analyser thread without leaking Bitmap references into the
 * scoring code (a Bitmap is only materialised for the winning candidate, see
 * [HeroFrameSelector]).
 */
fun interface SharpnessScorer {
    /**
     * @param luma BT.601 luminance bytes, row-major, length must equal `width * height`.
     *             Bytes are treated as unsigned 0..255.
     * @param width frame width in pixels (must be ≥ 3 for the kernel to fit).
     * @param height frame height in pixels (must be ≥ 3 for the kernel to fit).
     */
    fun score(luma: ByteArray, width: Int, height: Int): Double
}
