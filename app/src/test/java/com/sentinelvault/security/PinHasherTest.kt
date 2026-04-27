package com.sentinelvault.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.SecureRandom

class PinHasherTest {

    private val hasher = PinHasher(SecureRandom())

    @Test
    fun `hash then verify with same pin succeeds`() {
        val derived = hasher.hash("123456".toCharArray(), iterations = 1_000)
        assertThat(hasher.verify("123456".toCharArray(), derived.encoded)).isTrue()
    }

    @Test
    fun `verify with wrong pin fails`() {
        val derived = hasher.hash("123456".toCharArray(), iterations = 1_000)
        assertThat(hasher.verify("654321".toCharArray(), derived.encoded)).isFalse()
    }

    @Test
    fun `two hashes of same pin produce different encodings`() {
        val a = hasher.hash("9999".toCharArray(), iterations = 1_000)
        val b = hasher.hash("9999".toCharArray(), iterations = 1_000)
        assertThat(a.encoded).isNotEqualTo(b.encoded)
    }

    @Test
    fun `encoded format is parseable`() {
        val derived = hasher.hash("0000".toCharArray(), iterations = 1_000)
        val parts = derived.encoded.split('$')
        assertThat(parts).hasSize(4)
        assertThat(parts[0]).isEqualTo("pbkdf2_sha256")
        assertThat(parts[1].toInt()).isEqualTo(1_000)
        assertThat(parts[2]).matches("[0-9a-f]+")
        assertThat(parts[3]).matches("[0-9a-f]+")
    }

    @Test
    fun `verify rejects malformed encoding`() {
        assertThat(hasher.verify("0000".toCharArray(), "garbage")).isFalse()
        assertThat(hasher.verify("0000".toCharArray(), "pbkdf2_sha256\$x\$y\$z")).isFalse()
    }
}
