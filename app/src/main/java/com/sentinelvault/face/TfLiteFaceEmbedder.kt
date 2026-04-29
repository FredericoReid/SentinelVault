package com.sentinelvault.face

import android.content.Context
import android.graphics.Bitmap
import com.sentinelvault.data.db.entity.EmbeddingEntity
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import kotlin.math.sqrt

/**
 * MobileFaceNet TFLite embedder (see guide.md §3 - INT8 via NNAPI). Inference runs on the
 * caller's coroutine context; the [com.sentinelvault.face.EnrollmentRepository] always
 * dispatches to [kotlinx.coroutines.Dispatchers.Default].
 */
class TfLiteFaceEmbedder internal constructor(
    private val interpreter: Interpreter,
    override val outputSize: Int = EmbeddingEntity.VECTOR_SIZE
) : FaceEmbedder {

    private val inputBuffer: ByteBuffer = directFloatBuffer(INPUT_PIXELS * 3)
    private val outputArray: Array<FloatArray> = Array(1) { FloatArray(outputSize) }
    private val pixelScratch = IntArray(INPUT_PIXELS)

    override fun embed(bitmap: Bitmap): FloatArray {
        val resized = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        }
        try {
            writeInput(resized)
            interpreter.run(inputBuffer, outputArray)
        } catch (t: Throwable) {
            throw FaceEmbedderUnavailableException("MobileFaceNet inference failed: ${t.message}")
        } finally {
            if (resized !== bitmap) resized.recycle()
        }
        return l2Normalise(outputArray[0].copyOf())
    }

    override fun close() = interpreter.close()

    private fun writeInput(bitmap: Bitmap) {
        inputBuffer.rewind()
        bitmap.getPixels(pixelScratch, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (px in pixelScratch) {
            val r = ((px shr 16) and 0xFF) / 127.5f - 1f
            val g = ((px shr 8) and 0xFF) / 127.5f - 1f
            val b = (px and 0xFF) / 127.5f - 1f
            inputBuffer.putFloat(r); inputBuffer.putFloat(g); inputBuffer.putFloat(b)
        }
        inputBuffer.rewind()
    }

    private fun l2Normalise(vector: FloatArray): FloatArray {
        var sumSq = 0.0
        for (v in vector) sumSq += (v * v).toDouble()
        val norm = sqrt(sumSq).toFloat()
        if (norm > 0f) for (i in vector.indices) vector[i] = vector[i] / norm
        return vector
    }

    companion object {
        const val INPUT_SIZE: Int = 112
        private const val INPUT_PIXELS: Int = INPUT_SIZE * INPUT_SIZE

        /** @return a configured embedder, or `null` when the model asset is missing. */
        fun tryCreate(context: Context): TfLiteFaceEmbedder? {
            val model = context.tryLoadTfLiteAsset(TfLiteAssets.MOBILEFACENET_MODEL) ?: return null
            return try {
                val options = Interpreter.Options().apply {
                    setNumThreads(2)
                    setUseNNAPI(true)
                }
                TfLiteFaceEmbedder(Interpreter(model, options))
            } catch (_: Throwable) {
                null
            }
        }
    }
}
