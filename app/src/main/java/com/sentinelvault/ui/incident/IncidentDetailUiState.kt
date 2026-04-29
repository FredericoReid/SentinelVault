package com.sentinelvault.ui.incident

/**
 * Renderable state for the incident detail screen. The [evidencePath] is intentionally a
 * raw absolute path rather than a `Uri` because the artefact lives in the app's private
 * `files/evidence/` directory and must never be exposed via a `ContentProvider`
 * (guide.md §7 — "Zero-Internet, Zero-Sharing").
 */
data class IncidentDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val incidentId: Long = 0L,
    val typeLabel: String = "",
    val timestampLabel: String = "",
    val severity: Int = 0,
    val foregroundPackage: String? = null,
    val notes: String? = null,
    val evidencePath: String? = null,
    val evidenceMissing: Boolean = false
)
