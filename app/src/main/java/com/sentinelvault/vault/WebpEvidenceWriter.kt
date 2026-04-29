package com.sentinelvault.vault

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WebP-backed [EvidenceWriter]. The vault directory lives under [Context.getFilesDir] so it is
 * automatically protected by the per-app sandbox AND by the SQLCipher passphrase chain
 * (the directory's parent is `data/data/com.sentinelvault/files/`, never world-readable on a
 * non-rooted device — see guide.md §7).
 *
 * On API 30+ the loss-less / loss-y split via `WEBP_LOSSY` provides materially smaller files
 * (≈ 30 % vs JPEG quality 85 in our spot checks); below 30 we fall back to the deprecated
 * `WEBP` format which the runtime still honours. The format the writer actually used is
 * surfaced in [EvidenceWriter.WrittenEvidence.format] so tests and the dashboard can audit it.
 */
class WebpEvidenceWriter(
    private val context: Context,
    private val quality: Int = DEFAULT_QUALITY,
    private val rootDirOverride: File? = null
) : EvidenceWriter {

    private val root: File
        get() = (rootDirOverride ?: File(context.filesDir, EVIDENCE_DIR)).apply { if (!exists()) mkdirs() }

    override suspend fun write(bitmap: Bitmap, fileNameStem: String): EvidenceWriter.WrittenEvidence? {
        require(quality in 0..100) { "quality must be 0..100 (was $quality)" }
        val sanitisedStem = sanitiseStem(fileNameStem)
        return withContext(Dispatchers.IO) {
            val outFile = File(root, "$sanitisedStem.webp")
            try {
                FileOutputStream(outFile).use { fos ->
                    val ok: Boolean
                    val format: EvidenceWriter.Format
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        ok = bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, fos)
                        format = EvidenceWriter.Format.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        ok = bitmap.compress(Bitmap.CompressFormat.WEBP, quality, fos)
                        format = EvidenceWriter.Format.WEBP_LEGACY
                    }
                    if (!ok) {
                        outFile.delete()
                        return@withContext null
                    }
                    fos.flush()
                    EvidenceWriter.WrittenEvidence(
                        absolutePath = outFile.absolutePath,
                        sizeBytes = outFile.length(),
                        format = format
                    )
                }
            } catch (_: Throwable) {
                if (outFile.exists()) outFile.delete()
                null
            }
        }
    }

    override fun delete(absolutePath: String): Boolean {
        val file = File(absolutePath)
        if (!file.exists()) return false
        return file.delete()
    }

    private fun sanitiseStem(stem: String): String {
        val cleaned = stem.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        return cleaned.ifEmpty { "evidence_${System.nanoTime()}" }
    }

    companion object {
        const val EVIDENCE_DIR: String = "evidence"
        const val DEFAULT_QUALITY: Int = 80
    }
}
