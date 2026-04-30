package com.sentinelvault.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sentinelvault.MainActivity
import com.sentinelvault.R
import com.sentinelvault.vigilance.VigilanceState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the persistent notification that anchors [SentinelVigilanceService] (Task 9.2).
 *
 * The channel is created lazily on first [build] call (the call is idempotent — the platform
 * compares by ID). Importance is `LOW` so the OS never plays a sound or vibrates: this
 * notification is a status indicator, not an alert.
 *
 * The notification body changes with the live [VigilanceState] so the user can tell at a
 * glance whether the service is idle, verifying or has flagged a breach. `setOngoing(true)`
 * + `FOREGROUND_SERVICE_IMMEDIATE` make sure Android 12+ surfaces it within ~10 s instead of
 * the default 10 min deferral window for foreground-service notifications.
 */
@Singleton
class VigilanceNotificationFactory @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun build(state: VigilanceState): Notification {
        ensureChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_mono)
            .setContentTitle(context.getString(R.string.vigilance_notification_title))
            .setContentText(bodyFor(state))
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(contentPendingIntent())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
        }
        return builder.build()
    }

    /** Maps the state machine variants to the user-facing one-liner. Visible for testing. */
    internal fun bodyFor(state: VigilanceState): String {
        val resId = when (state) {
            is VigilanceState.Idle -> R.string.vigilance_notification_body_idle
            is VigilanceState.VerifyOnce,
            is VigilanceState.AlertLevel1,
            is VigilanceState.AlertLevel2 -> R.string.vigilance_notification_body_verifying
            is VigilanceState.BreachConfirmed -> R.string.vigilance_notification_body_breach
        }
        return context.getString(resId)
    }

    private fun ensureChannel() {
        val manager = NotificationManagerCompat.from(context)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.vigilance_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.vigilance_channel_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun contentPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val CHANNEL_ID: String = "sentinel_vigilance"
        const val NOTIFICATION_ID: Int = 0xC0FE
        private const val REQUEST_CODE: Int = 0xC0FF
    }
}
