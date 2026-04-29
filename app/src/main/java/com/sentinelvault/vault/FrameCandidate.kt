package com.sentinelvault.vault

/**
 * One frame produced by the Epic 6 investigation pipeline, decoupled from the camera buffer
 * lifecycle. The luminance plane is borrowed from the [com.sentinelvault.face.FrameAnalyzer]
 * after it has been rotated and centre-cropped; the original [bitmap] is retained because
 * the WebP encoder needs the full RGB representation (the luminance plane alone cannot be
 * round-tripped to a colour photo).
 *
 * @property bitmap        ARGB_8888 bitmap matching the displayed crop. Ownership transfers to
 *                         whoever consumes the candidate (typically [HeroFrameSelector]'s
 *                         caller, who must recycle every loser through `MemorySanitizer`).
 * @property capturedAtMs  wall-clock millis from [com.sentinelvault.triggers.TriggerClock].
 * @property luma          row-major BT.601 luminance bytes, unsigned 0..255.
 * @property width         pixel width of [luma] (and [bitmap]).
 * @property height        pixel height of [luma] (and [bitmap]).
 */
data class FrameCandidate(
    val bitmap: android.graphics.Bitmap,
    val capturedAtMs: Long,
    val luma: ByteArray,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FrameCandidate) return false
        return capturedAtMs == other.capturedAtMs &&
            width == other.width &&
            height == other.height &&
            bitmap === other.bitmap &&
            luma.contentEquals(other.luma)
    }

    override fun hashCode(): Int {
        var result = capturedAtMs.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + System.identityHashCode(bitmap)
        result = 31 * result + luma.contentHashCode()
        return result
    }
}
