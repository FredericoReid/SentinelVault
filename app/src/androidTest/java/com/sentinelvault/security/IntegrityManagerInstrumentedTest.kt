package com.sentinelvault.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntegrityManagerInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun current_signature_hash_is_64_hex_chars() {
        val mgr = IntegrityManager(context, expectedSignatureHashHex = "0".repeat(64))
        val hex = mgr.currentSignatureSha256Hex()
        assertThat(hex).isNotNull()
        assertThat(hex!!).matches("[0-9a-f]{64}")
    }

    @Test
    fun mismatched_expected_hash_returns_MISMATCH() {
        val bogus = "a".repeat(64)
        val mgr = IntegrityManager(context, expectedSignatureHashHex = bogus)
        assertThat(mgr.verify()).isEqualTo(IntegrityManager.Status.MISMATCH)
    }

    @Test
    fun matching_expected_hash_returns_OK() {
        val mgr = IntegrityManager(context, expectedSignatureHashHex = "0".repeat(64))
        val real = mgr.currentSignatureSha256Hex()!!
        val mgr2 = IntegrityManager(context, expectedSignatureHashHex = real)
        assertThat(mgr2.verify()).isEqualTo(IntegrityManager.Status.OK)
    }
}
