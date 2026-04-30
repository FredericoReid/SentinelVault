package com.sentinelvault

import android.app.Application
import com.sentinelvault.service.VigilanceServiceLauncher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Epic 9 / Task 9.4 moved [SentinelRuntime.start] OUT of this class
 * and into [com.sentinelvault.service.SentinelVigilanceService] — `Application.onCreate` runs
 * for every component cold-start (including the BOOT_COMPLETED receiver) but provides no
 * `LifecycleOwner` for CameraX. Anchoring the runtime to the foreground service keeps the
 * camera and the state machine on the same lifecycle.
 */
@HiltAndroidApp
class SentinelApp : Application() {

    @Inject lateinit var serviceLauncher: VigilanceServiceLauncher

    override fun onCreate() {
        super.onCreate()
        // No-op when vigilance is disabled or the OS is currently rejecting FGS starts; the
        // launcher records the deferred state and UserPresentReceiver retries on next unlock.
        serviceLauncher.ensureRunning(this)
    }
}
