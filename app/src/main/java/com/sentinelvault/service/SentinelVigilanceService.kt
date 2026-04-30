package com.sentinelvault.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sentinelvault.vigilance.VigilanceState
import com.sentinelvault.vigilance.VigilanceStateMachine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground [LifecycleService] that hosts the entire vigilance pipeline (Task 9.1).
 *
 * Lifecycle contract:
 *  1. `onCreate` calls `startForeground` first (the OS will ANR the service after 5 s
 *     otherwise on Android 12+), passing `FOREGROUND_SERVICE_TYPE_CAMERA` on API 29+ as
 *     mandated by Android 14's strict FGS-type matching.
 *  2. The [SentinelVigilanceServiceController] then binds the headless camera session to
 *     this service's `LifecycleOwner` and starts the runtime.
 *  3. A separate observer collects [VigilanceStateMachine.state] and pushes a refreshed
 *     notification on every transition so the user can tell from the shade whether the
 *     service is idle, verifying or has flagged a breach.
 *  4. `onTaskRemoved` does NOT call `stopSelf` — Recents swipe must not kill vigilance.
 *  5. `onDestroy` tears down in reverse order via the controller.
 */
@AndroidEntryPoint
class SentinelVigilanceService : LifecycleService() {

    @Inject lateinit var controller: SentinelVigilanceServiceController
    @Inject lateinit var notificationFactory: VigilanceNotificationFactory
    @Inject lateinit var stateMachine: VigilanceStateMachine

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat(VigilanceState.Idle())
        controller.onCreate(this)
        observeStateForNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        controller.onTaskRemoved()
        // DO NOT call super.onTaskRemoved() — the default implementation is a no-op but the
        // explicit absence here is part of the Task 9.1 contract: vigilance survives the swipe.
    }

    override fun onDestroy() {
        controller.onDestroy()
        super.onDestroy()
    }

    private fun startForegroundCompat(state: VigilanceState) {
        val notification = notificationFactory.build(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                VigilanceNotificationFactory.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(VigilanceNotificationFactory.NOTIFICATION_ID, notification)
        }
    }

    private fun observeStateForNotification() {
        lifecycleScope.launch {
            stateMachine.state.collectLatest { state ->
                val notification = notificationFactory.build(state)
                val manager = getSystemService(NOTIFICATION_SERVICE)
                    as android.app.NotificationManager
                manager.notify(VigilanceNotificationFactory.NOTIFICATION_ID, notification)
            }
        }
    }
}
