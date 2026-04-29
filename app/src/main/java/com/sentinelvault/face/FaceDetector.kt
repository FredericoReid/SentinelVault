package com.sentinelvault.face

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Detects whether a face is present and well-framed inside the supplied frame. Implementations
 * are expected to be cheap enough to run on every analysis frame; concrete BlazeFace/TFLite
 * inference lives in [TfLiteFaceDetector].
 */
interface FaceDetector {

    /**
     * @return the bounding box of the highest-confidence face, or `null` when no face is found
     *         or the score is below the implementation's acceptance threshold.
     */
    fun detect(bitmap: Bitmap): FaceBox?

    /** Convenience used by the AR mask overlay to flip its stroke colour. */
    fun isFaceWellFramed(bitmap: Bitmap): Boolean = detect(bitmap) != null
}

/**
 * Normalised face bounding box (coordinates in `[0f, 1f]`) plus a detection confidence score.
 * Keeping coordinates normalised lets the UI translate to view-space without re-running
 * detection on the original surface.
 */
data class FaceBox(
    val rect: RectF,
    val score: Float
)

/** Sentinel implementation used as a fallback when no model asset is bundled. */
object NoOpFaceDetector : FaceDetector {
    override fun detect(bitmap: Bitmap): FaceBox? = null
}
