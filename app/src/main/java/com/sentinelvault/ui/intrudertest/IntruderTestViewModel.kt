package com.sentinelvault.ui.intrudertest

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinelvault.face.EnrollmentRepository
import com.sentinelvault.face.FrameAnalyzer
import com.sentinelvault.security.MemorySanitizer
import com.sentinelvault.vigilance.FrameVerifier
import com.sentinelvault.vigilance.VerificationOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Operator-driven "intruder test" (guide.md §8 - test harness). Two consecutive
 * verification *windows* are run on the [FrameAnalyzer] stream so the verdict is the
 * majority over multiple frames instead of a single-shot decision — slow enough that the
 * operator and the subject have time to settle in front of the camera, robust enough that
 * a single noisy frame cannot fake a pass or a fail.
 *
 * Per-pulse pipeline:
 *  1. Phase flips into `CapturingPulseN` and a window timer is launched ([WINDOW_MS]).
 *  2. Every analyzer frame that arrives during the window is offered to [FrameVerifier].
 *     A [verifyMutex] serialises the calls so the embedder only runs once at a time;
 *     frames that arrive while a verification is in flight are dropped (and recycled).
 *  3. When the window expires, [IntruderTestAggregator.reduce] produces a single
 *     [PulseResult] from the collected samples.
 *  4. A short [PULSE_COOLDOWN_MS] gap separates the two pulses so the camera buffer
 *     rotates and the operator can micro-adjust the framing.
 */
@HiltViewModel
class IntruderTestViewModel @Inject constructor(
    private val frameVerifier: FrameVerifier,
    private val repository: EnrollmentRepository,
    private val sanitizer: MemorySanitizer,
    private val analysisDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow(IntruderTestUiState())
    val state: StateFlow<IntruderTestUiState> = _state.asStateFlow()

    /** AR-mask quality flow (mirrors the enrollment screen overlay). */
    val quality: StateFlow<Boolean> = _state
        .map { it.hasFace }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val phaseMutex = Mutex()
    private val verifyMutex = Mutex()
    private val samples: MutableList<VerificationOutcome> = mutableListOf()
    private var sessionJob: Job? = null

    /** Builds the CameraX analyzer that feeds both quality probing and verification. */
    fun buildAnalyzer(): ImageAnalysis.Analyzer = FrameAnalyzer(
        sanitizer = sanitizer,
        onFrame = { bitmap, _ -> processFrame(bitmap) }
    )

    /**
     * Routes one analyzer frame. While idle / between pulses we only run the cheap
     * `isFaceWellFramed` probe to keep the AR mask reactive; while a window is open we
     * also push the frame through [FrameVerifier].
     */
    private fun processFrame(bitmap: Bitmap) {
        val phase = _state.value.phase
        val capturing = phase == IntruderTestPhase.CapturingPulse1 ||
            phase == IntruderTestPhase.CapturingPulse2

        if (!capturing) {
            // Lightweight path - probe quality, recycle, done.
            viewModelScope.launch {
                val ok = withContext(analysisDispatcher) {
                    try { repository.isFaceWellFramed(bitmap) } catch (_: Throwable) { false }
                }
                sanitizer.recycle(bitmap)
                _state.update { if (it.hasFace == ok) it else it.copy(hasFace = ok) }
            }
            return
        }

        // Verification path - serialised through verifyMutex; drop if busy so the next
        // frame can take a turn instead of queuing up stale bitmaps.
        if (!verifyMutex.tryLock()) { sanitizer.recycle(bitmap); return }
        viewModelScope.launch {
            try {
                val outcome = runCatching { frameVerifier.verify(bitmap) }
                    .getOrElse { VerificationOutcome.Failure(it, System.currentTimeMillis()) }
                synchronized(samples) { samples.add(outcome) }
                _state.update { it.copy(hasFace = outcome !is VerificationOutcome.NoFace) }
            } finally {
                verifyMutex.unlock()
            }
        }
    }

    /** Begins a fresh double-verification session. Idempotent while a session is running. */
    fun requestStart() {
        viewModelScope.launch {
            phaseMutex.withLock {
                if (_state.value.isRunning) return@withLock
                _state.value = IntruderTestUiState(
                    phase = IntruderTestPhase.CapturingPulse1,
                    hasFace = _state.value.hasFace
                )
                synchronized(samples) { samples.clear() }
                sessionJob?.cancel()
                sessionJob = launch { runSession() }
            }
        }
    }

    /** Drops state back to [IntruderTestPhase.Idle] so the operator can run another test. */
    fun reset() {
        viewModelScope.launch {
            sessionJob?.cancel(); sessionJob = null
            phaseMutex.withLock {
                synchronized(samples) { samples.clear() }
                _state.value = IntruderTestUiState(hasFace = _state.value.hasFace)
            }
        }
    }

    private suspend fun runSession() {
        // --- Pulse 1 -----------------------------------------------------------------
        delay(WINDOW_MS)
        val pulse1 = collectAndReducePulse()
        phaseMutex.withLock {
            _state.value = _state.value.copy(
                pulse1 = pulse1, phase = IntruderTestPhase.EvaluatingPulse1
            )
        }
        delay(PULSE_COOLDOWN_MS)
        phaseMutex.withLock {
            synchronized(samples) { samples.clear() }
            _state.value = _state.value.copy(phase = IntruderTestPhase.CapturingPulse2)
        }
        // --- Pulse 2 -----------------------------------------------------------------
        delay(WINDOW_MS)
        val pulse2 = collectAndReducePulse()
        phaseMutex.withLock {
            val verdict = combine(_state.value.pulse1, pulse2)
            _state.value = _state.value.copy(
                pulse2 = pulse2, phase = IntruderTestPhase.Done, verdict = verdict
            )
        }
    }

    private fun collectAndReducePulse(): PulseResult {
        val snapshot = synchronized(samples) { samples.toList() }
        val outcome = IntruderTestAggregator.reduce(snapshot, System.currentTimeMillis())
        return outcome.toPulseResult()
    }

    companion object {
        const val WINDOW_MS: Long = 2_500L
        const val PULSE_COOLDOWN_MS: Long = 700L
    }
}
