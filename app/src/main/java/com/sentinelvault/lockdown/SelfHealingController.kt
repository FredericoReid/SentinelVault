package com.sentinelvault.lockdown

import com.sentinelvault.data.db.dao.EventLogDao
import com.sentinelvault.data.db.entity.EventLogEntity
import com.sentinelvault.triggers.TriggerClock
import com.sentinelvault.vigilance.VigilanceConfig
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Self-Healing for false rejects (guide.md §8 / Task 6.1). When the legitimate owner returns
 * after a confirmed breach and successfully clears the gatekeeper PIN, that breach is by
 * definition a false reject; the owner's face was good enough for them but not for the
 * cosine threshold.
 *
 * Two effects are published when [markFalseReject] is called:
 *  1. A `FALSE_REJECT_RESOLVED` row is appended to the encrypted timeline so the dashboard
 *     can show the operator both the original breach and its reversal.
 *  2. The cosine match threshold reported by [currentMatchThreshold] is shaved by
 *     [RELAX_PER_REJECT] for [GRACE_WINDOW_MS] milliseconds, capped by [MAX_RELAX] and
 *     floored at [MIN_THRESHOLD]. Outside the grace window the base config value is used
 *     unchanged, so a one-off authentication wobble cannot permanently weaken the model.
 *
 * Pure JVM logic: no Android imports, fully unit-testable with the standard `TriggerClock`
 * fake the Epic 4 tests already use.
 */
@Singleton
class SelfHealingController @Inject constructor(
    private val eventLogDao: EventLogDao,
    private val baseConfig: VigilanceConfig,
    private val clock: TriggerClock
) {

    private val _falseRejectCount: MutableStateFlow<Int> = MutableStateFlow(0)
    val falseRejectCount: StateFlow<Int> = _falseRejectCount.asStateFlow()

    private val lastFalseRejectAtMs: AtomicLong = AtomicLong(0L)

    /**
     * Records a false reject. [breachId] is forwarded into the `notes` column when present
     * so the dashboard can hyperlink the resolution back to the originating breach.
     */
    suspend fun markFalseReject(breachId: Long? = null): Long {
        val now = clock.nowMs()
        lastFalseRejectAtMs.set(now)
        _falseRejectCount.value = _falseRejectCount.value + 1
        return eventLogDao.insertBreach(
            EventLogEntity(
                timestampMs = now,
                type = EventLogEntity.Type.FALSE_REJECT_RESOLVED,
                severity = 0,
                foregroundPackage = null,
                evidencePath = null,
                notes = breachId?.let { "Override of breach #$it" }
            )
        )
    }

    /**
     * Cosine threshold that the verification engine should use right now. Inside the grace
     * window the threshold slides down linearly with [falseRejectCount], outside it the base
     * config value is restored.
     */
    fun currentMatchThreshold(): Float {
        val anchor = lastFalseRejectAtMs.get()
        if (anchor == 0L) return baseConfig.matchThreshold
        val ageMs = clock.nowMs() - anchor
        if (ageMs < 0L || ageMs > GRACE_WINDOW_MS) return baseConfig.matchThreshold
        val relax = (RELAX_PER_REJECT * _falseRejectCount.value).coerceAtMost(MAX_RELAX)
        return (baseConfig.matchThreshold - relax).coerceAtLeast(MIN_THRESHOLD)
    }

    /** Test / re-enrollment hook. Drops the relaxation back to baseline. */
    fun reset() {
        _falseRejectCount.value = 0
        lastFalseRejectAtMs.set(0L)
    }

    companion object {
        const val GRACE_WINDOW_MS: Long = 24L * 60L * 60L * 1_000L
        const val RELAX_PER_REJECT: Float = 0.03f
        const val MAX_RELAX: Float = 0.10f
        const val MIN_THRESHOLD: Float = 0.45f
    }
}
