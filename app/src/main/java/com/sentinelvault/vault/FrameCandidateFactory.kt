package com.sentinelvault.vault

import android.graphics.Bitmap

/**
 * Owner-agnostic seam that turns a borrowed camera [Bitmap] into a self-owned
 * [FrameCandidate] (an independent ARGB copy plus its row-major BT.601 luminance plane).
 *
 * The real implementation must allocate a fresh bitmap because the source frame is shared
 * with the verification pipeline and is recycled the moment [com.sentinelvault.vigilance.FrameVerifier.verify]
 * returns. A `null` return value is the contract for "could not snapshot" (e.g. recycled
 * source, OutOfMemoryError) and the caller treats it as a dropped frame.
 *
 * Defined as a `fun interface` so JVM unit tests can supply a deterministic stub without
 * touching `android.graphics.*`.
 */
fun interface FrameCandidateFactory {
    fun snapshot(bitmap: Bitmap, capturedAtMs: Long): FrameCandidate?
}
