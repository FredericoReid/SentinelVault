package com.sentinelvault.vigilance

import android.graphics.Bitmap
import com.sentinelvault.face.FaceDetector
import com.sentinelvault.face.FaceEmbedder
import com.sentinelvault.face.FaceEmbedderUnavailableException
import com.sentinelvault.security.MemorySanitizer
import com.sentinelvault.service.VigilanceSettings
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Epic 5 evaluation engine. One pulse = one [verify] call:
 *
 *  1. Detect a face. Missing face → [VerificationOutcome.NoFace] (no escalation).
 *  2. Probe liveness. Flat frame → [VerificationOutcome.NotLive] (counts as breach signal).
 *  3. Embed the face → 128-d vector.
 *  4. Cosine-compare against the owner template (loaded once per call, never cached longer
 *     than the call frame, then zeroed by [MemorySanitizer]).
 *  5. Match if `cosine ≥ matchThreshold`, mismatch otherwise.
 *
 * Inference runs on [dispatcher] (defaults to [Dispatchers.Default]) per the Memory Hygiene
 * clause of guide.md §3.7. The bitmap passed in is recycled before [verify] returns
 * regardless of outcome — callers MUST NOT touch it afterwards.
 */
@Singleton
class FrameVerifier @Inject constructor(
    @param:Named("vigilanceFaceDetector")
    private val faceDetector: FaceDetector,
    private val faceEmbedder: FaceEmbedder,
    private val livenessProbe: LivenessProbe,
    private val ownerTemplateProvider: OwnerTemplateProvider,
    private val selfHealing: com.sentinelvault.lockdown.SelfHealingController,
    private val sanitizer: MemorySanitizer,
    private val settings: VigilanceSettings,
    private val config: VigilanceConfig,
    private val clock: VerifierClock = VerifierClock.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    fun interface VerifierClock {
        fun nowMs(): Long
        companion object { val SYSTEM: VerifierClock = VerifierClock { System.currentTimeMillis() } }
    }

    suspend fun verify(bitmap: Bitmap): VerificationOutcome = withContext(dispatcher) {
        val now: Long
        try {
            now = clock.nowMs()
            val face = faceDetector.detect(bitmap) ?: return@withContext VerificationOutcome.NoFace(now)
            if (settings.isLenientFramingEnabled() && face.score < MIN_FACE_SCORE_FOR_EMBEDDING) {
                return@withContext VerificationOutcome.NoFace(now)
            }
            val liveness = livenessProbe.evaluate(bitmap)
            if (liveness is LivenessProbe.Result.Flat) {
                return@withContext VerificationOutcome.NotLive(liveness.variance, now)
            }
            val owner = ownerTemplateProvider.load()
                ?: return@withContext VerificationOutcome.OwnerNotEnrolled(now)
            val fresh = try {
                faceEmbedder.embed(bitmap)
            } catch (e: FaceEmbedderUnavailableException) {
                return@withContext VerificationOutcome.EmbedderUnavailable(
                    e.message ?: "unavailable", now
                )
            }
            try {
                if (fresh.size != owner.size) {
                    return@withContext VerificationOutcome.Failure(
                        IllegalStateException("Embedding size mismatch ${fresh.size} vs ${owner.size}"),
                        now
                    )
                }
                val similarity = CosineSimilarity.between(fresh, owner)
                val threshold = selfHealing.currentMatchThreshold()
                when {
                    similarity >= threshold -> {
                        if (similarity >= 0.85f && similarity < 1.0f) {
                            // Learning: blend the new high-confidence frame into the template
                            val updated = FloatArray(owner.size)
                            for (i in owner.indices) {
                                updated[i] = owner[i] * 0.9f + fresh[i] * 0.1f
                            }
                            ownerTemplateProvider.update(updated)
                        }
                        VerificationOutcome.Match(similarity, now)
                    }
                    shouldTreatAsRetry(face.score, similarity) -> VerificationOutcome.NoFace(now)
                    else -> VerificationOutcome.Mismatch(similarity, now)
                }
            } finally {
                sanitizer.zero(fresh)
                sanitizer.zero(owner)
            }
        } catch (t: Throwable) {
            VerificationOutcome.Failure(t, clock.nowMs())
        } finally {
            sanitizer.recycle(bitmap)
        }
    }

    private fun shouldTreatAsRetry(faceScore: Float, similarity: Float): Boolean {
        if (!settings.isLenientFramingEnabled()) return false
        if (similarity < config.matchThreshold - NEAR_MATCH_MARGIN) return false
        return faceScore < STRONG_FACE_SCORE
    }

    private companion object {
        private const val MIN_FACE_SCORE_FOR_EMBEDDING: Float = 0.87f
        private const val STRONG_FACE_SCORE: Float = 0.93f
        private const val NEAR_MATCH_MARGIN: Float = 0.10f
    }
}
