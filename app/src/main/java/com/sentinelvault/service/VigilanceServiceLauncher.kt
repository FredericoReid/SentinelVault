package com.sentinelvault.service

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralised launcher for [SentinelVigilanceService] (Task 9.4).
 *
 * Behaviour:
 *  * Honours the user's `vigilance_enabled` toggle — calling [ensureRunning] when the toggle
 *    is `false` is a no-op so callers (boot receiver, app onCreate, dashboard CTA) can call
 *    it unconditionally.
 *  * Gates on the runtime `CAMERA` grant (BUG-9.4): on Android 14+ the system kills any
 *    process that calls `startForeground(TYPE_CAMERA)` without `CAMERA` actually held.
 *    Returning [Result.MissingPermission] here lets the onboarding flow request the grant
 *    and then re-call [ensureRunning] from `MainActivity.onResume`.
 *  * Wraps [ContextCompat.startForegroundService] in the Android 12+ try/catch around
 *    [ForegroundServiceStartNotAllowedException]. When the OS rejects the start (cached
 *    state, no allow-list reason, etc.) the call records the failure for the next
 *    `ACTION_USER_PRESENT` retry through [UserPresentReceiver] — no crash, no silent loss.
 *  * Catches [SecurityException] defensively so a late permission revocation never crashes
 *    the application process; the launcher records a deferred state so the next user-present
 *    pulse can re-evaluate the gates.
 *  * Idempotent: starting the service twice is a documented no-op on the system side
 *    (`onStartCommand` returns `START_STICKY` and we drop the redundant intent).
 */
@Singleton
class VigilanceServiceLauncher @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val settings: VigilanceSettings,
    private val sdkGate: SdkGate = SdkGate.DEFAULT,
    private val permissionGate: PermissionGate = PermissionGate.DEFAULT
) {

    @Volatile private var deferredStart: Boolean = false

    /** True when the previous [ensureRunning] call was rejected and a retry is queued. */
    val hasDeferredStart: Boolean get() = deferredStart

    fun ensureRunning(context: Context = appContext): Result {
        if (!settings.isVigilanceEnabled()) return Result.Disabled
        if (!permissionGate.isCameraGranted(context)) {
            deferredStart = true
            return Result.MissingPermission(Manifest.permission.CAMERA)
        }
        val intent = Intent(context, SentinelVigilanceService::class.java)
        return try {
            ContextCompat.startForegroundService(context, intent)
            deferredStart = false
            Result.Started
        } catch (e: Throwable) {
            when {
                sdkGate.isAtLeastS() && e is ForegroundServiceStartNotAllowedException -> {
                    deferredStart = true
                    Result.Deferred(e)
                }
                e is SecurityException -> {
                    deferredStart = true
                    Result.Deferred(e)
                }
                else -> throw e
            }
        }
    }

    /** Stop the service explicitly. Used by the settings toggle (Task 9.7). */
    fun stop(context: Context = appContext) {
        deferredStart = false
        context.stopService(Intent(context, SentinelVigilanceService::class.java))
    }

    sealed interface Result {
        data object Started : Result
        data object Disabled : Result
        data class Deferred(val cause: Throwable) : Result
        data class MissingPermission(val permission: String) : Result
    }

    /**
     * Indirection over `Build.VERSION.SDK_INT >= S` so the unit test in
     * `:app:testDebugUnitTest` can drive the FGS-rejection branch (the JVM android stub
     * always reports SDK_INT == 0). Production binds [DEFAULT].
     */
    fun interface SdkGate {
        fun isAtLeastS(): Boolean
        companion object {
            val DEFAULT: SdkGate = SdkGate { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
        }
    }

    /**
     * Indirection over `ContextCompat.checkSelfPermission(CAMERA)` so the unit test can
     * drive both the granted and the missing-permission branches without a real `Context`.
     */
    fun interface PermissionGate {
        fun isCameraGranted(context: Context): Boolean
        companion object {
            val DEFAULT: PermissionGate = PermissionGate { ctx ->
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
    }
}
