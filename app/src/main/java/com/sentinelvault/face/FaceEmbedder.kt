package com.sentinelvault.face

import android.graphics.Bitmap
import com.sentinelvault.data.db.entity.EmbeddingEntity

/**
 * Produces an L2-normalised facial embedding (the 192-d vector consumed by Cosine Similarity
 * in Epic 5). Implementations MUST run their TFLite inference off the main thread - see the
 * Memory Hygiene clause of guide.md §3.
 */
interface FaceEmbedder {

    /** Output vector length. MobileFaceNet ships 192 floats per inference. */
    val outputSize: Int get() = EmbeddingEntity.VECTOR_SIZE

    /**
     * Runs the network on [bitmap] (already cropped and aligned to the face) and returns the
     * embedding. Callers own the returned array and SHOULD pass it through
     * [com.sentinelvault.security.MemorySanitizer] once persisted.
     *
     * @throws FaceEmbedderUnavailableException when the model could not be loaded.
     */
    fun embed(bitmap: Bitmap): FloatArray

    /** Releases native interpreter handles. Called from `onCleared`/Hilt teardown. */
    fun close() {}
}

class FaceEmbedderUnavailableException(message: String) : IllegalStateException(message)

/** Fallback used when the MobileFaceNet asset is missing on the device. */
object NoOpFaceEmbedder : FaceEmbedder {
    override fun embed(bitmap: Bitmap): FloatArray =
        throw FaceEmbedderUnavailableException("MobileFaceNet model is not bundled in /assets")
}
