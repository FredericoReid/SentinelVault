package com.sentinelvault.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinelvault.R
import com.sentinelvault.ui.components.EventCard

const val DASHBOARD_TEST_TAG: String = "dashboard_root"
const val DASHBOARD_LIST_TAG: String = "dashboard_timeline"
const val DASHBOARD_EMPTY_TAG: String = "dashboard_empty_state"
const val DASHBOARD_LOADING_TAG: String = "dashboard_loading"
const val DASHBOARD_ERROR_TAG: String = "dashboard_error"
const val DASHBOARD_INTRUDER_TEST_TAG: String = "dashboard_intruder_test"

@Composable
fun DashboardScreen(
    onIncidentClick: (Long) -> Unit,
    onLaunchIntruderTest: () -> Unit,
    onOpenVigilanceSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    statusViewModel: VigilanceStatusViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val vigilanceStatus by statusViewModel.status.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        vigilanceStatus = vigilanceStatus,
        onIncidentClick = onIncidentClick,
        onLaunchIntruderTest = onLaunchIntruderTest,
        onOpenVigilanceSettings = onOpenVigilanceSettings,
        modifier = modifier
    )
}

@Composable
internal fun DashboardContent(
    state: DashboardUiState,
    vigilanceStatus: VigilanceServiceStatus,
    onIncidentClick: (Long) -> Unit,
    onLaunchIntruderTest: () -> Unit,
    onOpenVigilanceSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .systemBarsPadding()
                .testTag(DASHBOARD_TEST_TAG)
        ) {
            DashboardHero(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            VigilanceStatusCard(
                status = vigilanceStatus,
                onClick = onOpenVigilanceSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            ActionRow(
                onLaunchIntruderTest = onLaunchIntruderTest,
                onOpenVigilanceSettings = onOpenVigilanceSettings,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
            SectionHeader(
                title = stringResource(R.string.dashboard_timeline_title),
                body = stringResource(R.string.dashboard_timeline_subtitle),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.isLoading -> LoadingState()
                    state.errorMessage != null -> ErrorState(state.errorMessage)
                    state.isEmpty -> EmptyState()
                    else -> TimelineList(rows = state.rows, onIncidentClick = onIncidentClick)
                }
            }
        }
    }
}

@Composable
private fun DashboardHero(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.dashboard_eyebrow),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = stringResource(R.string.dashboard_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.dashboard_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionRow(
    onLaunchIntruderTest: () -> Unit,
    onOpenVigilanceSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ActionTile(
            title = stringResource(R.string.dashboard_settings_title),
            body = stringResource(R.string.dashboard_settings_subtitle),
            onClick = onOpenVigilanceSettings,
            modifier = Modifier.weight(1f)
        )
        ActionTile(
            title = stringResource(R.string.vigilance_self_test_label),
            body = stringResource(R.string.vigilance_self_test_subtitle),
            onClick = onLaunchIntruderTest,
            modifier = Modifier.weight(1f).testTag(DASHBOARD_INTRUDER_TEST_TAG)
        )
    }
}

@Composable
private fun ActionTile(
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 10.dp, bottomEnd = 22.dp, bottomStart = 10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(DASHBOARD_LOADING_TAG),
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
        Text(
            text = stringResource(R.string.dashboard_empty_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.dashboard_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(DASHBOARD_ERROR_TAG),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.dashboard_error_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimelineList(
    rows: List<TimelineRowUi>,
    onIncidentClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(DASHBOARD_LIST_TAG),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
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
