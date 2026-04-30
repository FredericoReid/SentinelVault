package com.sentinelvault.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * JVM-only test for [VigilanceServiceLauncher] (Task 9.4, 9.5 & BUG-9.4). The launcher is
 * the single entry point for foreground-service starts, so the branches that matter are:
 *  1. user disabled vigilance     → no platform call, [Result.Disabled].
 *  2. CAMERA not granted          → no platform call, [Result.MissingPermission],
 *     deferred flag flips true so the next user-present retry can pick it up.
 *  3. happy path                  → exactly one platform start, [Result.Started].
 *  4. FGS rejection (Android 12+) → exception swallowed, [hasDeferredStart] flips true,
 *     [Result.Deferred] carries the original cause for the user-present retry.
 *  5. SecurityException           → swallowed, [Result.Deferred] (defensive net for late
 *     CAMERA revocation between the gate and the platform call).
 *  6. unrelated throwable         → rethrown to the caller, deferred flag stays false.
 *
 * The production code calls `ContextCompat.startForegroundService(context, intent)`, which
 * routes through plain [Context.startService] on the unit-test JVM stub (`SDK_INT == 0`) —
 * so that is the platform method we stub. The Android 12+ branch is exercised by injecting
 * a fake [VigilanceServiceLauncher.SdkGate] that returns `true`.
 */
class VigilanceServiceLauncherTest {

    private val context: Context = mockk<Context>(relaxed = true).also {
        every { it.startService(any()) } returns ComponentName("pkg", "Svc")
        every { it.stopService(any()) } returns true
    }
    private val settings: VigilanceSettings = mockk()
    private val sdkS: VigilanceServiceLauncher.SdkGate =
        VigilanceServiceLauncher.SdkGate { true }
    private val cameraGranted: VigilanceServiceLauncher.PermissionGate =
        VigilanceServiceLauncher.PermissionGate { true }
    private val cameraDenied: VigilanceServiceLauncher.PermissionGate =
        VigilanceServiceLauncher.PermissionGate { false }

    @Test
    fun `disabled toggle short-circuits start`() {
        every { settings.isVigilanceEnabled() } returns false
        val launcher = VigilanceServiceLauncher(context, settings, sdkS, cameraGranted)

        val result = launcher.ensureRunning(context)

        assertThat(result).isEqualTo(VigilanceServiceLauncher.Result.Disabled)
        verify(exactly = 0) { context.startService(any()) }
        assertThat(launcher.hasDeferredStart).isFalse()
    }

    @Test
    fun `missing CAMERA grant short-circuits start and defers retry`() {
        every { settings.isVigilanceEnabled() } returns true
        val launcher = VigilanceServiceLauncher(context, settings, sdkS, cameraDenied)

        val result = launcher.ensureRunning(context)

        assertThat(result).isInstanceOf(VigilanceServiceLauncher.Result.MissingPermission::class.java)
        assertThat((result as VigilanceServiceLauncher.Result.MissingPermission).permission)
            .isEqualTo(android.Manifest.permission.CAMERA)
        verify(exactly = 0) { context.startService(any()) }
        assertThat(launcher.hasDeferredStart).isTrue()
    }

    @Test
    fun `enabled toggle starts the service exactly once`() {
        every { settings.isVigilanceEnabled() } returns true
        val launcher = VigilanceServiceLauncher(context, settings, sdkS, cameraGranted)

        val result = launcher.ensureRunning(context)

        assertThat(result).isEqualTo(VigilanceServiceLauncher.Result.Started)
        verify(exactly = 1) { context.startService(any<Intent>()) }
        assertThat(launcher.hasDeferredStart).isFalse()
    }

    @Test
    fun `FGS rejection on Android 12+ defers the start and does not crash`() {
        val cause = ForegroundServiceStartNotAllowedException("cached")
        every { settings.isVigilanceEnabled() } returns true
        every { context.startService(any()) } throws cause
        val launcher = VigilanceServiceLauncher(context, settings, sdkS, cameraGranted)

        val result = launcher.ensureRunning(context)

        assertThat(result).isInstanceOf(VigilanceServiceLauncher.Result.Deferred::class.java)
        assertThat((result as VigilanceServiceLauncher.Result.Deferred).cause).isSameInstanceAs(cause)
        assertThat(launcher.hasDeferredStart).isTrue()
    }

    @Test
    fun `late SecurityException from the platform is swallowed and deferred`() {
        // Defensive net for the case where CAMERA is revoked between the launcher's gate and
        // the system's own validation in ActivityManager.setServiceForeground.
        val cause = SecurityException("Starting FGS with type camera requires CAMERA")
        every { settings.isVigilanceEnabled() } returns true
        every { context.startService(any()) } throws cause
        val launcher = VigilanceServiceLauncher(context, settings, sdkS, cameraGranted)

        val result = launcher.ensureRunning(context)

        assertThat(result).isInstanceOf(VigilanceServiceLauncher.Result.Deferred::class.java)
        assertThat((result as VigilanceServiceLauncher.Result.Deferred).cause).isSameInstanceAs(cause)
        assertThat(launcher.hasDeferredStart).isTrue()
    }

    @Test
    fun `FGS rejection on Android 11 or older is rethrown`() {
        // Pre-S devices do not throw FGSStartNotAllowedException, but the SDK gate must still
        // route the type into the rethrow branch when it reports false.
        val sdkR: VigilanceServiceLauncher.SdkGate = VigilanceServiceLauncher.SdkGate { false }
        every { settings.isVigilanceEnabled() } returns true
        every { context.startService(any()) } throws
            ForegroundServiceStartNotAllowedException("cached")
        val launcher = VigilanceServiceLauncher(context, settings, sdkR, cameraGranted)

        try {
            launcher.ensureRunning(context)
            assert(false) { "expected exception to propagate on pre-S" }
        } catch (_: ForegroundServiceStartNotAllowedException) {
            // expected
        }
        assertThat(launcher.hasDeferredStart).isFalse()
    }

    @Test
    fun `unrelated runtime exception is rethrown to the caller`() {
        every { settings.isVigilanceEnabled() } returns true
        every { context.startService(any()) } throws IllegalStateException("boom")
        val launcher = VigilanceServiceLauncher(context, settings, sdkS, cameraGranted)

        try {
            launcher.ensureRunning(context)
            assert(false) { "expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            assertThat(e).hasMessageThat().isEqualTo("boom")
        }
        assertThat(launcher.hasDeferredStart).isFalse()
    }

    @Test
    fun `stop clears the deferred flag and asks the platform to stop`() {
        every { settings.isVigilanceEnabled() } returns true
        every { context.startService(any()) } throws
            ForegroundServiceStartNotAllowedException("cached")
        val launcher = VigilanceServiceLauncher(context, settings, sdkS, cameraGranted)
        launcher.ensureRunning(context)
        assertThat(launcher.hasDeferredStart).isTrue()

        launcher.stop(context)

        verify(exactly = 1) { context.stopService(any()) }
        assertThat(launcher.hasDeferredStart).isFalse()
    }
}
