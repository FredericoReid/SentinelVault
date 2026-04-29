package com.sentinelvault.ui.gatekeeper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sentinelvault.ui.components.PinDots
import com.sentinelvault.ui.components.PinPadView

const val GATEKEEPER_TEST_TAG: String = "gatekeeper_root"
const val GATEKEEPER_TITLE_TAG: String = "gatekeeper_title"
const val GATEKEEPER_ERROR_TAG: String = "gatekeeper_error"

@Composable
fun GatekeeperScreen(
    onAuthenticated: () -> Unit,
    onPinCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GatekeeperViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val event by viewModel.events.collectAsState()

    LaunchedEffect(event) {
        when (event) {
            GatekeeperEvent.Authenticated -> { viewModel.consumeEvent(); onAuthenticated() }
            GatekeeperEvent.PinCreated -> { viewModel.consumeEvent(); onPinCreated() }
            null -> Unit
        }
    }

    GatekeeperContent(
        state = state,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        modifier = modifier
    )
}

@Composable
internal fun GatekeeperContent(
    state: GatekeeperUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize().testTag(GATEKEEPER_TEST_TAG)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                modifier = Modifier.testTag(GATEKEEPER_TITLE_TAG),
                text = titleFor(state.mode),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitleFor(state.mode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(24.dp))
            PinDots(filled = state.filledDots, total = state.targetLength)
            Spacer(Modifier.height(24.dp))
            PinPadView(
                onDigit = onDigit,
                onBackspace = onBackspace,
                enabled = !state.isLocked
            )
            Spacer(Modifier.height(16.dp))
            ErrorOrLockoutLine(state)
        }
    }
}

@Composable
private fun ErrorOrLockoutLine(state: GatekeeperUiState) {
    val text = when {
        state.isLocked -> "Locked. Try again in ${(state.lockoutRemainingMs + 999) / 1000}s."
        state.errorMessage is ErrorReason.PinMismatch -> "PINs do not match. Start over."
        state.errorMessage is ErrorReason.WrongPin -> "Wrong PIN (attempt ${state.errorMessage.attempts})."
        else -> ""
    }
    if (text.isNotEmpty()) {
        Text(
            modifier = Modifier.testTag(GATEKEEPER_ERROR_TAG),
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

private fun titleFor(mode: GatekeeperMode): String = when (mode) {
    GatekeeperMode.CreatePinChoose -> "Choose PIN"
    GatekeeperMode.CreatePinConfirm -> "Confirm PIN"
    GatekeeperMode.Login -> "Enter PIN"
}

private fun subtitleFor(mode: GatekeeperMode): String = when (mode) {
    GatekeeperMode.CreatePinChoose -> "Six digits. This is the only way back into the vault."
    GatekeeperMode.CreatePinConfirm -> "Re-enter the PIN to confirm."
    GatekeeperMode.Login -> "Authenticate to open the vault."
}
