package com.sentinelvault.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sentinelvault.service.VigilanceServiceLauncher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Manifest-registered receiver for [Intent.ACTION_USER_PRESENT]. The system fires this
 * broadcast every time the user finishes the keyguard challenge — the canonical "device just
 * got unlocked" signal that motivates the whole SentinelVault threat model.
 *
 * Epic 9 / Task 9.4 added a second responsibility: every unlock is also a chance to
 * (re)start [com.sentinelvault.service.SentinelVigilanceService] when it was killed by the
 * OS or refused on a previous start because of `ForegroundServiceStartNotAllowedException`.
 * The launcher call is idempotent, so the worst case is a redundant intent.
 *
 * `ACTION_USER_PRESENT` is part of the Android implicit-broadcast exception list, so it can
 * still be declared in `AndroidManifest.xml` on Android 8+ without having to keep a sticky
 * service alive solely for `registerReceiver`.
 */
@AndroidEntryPoint
class UserPresentReceiver : BroadcastReceiver() {

    @Inject lateinit var orchestrator: TriggerOrchestrator
    @Inject lateinit var clock: TriggerClock
    @Inject lateinit var serviceLauncher: VigilanceServiceLauncher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        orchestrator.emit(TriggerEvent.UserPresent(clock.nowMs()))
        serviceLauncher.ensureRunning(context)
    }
}
