package com.sentinelvault.vault

import android.graphics.Bitmap
import com.sentinelvault.security.MemorySanitizer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rolling capture buffer that holds the last [maxFrames] [FrameCandidate]s produced by the
 * verification pipeline while an investigation is in progress. Bridges the Epic 5 pulse loop
 * with the Epic 7 vault: [com.sentinelvault.vigilance.DefaultVerificationEngine] tees every
 * captured frame through [offer], and [com.sentinelvault.lockdown.LockdownCoordinator] arms
 * the recorder when the state machine enters an alert state and drains it on
 * `BreachConfirmed`.
 *
 * Memory hygiene rules (guide.md §3.7):
 *  * When disarmed, [offer] is a no-op — no clone, no luma allocation, zero work.
 *  * When [maxFrames] is exceeded, the oldest candidate is recycled through
 *    [MemorySanitizer.recycle] before being dropped.
 *  * [disarm] recycles every retained candidate; [drain] hands ownership over to the caller
 *    (the [EvidenceVault] then sanitises losers and the hero in turn).
 */
class BurstRecorder(
    private val factory: FrameCandidateFactory,
    private val sanitizer: MemorySanitizer,
    private val maxFrames: Int = DEFAULT_MAX_FRAMES
) {

    init {
        require(maxFrames > 0) { "maxFrames must be positive (was $maxFrames)" }
    }

    private val mutex = Mutex()
    private val buffer: ArrayDeque<FrameCandidate> = ArrayDeque(maxFrames)

    @Volatile
    private var armed: Boolean = false

    /** True between [arm] and the next [disarm] / [drain]. Read without acquiring the mutex. */
    fun isArmed(): Boolean = armed

    /** Latest snapshot count. Cheap enough for tests; not synchronised. */
    fun size(): Int = buffer.size

    /** Idempotent. Subsequent [offer] calls start retaining clones until [disarm] / [drain]. */
    suspend fun arm() = mutex.withLock { armed = true }

    /**
     * Idempotent. Recycles every retained candidate, clears the buffer and stops accepting
     * frames. Called by the coordinator when the alert collapses back to `Idle` without a
     * confirmed breach.
     */
    suspend fun disarm() = mutex.withLock {
        armed = false
        for (candidate in buffer) sanitizer.recycle(candidate.bitmap)
        buffer.clear()
    }

    /**
     * Tees the borrowed [bitmap] into the buffer when armed. The original bitmap is **not**
     * retained — the [factory] clones it and derives the luminance plane so the caller can
     * keep handing the source to the verification pipeline (which will recycle it).
     *
     * @return `true` when a candidate was retained, `false` when the recorder was disarmed or
     *         the factory failed to snapshot. Callers do not need this value in production —
     *         it exists for tests and diagnostics.
     */
    suspend fun offer(bitmap: Bitmap, capturedAtMs: Long): Boolean {
        if (!armed) return false
        return mutex.withLock {
            if (!armed) return@withLock false
            val candidate = factory.snapshot(bitmap, capturedAtMs) ?: return@withLock false
            if (buffer.size >= maxFrames) {
                val evicted = buffer.removeFirst()
                sanitizer.recycle(evicted.bitmap)
            }
            buffer.addLast(candidate)
            true
        }
    }

    /**
     * Atomically returns every retained candidate (oldest-first) and disarms the recorder.
     * Ownership of the returned bitmaps transfers to the caller — the recorder no longer
     * tracks them and will not recycle them on its own.
     */
    suspend fun drain(): List<FrameCandidate> = mutex.withLock {
        val snapshot = buffer.toList()
        buffer.clear()
        armed = false
        snapshot
    }

    companion object {
        /**
         * Default ring-buffer depth. Five frames cover the AlertLevel2 latency budget
         * (≤ 200 ms, guide.md §3.8) at the 3-second pulse interval and stay well under the
         * per-pulse memory ceiling (≈ 5 × 1 MB ARGB clones).
         */
        const val DEFAULT_MAX_FRAMES: Int = 5
    }
}
