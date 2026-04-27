package com.sentinelvault.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper over the Android Keystore System producing/using AES-256 GCM keys.
 *
 * Each logical secret (identified by [alias]) is bound to its own hardware-backed key
 * (StrongBox-preferred when supported). Plaintext is never persisted; only the IV (12 bytes)
 * is prepended to the ciphertext to allow [decrypt] to be self-contained.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    fun encrypt(alias: String, plaintext: ByteArray): ByteArray {
        val key = getOrCreateKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val iv = cipher.iv
        require(iv.size == GCM_IV_LENGTH) { "Unexpected GCM IV length: ${iv.size}" }
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(iv.size + ciphertext.size).also {
            System.arraycopy(iv, 0, it, 0, iv.size)
            System.arraycopy(ciphertext, 0, it, iv.size, ciphertext.size)
        }
    }

    fun decrypt(alias: String, ivAndCiphertext: ByteArray): ByteArray {
        require(ivAndCiphertext.size > GCM_IV_LENGTH) { "Payload too small" }
        val key = loadKey(alias) ?: error("Key not found for alias=$alias")
        val iv = ivAndCiphertext.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = ivAndCiphertext.copyOfRange(GCM_IV_LENGTH, ivAndCiphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher.doFinal(ciphertext)
    }

    fun hasKey(alias: String): Boolean = androidKeystore().containsAlias(alias)

    fun deleteKey(alias: String) {
        val ks = androidKeystore()
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
    }

    private fun getOrCreateKey(alias: String): SecretKey =
        loadKey(alias) ?: generateKey(alias)

    private fun loadKey(alias: String): SecretKey? {
        val ks = androidKeystore()
        if (!ks.containsAlias(alias)) return null
        return (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun generateKey(alias: String): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_BITS)
            .setRandomizedEncryptionRequired(true)

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun androidKeystore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}
