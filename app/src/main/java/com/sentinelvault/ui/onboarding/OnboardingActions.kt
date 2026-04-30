package com.sentinelvault.ui.onboarding

import android.content.Context
import android.content.Intent

internal data class PermissionStatuses(
    val camera: Boolean,
    val overlay: Boolean,
    val usageStats: Boolean,
    val deviceAdmin: Boolean,
    val batteryExempt: Boolean,
    val notifications: Boolean
)

internal fun collectStatuses(context: Context): PermissionStatuses = PermissionStatuses(
    camera = PermissionsCoordinator.isCameraGranted(context),
    overlay = PermissionsCoordinator.isOverlayGranted(context),
    usageStats = PermissionsCoordinator.isUsageStatsGranted(context),
    deviceAdmin = PermissionsCoordinator.isDeviceAdminGranted(context),
    batteryExempt = PermissionsCoordinator.isBatteryOptimisationIgnored(context),
    notifications = PermissionsCoordinator.isPostNotificationsGranted(context)
)

internal fun isPageSatisfied(page: OnboardingPage, s: PermissionStatuses): Boolean = when (page) {
    OnboardingPage.Welcome -> true
    OnboardingPage.Camera -> s.camera
    OnboardingPage.Overlay -> s.overlay
    OnboardingPage.UsageStats -> s.usageStats
    OnboardingPage.Accessibility -> false
    OnboardingPage.DeviceAdmin -> s.deviceAdmin
    OnboardingPage.BatteryExemption -> s.batteryExempt
    OnboardingPage.Notifications -> s.notifications
    OnboardingPage.RestrictedSettings -> false
    OnboardingPage.Done -> true
}

internal fun runCta(
    page: OnboardingPage,
    context: Context,
    requestCamera: () -> Unit,
    requestNotifications: () -> Unit,
    launchSettings: (Intent) -> Unit,
    onFinished: () -> Unit
) {
    when (page) {
        OnboardingPage.Welcome -> Unit
        OnboardingPage.Camera -> requestCamera()
        OnboardingPage.Overlay -> launchSettings(PermissionsCoordinator.overlayIntent(context))
        OnboardingPage.UsageStats -> launchSettings(PermissionsCoordinator.usageStatsIntent())
        OnboardingPage.Accessibility -> launchSettings(PermissionsCoordinator.accessibilityIntent())
        OnboardingPage.DeviceAdmin -> launchSettings(
            PermissionsCoordinator.deviceAdminIntent(
                context = context,
                explanation = "SentinelVault needs admin rights to lock the screen on intrusion."
            )
        )
        OnboardingPage.BatteryExemption ->
            launchSettings(PermissionsCoordinator.batteryOptimisationIntent(context))
        OnboardingPage.Notifications -> requestNotifications()
        OnboardingPage.RestrictedSettings -> launchSettings(PermissionsCoordinator.appInfoIntent(context))
        OnboardingPage.Done -> onFinished()
    }
}
