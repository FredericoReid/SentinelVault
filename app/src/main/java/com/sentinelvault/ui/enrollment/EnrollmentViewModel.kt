package com.sentinelvault.ui.enrollment

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinelvault.face.EnrollmentRepository
import com.sentinelvault.face.FrameAnalyzer
import com.sentinelvault.security.MemorySanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the [EnrollmentScreen]: reflects per-frame quality from the analyzer and persists the
 * captured face vector through [EnrollmentRepository]. The repository owns Dispatchers.Default
 * dispatch, so every method here is safe to call from the main thread.
 */
@HiltViewModel
class EnrollmentViewModel @Inject constructor(
    private val repository: EnrollmentRepository,
    private val sanitizer: MemorySanitizer,
    private val analysisDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow(EnrollmentUiState())
    val state: StateFlow<EnrollmentUiState> = _state.asStateFlow()

    /** Quality-only flow consumed by the [com.sentinelvault.ui.components.ArMaskOverlay]. */
    val quality: StateFlow<Boolean> = _state
        .map { it.hasFace }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _events = MutableStateFlow<EnrollmentEvent?>(null)
    val events: StateFlow<EnrollmentEvent?> = _events.asStateFlow()

    /** Updated from the CameraX analyzer; flips the AR mask colour. */
    fun onFrameQuality(hasFace: Boolean) {
        _state.update { if (it.hasFace == hasFace) it else it.copy(hasFace = hasFace) }
    }

    /**
     * Per-frame quality probe. Runs detection on [analysisDispatcher] and recycles the
     * bitmap right after, honouring the Memory Hygiene clause of guide.md §3.
     */
    fun processFrame(bitmap: Bitmap) {
        viewModelScope.launch {
            val ok = withContext(analysisDispatcher) {
                try { repository.isFaceWellFramed(bitmap) } catch (_: Throwable) { false }
            }
            sanitizer.recycle(bitmap)
            onFrameQuality(ok)
        }
    }

    /** Builds the CameraX analyzer that feeds [processFrame]. Owned by the screen lifecycle. */
    fun buildAnalyzer(): ImageAnalysis.Analyzer = FrameAnalyzer(
        sanitizer = sanitizer,
        onFrame = { bitmap, _ -> processFrame(bitmap) }
    )

    /**
     * Captures the supplied frame, runs detection + embedding and persists the owner vector.
     * The bitmap ownership is transferred to the repository (always recycled).
     */
    fun capture(bitmap: Bitmap) {
        if (_state.value.status == EnrollmentStatus.Capturing) {
            // Another capture is in flight; drop the bitmap to avoid leaking the buffer.
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        _state.update { it.copy(status = EnrollmentStatus.Capturing, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.enroll(bitmap)
            applyResult(result)
        }
    }

    fun consumeEvent() { _events.value = null }

    fun resetError() {
        _state.update {
            if (it.status == EnrollmentStatus.NoFace ||
                it.status == EnrollmentStatus.Error ||
                it.status == EnrollmentStatus.EmbedderUnavailable
            ) it.copy(status = EnrollmentStatus.Idle, errorMessage = null) else it
        }
    }

    private fun applyResult(result: EnrollmentRepository.Result) {
        when (result) {
            EnrollmentRepository.Result.Success -> {
                _state.update { it.copy(status = EnrollmentStatus.Saved, errorMessage = null) }
                _events.value = EnrollmentEvent.EnrollmentCompleted
            }
            EnrollmentRepository.Result.NoFaceDetected -> _state.update {
                it.copy(status = EnrollmentStatus.NoFace, errorMessage = null)
            }
            is EnrollmentRepository.Result.EmbedderUnavailable -> _state.update {
                it.copy(
                    status = EnrollmentStatus.EmbedderUnavailable,
                    errorMessage = result.reason
                )
            }
            is EnrollmentRepository.Result.Failure -> _state.update {
                it.copy(
                    status = EnrollmentStatus.Error,
                    errorMessage = result.cause.message ?: "unknown failure"
                )
            }
        }
    }
}
