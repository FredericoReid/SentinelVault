package com.sentinelvault.face

import android.graphics.Bitmap
import com.sentinelvault.data.db.dao.EmbeddingDao
import com.sentinelvault.data.db.entity.EmbeddingEntity
import com.sentinelvault.security.MemorySanitizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Glue between the camera frame, the TFLite stack and persistent storage.
 *
 *  1. Inference is forced onto [Dispatchers.Default] (CPU-bound, see guide.md §3).
 *  2. Captured bitmaps and intermediate vectors are sanitised through [MemorySanitizer]
 *     immediately after persistence (Zero-Knowledge clause, guide.md §7).
 *  3. The owner vector lands in the encrypted Room store via [EmbeddingDao].
 */
@Singleton
class EnrollmentRepository @Inject constructor(
    private val faceDetector: FaceDetector,
    private val faceEmbedder: FaceEmbedder,
    private val embeddingDao: EmbeddingDao,
    private val sanitizer: MemorySanitizer,
    private val clock: Clock = Clock.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    fun interface Clock {
        fun nowMs(): Long
        companion object { val SYSTEM: Clock = Clock { System.currentTimeMillis() } }
    }

    sealed interface Result {
        object Success : Result
        object NoFaceDetected : Result
        data class EmbedderUnavailable(val reason: String) : Result
        data class Failure(val cause: Throwable) : Result
    }

    /**
     * Runs detection -> embedding -> persistence on [bitmap]. The bitmap is recycled before
     * the function returns regardless of outcome, so callers MUST NOT touch it afterwards.
     */
    suspend fun enroll(bitmap: Bitmap): Result = withContext(dispatcher) {
        try {
            val faceBox = faceDetector.detect(bitmap)
                ?: return@withContext Result.NoFaceDetected
            val raw = try {
                faceEmbedder.embed(bitmap)
            } catch (e: FaceEmbedderUnavailableException) {
                return@withContext Result.EmbedderUnavailable(e.message ?: "unavailable")
            }
            require(raw.size == EmbeddingEntity.VECTOR_SIZE) {
                "Expected ${EmbeddingEntity.VECTOR_SIZE}-d embedding but got ${raw.size}"
            }
            val persisted = raw.copyOf()
            embeddingDao.upsert(
                EmbeddingEntity(vector = persisted, createdAtMs = clock.nowMs())
            )
            sanitizer.zero(raw)
            // ack the bbox to silence unused-warning while keeping it available for future
            // pipeline stages (Epic 5 will gate on faceBox.score).
            faceBox.score
            Result.Success
        } catch (t: Throwable) {
            Result.Failure(t)
        } finally {
            sanitizer.recycle(bitmap)
        }
    }

    /** Lightweight quality probe used by the AR mask overlay (see guide.md §4). */
    fun isFaceWellFramed(bitmap: Bitmap): Boolean = faceDetector.isFaceWellFramed(bitmap)

    /** Returns whether an owner vector has already been persisted. */
    suspend fun isEnrolled(): Boolean = embeddingDao.getOwner() != null
}
