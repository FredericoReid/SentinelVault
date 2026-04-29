package com.sentinelvault.vault

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

class LaplacianVarianceScorerTest {

    private val scorer = LaplacianVarianceScorer()

    private fun flat(width: Int, height: Int, value: Int = 128): ByteArray =
        ByteArray(width * height) { value.toByte() }

    private fun checkerboard(width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                out[i++] = if ((x + y) % 2 == 0) 0.toByte() else 255.toByte()
            }
        }
        return out
    }

    private fun gaussianNoise(width: Int, height: Int, sigma: Double = 4.0, seed: Long = 7L): ByteArray {
        val rng = Random(seed)
        val out = ByteArray(width * height)
        for (i in out.indices) {
            val n = (128 + rng.nextDouble(-sigma, sigma)).toInt().coerceIn(0, 255)
            out[i] = n.toByte()
        }
        return out
    }

    @Test
    fun `flat frame scores zero variance`() {
        val score = scorer.score(flat(8, 8), 8, 8)
        assertThat(score).isEqualTo(0.0)
    }

    @Test
    fun `high contrast checkerboard scores much higher than flat`() {
        val sharp = scorer.score(checkerboard(16, 16), 16, 16)
        val flat = scorer.score(flat(16, 16), 16, 16)
        assertThat(sharp).isGreaterThan(flat)
        assertThat(sharp).isGreaterThan(1_000.0)
    }

    @Test
    fun `sharp frame outranks lightly noisy frame`() {
        val sharp = scorer.score(checkerboard(32, 32), 32, 32)
        val blurry = scorer.score(gaussianNoise(32, 32, sigma = 2.0), 32, 32)
        assertThat(sharp).isGreaterThan(blurry)
    }

    @Test
    fun `unsigned byte handling treats 255 as 255 not as -1`() {
        // If the scorer were using signed bytes, a row of 255 next to a row of 0 would yield
        // a Laplacian magnitude of |0 + 0 + 0 + 0 - 4 * (-1)| = 4 instead of |1020 - 0| = 1020.
        val w = 8
        val h = 8
        val frame = ByteArray(w * h) { 0 }
        // paint the middle row to 255
        for (x in 0 until w) frame[3 * w + x] = 255.toByte()
        val score = scorer.score(frame, w, h)
        assertThat(score).isGreaterThan(50_000.0)
    }

    @Test
    fun `rejects frames smaller than the minimum kernel`() {
        val tooSmall = ByteArray(4)
        runCatching { scorer.score(tooSmall, 2, 2) }
            .also { assertThat(it.isFailure).isTrue() }
    }

    @Test
    fun `rejects mismatched buffer length`() {
        runCatching { scorer.score(ByteArray(10), 4, 4) }
            .also { assertThat(it.isFailure).isTrue() }
    }
}
