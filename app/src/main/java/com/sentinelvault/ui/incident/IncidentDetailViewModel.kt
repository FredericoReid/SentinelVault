package com.sentinelvault.ui.incident

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinelvault.data.db.dao.EventLogDao
import com.sentinelvault.data.db.entity.EventLogEntity
import com.sentinelvault.ui.dashboard.EventLogFormatter
import com.sentinelvault.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads a single [EventLogEntity] by id (taken from the nav argument) and surfaces it as an
 * [IncidentDetailUiState]. Performs a best-effort `File.exists()` probe so the UI can warn the
 * user when the FIFO retention policy has already evicted the artefact (`evidenceMissing = true`)
 * even though the row itself still carries a path string.
 */
@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    private val eventLogDao: EventLogDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val incidentId: Long = savedStateHandle.get<Long>(Routes.INCIDENT_ID_ARG) ?: 0L

    private val _state: MutableStateFlow<IncidentDetailUiState> =
        MutableStateFlow(IncidentDetailUiState(incidentId = incidentId))
    val state: StateFlow<IncidentDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun reload() {
        load()
    }

    private fun load() {
        if (incidentId <= 0L) {
            _state.value = IncidentDetailUiState(isLoading = false, notFound = true, incidentId = incidentId)
            return
        }
        _state.value = _state.value.copy(isLoading = true, notFound = false)
        viewModelScope.launch {
            val row = eventLogDao.findById(incidentId)
            if (row == null) {
                _state.value = IncidentDetailUiState(
                    isLoading = false,
                    notFound = true,
                    incidentId = incidentId
                )
                return@launch
            }
            _state.value = row.toUiState()
        }
    }

    private suspend fun EventLogEntity.toUiState(): IncidentDetailUiState {
        val path = evidencePath
        val missing = path != null && !pathExists(path)
        return IncidentDetailUiState(
            isLoading = false,
            notFound = false,
            incidentId = id,
            typeLabel = EventLogFormatter.typeLabel(type),
            timestampLabel = EventLogFormatter.timestampLabel(timestampMs),
            severity = severity,
            foregroundPackage = foregroundPackage,
            notes = notes,
            evidencePath = path,
            evidenceMissing = missing
        )
    }

    private suspend fun pathExists(path: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { File(path).exists() }.getOrDefault(false)
    }
}
