package com.sentinelvault.triggers

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Single fan-in bus for every Epic 4 trigger source. Each producer (broadcast receiver,
 * accessibility service, sensor listener, foreground tracker) calls [emit]; Epic 5's state
 * machine subscribes to [events] and decides whether to spend camera frames.
 *
 * The internal flow uses [BufferOverflow.DROP_OLDEST] with a small replay window so a slow
 * collector can never stall the producers (sensors fire at 50 Hz, broadcasts are bursty).
 * Replay = 1 lets a freshly attached collector immediately see the latest trigger, which
 * matters during process resurrection right after `ACTION_USER_PRESENT`.
 */
@Singleton
class TriggerOrchestrator @Inject constructor() {

    private val _events: MutableSharedFlow<TriggerEvent> = MutableSharedFlow(
        replay = REPLAY,
        extraBufferCapacity = EXTRA_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<TriggerEvent> = _events.asSharedFlow()

    /**
     * Push a [TriggerEvent] onto the bus. Returns `true` if it was accepted by the buffer
     * (always true for the configured DROP_OLDEST policy). Synchronous and lock-free so it
     * is safe to call from `SensorEventListener.onSensorChanged`.
     */
    fun emit(event: TriggerEvent): Boolean = _events.tryEmit(event)

    companion object {
        const val REPLAY: Int = 1
        const val EXTRA_BUFFER: Int = 64
    }
}
