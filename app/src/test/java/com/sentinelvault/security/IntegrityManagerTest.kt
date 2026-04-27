package com.sentinelvault.security

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

/**
 * Unit-level checks for [IntegrityManager]. The Android signing APIs are not exercised here
 * (that is covered by an instrumented test); we only validate the placeholder-detection
 * branch which is pure JVM logic.
 */
class IntegrityManagerTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun `placeholder DEADBEEF hash is treated as not configured`() {
        val mgr = IntegrityManager(
            context,
            expectedSignatureHashHex = "00000000000000000000000000000000000000000000000000000000DEADBEEF"
        )
        assertThat(mgr.verify()).isEqualTo(IntegrityManager.Status.NOT_CONFIGURED)
    }

    @Test
    fun `all-zero hash is treated as not configured`() {
        val mgr = IntegrityManager(context, expectedSignatureHashHex = "0".repeat(64))
        assertThat(mgr.verify()).isEqualTo(IntegrityManager.Status.NOT_CONFIGURED)
    }

    @Test
    fun `blank hash is treated as not configured`() {
        val mgr = IntegrityManager(context, expectedSignatureHashHex = "")
        assertThat(mgr.verify()).isEqualTo(IntegrityManager.Status.NOT_CONFIGURED)
    }
}
