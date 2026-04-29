package com.sentinelvault.security.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * Stub Device Admin receiver. The full lockdown logic lands in Epic 6 – here we only
 * register the component so the onboarding flow (Epic 2) can deep-link the user to the
 * "Activate device admin" system dialog.
 */
class SentinelDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, SentinelDeviceAdminReceiver::class.java)
    }
}
