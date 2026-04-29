package com.sentinelvault.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Manifest-registered receiver for [Intent.ACTION_USER_PRESENT]. The system fires this
 * broadcast every time the user finishes the keyguard challenge — the canonical "device just
 * got unlocked" signal that motivates the whole SentinelVault threat model.
 *
 * The receiver itself does no work beyond pushing a [TriggerEvent.UserPresent] onto the
 * orchestrator bus; the Epic 5 state machine decides whether to escalate to a camera frame.
 *
 * `ACTION_USER_PRESENT` is part of the Android implicit-broadcast exception list, so it can
 * still be declared in `AndroidManifest.xml` on Android 8+ without having to keep a sticky
 * service alive solely for `registerReceiver`.
 */
@AndroidEntryPoint
class UserPresentReceiver : BroadcastReceiver() {

    @Inject lateinit var orchestrator: TriggerOrchestrator
    @Inject lateinit var clock: TriggerClock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        orchestrator.emit(TriggerEvent.UserPresent(clock.nowMs()))
    }
}
