package com.sentinelvault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp

/**
 * Single timeline row for the [com.sentinelvault.ui.dashboard.DashboardScreen]. Stateless and
 * data-driven so the dashboard view-model can drop one in for every `EventLogEntity` without
 * re-creating composables on recomposition.
 *
 * Tap target: the entire surface. The caller wires [onClick] to navigate to
 * `route_incident_detail/{id}`.
 *
 * Severity → colour mapping mirrors the legend in [com.sentinelvault.lockdown.DefaultLockdownAction]:
 *  * `0` → muted grey (informational, e.g. self-heal resolved a false reject).
 *  * `1` → AlertNeon dimmed (soft-only lockdown, admin inactive).
 *  * `2`+ → AlertNeon bright (confirmed breach + hard lock).
 */
const val EVENT_CARD_TAG_PREFIX: String = "event_card_"

fun eventCardTag(eventId: Long): String = "$EVENT_CARD_TAG_PREFIX$eventId"

@Composable
fun EventCard(
    eventId: Long,
    typeLabel: String,
    timestampLabel: String,
    severity: Int,
    foregroundPackage: String?,
    notes: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(eventCardTag(eventId)),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 10.dp, bottomEnd = 22.dp, bottomStart = 10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SeverityDot(severity = severity)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = timestampLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!foregroundPackage.isNullOrBlank()) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = foregroundPackage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!notes.isNullOrBlank()) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SeverityDot(severity: Int) {
    val color: Color = when {
        severity >= 2 -> MaterialTheme.colorScheme.error
        severity == 1 -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
    }
    Surface(
        modifier = Modifier.size(12.dp),
        shape = CircleShape,
        color = color
    ) { Spacer(Modifier.width(0.dp)) }
}
