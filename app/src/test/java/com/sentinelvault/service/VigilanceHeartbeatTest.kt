package com.sentinelvault.service

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * JVM test for [VigilanceHeartbeat] (Task 9.7). The freshness window is the contract that
 * matters most — the dashboard's status truth table inverts on it. We mock
 * [SharedPreferences] so the test runs without a Robolectric / Android runtime.
 */
class VigilanceHeartbeatTest {

    private val editor: SharedPreferences.Editor = mockk(relaxed = true) {
        // SharedPreferences.Editor.putLong / remove return the same editor so the production
        // code can chain `.apply()`. The relaxed mock returns a *different* relaxed instance
        // by default, which would route `apply()` away from the editor we verify against.
        every { putLong(any(), any()) } returns this
        every { remove(any()) } returns this
    }
    private val prefs: SharedPreferences = mockk(relaxed = true) {
        every { edit() } returns editor
    }
    private val context: Context = mockk(relaxed = true) {
        every { getSharedPreferences(any(), any()) } returns prefs
    }

    @Test
    fun `beat persists the supplied timestamp`() {
        val hb = VigilanceHeartbeat(context)

        hb.beat(nowMs = 12_345L)

        verify { editor.putLong(any(), 12_345L) }
        verify { editor.apply() }
    }

    @Test
    fun `isFresh returns false when no heartbeat has been written`() {
        every { prefs.getLong(any(), any()) } returns 0L
        val hb = VigilanceHeartbeat(context)

        assertThat(hb.isFresh(nowMs = 1_000L)).isFalse()
    }

    @Test
    fun `isFresh returns true within the staleness window`() {
        every { prefs.getLong(any(), any()) } returns 1_000L
        val hb = VigilanceHeartbeat(context)

        assertThat(hb.isFresh(nowMs = 1_000L + VigilanceHeartbeat.STALE_AFTER_MS)).isTrue()
    }

    @Test
    fun `isFresh returns false beyond the staleness window`() {
        every { prefs.getLong(any(), any()) } returns 1_000L
        val hb = VigilanceHeartbeat(context)

        assertThat(hb.isFresh(nowMs = 1_000L + VigilanceHeartbeat.STALE_AFTER_MS + 1L)).isFalse()
    }

    @Test
    fun `clear removes the stored timestamp`() {
        val hb = VigilanceHeartbeat(context)

        hb.clear()

        verify { editor.remove(any()) }
        verify { editor.apply() }
    }
}
