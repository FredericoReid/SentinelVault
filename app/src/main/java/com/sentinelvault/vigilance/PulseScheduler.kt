package com.sentinelvault.vigilance

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits a `Unit` every [intervalMs] for the lifetime of the collector. Wrapped behind an
 * interface so unit tests can substitute a deterministic ticker driven by
 * `TestScope.advanceTimeBy` instead of relying on wall-clock delays.
 */
fun interface PulseScheduler {
    fun ticks(intervalMs: Long): Flow<Unit>

    companion object {
        val DEFAULT: PulseScheduler = PulseScheduler { interval ->
            require(interval > 0L) { "interval must be positive" }
            flow {
                while (true) {
                    delay(interval)
                    emit(Unit)
                }
            }
        }
    }
}
