package com.sentinelvault.vault

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EvidenceRetentionPolicyTest {

    private fun file(eventId: Long, sizeBytes: Long, timestampMs: Long): EvidenceFile =
        EvidenceFile(
            eventId = eventId,
            absolutePath = "/vault/$eventId.webp",
            sizeBytes = sizeBytes,
            timestampMs = timestampMs
        )

    @Test
    fun `empty catalog reports an empty pass`() = runTest {
        val report = EvidenceRetentionPolicy().enforce(emptyList()) { true }
        assertThat(report.evicted).isEmpty()
        assertThat(report.initialBytes).isEqualTo(0L)
        assertThat(report.finalBytes).isEqualTo(0L)
        assertThat(report.withinBudget).isTrue()
    }

    @Test
    fun `catalog already under budget evicts nothing`() = runTest {
        val catalog = listOf(file(1L, 100L, 10L), file(2L, 200L, 20L))
        val policy = EvidenceRetentionPolicy(limitBytes = 1_000L)

        val report = policy.enforce(catalog) { error("evict must not be called") }

        assertThat(report.evicted).isEmpty()
        assertThat(report.freedBytes).isEqualTo(0L)
        assertThat(report.finalBytes).isEqualTo(300L)
        assertThat(report.withinBudget).isTrue()
    }

    @Test
    fun `1_6 GB catalog evicts oldest until under 1_5 GB budget`() = runTest {
        // 16 hero frames × 100 MB each = 1.6 GB, oldest first.
        val mega = 100L * 1_000_000L
        val catalog = (0 until 16).map { i ->
            file(eventId = i.toLong(), sizeBytes = mega, timestampMs = 1_000L + i.toLong())
        }
        val policy = EvidenceRetentionPolicy() // default 1.5 GB
        val evictedPaths = ArrayList<String>()

        val report = policy.enforce(catalog) { evictedPaths += it.absolutePath; true }

        assertThat(report.withinBudget).isTrue()
        assertThat(report.finalBytes).isAtMost(EvidenceRetentionPolicy.DEFAULT_LIMIT_BYTES)
        assertThat(report.evicted.map { it.eventId }).containsExactly(0L).inOrder()
        assertThat(report.freedBytes).isEqualTo(mega)
        assertThat(evictedPaths).hasSize(1)
    }

    @Test
    fun `eviction follows strict FIFO order by timestamp`() = runTest {
        val catalog = listOf(
            file(eventId = 7L, sizeBytes = 400L, timestampMs = 30L), // newest
            file(eventId = 8L, sizeBytes = 400L, timestampMs = 10L), // oldest
            file(eventId = 9L, sizeBytes = 400L, timestampMs = 20L)
        ).sortedBy { it.timestampMs }
        val policy = EvidenceRetentionPolicy(limitBytes = 500L)
        val order = ArrayList<Long>()

        val report = policy.enforce(catalog) { order += it.eventId; true }

        assertThat(order).containsExactly(8L, 9L).inOrder()
        assertThat(report.evicted.map { it.eventId }).containsExactly(8L, 9L).inOrder()
        assertThat(report.finalBytes).isEqualTo(400L)
    }

    @Test
    fun `evict returning false does not subtract phantom bytes`() = runTest {
        val catalog = listOf(
            file(1L, 600L, 10L),
            file(2L, 600L, 20L)
        )
        val policy = EvidenceRetentionPolicy(limitBytes = 1_000L)
        val attempted = ArrayList<Long>()

        val report = policy.enforce(catalog) { evidence ->
            attempted += evidence.eventId
            evidence.eventId != 1L // first eviction fails
        }

        assertThat(attempted).containsExactly(1L, 2L).inOrder()
        assertThat(report.evicted.map { it.eventId }).containsExactly(2L)
        assertThat(report.finalBytes).isEqualTo(600L)
        assertThat(report.withinBudget).isTrue()
    }

    @Test
    fun `negative or zero limit is rejected`() {
        runCatching { EvidenceRetentionPolicy(limitBytes = 0L) }
            .also { assertThat(it.isFailure).isTrue() }
        runCatching { EvidenceRetentionPolicy(limitBytes = -1L) }
            .also { assertThat(it.isFailure).isTrue() }
    }
}
