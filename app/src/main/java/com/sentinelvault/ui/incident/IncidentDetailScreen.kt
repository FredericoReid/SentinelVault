package com.sentinelvault.ui.incident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val INCIDENT_DETAIL_TEST_TAG: String = "incident_detail_root"

@Composable
fun IncidentDetailScreen(incidentId: Long, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize().testTag(INCIDENT_DETAIL_TEST_TAG)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Incident #$incidentId", style = MaterialTheme.typography.headlineLarge)
            Text(text = "Detail view ships with Epic 7.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
