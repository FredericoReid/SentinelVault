package com.sentinelvault.vault

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Single-pass Laplacian-variance focus measure. The Laplacian operator approximates a 2-D
 * second derivative; its variance across the frame is the textbook "blur metric" used in
 * autofocus systems (Pertuz et al., 2013). Pure motion blur and out-of-focus frames collapse
 * to a low variance; sharp facial detail produces a tall histogram tail and therefore a
 * higher score.
 *
 * Implementation notes:
 *  * The 4-neighbour kernel (`-4·c + n + s + e + w`) is used instead of the 8-neighbour
 *    variant to halve the per-pixel work; the relative ordering between candidates is
 *    preserved (the two operators are colinear up to a constant factor for the frames the
 *    camera pulses produce).
 *  * Variance is computed in a single pass via Welford's running mean / sum-of-squares to
 *    avoid materialising an intermediate float buffer (the Pixel-6 budget in guide.md §3.8
 *    leaves no room for a per-pulse 224×224×4-byte allocation).
 *  * `luma` is treated as **unsigned** bytes — `b.toInt() and 0xFF` — because Kotlin/JVM
 *    `Byte` is signed and a naive cast would wrap dark pixels into negative values.
 */
@Singleton
class LaplacianVarianceScorer @Inject constructor() : SharpnessScorer {

    override fun score(luma: ByteArray, width: Int, height: Int): Double {
        require(width >= MIN_DIMENSION && height >= MIN_DIMENSION) {
            "luminance frame must be at least ${MIN_DIMENSION}x$MIN_DIMENSION (was ${width}x$height)"
        }
        require(luma.size == width * height) {
            "luminance length ${luma.size} does not match ${width}x$height = ${width * height}"
        }
        var count = 0L
        var mean = 0.0
        var m2 = 0.0
        var y = 1
        while (y < height - 1) {
            val rowOffset = y * width
            var x = 1
            while (x < width - 1) {
                val i = rowOffset + x
                val center = luma[i].toInt() and 0xFF
                val north = luma[i - width].toInt() and 0xFF
                val south = luma[i + width].toInt() and 0xFF
                val west = luma[i - 1].toInt() and 0xFF
                val east = luma[i + 1].toInt() and 0xFF
                val laplacian = (north + south + east + west - 4 * center).toDouble()
                count += 1
                val delta = laplacian - mean
                mean += delta / count
                m2 += delta * (laplacian - mean)
                x += 1
            }
            y += 1
        }
        return if (count > 1L) m2 / max(1L, count - 1L) else 0.0
    }

    companion object {
        const val MIN_DIMENSION: Int = 3
    }
}
