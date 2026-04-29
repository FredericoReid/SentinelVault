package com.sentinelvault.data.auth

import com.google.common.truth.Truth.assertThat
import com.sentinelvault.security.PinHasher
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom

/**
 * Pure-JVM tests for the brute-force / lockout state machine. SecurePreferences is mocked
 * so we don't need an Android runtime; every put/get is recorded against an in-memory map.
 */
class PinRepositoryTest {

    private val storage = mutableMapOf<String, Any?>()
    private lateinit var prefs: SecurePreferences
    private lateinit var hasher: PinHasher
    private var fakeNow = 0L
    private val clock = PinRepository.Clock { fakeNow }
    private lateinit var repo: PinRepository

    @Before
    fun setUp() {
        storage.clear()
        prefs = mockk(relaxed = true)
        wireFakeStorage()
        hasher = PinHasher(SecureRandom())
        repo = PinRepository(prefs, hasher, clock)
    }

    @Test
    fun `isPinSet returns false then true after setPin`() {
        assertThat(repo.isPinSet()).isFalse()
        repo.setPin("123456".toCharArray())
        assertThat(repo.isPinSet()).isTrue()
    }

    @Test
    fun `verify against unconfigured returns NotConfigured`() {
        assertThat(repo.verify("0000".toCharArray()))
            .isEqualTo(PinRepository.VerifyResult.NotConfigured)
    }

    @Test
    fun `successful verify resets failed attempts`() {
        repo.setPin("123456".toCharArray())
        repeat(2) { repo.verify("000000".toCharArray()) }
        assertThat(repo.failedAttempts()).isEqualTo(2)
        val result = repo.verify("123456".toCharArray())
        assertThat(result).isEqualTo(PinRepository.VerifyResult.Success)
        assertThat(repo.failedAttempts()).isEqualTo(0)
    }

    @Test
    fun `third failure triggers base lockout`() {
        repo.setPin("123456".toCharArray())
        repo.verify("000000".toCharArray())
        repo.verify("000000".toCharArray())
        val third = repo.verify("000000".toCharArray()) as PinRepository.VerifyResult.Failure
        assertThat(third.attempts).isEqualTo(3)
        assertThat(third.nextLockoutMs).isEqualTo(30_000L)
        assertThat(repo.lockoutRemainingMs()).isEqualTo(30_000L)
    }

    @Test
    fun `verify during lockout returns LockedOut without consuming attempt`() {
        repo.setPin("123456".toCharArray())
        repeat(3) { repo.verify("000000".toCharArray()) }
        val before = repo.failedAttempts()
        val result = repo.verify("123456".toCharArray())
        assertThat(result).isInstanceOf(PinRepository.VerifyResult.LockedOut::class.java)
        assertThat(repo.failedAttempts()).isEqualTo(before)
    }

    @Test
    fun `lockout expires after time advance`() {
        repo.setPin("123456".toCharArray())
        repeat(3) { repo.verify("000000".toCharArray()) }
        fakeNow += 30_001L
        assertThat(repo.lockoutRemainingMs()).isEqualTo(0L)
        assertThat(repo.verify("123456".toCharArray()))
            .isEqualTo(PinRepository.VerifyResult.Success)
    }

    @Test
    fun `lockout grows exponentially up to cap`() {
        repo.setPin("123456".toCharArray())
        val ms = mutableListOf<Long>()
        repeat(10) {
            val r = repo.verify("000000".toCharArray()) as PinRepository.VerifyResult.Failure
            ms += r.nextLockoutMs
            fakeNow += r.nextLockoutMs + 1
        }
        assertThat(ms[0]).isEqualTo(0L)
        assertThat(ms[1]).isEqualTo(0L)
        assertThat(ms[2]).isEqualTo(30_000L)
        assertThat(ms[3]).isEqualTo(60_000L)
        assertThat(ms.last()).isAtMost(30L * 60L * 1000L)
    }

    @Test
    fun `setPin rejects invalid lengths`() {
        try {
            repo.setPin("123".toCharArray())
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { /* ok */ }
    }

    private fun wireFakeStorage() {
        val keySlot = slot<String>()
        val strSlot = slot<String>()
        val intSlot = slot<Int>()
        val intDef = slot<Int>()
        val longSlot = slot<Long>()
        val longDef = slot<Long>()

        every { prefs.contains(capture(keySlot)) } answers { storage.containsKey(keySlot.captured) }
        every { prefs.putEncryptedString(capture(keySlot), capture(strSlot)) } answers {
            storage[keySlot.captured] = strSlot.captured
        }
        every { prefs.getEncryptedString(capture(keySlot)) } answers { storage[keySlot.captured] as? String }
        every { prefs.putInt(capture(keySlot), capture(intSlot)) } answers {
            storage[keySlot.captured] = intSlot.captured
        }
        every { prefs.getInt(capture(keySlot), capture(intDef)) } answers {
            (storage[keySlot.captured] as? Int) ?: intDef.captured
        }
        every { prefs.putLong(capture(keySlot), capture(longSlot)) } answers {
            storage[keySlot.captured] = longSlot.captured
        }
        every { prefs.getLong(capture(keySlot), capture(longDef)) } answers {
            (storage[keySlot.captured] as? Long) ?: longDef.captured
        }
        every { prefs.remove(*anyVararg()) } answers {
            val args = invocation.args[0] as Array<*>
            args.forEach { storage.remove(it as String) }
        }
    }
}
