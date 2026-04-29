package com.sentinelvault.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinelvault.ui.components.EventCard

const val DASHBOARD_TEST_TAG: String = "dashboard_root"
const val DASHBOARD_LIST_TAG: String = "dashboard_timeline"
const val DASHBOARD_EMPTY_TAG: String = "dashboard_empty_state"
const val DASHBOARD_LOADING_TAG: String = "dashboard_loading"
const val DASHBOARD_ERROR_TAG: String = "dashboard_error"

@Composable
fun DashboardScreen(
    onIncidentClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardContent(state = state, onIncidentClick = onIncidentClick, modifier = modifier)
}

@Composable
internal fun DashboardContent(
    state: DashboardUiState,
    onIncidentClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize().testTag(DASHBOARD_TEST_TAG)) {
        when {
            state.isLoading -> LoadingState()
            state.errorMessage != null -> ErrorState(state.errorMessage)
            state.isEmpty -> EmptyState()
            else -> TimelineList(rows = state.rows, onIncidentClick = onIncidentClick)
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize().testTag(DASHBOARD_LOADING_TAG),
        contentAlignment = Alignment.Center
    ) { CircularProgressIndicator() }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(DASHBOARD_EMPTY_TAG),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Vault", style = MaterialTheme.typography.headlineLarge)
        Text(text = "No incidents recorded.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(DASHBOARD_ERROR_TAG),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Vault unavailable", style = MaterialTheme.typography.headlineLarge)
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TimelineList(
    rows: List<TimelineRowUi>,
    onIncidentClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(DASHBOARD_LIST_TAG),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = rows, key = { it.id }) { row ->
            EventCard(
                eventId = row.id,
                typeLabel = row.typeLabel,
                timestampLabel = row.timestampLabel,
                severity = row.severity,
                foregroundPackage = row.foregroundPackage,
                notes = row.notes,
                onClick = { onIncidentClick(row.id) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
