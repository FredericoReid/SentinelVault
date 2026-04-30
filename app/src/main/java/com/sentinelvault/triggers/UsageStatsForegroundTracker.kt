package com.sentinelvault.triggers

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pull-based [ForegroundAppTracker] that walks the `UsageEvents` ring backwards looking for
 * the most recent `ACTIVITY_RESUMED` event (the Android 10+ replacement for the deprecated
 * `MOVE_TO_FOREGROUND` constant; identical semantics, identical numeric value `1`). This is
 * the always-on baseline because — unlike the AccessibilityService — a single AppOp grant
 * survives reboots.
 *
 * It is intentionally stateless: callers (the orchestrator's polling loop) own the cadence,
 * which keeps the unit test trivial — feed a fake [UsageStatsManager] and assert.
 */
@Singleton
class UsageStatsForegroundTracker @Inject constructor(
    private val usageStatsManager: UsageStatsManager
) : ForegroundAppTracker {

    /**
     * Factory for the reusable [UsageEvents.Event] buffer. Overridable from tests so the
     * Android framework stub (which throws on construction) can be swapped for a MockK
     * instance without polluting the production hot path with extra abstractions.
     */
    internal var eventFactory: () -> UsageEvents.Event = { UsageEvents.Event() }

    override fun currentForegroundPackage(sinceMs: Long): String? {
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(sinceMs, now) ?: return null
        var lastPackage: String? = null
        val event = eventFactory()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }
}
