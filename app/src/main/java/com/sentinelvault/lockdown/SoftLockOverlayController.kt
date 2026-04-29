package com.sentinelvault.lockdown

/**
 * Three-state controller for the Epic 6 SYSTEM_ALERT_WINDOW soft-lock overlay (guide.md §1.4
 * / Task 6.1). Decoupled from the WindowManager so the [LockdownCoordinator] can be unit
 * tested with a fake recorder, and so future surfaces (e.g. an in-app shade for the
 * dashboard preview) can reuse the same state machine.
 *
 *  * [arm] — invoked when the vigilance machine reaches `AlertLevel2`. The view is fully
 *    inflated and pre-attached at zero alpha so the [show] transition costs ≤ 200 ms (matches
 *    the §3.8 latency budget for the lock action).
 *  * [show] — invoked on `BreachConfirmed`. The overlay becomes opaque, blocks touches and
 *    captures focus to deter further interaction with whatever app the intruder had open.
 *  * [dismiss] — invoked when the owner returns through the gatekeeper. Detaches the view
 *    from the window and releases its resources.
 */
interface SoftLockOverlayController {

    /** Snapshot of the current overlay state, primarily for assertions and idempotency. */
    val mode: Mode

    fun arm()
    fun show()
    fun dismiss()

    enum class Mode {
        /** No view attached, no resources held. */
        OFF,

        /** View pre-inflated and attached invisibly to the window for fast escalation. */
        ARMED,

        /** View visible, opaque, focus-stealing — the breach is being announced. */
        SHOWING
    }
}
