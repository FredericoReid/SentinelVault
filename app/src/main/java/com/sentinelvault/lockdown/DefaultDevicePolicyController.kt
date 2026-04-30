package com.sentinelvault.lockdown

import android.app.admin.DevicePolicyManager
import android.content.Context
import com.sentinelvault.security.admin.SentinelDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [DevicePolicyController]. Uses the [SentinelDeviceAdminReceiver] component
 * declared in the manifest (Epic 1 wiring) and the system [DevicePolicyManager].
 *
 * Note on `KEYGUARD_DISABLE_BIOMETRICS`: guide.md §1.4 mandates disabling biometric unlock
 * for the next session. That flag is reachable through `setKeyguardDisabledFeatures`, which
 * requires Device Owner / Profile Owner status — out of reach for a sideloaded admin app.
 * We therefore call the always-allowed [DevicePolicyManager.lockNow] and accept that the
 * user can re-enter via the OS biometric on the very next unlock; the next pulse will
 * immediately re-evaluate and re-lock if the intruder is still in front of the camera.
 */
@Singleton
class DefaultDevicePolicyController @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DevicePolicyController {

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val component = SentinelDeviceAdminReceiver.componentName(context)

    override fun isAdminActive(): Boolean = dpm.isAdminActive(component)

    override fun lockNow(): Boolean = try {
        dpm.lockNow()
        true
    } catch (_: SecurityException) {
        false
    }
}
