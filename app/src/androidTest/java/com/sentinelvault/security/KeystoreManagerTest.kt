package com.sentinelvault.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreManagerTest {

    private val alias = "sentinel_test_alias"
    private val manager = KeystoreManager()

    @Before
    fun setUp() {
        manager.deleteKey(alias)
    }

    @After
    fun tearDown() {
        manager.deleteKey(alias)
    }

    @Test
    fun encrypt_then_decrypt_recovers_plaintext() {
        val plain = "the password is sentinel".toByteArray()
        val sealed = manager.encrypt(alias, plain)
        assertThat(sealed).isNotEqualTo(plain)
        val recovered = manager.decrypt(alias, sealed)
        assertThat(recovered).isEqualTo(plain)
    }

    @Test
    fun two_encryptions_of_same_plaintext_differ_due_to_random_iv() {
        val plain = "same input".toByteArray()
        val a = manager.encrypt(alias, plain)
        val b = manager.encrypt(alias, plain)
        assertThat(a).isNotEqualTo(b)
        assertThat(manager.decrypt(alias, a)).isEqualTo(plain)
        assertThat(manager.decrypt(alias, b)).isEqualTo(plain)
    }

    @Test
    fun key_lifecycle() {
        assertThat(manager.hasKey(alias)).isFalse()
        manager.encrypt(alias, byteArrayOf(1, 2, 3))
        assertThat(manager.hasKey(alias)).isTrue()
        manager.deleteKey(alias)
        assertThat(manager.hasKey(alias)).isFalse()
    }
}
