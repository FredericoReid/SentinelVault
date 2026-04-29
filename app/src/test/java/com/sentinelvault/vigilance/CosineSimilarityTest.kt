package com.sentinelvault.vigilance

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CosineSimilarityTest {

    @Test
    fun `identical vectors yield 1`() {
        val v = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        assertThat(CosineSimilarity.between(v, v)).isWithin(1e-6f).of(1f)
    }

    @Test
    fun `opposite vectors yield -1`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(-1f, 0f, 0f)
        assertThat(CosineSimilarity.between(a, b)).isWithin(1e-6f).of(-1f)
    }

    @Test
    fun `orthogonal vectors yield 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertThat(CosineSimilarity.between(a, b)).isWithin(1e-6f).of(0f)
    }

    @Test
    fun `unnormalised vectors are still in -1 to 1 range`() {
        val a = floatArrayOf(3f, 4f)        // length 5
        val b = floatArrayOf(4f, 3f)        // length 5
        // dot = 24, denom = 25 → 0.96
        assertThat(CosineSimilarity.between(a, b)).isWithin(1e-6f).of(0.96f)
    }

    @Test
    fun `zero magnitude operand returns 0 instead of NaN`() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertThat(CosineSimilarity.between(a, b)).isEqualTo(0f)
    }

    @Test
    fun `empty vectors return 0`() {
        assertThat(CosineSimilarity.between(FloatArray(0), FloatArray(0))).isEqualTo(0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `length mismatch is rejected`() {
        CosineSimilarity.between(floatArrayOf(1f), floatArrayOf(1f, 2f))
    }

    @Test
    fun `result is clamped against floating-point drift`() {
        // construct vectors whose normalised dot may drift > 1 due to fp accumulation
        val a = FloatArray(128) { 0.0883883f }   // 1/sqrt(128) approx
        val b = a.copyOf()
        val sim = CosineSimilarity.between(a, b)
        assertThat(sim).isAtMost(1f)
        assertThat(sim).isAtLeast(-1f)
    }
}
