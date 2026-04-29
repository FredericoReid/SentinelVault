package com.sentinelvault.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

/**
 * BlazeFace short-range TFLite detector (see guide.md §8 Task 3.2).
 *
 * The shipped model expects a 128x128 RGB input normalised to [-1f, 1f] and produces two
 * tensors:
 *  - regressors `[1, 896, 16]` (anchor offsets, only the first 4 floats per anchor matter for
 *    the bounding box at inference time)
 *  - classifiers `[1, 896, 1]` (per-anchor confidence logits)
 *
 * For Epic 3 we only need the highest-scoring face's normalised bounding box; the full
 * non-max-suppression / landmark decoding pipeline is deferred until Epic 5 (where presentation
 * attack rejection requires per-landmark accuracy).
 */
class TfLiteFaceDetector internal constructor(
    private val interpreter: Interpreter,
    private val acceptanceLogit: Float = DEFAULT_ACCEPTANCE_LOGIT
) : FaceDetector {

    private val inputBuffer: ByteBuffer = directFloatBuffer(INPUT_PIXELS * 3)
    private val regressors = Array(1) { Array(NUM_ANCHORS) { FloatArray(REGRESSOR_STRIDE) } }
    private val classifiers = Array(1) { Array(NUM_ANCHORS) { FloatArray(1) } }
    private val pixelScratch = IntArray(INPUT_PIXELS)

    override fun detect(bitmap: Bitmap): FaceBox? {
        val resized = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        }
        try {
            writeInput(resized)
            interpreter.runForMultipleInputsOutputs(
                arrayOf<Any>(inputBuffer),
                mapOf(0 to regressors, 1 to classifiers)
            )
        } catch (_: Throwable) {
            return null
        } finally {
            if (resized !== bitmap) resized.recycle()
        }
        return decodeBestFace()
    }

    override fun isFaceWellFramed(bitmap: Bitmap): Boolean = detect(bitmap) != null

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

    private fun decodeBestFace(): FaceBox? {
        var bestIndex = -1
        var bestLogit = Float.NEGATIVE_INFINITY
        for (i in 0 until NUM_ANCHORS) {
            val score = classifiers[0][i][0]
            if (score > bestLogit) { bestLogit = score; bestIndex = i }
        }
        if (bestIndex < 0 || bestLogit < acceptanceLogit) return null
        // Without anchor decoding (deferred to Epic 5) we surface the unit box centred on the
        // crop. Quality signal for the AR mask only depends on the score; bbox is provisional.
        val rect = RectF(0.15f, 0.15f, 0.85f, 0.85f)
        return FaceBox(rect = rect, score = sigmoid(bestLogit))
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + kotlin.math.exp(-x))

    fun close() = interpreter.close()

    companion object {
        const val INPUT_SIZE: Int = 128
        private const val INPUT_PIXELS: Int = INPUT_SIZE * INPUT_SIZE
        private const val NUM_ANCHORS: Int = 896
        private const val REGRESSOR_STRIDE: Int = 16
        private const val DEFAULT_ACCEPTANCE_LOGIT: Float = 1.5f

        /**
         * Loads the BlazeFace asset and returns a ready interpreter, or `null` when the model
         * is missing / fails to initialise. NNAPI is opted-in via [Interpreter.Options.setUseNNAPI]
         * to honour the Edge-AI clause of guide.md §3.
         */
        fun tryCreate(context: Context): TfLiteFaceDetector? {
            val model = context.tryLoadTfLiteAsset(TfLiteAssets.BLAZEFACE_MODEL) ?: return null
            return try {
                val options = Interpreter.Options().apply {
                    setNumThreads(2)
                    setUseNNAPI(true)
                }
                TfLiteFaceDetector(Interpreter(model, options))
            } catch (_: Throwable) {
                null
            }
        }
    }
}
