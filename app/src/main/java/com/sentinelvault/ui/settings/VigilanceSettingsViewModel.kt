package com.sentinelvault.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.sentinelvault.service.VigilanceServiceLauncher
import com.sentinelvault.service.VigilanceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs the on/off switch on the vigilance settings sub-screen (Task 9.7). The persisted
 * truth is in [VigilanceSettings]; the view-model only mirrors it into a [StateFlow] so the
 * Composable can recompose on toggle, and routes the on/off transition through
 * [VigilanceServiceLauncher] so the foreground service starts/stops in the same gesture.
 */
@HiltViewModel
class VigilanceSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: VigilanceSettings,
    private val launcher: VigilanceServiceLauncher
) : ViewModel() {

    private val _uiState: MutableStateFlow<VigilanceSettingsUiState> =
        MutableStateFlow(settings.readUiState())
    val uiState: StateFlow<VigilanceSettingsUiState> = _uiState.asStateFlow()

    fun setEnabled(value: Boolean) {
        settings.setVigilanceEnabled(value)
        if (value) launcher.ensureRunning(context) else launcher.stop(context)
        refresh()
    }

    fun setDeskLiftEnabled(value: Boolean) {
        settings.setDeskLiftTriggerEnabled(value)
        refresh()
    }

    fun setLenientFramingEnabled(value: Boolean) {
        settings.setLenientFramingEnabled(value)
        refresh()
    }

    private fun refresh() {
        _uiState.value = settings.readUiState()
    }
}

data class VigilanceSettingsUiState(
    val enabled: Boolean,
    val deskLiftEnabled: Boolean,
    val lenientFramingEnabled: Boolean
)

private fun VigilanceSettings.readUiState(): VigilanceSettingsUiState = VigilanceSettingsUiState(
    enabled = isVigilanceEnabled(),
    deskLiftEnabled = isDeskLiftTriggerEnabled(),
    lenientFramingEnabled = isLenientFramingEnabled()
)
