package com.sentinelvault.triggers

/**
 * Minimum surface required to learn which package owns the top activity. Two implementations
 * exist:
 *  * [UsageStatsForegroundTracker] — polls `UsageStatsManager`, requires the
 *    `PACKAGE_USAGE_STATS` AppOp granted manually by the user (Settings → Special access).
 *  * [SentinelAccessibilityService] — push-based, requires the user to enable Sentinel under
 *    Settings → Accessibility. Acts as the realtime source whenever it is enabled.
 *
 * The two are wired in parallel: whichever one observes the change first emits a
 * [TriggerEvent.ForegroundAppChanged]; the orchestrator deduplicates by holding the last
 * package name and dropping repeats.
 */
interface ForegroundAppTracker {

    /**
     * Resolve the package owning the top activity right now, or `null` when the lookup is
     * unavailable (permission missing, no usage events buffered yet, etc.).
     *
     * @param sinceMs lower bound of the lookup window. Implementations may ignore it; the
     *                UsageStats poller passes `now - 5_000` to keep the query cheap.
     */
    fun currentForegroundPackage(sinceMs: Long): String?
}
