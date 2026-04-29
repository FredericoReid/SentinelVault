package com.sentinelvault.triggers

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pull-based [ForegroundAppTracker] that walks the `UsageEvents` ring backwards looking for
 * the most recent `MOVE_TO_FOREGROUND` (or `ACTIVITY_RESUMED` on Android 10+). This is the
 * implementation used as the always-on baseline because — unlike the AccessibilityService —
 * a single AppOp grant survives reboots.
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
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == ACTIVITY_RESUMED
            ) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }

    companion object {
        // UsageEvents.Event.ACTIVITY_RESUMED is only public from API 29 but is functionally
        // equivalent to the deprecated MOVE_TO_FOREGROUND on older devices.
        private const val ACTIVITY_RESUMED: Int = 1
    }
}
