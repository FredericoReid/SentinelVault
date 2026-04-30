package com.sentinelvault.service

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-process heartbeat used by the dashboard's [com.sentinelvault.ui.dashboard.VigilanceStatusViewModel]
 * to decide whether [SentinelVigilanceService] is alive (Task 9.7). The service writes a
 * fresh wall-clock timestamp every [HEARTBEAT_INTERVAL_MS] ms; the dashboard considers status
 * `ACTIVE` while the last heartbeat is at most [STALE_AFTER_MS] ms old.
 *
 * Implementation note: `ActivityManager.getRunningServices` was deprecated in API 26 (and from
 * API 26 only returns the caller's own services anyway), so a self-reported heartbeat is the
 * recommended replacement. A plain [SharedPreferences] file is sufficient — the heartbeat
 * carries no sensitive content, and `apply()` is async-write-safe across processes.
 */
@Singleton
class VigilanceHeartbeat @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Persists [nowMs] (defaults to [System.currentTimeMillis]) as the latest heartbeat. */
    fun beat(nowMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_BEAT_MS, nowMs).apply()
    }

    /** @return the timestamp of the last [beat], or `0L` when no heartbeat has been written. */
    fun lastBeatMs(): Long = prefs.getLong(KEY_LAST_BEAT_MS, 0L)

    /** Convenience wrapper around [lastBeatMs] for the freshness check. */
    fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = lastBeatMs()
        return last != 0L && (nowMs - last) <= STALE_AFTER_MS
    }

    /** Wipes the heartbeat. Called from the service's `onDestroy` so a stopped service does
     *  not appear "active" until the staleness window elapses. */
    fun clear() {
        prefs.edit().remove(KEY_LAST_BEAT_MS).apply()
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MS: Long = 30_000L
        const val STALE_AFTER_MS: Long = 90_000L
        private const val PREFS_NAME = "sentinel_vigilance_heartbeat"
        private const val KEY_LAST_BEAT_MS = "last_beat_ms"
    }
}
