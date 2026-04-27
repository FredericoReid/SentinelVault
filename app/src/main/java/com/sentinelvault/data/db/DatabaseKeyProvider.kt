package com.sentinelvault.data.db

import android.content.Context
import com.sentinelvault.security.KeystoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates and persists the SQLCipher passphrase.
 *
 * Strategy: a 32-byte random key is created on first launch, encrypted with an
 * AES-256 GCM key from the Android Keystore ([KeystoreManager]) and stored in the
 * app's private files dir. The plaintext key never touches disk.
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager,
    private val random: SecureRandom
) {

    fun getOrCreatePassphrase(): ByteArray {
        val file = File(context.filesDir, KEY_FILE)
        if (file.exists()) {
            val sealed = file.readBytes()
            return keystoreManager.decrypt(KEY_ALIAS, sealed)
        }
        val plain = ByteArray(KEY_BYTES).also { random.nextBytes(it) }
        val sealed = keystoreManager.encrypt(KEY_ALIAS, plain)
        file.writeBytes(sealed)
        return plain
    }

    fun reset() {
        val file = File(context.filesDir, KEY_FILE)
        if (file.exists()) file.delete()
        keystoreManager.deleteKey(KEY_ALIAS)
    }

    private companion object {
        private const val KEY_ALIAS = "sentinel_db_master"
        private const val KEY_FILE = "sentinel_db.key"
        private const val KEY_BYTES = 32
    }
}
