package com.sentinelvault.triggers

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * `UsageEvents.Event` is a final framework class with package-private setters, so the test
 * leans on MockK's `relaxed = true` capability to feed deterministic event-type / packageName
 * pairs into the iterator.
 */
class UsageStatsForegroundTrackerTest {

    /** Per-call (eventType, packageName) tuple driven into the reusable buffer. */
    private data class Sample(val type: Int, val pkg: String)

    /**
     * Builds a mocked [UsageEvents] cursor that, on every `getNextEvent(buffer)` call, restubs
     * the *same* buffer mock with the next [Sample]. This mirrors the production reuse pattern
     * without requiring the framework `UsageEvents.Event()` constructor (which throws on JVM).
     */
    private fun usageEvents(samples: List<Sample>, buffer: UsageEvents.Event): UsageEvents {
        val iter = mockk<UsageEvents>()
        val cursor = samples.iterator()
        every { iter.hasNextEvent() } answers { cursor.hasNext() }
        every { iter.getNextEvent(buffer) } answers {
            val src = cursor.next()
            every { buffer.eventType } returns src.type
            every { buffer.packageName } returns src.pkg
            true
        }
        return iter
    }

    private fun trackerWith(samples: List<Sample>?, buffer: UsageEvents.Event = mockk(relaxed = true)) =
        UsageStatsForegroundTracker(mockk<UsageStatsManager>().also { mgr ->
            every { mgr.queryEvents(any(), any()) } returns samples?.let { usageEvents(it, buffer) }
        }).also { it.eventFactory = { buffer } }

    @Test
    fun `returns the most recent foreground package`() {
        val tracker = trackerWith(
            listOf(
                Sample(UsageEvents.Event.MOVE_TO_FOREGROUND, "com.first"),
                Sample(UsageEvents.Event.MOVE_TO_BACKGROUND, "com.first"),
                Sample(UsageEvents.Event.MOVE_TO_FOREGROUND, "com.second")
            )
        )
        assertThat(tracker.currentForegroundPackage(0L)).isEqualTo("com.second")
    }

    @Test
    fun `returns null when the manager yields no events`() {
        val tracker = trackerWith(emptyList())
        assertThat(tracker.currentForegroundPackage(0L)).isNull()
    }

    @Test
    fun `returns null when the system surfaces a null UsageEvents`() {
        val tracker = trackerWith(samples = null)
        assertThat(tracker.currentForegroundPackage(0L)).isNull()
    }
}
