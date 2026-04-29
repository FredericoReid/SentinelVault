package com.sentinelvault.vault

import android.graphics.Bitmap

/**
 * Writes a single intruder frame to encrypted-at-rest storage. The implementation is in
 * charge of choosing the on-disk representation (WebP today, see [WebpEvidenceWriter]) and
 * of returning a stable absolute path that can later be loaded back into a Compose
 * `Image`. Callers MUST pass an undamaged bitmap — sanitisation of intermediate buffers is
 * not part of the writer's contract.
 */
interface EvidenceWriter {

    /**
     * @param bitmap the hero frame to persist. The writer does NOT recycle it; the caller
     *               keeps ownership and is expected to send it through `MemorySanitizer`.
     * @param fileNameStem stem of the output file (no extension); the writer appends its own.
     * @return the produced [WrittenEvidence] record on success, or `null` on I/O failure.
     */
    suspend fun write(bitmap: Bitmap, fileNameStem: String): WrittenEvidence?

    /** Best-effort delete; returns `true` when the file was found and removed. */
    fun delete(absolutePath: String): Boolean

    data class WrittenEvidence(
        val absolutePath: String,
        val sizeBytes: Long,
        val format: Format
    )

    enum class Format { WEBP_LOSSY, WEBP_LEGACY }
}
