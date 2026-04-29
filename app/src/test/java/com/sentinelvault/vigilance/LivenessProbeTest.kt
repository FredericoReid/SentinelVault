package com.sentinelvault.vigilance

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Test

class LivenessProbeTest {

    /**
     * Builds a `Bitmap` mock whose [Bitmap.getPixels] writes [pixels] into the destination
     * array. The probe never asks for anything else so we keep the mock minimal.
     */
    private fun bitmapOf(width: Int, height: Int, pixels: IntArray): Bitmap = mockk {
        every { this@mockk.width } returns width
        every { this@mockk.height } returns height
        val dst = slot<IntArray>()
        every {
            getPixels(capture(dst), 0, width, 0, 0, width, height)
        } answers { pixels.copyInto(dst.captured) }
    }

    @Test
    fun `flat solid-grey frame is rejected with near-zero variance`() {
        val pixels = IntArray(64 * 64) { 0xFF808080.toInt() } // mid-grey everywhere
        val probe = VarianceLivenessProbe(minimumVariance = 60f, pixelStride = 1)
        val result = probe.evaluate(bitmapOf(64, 64, pixels))
        assertThat(result).isInstanceOf(LivenessProbe.Result.Flat::class.java)
        assertThat(result.variance).isLessThan(1f)
    }

    @Test
    fun `high-variance noisy frame is accepted as live`() {
        // alternating black / white produces population variance ≈ (255/2)^2 = 16256
        val pixels = IntArray(32 * 32) { idx ->
            if (idx % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val probe = VarianceLivenessProbe(minimumVariance = 60f, pixelStride = 1)
        val result = probe.evaluate(bitmapOf(32, 32, pixels))
        assertThat(result).isInstanceOf(LivenessProbe.Result.Live::class.java)
        assertThat(result.variance).isGreaterThan(60f)
    }

    @Test
    fun `zero-sized bitmap collapses to flat with zero variance`() {
        val probe = VarianceLivenessProbe()
        val result = probe.evaluate(bitmapOf(0, 0, IntArray(0)))
        assertThat(result).isInstanceOf(LivenessProbe.Result.Flat::class.java)
        assertThat(result.variance).isEqualTo(0f)
    }

    @Test
    fun `pixel stride keeps cost down without collapsing variance`() {
        // gradient frame — mean luma ≈ 127, variance very large no matter the stride
        val w = 128; val h = 128
        val pixels = IntArray(w * h) { idx ->
            val v = ((idx * 255) / (w * h)) and 0xFF
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val probeStrided = VarianceLivenessProbe(minimumVariance = 60f, pixelStride = 64)
        val result = probeStrided.evaluate(bitmapOf(w, h, pixels))
        assertThat(result).isInstanceOf(LivenessProbe.Result.Live::class.java)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive stride is rejected at construction`() {
        VarianceLivenessProbe(pixelStride = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative minimum variance is rejected at construction`() {
        VarianceLivenessProbe(minimumVariance = -1f)
    }
}
