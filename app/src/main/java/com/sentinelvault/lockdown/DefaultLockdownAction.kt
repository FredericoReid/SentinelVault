package com.sentinelvault.lockdown

import com.sentinelvault.data.db.dao.EventLogDao
import com.sentinelvault.data.db.entity.EventLogEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Production [LockdownAction]. Wraps the two ordered side-effects of a confirmed breach
 * (vault write + hardware lock) under a single mutex so concurrent state-machine emissions
 * cannot double-write the timeline.
 *
 * Severity legend (matches the dashboard colour scale, Epic 7):
 *  * `2` — confirmed breach with hardware lock applied.
 *  * `1` — confirmed breach but lock did not fire (admin inactive or revoked).
 */
@Singleton
class DefaultLockdownAction @Inject constructor(
    private val eventLogDao: EventLogDao,
    private val devicePolicy: DevicePolicyController
) : LockdownAction {

    private val mutex = Mutex()

    override suspend fun trigger(reason: BreachReason): LockdownAction.Result = mutex.withLock {
        val breachId = eventLogDao.insertBreach(
            EventLogEntity(
                timestampMs = reason.timestampMs,
                type = EventLogEntity.Type.BREACH_CONFIRMED,
                severity = SEVERITY_BREACH,
                foregroundPackage = reason.foregroundPackage,
                evidencePath = null,
                notes = "mismatchStreak=${reason.mismatchStreak}"
            )
        )
        val locked = devicePolicy.isAdminActive() && devicePolicy.lockNow()
        val lockEventId = eventLogDao.insertBreach(
            EventLogEntity(
                timestampMs = reason.timestampMs,
                type = EventLogEntity.Type.LOCKDOWN_TRIGGERED,
                severity = if (locked) SEVERITY_HARD_LOCK else SEVERITY_SOFT_ONLY,
                foregroundPackage = reason.foregroundPackage,
                evidencePath = null,
                notes = if (locked) "lockNow=ok" else "lockNow=skipped"
            )
        )
        if (locked) {
            LockdownAction.Result.HardLocked(breachId, lockEventId)
        } else {
            LockdownAction.Result.SoftLockedOnly(breachId, lockEventId)
        }
    }

    private companion object {
        const val SEVERITY_BREACH: Int = 2
        const val SEVERITY_HARD_LOCK: Int = 2
        const val SEVERITY_SOFT_ONLY: Int = 1
    }
}
