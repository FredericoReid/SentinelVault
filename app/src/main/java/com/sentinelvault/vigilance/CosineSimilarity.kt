package com.sentinelvault.vigilance

import kotlin.math.sqrt

/**
 * Cosine similarity between two real-valued vectors. The Epic 5 evaluation engine compares
 * fresh MobileFaceNet embeddings against the stored owner template using this metric (see
 * guide.md §1.4 — "computes the cosine similarity to the owner template").
 *
 * The function is dimension-agnostic but enforces equal length to surface integration bugs
 * early; in production both inputs are 192-d and L2-normalised so the dot product is already
 * the cosine. The explicit normalisation step below is therefore cheap and keeps the helper
 * usable for ad-hoc, non-normalised comparisons (e.g. unit tests, livelihood probes).
 */
object CosineSimilarity {

    /**
     * @return the cosine similarity in `[-1.0, 1.0]`. Returns `0f` when either operand has
     *         zero magnitude (orthogonal-to-everything convention) so callers don't need to
     *         guard against `NaN` from a division by zero.
     */
    fun between(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding length mismatch: ${a.size} vs ${b.size}" }
        if (a.isEmpty()) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            val ai = a[i]
            val bi = b[i]
            dot += ai * bi
            na += ai * ai
            nb += bi * bi
        }
        val denom = sqrt(na) * sqrt(nb)
        if (denom == 0f) return 0f
        val raw = dot / denom
        // Numerical safety: float drift can push 1.0 to 1.0000001.
        return when {
            raw > 1f -> 1f
            raw < -1f -> -1f
            else -> raw
        }
    }
    fun normalize(v: FloatArray): FloatArray {
        var mag = 0f
        for (f in v) mag += f * f
        val denom = sqrt(mag)
        if (denom == 0f) return v.copyOf()
        val result = FloatArray(v.size)
        for (i in v.indices) result[i] = v[i] / denom
        return result
    }
}
