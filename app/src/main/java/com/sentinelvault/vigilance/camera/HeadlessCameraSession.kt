package com.sentinelvault.vigilance.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sentinelvault.security.MemorySanitizer
import com.sentinelvault.vigilance.orientation.OrientationTracker
import com.sentinelvault.vigilance.orientation.toSurfaceRotation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Headless CameraX session owned by [com.sentinelvault.service.SentinelVigilanceService] (Task
 * 9.3). Binds an `ImageAnalysis` use-case (and ONLY that) to the service's `LifecycleOwner`,
 * then exposes a [next] coroutine the verification engine can poll for fresh frames.
 *
 *  * No `Preview` use-case → no `SurfaceProvider`, which is what makes the session "headless"
 *    and avoids the Android 14 strict-mode crash about FGS-camera without a visible window.
 *  * `STRATEGY_KEEP_ONLY_LATEST` + a `Channel.CONFLATED` together guarantee the consumer only
 *    ever sees the most recent frame — the verification path is much slower than the camera
 *    pipeline (~10 fps), so back-pressure must be a single-slot replace.
 *  * The bitmap's "up" is always the user's "up", not the sensor's native orientation.
 *    [OrientationTracker] feeds `ImageAnalysis.targetRotation` changes after the bind.
 *  * Bitmaps are allocated fresh per frame (CameraX `imageProxy.toBitmap()` returns an
 *    owned `ARGB_8888` bitmap when the analyzer is configured for RGBA output); the
 *    consumer is responsible for recycling them via [MemorySanitizer], matching the
 *    FrameVerifier contract from Epic 5.
 */
interface HeadlessCameraSession {
    fun bind(lifecycleOwner: LifecycleOwner)
    suspend fun next(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Bitmap?
    fun release()

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 1_500L
        const val TARGET_WIDTH: Int = 640
        const val TARGET_HEIGHT: Int = 480
    }
}

/**
 * Production [HeadlessCameraSession]. Lifted out of the interface so the unit tests in
 * `:app:testDebugUnitTest` can substitute a fake without dragging the CameraX initializer
 * (which crashes on the JVM android stub jar).
 */
@Singleton
class CameraXHeadlessCameraSession @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sanitizer: MemorySanitizer,
    private val orientationTracker: OrientationTracker,
    private val providerFactory: CameraProviderFactory = CameraProviderFactory.DEFAULT
) : HeadlessCameraSession {

    private val frames: Channel<Bitmap> = Channel(capacity = Channel.CONFLATED)

    @Volatile private var provider: ProcessCameraProvider? = null
    @Volatile private var bound: Boolean = false
    @Volatile private var analysis: ImageAnalysis? = null
    private val bindLock = Any()
    private var rotationJob: kotlinx.coroutines.Job? = null
    private val analyzerExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    override fun bind(lifecycleOwner: LifecycleOwner) {
        synchronized(bindLock) {
            if (bound) return
            val cameraProvider = providerFactory.get(context)
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                android.util.Size(
                                    HeadlessCameraSession.TARGET_WIDTH,
                                    HeadlessCameraSession.TARGET_HEIGHT
                                ),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                .build().apply {
                    targetRotation = orientationTracker.rotation.value.toSurfaceRotation()
                }
            analysis.setAnalyzer(analyzerExecutor) { proxy ->
                handleFrame(proxy)
            }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                analysis
            )
            provider = cameraProvider
            this.analysis = analysis
            rotationJob = lifecycleOwner.lifecycleScope.launch {
                orientationTracker.rotation.collectLatest { rotationDegrees ->
                    synchronized(bindLock) {
                        this@CameraXHeadlessCameraSession.analysis?.targetRotation =
                            rotationDegrees.toSurfaceRotation()
                    }
                }
            }
            bound = true
        }
    }

    override suspend fun next(timeoutMs: Long): Bitmap? =
        withTimeoutOrNull(timeoutMs) { frames.receive() }

    override fun release() {
        synchronized(bindLock) {
            rotationJob?.cancel(); rotationJob = null
            try { provider?.unbindAll() } catch (_: Throwable) { /* tolerated */ }
            provider = null
            analysis = null
            bound = false
            // Drain the channel so a stale frame cannot be served after release().
            while (true) {
                val drained = frames.tryReceive().getOrNull() ?: break
                sanitizer.recycle(drained)
            }
        }
    }

    private fun handleFrame(proxy: ImageProxy) {
        val bitmap: Bitmap? = try { proxy.toBitmap() } catch (_: Throwable) { null }
        sanitizer.close(proxy)
        if (bitmap == null) return
        // CONFLATED channel: trySend either accepts and replaces the previous slot, or fails
        // when the consumer was cancelled. In the failure case we recycle to keep the
        // memory-hygiene contract intact.
        val sent = frames.trySend(bitmap).isSuccess
        if (!sent) sanitizer.recycle(bitmap)
    }
}
