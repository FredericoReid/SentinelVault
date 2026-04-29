package com.sentinelvault.triggers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContextTokenManagerTest {

    private val youtube = "com.google.android.youtube"
    private val whatsapp = "com.whatsapp"
    private val gallery = "com.android.gallery3d"

    private class FakeClock(var now: Long = 1_000L) : TriggerClock {
        override fun nowMs(): Long = now
    }

    private fun fixture(now: Long = 1_000L): Triple<ContextTokenManager, TriggerOrchestrator, FakeClock> {
        val orchestrator = TriggerOrchestrator()
        val clock = FakeClock(now)
        val manager = ContextTokenManager(orchestrator, SensitiveAppRegistry(), clock)
        return Triple(manager, orchestrator, clock)
    }

    @Test
    fun `issue mints a token for the supplied package`() {
        val (manager, _, clock) = fixture()
        val token = manager.issue(youtube)
        assertThat(token.packageName).isEqualTo(youtube)
        assertThat(token.issuedAtMs).isEqualTo(clock.now)
        assertThat(manager.token.value).isEqualTo(token)
    }

    @Test
    fun `staying on the same package keeps the token`() {
        val (manager, _, _) = fixture()
        manager.issue(youtube)
        val active = manager.onForegroundAppChanged(youtube)
        assertThat(active).isNotNull()
        assertThat(manager.token.value).isNotNull()
    }

    @Test
    fun `switching to a different generic package emits a context breach and revokes`() = runBlockingCollect {
        val (manager, orchestrator, _) = fixture()
        val events = collect(orchestrator)
        manager.issue(youtube)
        manager.onForegroundAppChanged(gallery)
        assertThat(manager.token.value).isNull()
        val breach = events.filterIsInstance<TriggerEvent.ContextBreach>().single()
        assertThat(breach.tokenPackage).isEqualTo(youtube)
        assertThat(breach.attemptedPackage).isEqualTo(gallery)
    }

    @Test
    fun `opening a sensitive app emits SensitiveAppOpened and ContextBreach`() = runBlockingCollect {
        val (manager, orchestrator, _) = fixture()
        val events = collect(orchestrator)
        manager.issue(youtube)
        manager.onForegroundAppChanged(whatsapp)
        val opened = events.filterIsInstance<TriggerEvent.SensitiveAppOpened>().single()
        val breach = events.filterIsInstance<TriggerEvent.ContextBreach>().single()
        assertThat(opened.packageName).isEqualTo(whatsapp)
        assertThat(breach.attemptedPackage).isEqualTo(whatsapp)
        assertThat(manager.token.value).isNull()
    }

    @Test
    fun `expired token is treated as missing without emitting a breach`() = runBlockingCollect {
        val (manager, orchestrator, clock) = fixture(now = 0L)
        val events = collect(orchestrator)
        manager.issue(youtube, ttlMs = 1_000L)
        clock.now = 5_000L
        manager.onForegroundAppChanged(gallery)
        assertThat(manager.token.value).isNull()
        assertThat(events.filterIsInstance<TriggerEvent.ContextBreach>()).isEmpty()
    }

    @Test
    fun `revoke clears the token without emitting events`() = runBlockingCollect {
        val (manager, orchestrator, _) = fixture()
        val events = collect(orchestrator)
        manager.issue(youtube)
        manager.revoke()
        assertThat(manager.token.value).isNull()
        assertThat(events).isEmpty()
    }

    @Test
    fun `isCovered honours expiry and package binding`() {
        val (manager, _, clock) = fixture(now = 0L)
        manager.issue(youtube, ttlMs = 1_000L)
        assertThat(manager.isCovered(youtube)).isTrue()
        assertThat(manager.isCovered(gallery)).isFalse()
        clock.now = 2_000L
        assertThat(manager.isCovered(youtube)).isFalse()
    }
}
