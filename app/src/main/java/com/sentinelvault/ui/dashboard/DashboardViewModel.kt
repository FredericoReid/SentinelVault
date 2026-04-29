package com.sentinelvault.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinelvault.data.db.dao.EventLogDao
import com.sentinelvault.data.db.entity.EventLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Streams the encrypted incident timeline into [DashboardUiState]. Built on top of the
 * [EventLogDao] `Flow` so the SQLCipher database remains the single source of truth — the
 * view-model never caches a snapshot of its own.
 *
 * The mapping ([EventLogEntity] → [TimelineRowUi]) flows through [EventLogFormatter] so
 * unit tests can validate the type-code → label table without spinning up Compose.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val eventLogDao: EventLogDao
) : ViewModel() {

    private val _state: MutableStateFlow<DashboardUiState> = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            eventLogDao.getIncidentTimeline()
                .map { rows -> rows.map(EventLogFormatter::toUi) }
                .catch { throwable ->
                    _state.value = DashboardUiState(
                        rows = emptyList(),
                        isLoading = false,
                        errorMessage = throwable.message ?: throwable.javaClass.simpleName
                    )
                }
                .collect { uiRows ->
                    _state.value = DashboardUiState(
                        rows = uiRows,
                        isLoading = false,
                        errorMessage = null
                    )
                }
        }
    }
}
