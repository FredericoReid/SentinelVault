package com.sentinelvault.face

import android.content.Context
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Bundled-asset paths for the Edge-AI models. Drop the corresponding `.tflite` files under
 * `app/src/main/assets/models/` to enable on-device inference; otherwise the matching
 * implementation falls back to a no-op (see [TfLiteFaceDetector.tryCreate] and
 * [TfLiteFaceEmbedder.tryCreate]). Keeping these as constants avoids typo drift across the
 * detector, embedder and Hilt module.
 */
internal object TfLiteAssets {
    const val BLAZEFACE_MODEL: String = "models/blazeface_short_range.tflite"
    const val MOBILEFACENET_MODEL: String = "models/mobilefacenet.tflite"
}

/**
 * Memory-maps a TFLite model from [Context.getAssets]. Returns `null` when the asset is
 * absent so that callers can surface a "model not configured" UI state instead of crashing.
 */
internal fun Context.tryLoadTfLiteAsset(path: String): MappedByteBuffer? = try {
    assets.openFd(path).use { fd ->
        FileInputStream(fd.fileDescriptor).channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }
} catch (_: Throwable) {
    null
}

/** Allocates a little-endian direct buffer sized to hold [floatCount] floats. */
internal fun directFloatBuffer(floatCount: Int): ByteBuffer =
    ByteBuffer.allocateDirect(floatCount * Float.SIZE_BYTES).apply {
        order(java.nio.ByteOrder.nativeOrder())
    }
