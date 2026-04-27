package com.sentinelvault.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.sentinelvault.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies that the running APK has been signed with the certificate whose SHA-256 hash was
 * baked into the build via [BuildConfig.APK_SIGNATURE_SHA256] (sourced from local.properties).
 *
 * If the value embedded in BuildConfig is the placeholder zero/DEADBEEF hash (i.e. nobody
 * configured a real release signing cert yet), verification is skipped and the result is
 * [Status.NOT_CONFIGURED]. This keeps debug/CI builds usable while still giving a strong
 * tamper signal in true release builds.
 */
@Singleton
class IntegrityManager(
    private val context: Context,
    private val expectedSignatureHashHex: String
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, BuildConfig.APK_SIGNATURE_SHA256)

    enum class Status { OK, MISMATCH, NOT_CONFIGURED, UNAVAILABLE }

    fun verify(): Status {
        if (!isConfigured(expectedSignatureHashHex)) return Status.NOT_CONFIGURED
        val current = currentSignatureSha256Hex() ?: return Status.UNAVAILABLE
        return if (current.equals(expectedSignatureHashHex, ignoreCase = true)) {
            Status.OK
        } else {
            Status.MISMATCH
        }
    }

    fun currentSignatureSha256Hex(): String? {
        val signatures = readSignatures() ?: return null
        if (signatures.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())
        return digest.toHex()
    }

    @Suppress("DEPRECATION")
    private fun readSignatures(): Array<Signature>? = try {
        val pm = context.packageManager
        val pkg = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo
            when {
                signing == null -> null
                signing.hasMultipleSigners() -> signing.apkContentsSigners
                else -> signing.signingCertificateHistory
            }
        } else {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        }
    } catch (_: Throwable) {
        null
    }

    private fun isConfigured(hex: String): Boolean {
        if (hex.isBlank()) return false
        val normalized = hex.lowercase()
        if (normalized.endsWith("deadbeef")) return false
        return normalized.any { it != '0' }
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private companion object {
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
