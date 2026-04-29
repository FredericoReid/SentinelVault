package com.sentinelvault.face

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.sentinelvault.security.MemorySanitizer
import java.io.ByteArrayOutputStream

/**
 * CameraX [ImageAnalysis.Analyzer] that converts each YUV_420_888 frame into an ARGB bitmap,
 * forwards it to [onFrame] for quality probing, then releases every intermediate buffer
 * through the supplied [MemorySanitizer]. The frame interval is throttled by
 * [analysisIntervalMs] to keep the detector under ~10 Hz, well within the per-frame budget.
 */
class FrameAnalyzer(
    private val sanitizer: MemorySanitizer,
    private val onFrame: (Bitmap, rotationDegrees: Int) -> Unit,
    private val analysisIntervalMs: Long = DEFAULT_INTERVAL_MS
) : ImageAnalysis.Analyzer {

    @Volatile private var lastAnalysisAt: Long = 0L

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisAt < analysisIntervalMs) {
            sanitizer.close(image); return
        }
        lastAnalysisAt = now
        val bitmap = try {
            image.toRotatedArgbBitmap()
        } catch (_: Throwable) {
            null
        }
        sanitizer.close(image)
        if (bitmap != null) onFrame(bitmap, 0)
    }

    companion object {
        const val DEFAULT_INTERVAL_MS: Long = 120L
    }
}

internal fun ImageProxy.toRotatedArgbBitmap(): Bitmap {
    val nv21 = yuv420ToNv21(this)
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, out)
    val bytes = out.toByteArray()
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: error("Unable to decode YUV frame")
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    if (rotated !== raw) raw.recycle()
    return rotated
}

private const val JPEG_QUALITY = 90

/** Minimal YUV_420_888 -> NV21 conversion sufficient for downstream JPEG encoding. */
private fun yuv420ToNv21(image: ImageProxy): ByteArray {
    val width = image.width
    val height = image.height
    val ySize = width * height
    val nv21 = ByteArray(ySize + ySize / 2)

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    copyPlane(yPlane.buffer, yPlane.rowStride, yPlane.pixelStride, width, height, nv21, 0)

    var offset = ySize
    val chromaHeight = height / 2
    val chromaWidth = width / 2
    val vBuffer = vPlane.buffer
    val uBuffer = uPlane.buffer
    val vRowStride = vPlane.rowStride
    val uRowStride = uPlane.rowStride
    val vPixelStride = vPlane.pixelStride
    val uPixelStride = uPlane.pixelStride
    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            val vIndex = row * vRowStride + col * vPixelStride
            val uIndex = row * uRowStride + col * uPixelStride
            nv21[offset++] = vBuffer.get(vIndex)
            nv21[offset++] = uBuffer.get(uIndex)
        }
    }
    return nv21
}

private fun copyPlane(
    buffer: java.nio.ByteBuffer,
    rowStride: Int,
    pixelStride: Int,
    width: Int,
    height: Int,
    dst: ByteArray,
    dstOffset: Int
) {
    var pos = dstOffset
    val rowBuf = ByteArray(rowStride)
    for (row in 0 until height) {
        buffer.position(row * rowStride)
        val toRead = minOf(rowStride, buffer.remaining())
        buffer.get(rowBuf, 0, toRead)
        if (pixelStride == 1) {
            System.arraycopy(rowBuf, 0, dst, pos, width); pos += width
        } else {
            for (col in 0 until width) { dst[pos++] = rowBuf[col * pixelStride] }
        }
    }
}
