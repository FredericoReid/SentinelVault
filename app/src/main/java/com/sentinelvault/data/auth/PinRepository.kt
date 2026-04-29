package com.sentinelvault.data.auth

import com.sentinelvault.security.PinHasher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Stores the Admin PIN hash and exposes brute-force-aware verification.
 *
 * Lockout policy (exponential, capped):
 *  - 0..2 consecutive failures: no lockout.
 *  - n >= 3 failures: lockout = min(2^(n-3) * BASE_LOCKOUT_MS, MAX_LOCKOUT_MS).
 *  Successful verification resets the counter.
 */
@Singleton
class PinRepository @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val pinHasher: PinHasher,
    private val clock: Clock = Clock.SYSTEM
) {

    fun interface Clock {
        fun nowMs(): Long
        companion object { val SYSTEM: Clock = Clock { System.currentTimeMillis() } }
    }

    sealed interface VerifyResult {
        object Success : VerifyResult
        data class Failure(val attempts: Int, val nextLockoutMs: Long) : VerifyResult
        data class LockedOut(val remainingMs: Long) : VerifyResult
        object NotConfigured : VerifyResult
    }

    fun isPinSet(): Boolean = securePreferences.contains(KEY_PIN_HASH)

    fun setPin(pin: CharArray) {
        require(pin.size in MIN_LEN..MAX_LEN) { "PIN length must be in $MIN_LEN..$MAX_LEN" }
        val derived = pinHasher.hash(pin)
        securePreferences.putEncryptedString(KEY_PIN_HASH, derived.encoded)
        resetFailures()
    }

    fun clear() {
        securePreferences.remove(KEY_PIN_HASH, KEY_FAILED_ATTEMPTS, KEY_LOCKOUT_ENDS_AT)
    }

    fun lockoutRemainingMs(): Long {
        val until = securePreferences.getLong(KEY_LOCKOUT_ENDS_AT, 0L)
        val now = clock.nowMs()
        return if (until > now) until - now else 0L
    }

    fun failedAttempts(): Int = securePreferences.getInt(KEY_FAILED_ATTEMPTS, 0)

    fun verify(pin: CharArray): VerifyResult {
        val encoded = securePreferences.getEncryptedString(KEY_PIN_HASH)
            ?: return VerifyResult.NotConfigured

        val remaining = lockoutRemainingMs()
        if (remaining > 0L) return VerifyResult.LockedOut(remaining)

        return if (pinHasher.verify(pin, encoded)) {
            resetFailures()
            VerifyResult.Success
        } else {
            val attempts = failedAttempts() + 1
            securePreferences.putInt(KEY_FAILED_ATTEMPTS, attempts)
            val lockout = lockoutForAttempts(attempts)
            if (lockout > 0L) {
                securePreferences.putLong(KEY_LOCKOUT_ENDS_AT, clock.nowMs() + lockout)
            }
            VerifyResult.Failure(attempts, lockout)
        }
    }

    private fun resetFailures() {
        securePreferences.remove(KEY_FAILED_ATTEMPTS, KEY_LOCKOUT_ENDS_AT)
    }

    private fun lockoutForAttempts(attempts: Int): Long {
        if (attempts < LOCKOUT_THRESHOLD) return 0L
        val exponent = min(attempts - LOCKOUT_THRESHOLD, MAX_EXPONENT)
        val ms = BASE_LOCKOUT_MS shl exponent
        return min(ms, MAX_LOCKOUT_MS)
    }

    companion object {
        const val MIN_LEN: Int = 4
        const val MAX_LEN: Int = 8

        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_LOCKOUT_ENDS_AT = "pin_lockout_ends_at"

        private const val LOCKOUT_THRESHOLD = 3
        private const val BASE_LOCKOUT_MS = 30_000L
        private const val MAX_LOCKOUT_MS = 30L * 60L * 1000L
        private const val MAX_EXPONENT = 6
    }
}
