package com.sentinelvault.triggers

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Push-based foreground tracker. When the user enables Sentinel under
 * Settings → Accessibility, Android calls [onAccessibilityEvent] every time the window
 * changes — instantaneous (~5 ms) compared to the 200–500 ms polling latency of
 * [UsageStatsForegroundTracker].
 *
 * The service filters for `TYPE_WINDOW_STATE_CHANGED` and forwards the package name through
 * the [TriggerOrchestrator]. Sensitive-app classification is delegated to
 * [ContextTokenManager] so the policy lives in one place.
 *
 * Failure mode: if the user revokes accessibility access, this class simply stops emitting
 * and the app falls back to the UsageStats poller without any code change.
 */
@AndroidEntryPoint
class SentinelAccessibilityService : AccessibilityService() {

    @Inject lateinit var orchestrator: TriggerOrchestrator
    @Inject lateinit var contextTokenManager: ContextTokenManager
    @Inject lateinit var clock: TriggerClock

    private var lastPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // ignore Sentinel's own UI
        if (pkg == lastPackage) return
        val previous = lastPackage
        lastPackage = pkg
        val now = clock.nowMs()
        orchestrator.emit(TriggerEvent.ForegroundAppChanged(previous, pkg, now))
        contextTokenManager.onForegroundAppChanged(pkg, now)
    }

    override fun onInterrupt() { /* no-op: stateless forwarder */ }
}
