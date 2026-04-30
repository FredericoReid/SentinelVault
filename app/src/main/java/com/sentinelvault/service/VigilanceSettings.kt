package com.sentinelvault.service

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's "Vigilância contínua" toggle (Task 9.7) and exposes the read used by
 * [BootReceiver] / [VigilanceServiceLauncher] to gate the foreground service.
 *
 * The flag itself is not a secret (it merely reflects a user setting), so the storage backend
 * is plain [SharedPreferences] inside the app's private dir. Wrapping it behind an interface
 * lets the unit tests substitute an in-memory implementation without touching Android.
 */
interface VigilanceSettings {
    fun isVigilanceEnabled(): Boolean
    fun setVigilanceEnabled(enabled: Boolean)
    fun isDeskLiftTriggerEnabled(): Boolean
    fun setDeskLiftTriggerEnabled(enabled: Boolean)
    fun isLenientFramingEnabled(): Boolean
    fun setLenientFramingEnabled(enabled: Boolean)

    companion object {
        const val DEFAULT_ENABLED: Boolean = true
        const val DEFAULT_DESK_LIFT_ENABLED: Boolean = true
        const val DEFAULT_LENIENT_FRAMING_ENABLED: Boolean = true
    }
}

@Singleton
class SharedPreferencesVigilanceSettings @Inject constructor(
    @param:ApplicationContext private val context: Context
) : VigilanceSettings {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun isVigilanceEnabled(): Boolean =
        prefs.getBoolean(KEY_ENABLED, VigilanceSettings.DEFAULT_ENABLED)

    override fun setVigilanceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    override fun isDeskLiftTriggerEnabled(): Boolean =
        prefs.getBoolean(KEY_DESK_LIFT_ENABLED, VigilanceSettings.DEFAULT_DESK_LIFT_ENABLED)

    override fun setDeskLiftTriggerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DESK_LIFT_ENABLED, enabled).apply()
    }

    override fun isLenientFramingEnabled(): Boolean =
        prefs.getBoolean(KEY_LENIENT_FRAMING_ENABLED, VigilanceSettings.DEFAULT_LENIENT_FRAMING_ENABLED)

    override fun setLenientFramingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LENIENT_FRAMING_ENABLED, enabled).apply()
    }

    private companion object {
        const val PREFS_NAME = "sentinel_vigilance_prefs"
        const val KEY_ENABLED = "vigilance_enabled"
        const val KEY_DESK_LIFT_ENABLED = "desk_lift_enabled"
        const val KEY_LENIENT_FRAMING_ENABLED = "lenient_framing_enabled"
    }
}
