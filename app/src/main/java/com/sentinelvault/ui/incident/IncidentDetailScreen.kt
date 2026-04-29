package com.sentinelvault.ui.incident

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

const val INCIDENT_DETAIL_TEST_TAG: String = "incident_detail_root"
const val INCIDENT_EVIDENCE_IMAGE_TAG: String = "incident_evidence_image"
const val INCIDENT_EVIDENCE_MISSING_TAG: String = "incident_evidence_missing"
const val INCIDENT_LOADING_TAG: String = "incident_loading"
const val INCIDENT_NOT_FOUND_TAG: String = "incident_not_found"

@Composable
fun IncidentDetailScreen(
    incidentId: Long,
    modifier: Modifier = Modifier,
    viewModel: IncidentDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    IncidentDetailContent(state = state, fallbackId = incidentId, modifier = modifier)
}

@Composable
internal fun IncidentDetailContent(
    state: IncidentDetailUiState,
    fallbackId: Long,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize().testTag(INCIDENT_DETAIL_TEST_TAG)) {
        when {
            state.isLoading -> LoadingBlock()
            state.notFound -> NotFoundBlock(fallbackId)
            else -> DetailBlock(state)
        }
    }
}

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier.fillMaxSize().testTag(INCIDENT_LOADING_TAG),
        contentAlignment = Alignment.Center
    ) { CircularProgressIndicator() }
}

@Composable
private fun NotFoundBlock(id: Long) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(INCIDENT_NOT_FOUND_TAG),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Incident #$id", style = MaterialTheme.typography.headlineLarge)
        Text(text = "This incident is no longer in the vault.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailBlock(state: IncidentDetailUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = state.typeLabel, style = MaterialTheme.typography.headlineLarge)
        Text(text = state.timestampLabel, style = MaterialTheme.typography.bodyMedium)
        Text(text = "Severity: ${state.severity}", style = MaterialTheme.typography.bodyMedium)
        if (!state.foregroundPackage.isNullOrBlank()) {
            Text(text = "App: ${state.foregroundPackage}", style = MaterialTheme.typography.bodyMedium)
        }
        if (!state.notes.isNullOrBlank()) {
            Text(text = state.notes, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.size(8.dp))
        EvidenceBlock(path = state.evidencePath, missing = state.evidenceMissing)
    }
}

@Composable
private fun EvidenceBlock(path: String?, missing: Boolean) {
    when {
        path == null -> {
            Text(
                text = "No evidence captured for this incident.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        missing -> {
            Text(
                modifier = Modifier.fillMaxWidth().testTag(INCIDENT_EVIDENCE_MISSING_TAG),
                text = "Evidence file evicted by the 1.5 GB retention policy.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        else -> {
            val bitmap = remember(path) {
                runCatching { BitmapFactory.decodeFile(File(path).absolutePath) }.getOrNull()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Intruder evidence",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag(INCIDENT_EVIDENCE_IMAGE_TAG)
                )
            } else {
                Text(
                    modifier = Modifier.testTag(INCIDENT_EVIDENCE_MISSING_TAG),
                    text = "Evidence file could not be decoded.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
