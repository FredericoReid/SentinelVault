package com.sentinelvault.ui.dashboard

/**
 * Renderable view of an `event_log` row for the dashboard timeline. Pre-formatted strings
 * keep the composables free of `Locale` / `DateFormat` plumbing and make assertions in the
 * unit tests trivial.
 */
data class TimelineRowUi(
    val id: Long,
    val typeLabel: String,
    val timestampLabel: String,
    val severity: Int,
    val foregroundPackage: String?,
    val notes: String?
)

/** Top-level dashboard state, exposed as a [kotlinx.coroutines.flow.StateFlow]. */
data class DashboardUiState(
    val rows: List<TimelineRowUi> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && rows.isEmpty() && errorMessage == null
}
