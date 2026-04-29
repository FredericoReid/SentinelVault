package com.sentinelvault.triggers

/**
 * Single time source shared by every Epic 4 component. Abstracted so unit tests can pin the
 * clock and assert deterministic [TriggerEvent.timestampMs] values without reaching for
 * `mockkStatic(System::class)`.
 */
fun interface TriggerClock {
    fun nowMs(): Long

    companion object {
        val SYSTEM: TriggerClock = TriggerClock { System.currentTimeMillis() }
    }
}
