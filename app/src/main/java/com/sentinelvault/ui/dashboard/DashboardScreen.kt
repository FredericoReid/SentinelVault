package com.sentinelvault.ui.dashboard

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

const val DASHBOARD_TEST_TAG: String = "dashboard_root"

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize().testTag(DASHBOARD_TEST_TAG)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Vault", style = MaterialTheme.typography.headlineLarge)
            Text(text = "Timeline coming in Epic 7.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
