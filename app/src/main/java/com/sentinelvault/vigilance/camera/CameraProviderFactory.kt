package com.sentinelvault.vigilance.camera

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider

/**
 * Indirection over `ProcessCameraProvider.getInstance(context).get()` so [CameraXHeadlessCameraSession]
 * can be exercised in unit tests with a mocked provider. The default implementation blocks
 * on the `ListenableFuture` (CameraX initialisation is fast and we only run this once during
 * service startup).
 */
fun interface CameraProviderFactory {
    fun get(context: Context): ProcessCameraProvider

    companion object {
        val DEFAULT: CameraProviderFactory = CameraProviderFactory { ctx ->
            ProcessCameraProvider.getInstance(ctx).get()
        }
    }
}
