package com.sentinelvault.ui.components

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

const val CAMERA_PREVIEW_TEST_TAG: String = "camera_preview"

/**
 * Compose wrapper around CameraX exposing a front-facing preview, an [ImageAnalysis] use case
 * (for the AR mask quality probe) and an [ImageCapture] use case (for enrolment frames).
 *
 *  - [onAnalyzerReady] is invoked once with a writable analyzer slot the caller can populate
 *    (kept here instead of being passed in to keep the preview Composable testable in isolation).
 *  - [onCaptureReady] hands back the configured [ImageCapture] so the screen can trigger a
 *    still capture from a button click.
 */
@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    analyzer: ImageAnalysis.Analyzer?,
    onCaptureReady: (ImageCapture) -> Unit,
    onError: (Throwable) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = modifier.testTag(CAMERA_PREVIEW_TEST_TAG),
        factory = { ctx ->
            PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        },
        update = { previewView ->
            bindCamera(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                analyzer = analyzer,
                executor = executor,
                onCaptureReady = onCaptureReady,
                onError = onError
            )
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }
}

private fun bindCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    analyzer: ImageAnalysis.Analyzer?,
    executor: ExecutorService,
    onCaptureReady: (ImageCapture) -> Unit,
    onError: (Throwable) -> Unit
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        try {
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply { if (analyzer != null) setAnalyzer(executor, analyzer) }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis,
                capture
            )
            onCaptureReady(capture)
        } catch (t: Throwable) {
            onError(t)
        }
    }, ContextCompat.getMainExecutor(context))
}
