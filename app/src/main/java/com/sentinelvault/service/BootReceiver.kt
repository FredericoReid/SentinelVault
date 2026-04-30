package com.sentinelvault.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sentinelvault.vigilance.OwnerTemplateProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms [SentinelVigilanceService] after a reboot (Task 9.5). Resolves BUG-9.3.
 *
 * The receiver listens for both [Intent.ACTION_BOOT_COMPLETED] (post-unlock) and
 * [Intent.ACTION_LOCKED_BOOT_COMPLETED] (Direct Boot Aware, fires before the user has
 * unlocked). Today we only act on the former — the SQLCipher-backed
 * [OwnerTemplateProvider] requires Credential-Encrypted storage, which is unavailable
 * before the first user unlock — but accepting both intents keeps the manifest stable for
 * a future Direct-Boot-aware refactor.
 *
 * Two gates protect against pointless start-ups:
 *  1. No enrolled owner template → nothing to verify against.
 *  2. User explicitly disabled vigilance from the settings switch (Task 9.7).
 *
 * The Hilt-injected dependencies are resolved through the `@AndroidEntryPoint` machinery; the
 * coroutine launched here is short-lived (one DAO read + one start-foreground call) so we do
 * not bother with `goAsync()` — finishing within ~50 ms is well inside the 10 s broadcast
 * budget.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: VigilanceSettings
    @Inject lateinit var ownerTemplateProvider: OwnerTemplateProvider
    @Inject lateinit var launcher: VigilanceServiceLauncher

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandle(intent.action)) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (ownerTemplateProvider.load() == null) return@launch
                launcher.ensureRunning(context)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Pure-function gate extracted from [onReceive] so the unit tests can exercise the
     * decision matrix without having to instantiate the Hilt-generated subclass (whose
     * `onReceive` would otherwise short-circuit in the JVM with no Hilt application).
     */
    internal fun shouldHandle(action: String?): Boolean {
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return false
        if (!settings.isVigilanceEnabled()) return false
        // Act only on the unlocked boot intent (CE storage available); the locked variant
        // is accepted by the manifest filter for forward compatibility but ignored here.
        return action == Intent.ACTION_BOOT_COMPLETED
    }
}
