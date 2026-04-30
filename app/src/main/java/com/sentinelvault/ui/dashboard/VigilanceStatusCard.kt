package com.sentinelvault.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sentinelvault.R

/**
 * Status card surfaced at the top of the dashboard (Task 9.7). Stateless and data-driven —
 * the dashboard owns the [VigilanceStatusViewModel] and feeds the latest [status] in. A
 * tap routes to the vigilance-settings sub-screen so DEGRADED / INACTIVE states have a
 * one-tap remediation path.
 *
 * The dot colour mirrors the EventCard severity legend so the dashboard reads consistently:
 *  * ACTIVE   → primary (blue) — the service is alive and unrestricted.
 *  * DEGRADED → error dimmed (amber-ish via 55 % alpha) — running but at risk of OEM kills.
 *  * INACTIVE → onSurface 30 % — switched off or heartbeat stale.
 */
const val DASHBOARD_VIGILANCE_STATUS_TAG: String = "dashboard_vigilance_status"

@Composable
fun VigilanceStatusCard(
    status: VigilanceServiceStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(
        when (status) {
            VigilanceServiceStatus.ACTIVE -> R.string.vigilance_status_active
            VigilanceServiceStatus.DEGRADED -> R.string.vigilance_status_degraded
            VigilanceServiceStatus.INACTIVE -> R.string.vigilance_status_inactive
        }
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(DASHBOARD_VIGILANCE_STATUS_TAG),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusDot(status = status)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "ESCUDO DE FUNDO",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Ajustar",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StatusDot(status: VigilanceServiceStatus) {
    val color: Color = when (status) {
        VigilanceServiceStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        VigilanceServiceStatus.DEGRADED -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
        VigilanceServiceStatus.INACTIVE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
    }
    Surface(modifier = Modifier.size(12.dp), shape = CircleShape, color = color) {}
}
