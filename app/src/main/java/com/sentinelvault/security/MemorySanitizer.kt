package com.sentinelvault.security

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements the Zero-Knowledge clause of the SentinelVault threat model (see guide.md §7):
 * any in-memory artefact derived from the owner's face (frames, embeddings, scratch buffers)
 * MUST be overwritten with zeros and freed before garbage collection can be observed.
 *
 * Methods are intentionally trivial / side-effect-only so that mocking in unit tests is cheap
 * and so the sanitization steps in [com.sentinelvault.face.EnrollmentRepository] read as a
 * straight, auditable pipeline.
 */
@Singleton
open class MemorySanitizer @Inject constructor() {

    /** Overwrites every element with `0f` in place. Safe to call on empty/zero-length arrays. */
    open fun zero(array: FloatArray) {
        if (array.isEmpty()) return
        for (i in array.indices) array[i] = 0f
    }

    /** Overwrites every byte with `0` in place. */
    open fun zero(array: ByteArray) {
        if (array.isEmpty()) return
        for (i in array.indices) array[i] = 0
    }

    /** Rewinds and zero-fills a direct or heap [ByteBuffer], leaving position back at 0. */
    open fun zero(buffer: ByteBuffer) {
        buffer.rewind()
        val zero: Byte = 0
        while (buffer.hasRemaining()) buffer.put(zero)
        buffer.rewind()
    }

    /**
     * Wipes the bitmap's pixel data with transparent black, then releases the underlying
     * native allocation via [Bitmap.recycle]. No-op on already-recycled bitmaps.
     */
    open fun recycle(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        if (bitmap.isMutable) bitmap.eraseColor(0)
        bitmap.recycle()
    }

    /**
     * Closes a CameraX [ImageProxy], releasing the underlying camera buffer back to the pool.
     * The frames flowing through `ImageAnalysis` are reference-counted and the next frame is
     * blocked until the current one is closed (see guide.md §3 - Memory Hygiene).
     */
    open fun close(image: ImageProxy?) {
        try { image?.close() } catch (_: Throwable) { /* already closed */ }
    }
}
