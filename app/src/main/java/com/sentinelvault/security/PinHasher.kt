package com.sentinelvault.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives and verifies Admin PIN hashes using PBKDF2-HMAC-SHA256.
 *
 * Storage format (single Base64-free string, ':' separated):
 *   pbkdf2_sha256$<iterations>$<saltHex>$<hashHex>
 */
@Singleton
class PinHasher @Inject constructor(
    private val random: SecureRandom = SecureRandom()
) {

    data class Derived(val encoded: String)

    fun hash(pin: CharArray, iterations: Int = DEFAULT_ITERATIONS): Derived {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val hash = pbkdf2(pin, salt, iterations, KEY_BITS)
        return Derived("pbkdf2_sha256\$$iterations\$${salt.toHex()}\$${hash.toHex()}")
    }

    fun verify(pin: CharArray, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2_sha256") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = parts[2].fromHex() ?: return false
        val expected = parts[3].fromHex() ?: return false
        val computed = pbkdf2(pin, salt, iterations, expected.size * 8)
        return constantTimeEquals(expected, computed)
    }

    private fun pbkdf2(pin: CharArray, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, keyBits)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private fun String.fromHex(): ByteArray? {
        if (length % 2 != 0) return null
        val out = ByteArray(length / 2)
        for (i in out.indices) {
            val hi = Character.digit(this[i * 2], 16)
            val lo = Character.digit(this[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private companion object {
        private const val DEFAULT_ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
