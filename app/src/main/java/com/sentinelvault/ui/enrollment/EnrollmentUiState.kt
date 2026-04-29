package com.sentinelvault.ui.enrollment

/**
 * Discrete states walked by [EnrollmentViewModel] across one capture session. The ordering is
 * Idle -> Capturing -> (Saved | Error subtypes); the UI is purely a function of this enum.
 */
enum class EnrollmentStatus { Idle, Capturing, Saved, NoFace, EmbedderUnavailable, Error }

/**
 * Snapshot of everything the enrollment screen needs to render. Frame quality (`hasFace`) is
 * driven by the per-frame analyzer and decoupled from [status] so the AR mask keeps reacting
 * even while a capture is being persisted.
 */
data class EnrollmentUiState(
    val status: EnrollmentStatus = EnrollmentStatus.Idle,
    val hasFace: Boolean = false,
    val errorMessage: String? = null
)

sealed interface EnrollmentEvent {
    object EnrollmentCompleted : EnrollmentEvent
}
