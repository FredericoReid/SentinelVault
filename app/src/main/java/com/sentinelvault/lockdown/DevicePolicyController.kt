package com.sentinelvault.lockdown

/**
 * Thin abstraction over `android.app.admin.DevicePolicyManager` so the Epic 6 hard-lock path
 * can be unit-tested without an instrumented device. The single production implementation is
 * [DefaultDevicePolicyController]; the Epic 6 tests substitute a fake.
 *
 *  * [isAdminActive] – returns whether the user has activated the
 *    [com.sentinelvault.security.admin.SentinelDeviceAdminReceiver] component. Lockdown is a
 *    no-op when this is `false`; the Epic 2 onboarding flow is responsible for the dialog.
 *  * [lockNow] – invokes `DevicePolicyManager.lockNow()`. Returns `false` if the call threw
 *    a [SecurityException] (admin was revoked between the check and the call) so the caller
 *    can still persist the breach event for the dashboard.
 */
interface DevicePolicyController {
    fun isAdminActive(): Boolean
    fun lockNow(): Boolean
}
