package com.sentinelvault.ui.onboarding

import android.content.Context
import android.os.Build
import com.sentinelvault.R

/**
 * Maps `Build.MANUFACTURER` to the localised manual-instructions string used by the
 * battery-exemption onboarding page (Task 9.6). Lifted out of the Composable so unit tests
 * can verify each branch without spinning up Compose.
 */
internal object OemBatteryInstructions {

    fun resolve(context: Context, manufacturer: String = Build.MANUFACTURER ?: ""): String {
        val key = manufacturer.lowercase()
        val resId = when {
            key.contains("samsung") -> R.string.onboarding_battery_manual_samsung
            key.contains("xiaomi") || key.contains("redmi") || key.contains("poco") ->
                R.string.onboarding_battery_manual_xiaomi
            key.contains("huawei") || key.contains("honor") ->
                R.string.onboarding_battery_manual_huawei
            key.contains("oppo") || key.contains("realme") || key.contains("oneplus") ->
                R.string.onboarding_battery_manual_oppo
            else -> R.string.onboarding_battery_manual_generic
        }
        return context.getString(resId)
    }
}
