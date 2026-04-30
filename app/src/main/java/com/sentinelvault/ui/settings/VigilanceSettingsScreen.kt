package com.sentinelvault.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinelvault.R
import com.sentinelvault.ui.components.SecurityButton

const val VIGILANCE_SETTINGS_TEST_TAG: String = "vigilance_settings_root"
const val VIGILANCE_SETTINGS_SWITCH_TAG: String = "vigilance_settings_switch"
const val VIGILANCE_SETTINGS_DESK_LIFT_TAG: String = "vigilance_settings_desk_lift"
const val VIGILANCE_SETTINGS_LENIENT_TAG: String = "vigilance_settings_lenient"
const val VIGILANCE_SETTINGS_BACK_TAG: String = "vigilance_settings_back"

/**
 * Vigilance sub-screen (Task 9.7). Single switch that flips the persistent
 * `vigilance_enabled` flag and starts/stops [com.sentinelvault.service.SentinelVigilanceService]
 * in the same gesture (handled by [VigilanceSettingsViewModel]).
 */
@Composable
fun VigilanceSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VigilanceSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Surface(
        modifier = modifier.fillMaxSize().testTag(VIGILANCE_SETTINGS_TEST_TAG),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
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
                .systemBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(VIGILANCE_SETTINGS_BACK_TAG)
                ) { Text(text = stringResource(R.string.settings_back)) }
            }
            item {
                SettingsHero()
            }
            item {
                SectionHeader(
                    title = stringResource(R.string.settings_engine_section_title),
                    body = stringResource(R.string.settings_engine_section_body)
                )
            }
            item {
                SettingToggleCard(
                    title = stringResource(R.string.vigilance_settings_title),
                    body = stringResource(R.string.vigilance_settings_subtitle),
                    checked = uiState.enabled,
                    onCheckedChange = viewModel::setEnabled,
                    modifier = Modifier.testTag(VIGILANCE_SETTINGS_SWITCH_TAG)
                )
            }
            item {
                SectionHeader(
                    title = stringResource(R.string.settings_trigger_section_title),
                    body = stringResource(R.string.settings_trigger_section_body)
                )
            }
            item {
                SettingToggleCard(
                    title = stringResource(R.string.settings_desk_lift_title),
                    body = stringResource(R.string.settings_desk_lift_body),
                    checked = uiState.deskLiftEnabled,
                    onCheckedChange = viewModel::setDeskLiftEnabled,
                    modifier = Modifier.testTag(VIGILANCE_SETTINGS_DESK_LIFT_TAG)
                )
            }
            item {
                SectionHeader(
                    title = stringResource(R.string.settings_recognition_section_title),
                    body = stringResource(R.string.settings_recognition_section_body)
                )
            }
            item {
                SettingToggleCard(
                    title = stringResource(R.string.settings_lenient_title),
                    body = stringResource(R.string.settings_lenient_body),
                    checked = uiState.lenientFramingEnabled,
                    onCheckedChange = viewModel::setLenientFramingEnabled,
                    modifier = Modifier.testTag(VIGILANCE_SETTINGS_LENIENT_TAG)
                )
            }
            item {
                DiagnosticsCard()
            }
            item {
                Spacer(Modifier.height(8.dp))
                SecurityButton(
                    text = stringResource(R.string.settings_back_to_dashboard),
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SettingsHero() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_eyebrow),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
private fun SettingToggleCard(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 10.dp, bottomEnd = 24.dp, bottomStart = 10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}

@Composable
private fun DiagnosticsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 12.dp, bottomEnd = 26.dp, bottomStart = 12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_diagnostics_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.settings_diagnostics_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
