package com.sentinelvault.vigilance.orientation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OrientationTrackerTest {

    @Test
    fun `canonicalize maps quadrants to 0 90 180 270`() {
        assertThat(OrientationTracker.canonicalize(0)).isEqualTo(0)
        assertThat(OrientationTracker.canonicalize(44)).isEqualTo(0)
        assertThat(OrientationTracker.canonicalize(46)).isEqualTo(90)
        assertThat(OrientationTracker.canonicalize(134)).isEqualTo(90)
        assertThat(OrientationTracker.canonicalize(136)).isEqualTo(180)
        assertThat(OrientationTracker.canonicalize(224)).isEqualTo(180)
        assertThat(OrientationTracker.canonicalize(226)).isEqualTo(270)
        assertThat(OrientationTracker.canonicalize(314)).isEqualTo(270)
        assertThat(OrientationTracker.canonicalize(315)).isEqualTo(0)
    }

    @Test
    fun `unknown orientation is ignored`() {
        assertThat(OrientationTracker.canonicalize(android.view.OrientationEventListener.ORIENTATION_UNKNOWN))
            .isNull()
    }

    @Test
    fun `rotation changes only after debounce window`() {
        val backend = FakeBackend()
        val clock = FakeClock()
        val tracker = OrientationTracker(backend, clock)
        tracker.start()

        backend.emit(50)
        assertThat(tracker.rotation.value).isEqualTo(0)

        clock.now = 199L
        backend.emit(60)
        assertThat(tracker.rotation.value).isEqualTo(0)

        clock.now = 200L
        backend.emit(60)
        assertThat(tracker.rotation.value).isEqualTo(90)
    }

    @Test
    fun `start and stop are idempotent`() {
        val backend = FakeBackend()
        val tracker = OrientationTracker(backend, FakeClock())

        assertThat(tracker.start()).isTrue()
        assertThat(tracker.start()).isTrue()
        tracker.stop()
        tracker.stop()

        assertThat(backend.enableCalls).isEqualTo(1)
        assertThat(backend.disableCalls).isEqualTo(1)
    }

    private class FakeClock(var now: Long = 0L) : OrientationTracker.Clock {
        override fun nowMs(): Long = now
    }

    private class FakeBackend : OrientationTracker.OrientationBackend {
        private var callback: ((Int) -> Unit)? = null
        var enableCalls: Int = 0
        var disableCalls: Int = 0

        override fun startListening(): Boolean {
            enableCalls += 1
            return true
        }

        override fun stopListening() {
            disableCalls += 1
        }

        override fun setCallback(callback: (Int) -> Unit) {
            this.callback = callback
        }

        fun emit(raw: Int) {
            callback?.invoke(raw)
        }
    }

}