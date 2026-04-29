package com.sentinelvault.vault

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.sentinelvault.security.MemorySanitizer
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BurstRecorderTest {

    /**
     * Stub factory that returns a deterministic [FrameCandidate] per call. Avoids
     * `Bitmap.copy` (which would explode under the JVM `Stub!` runtime) by emitting a
     * mocked bitmap and a synthetic luma plane.
     */
    private class StubFactory : FrameCandidateFactory {
        var calls: Int = 0; private set
        var failNext: Boolean = false
        override fun snapshot(bitmap: Bitmap, capturedAtMs: Long): FrameCandidate? {
            calls += 1
            if (failNext) { failNext = false; return null }
            return FrameCandidate(
                bitmap = mockk<Bitmap>(relaxed = true),
                capturedAtMs = capturedAtMs,
                luma = ByteArray(4),
                width = 2,
                height = 2
            )
        }
    }

    private fun newRecorder(
        maxFrames: Int = 3,
        sanitizer: MemorySanitizer = mockk(relaxed = true),
        factory: FrameCandidateFactory = StubFactory()
    ): BurstRecorder = BurstRecorder(factory, sanitizer, maxFrames)

    @Test
    fun `cold offer is dropped without invoking factory`() = runTest {
        val factory = StubFactory()
        val recorder = newRecorder(factory = factory)
        val source = mockk<Bitmap>(relaxed = true)

        val accepted = recorder.offer(source, capturedAtMs = 100L)

        assertThat(accepted).isFalse()
        assertThat(recorder.size()).isEqualTo(0)
        assertThat(factory.calls).isEqualTo(0)
    }

    @Test
    fun `armed offer accumulates candidates up to the cap`() = runTest {
        val factory = StubFactory()
        val recorder = newRecorder(maxFrames = 3, factory = factory)
        recorder.arm()

        repeat(3) { i -> recorder.offer(mockk<Bitmap>(relaxed = true), capturedAtMs = i.toLong()) }

        assertThat(recorder.isArmed()).isTrue()
        assertThat(recorder.size()).isEqualTo(3)
        assertThat(factory.calls).isEqualTo(3)
    }

    @Test
    fun `ring buffer evicts the oldest candidate and recycles its bitmap`() = runTest {
        val sanitizer = mockk<MemorySanitizer>(relaxed = true)
        val recorder = newRecorder(maxFrames = 2, sanitizer = sanitizer)
        recorder.arm()
        recorder.offer(mockk<Bitmap>(relaxed = true), capturedAtMs = 1L)
        recorder.offer(mockk<Bitmap>(relaxed = true), capturedAtMs = 2L)
        recorder.offer(mockk<Bitmap>(relaxed = true), capturedAtMs = 3L)

        assertThat(recorder.size()).isEqualTo(2)
        verify(exactly = 1) { sanitizer.recycle(any<Bitmap>()) }

        val drained = recorder.drain()
        assertThat(drained.map { it.capturedAtMs }).containsExactly(2L, 3L).inOrder()
    }

    @Test
    fun `disarm recycles every retained candidate and clears the buffer`() = runTest {
        val sanitizer = mockk<MemorySanitizer>(relaxed = true)
        val recorder = newRecorder(maxFrames = 4, sanitizer = sanitizer)
        recorder.arm()
        repeat(3) { i -> recorder.offer(mockk<Bitmap>(relaxed = true), capturedAtMs = i.toLong()) }

        recorder.disarm()

        assertThat(recorder.isArmed()).isFalse()
        assertThat(recorder.size()).isEqualTo(0)
        verify(exactly = 3) { sanitizer.recycle(any<Bitmap>()) }
    }

    @Test
    fun `drain returns retained candidates in capture order and disarms`() = runTest {
        val recorder = newRecorder(maxFrames = 4)
        recorder.arm()
        recorder.offer(mockk<Bitmap>(relaxed = true), capturedAtMs = 10L)
        recorder.offer(mockk<Bitmap>(relaxed = true), capturedAtMs = 20L)

        val drained = recorder.drain()

        assertThat(drained.map { it.capturedAtMs }).containsExactly(10L, 20L).inOrder()
        assertThat(recorder.isArmed()).isFalse()
        assertThat(recorder.size()).isEqualTo(0)
    }

    @Test
    fun `drain on a cold recorder returns empty and stays disarmed`() = runTest {
        val recorder = newRecorder()
        val drained = recorder.drain()
        assertThat(drained).isEmpty()
        assertThat(recorder.isArmed()).isFalse()
    }

    @Test
    fun `arm is idempotent`() = runTest {
        val recorder = newRecorder()
        recorder.arm()
        recorder.arm()
        assertThat(recorder.isArmed()).isTrue()
    }

    @Test
    fun `factory returning null counts as a dropped frame`() = runTest {
        val factory = StubFactory().apply { failNext = true }
        val sanitizer = mockk<MemorySanitizer>(relaxed = true)
        val recorder = newRecorder(sanitizer = sanitizer, factory = factory)
        recorder.arm()

        val accepted = recorder.offer(mockk<Bitmap>(relaxed = true), capturedAtMs = 1L)

        assertThat(accepted).isFalse()
        assertThat(recorder.size()).isEqualTo(0)
        verify(exactly = 0) { sanitizer.recycle(any<Bitmap>()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive maxFrames is rejected`() {
        BurstRecorder(StubFactory(), mockk(relaxed = true), maxFrames = 0)
    }
}
