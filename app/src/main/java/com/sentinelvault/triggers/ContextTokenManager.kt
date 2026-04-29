package com.sentinelvault.triggers

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State holder for the "context-aware verification" rule. The lifecycle of a token is:
 *
 *  1. Owner verified while package P is in the foreground → [issue] mints a token bound to P.
 *  2. While P stays in the foreground, [onForegroundAppChanged] is a no-op.
 *  3. When the foreground switches to package Q:
 *      * Q == P                             → token holds (same app re-resumed).
 *      * Q is in [SensitiveAppRegistry]      → token revoked, [TriggerEvent.SensitiveAppOpened]
 *        and [TriggerEvent.ContextBreach] are both emitted (Epic 5 escalates).
 *      * Q is generic                        → token revoked,
 *        [TriggerEvent.ContextBreach] is emitted; the state machine schedules a fresh check.
 *  4. After [ContextToken.ttlMs], the token expires regardless of foreground activity.
 *
 * The class is intentionally thread-safe via @Synchronized rather than coroutines because
 * `SensorEventListener.onSensorChanged` and the AccessibilityService callback can race.
 */
@Singleton
class ContextTokenManager @Inject constructor(
    private val orchestrator: TriggerOrchestrator,
    private val sensitiveApps: SensitiveAppRegistry,
    private val clock: TriggerClock
) {

    private val _token: MutableStateFlow<ContextToken?> = MutableStateFlow(null)
    val token: StateFlow<ContextToken?> = _token.asStateFlow()

    /** Mint or refresh the token for [packageName] following a successful owner check. */
    @Synchronized
    fun issue(packageName: String, ttlMs: Long = ContextToken.DEFAULT_TTL_MS): ContextToken {
        val fresh = ContextToken(packageName, clock.nowMs(), ttlMs)
        _token.value = fresh
        return fresh
    }

    /** Wipe the active token without emitting any trigger (used after a confirmed breach). */
    @Synchronized
    fun revoke() {
        _token.value = null
    }

    /**
     * Notify the manager that the foreground app changed. Returns the new active token (or
     * `null` if it was revoked). Used by both the [SentinelAccessibilityService] push path
     * and the periodic [UsageStatsForegroundTracker] poller.
     */
    @Synchronized
    fun onForegroundAppChanged(newPackage: String, nowMs: Long = clock.nowMs()): ContextToken? {
        val current = _token.value
        if (sensitiveApps.isSensitive(newPackage)) {
            orchestrator.emit(TriggerEvent.SensitiveAppOpened(newPackage, nowMs))
        }
        if (current == null) return null
        if (current.isExpired(nowMs)) {
            _token.value = null
            return null
        }
        if (current.coversPackage(newPackage)) return current
        // Different package — token must be revoked and a breach emitted.
        orchestrator.emit(
            TriggerEvent.ContextBreach(
                tokenPackage = current.packageName,
                attemptedPackage = newPackage,
                timestampMs = nowMs
            )
        )
        _token.value = null
        return null
    }

    /** True iff the token currently exists and covers [packageName]. */
    fun isCovered(packageName: String, nowMs: Long = clock.nowMs()): Boolean {
        val active = _token.value ?: return false
        return !active.isExpired(nowMs) && active.coversPackage(packageName)
    }
}
