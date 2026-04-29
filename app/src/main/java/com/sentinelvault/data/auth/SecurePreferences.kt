package com.sentinelvault.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.sentinelvault.security.KeystoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin facade over [SharedPreferences] that transparently encrypts the values it considers
 * sensitive using a Keystore-backed AES-256 GCM key (see [KeystoreManager]).
 *
 * Non-sensitive bookkeeping (failure counters, lockout deadlines) is stored in plain text –
 * those values are integrity-protected by the OS sandbox, not by encryption.
 */
@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext context: Context,
    private val keystoreManager: KeystoreManager
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun putEncryptedString(key: String, value: String) {
        val sealed = keystoreManager.encrypt(KEY_ALIAS, value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    fun getEncryptedString(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val sealed = Base64.decode(raw, Base64.NO_WRAP)
            String(keystoreManager.decrypt(KEY_ALIAS, sealed), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)

    fun remove(vararg keys: String) {
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    private companion object {
        private const val PREFS_NAME = "sentinel_secure_prefs"
        private const val KEY_ALIAS = "sentinel_prefs_master"
    }
}
