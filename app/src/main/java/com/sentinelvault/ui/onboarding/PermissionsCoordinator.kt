package com.sentinelvault.ui.onboarding

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.sentinelvault.security.admin.SentinelDeviceAdminReceiver

/**
 * Pure status checks + intent factories for every permission/setting the onboarding flow
 * has to negotiate. Centralised so screen code stays declarative.
 */
object PermissionsCoordinator {

    fun isCameraGranted(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun isOverlayGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun overlayIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    fun isUsageStatsGranted(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        // The (op, uid, packageName) overload is deprecated in API 36; the recommended
        // AttributionSource path is only available on `noteOp*` family methods, not on
        // `unsafeCheckOpNoThrow`. Until the platform exposes a non-deprecated `checkOp`
        // for arbitrary (uid, package) tuples we keep the SDK-gated fallback below.
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageStatsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun isDeviceAdminGranted(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(SentinelDeviceAdminReceiver.componentName(context))
    }

    fun deviceAdminIntent(context: Context, explanation: String): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                SentinelDeviceAdminReceiver.componentName(context)
            )
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)
        }

    /**
     * Accessibility settings open the system list; we cannot programmatically detect activation
     * before the actual AccessibilityService ships in Epic 4. The onboarding therefore only
     * launches the screen and lets the user toggle the entry manually.
     */
    fun accessibilityIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Android 13+ surfaces sideloaded apps under "Restricted settings" → App Info screen. */
    fun appInfoIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )

    fun requiresRestrictedSettingsBypass(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Epic 9 / Task 9.6: battery-optimisation exemption is what allows the foreground service
     * to survive Doze on Samsung/Xiaomi/Huawei. The platform check itself works on every
     * supported API (the [PowerManager] is available since Android 6).
     */
    fun isBatteryOptimisationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Returns the system intent that asks the user to whitelist the app from Doze. Marked as
     * `@SuppressLint("BatteryLife")` at the call site is unnecessary because we own the app
     * binary and the Play-Store review of the permission does not apply to sideloaded builds.
     */
    fun batteryOptimisationIntent(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )

    /** Epic 9 / Task 9.2: Android 13+ runtime check for the persistent notification. */
    fun isPostNotificationsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}
