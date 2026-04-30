package com.sentinelvault.ui.intrudertest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinelvault.ui.components.ArMaskOverlay
import com.sentinelvault.ui.components.CameraPreviewView
import com.sentinelvault.ui.components.SecurityButton
import com.sentinelvault.ui.theme.AlertNeon
import com.sentinelvault.ui.theme.DeepBlack

const val INTRUDER_TEST_TEST_TAG: String = "intruder_test_root"
const val INTRUDER_TEST_START_TAG: String = "intruder_test_start"
const val INTRUDER_TEST_VERDICT_TAG: String = "intruder_test_verdict"

private val OK_GREEN: Color = Color(0xFF00E676)
private val WARN_AMBER: Color = Color(0xFFFFB300)

/**
 * Operator-facing test harness (see guide.md §8 - test mode). The screen owns the
 * camera preview and AR mask; the actual sampling is driven entirely by the view-model
 * through the [androidx.camera.core.ImageAnalysis] stream — frame aggregation runs across
 * a multi-second window per pulse, so the screen has no take-picture button to wire.
 */
@Composable
fun IntruderTestScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IntruderTestViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val granted = rememberCameraPermission(context)

    Surface(
        color = DeepBlack,
        modifier = modifier.fillMaxSize().testTag(INTRUDER_TEST_TEST_TAG)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeaderText(state)
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (granted.value) CameraStage(viewModel = viewModel, state = state)
                else PermissionPlaceholder()
            }
            PulseCard(label = "Verification 1", pulse = state.pulse1)
            PulseCard(label = "Verification 2", pulse = state.pulse2)
            VerdictBanner(state = state)
            Controls(state = state, onStart = viewModel::requestStart, onReset = viewModel::reset, onBack = onBack)
        }
    }
}

@Composable
private fun rememberCameraPermission(context: Context): androidx.compose.runtime.MutableState<Boolean> {
    val granted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> granted.value = ok }
    LaunchedEffect(Unit) { if (!granted.value) launcher.launch(Manifest.permission.CAMERA) }
    return granted
}

@Composable
private fun PermissionPlaceholder() {
    Text(
        text = "Camera permission is required to run the intruder test.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun HeaderText(state: IntruderTestUiState) {
    val text = when (state.phase) {
        IntruderTestPhase.Idle ->
            "Ask a non-owner to look at the camera, then tap Start. Each check samples for ~2.5 s."
        IntruderTestPhase.CapturingPulse1 -> "Hold still — sampling verification 1…"
        IntruderTestPhase.EvaluatingPulse1 -> "Verification 1 done. Brief pause before verification 2…"
        IntruderTestPhase.CapturingPulse2 -> "Hold still — sampling verification 2…"
        IntruderTestPhase.EvaluatingPulse2 -> "Aggregating verification 2…"
        IntruderTestPhase.Done -> "Test complete."
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CameraStage(viewModel: IntruderTestViewModel, @Suppress("UNUSED_PARAMETER") state: IntruderTestUiState) {
    val analyzer = remember(viewModel) { viewModel.buildAnalyzer() }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val onReady = remember { { _: androidx.camera.core.ImageCapture -> } }
    val onError = remember { { t: Throwable -> cameraError = t.message } }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            CameraPreviewView(
                modifier = Modifier.fillMaxSize(),
                analyzer = analyzer,
                onCaptureReady = onReady,
                onError = onError
            )
            ArMaskOverlay(quality = viewModel.quality, modifier = Modifier.fillMaxSize())
        }
        cameraError?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = it, color = AlertNeon, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PulseCard(label: String, pulse: PulseResult?) {
    val text = pulse?.label ?: "—"
    val color = when (pulse?.outcome) {
        null -> MaterialTheme.colorScheme.onBackground
        is com.sentinelvault.vigilance.VerificationOutcome.Match -> OK_GREEN
        is com.sentinelvault.vigilance.VerificationOutcome.Mismatch,
        is com.sentinelvault.vigilance.VerificationOutcome.NotLive -> AlertNeon
        else -> WARN_AMBER
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

@Composable
private fun VerdictBanner(state: IntruderTestUiState) {
    val verdict = state.verdict ?: return
    val (text, color) = when (verdict) {
        TestVerdict.IntruderConfirmed -> "Intruder confirmed — both checks rejected the face." to OK_GREEN
        TestVerdict.OwnerConfirmed -> "Owner confirmed — the face matched twice." to AlertNeon
        TestVerdict.Inconclusive -> "Inconclusive — the two checks disagreed. Try again." to WARN_AMBER
        TestVerdict.NoFace -> "No face was detected on either check." to WARN_AMBER
        TestVerdict.OwnerNotEnrolled -> "Owner is not enrolled yet — enroll first, then re-test." to AlertNeon
        TestVerdict.ModelUnavailable -> "Face model unavailable on this device." to AlertNeon
        TestVerdict.PipelineError -> "Pipeline error during the test." to AlertNeon
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().testTag(INTRUDER_TEST_VERDICT_TAG)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Controls(
    state: IntruderTestUiState,
    onStart: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    val isDone = state.phase == IntruderTestPhase.Done
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SecurityButton(
            text = when {
                state.isRunning -> "Running…"
                isDone -> "Run again"
                else -> "Start test"
            },
            enabled = !state.isRunning,
            onClick = if (isDone) onReset else onStart,
            modifier = Modifier.fillMaxWidth().testTag(INTRUDER_TEST_START_TAG)
        )
        SecurityButton(
            text = "Back to dashboard",
            enabled = !state.isRunning,
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
