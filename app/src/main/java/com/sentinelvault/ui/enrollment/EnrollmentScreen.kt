package com.sentinelvault.ui.enrollment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinelvault.R
import com.sentinelvault.face.toRotatedArgbBitmap
import com.sentinelvault.ui.components.ArMaskOverlay
import com.sentinelvault.ui.components.CameraPreviewView
import com.sentinelvault.ui.components.SecurityButton
import com.sentinelvault.ui.theme.AlertNeon
import com.sentinelvault.ui.theme.DeepBlack

const val ENROLLMENT_TEST_TAG: String = "enrollment_root"
const val ENROLLMENT_CAPTURE_TAG: String = "enrollment_capture"
const val ENROLLMENT_STATUS_TAG: String = "enrollment_status"

/**
 * Camera-driven owner enrollment (guide.md §8 - Epic 3). Lays out the AR-masked preview, a
 * capture trigger and a status line. The screen is pure UI: every TFLite invocation, frame
 * sanitisation and persistence step belongs to [EnrollmentViewModel].
 */
@Composable
fun EnrollmentScreen(
    modifier: Modifier = Modifier,
    onCompleted: () -> Unit = {},
    viewModel: EnrollmentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val hasCameraPermission = rememberCameraPermission(context)

    LaunchedEffect(event) {
        if (event == EnrollmentEvent.EnrollmentCompleted) {
            viewModel.consumeEvent(); onCompleted()
        }
    }

    Surface(
        color = DeepBlack,
        modifier = modifier.fillMaxSize().testTag(ENROLLMENT_TEST_TAG)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = enrollmentHeadline(state),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission.value) CameraStage(state = state, viewModel = viewModel, context = context)
                else PermissionPlaceholder()
            }
            StatusLine(state = state)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun enrollmentHeadline(state: EnrollmentUiState): String = when {
    state.status == EnrollmentStatus.Capturing -> "Saving your face\u2026"
    state.status == EnrollmentStatus.Saved -> "Face saved"
    state.hasFace -> "Looks good \u2014 tap Save Face to enroll"
    else -> stringResource(R.string.enrollment_title)
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
        text = "Camera permission is required to enroll your face.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CameraStage(state: EnrollmentUiState, viewModel: EnrollmentViewModel, context: Context) {
    val analyzer = remember(viewModel) { viewModel.buildAnalyzer() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }
    val onCaptureReady = remember { { capture: ImageCapture -> imageCapture = capture } }
    val onCameraError = remember { { t: Throwable -> captureError = t.message } }
    val canCapture = imageCapture != null &&
        state.hasFace &&
        state.status != EnrollmentStatus.Capturing &&
        state.status != EnrollmentStatus.Saved

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            CameraPreviewView(
                modifier = Modifier.fillMaxSize(),
                analyzer = analyzer,
                onCaptureReady = onCaptureReady,
                onError = onCameraError
            )
            ArMaskOverlay(quality = viewModel.quality, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(16.dp))
        SecurityButton(
            text = if (state.status == EnrollmentStatus.Capturing) "Saving\u2026" else "Save Face",
            enabled = canCapture,
            onClick = {
                captureError = null
                imageCapture?.takePicture(
                    ContextCompat.getMainExecutor(context),
                    captureCallback(viewModel) { captureError = it }
                )
            },
            modifier = Modifier.fillMaxWidth().testTag(ENROLLMENT_CAPTURE_TAG)
        )
        captureError?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = it, color = AlertNeon, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun captureCallback(
    viewModel: EnrollmentViewModel,
    onError: (String) -> Unit
): ImageCapture.OnImageCapturedCallback = object : ImageCapture.OnImageCapturedCallback() {
    override fun onCaptureSuccess(image: ImageProxy) {
        val bitmap = runCatching { image.toRotatedArgbBitmap() }.getOrNull()
        try { image.close() } catch (_: Throwable) {}
        if (bitmap != null) viewModel.capture(bitmap)
    }
    override fun onError(exception: ImageCaptureException) {
        onError(exception.message ?: "capture failed")
    }
}

@Composable
private fun StatusLine(state: EnrollmentUiState) {
    val labelAndColor: Pair<String, Color>? = when (state.status) {
        EnrollmentStatus.Idle -> null
        EnrollmentStatus.Capturing -> "Processing\u2026" to MaterialTheme.colorScheme.primary
        EnrollmentStatus.Saved -> "Enrollment saved" to Color(0xFF00E676)
        EnrollmentStatus.NoFace -> "No face detected, try again" to AlertNeon
        EnrollmentStatus.EmbedderUnavailable -> (state.errorMessage ?: "Model unavailable") to AlertNeon
        EnrollmentStatus.Error -> (state.errorMessage ?: "Unknown error") to AlertNeon
    }
    Text(
        text = labelAndColor?.first.orEmpty(),
        color = labelAndColor?.second ?: Color.Transparent,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().testTag(ENROLLMENT_STATUS_TAG),
        textAlign = TextAlign.Center
    )
}
