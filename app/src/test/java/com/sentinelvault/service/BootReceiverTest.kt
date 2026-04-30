package com.sentinelvault.service

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.sentinelvault.vigilance.OwnerTemplateProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * JVM test for [BootReceiver] (Task 9.5).
 *
 * The happy path goes through `goAsync()`, which depends on framework state that is `null`
 * on the unit-test JVM. We therefore exercise the [BootReceiver.shouldHandle] gate function
 * directly — unrelated action, disabled toggle, locked-boot deferral, happy path — which
 * covers the full decision matrix without dragging in the Hilt-generated subclass. The
 * end-to-end auto-start (DAO read + foreground-service launch) is covered by the on-device
 * instrumentation suite.
 */
class BootReceiverTest {

    private val settings: VigilanceSettings = mockk()
    private val templates: OwnerTemplateProvider = mockk()
    private val launcher: VigilanceServiceLauncher = mockk(relaxed = true)

    private fun receiver(): BootReceiver = BootReceiver().apply {
        this.settings = this@BootReceiverTest.settings
        this.ownerTemplateProvider = this@BootReceiverTest.templates
        this.launcher = this@BootReceiverTest.launcher
    }

    @Test
    fun `unrelated action is ignored`() {
        assertThat(receiver().shouldHandle("com.example.OTHER")).isFalse()
    }

    @Test
    fun `null action is ignored`() {
        assertThat(receiver().shouldHandle(null)).isFalse()
    }

    @Test
    fun `disabled vigilance short-circuits boot-completed`() {
        every { settings.isVigilanceEnabled() } returns false

        assertThat(receiver().shouldHandle(Intent.ACTION_BOOT_COMPLETED)).isFalse()
    }

    @Test
    fun `locked boot completed is accepted by the filter but not handled`() {
        // Forward-compat: the manifest filter accepts LOCKED_BOOT_COMPLETED so a future
        // Direct-Boot-aware refactor needs no manifest change, but today the receiver must
        // not act on it (CE storage / SQLCipher key are not yet available).
        every { settings.isVigilanceEnabled() } returns true

        assertThat(receiver().shouldHandle(Intent.ACTION_LOCKED_BOOT_COMPLETED)).isFalse()
    }

    @Test
    fun `boot completed with vigilance enabled is handled`() {
        every { settings.isVigilanceEnabled() } returns true

        assertThat(receiver().shouldHandle(Intent.ACTION_BOOT_COMPLETED)).isTrue()
    }
}
