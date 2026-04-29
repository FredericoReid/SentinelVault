package com.sentinelvault.triggers

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Test fixture that wraps `runTest(UnconfinedTestDispatcher)` and exposes a [collect] helper
 * which subscribes to [TriggerOrchestrator.events] from `backgroundScope`. The background
 * scope is cancelled automatically when the test body returns, so the never-completing
 * `SharedFlow` collector does not stall `runTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runBlockingCollect(block: TriggerCollectScope.() -> Unit) =
    runTest(UnconfinedTestDispatcher()) {
        val impl = TriggerCollectScopeImpl(this)
        impl.block()
    }

interface TriggerCollectScope {
    fun collect(orchestrator: TriggerOrchestrator): List<TriggerEvent>
}

@OptIn(ExperimentalCoroutinesApi::class)
private class TriggerCollectScopeImpl(private val scope: TestScope) : TriggerCollectScope {
    override fun collect(orchestrator: TriggerOrchestrator): List<TriggerEvent> {
        val received = mutableListOf<TriggerEvent>()
        scope.backgroundScope.launch {
            orchestrator.events.collect { received.add(it) }
        }
        return received
    }
}
