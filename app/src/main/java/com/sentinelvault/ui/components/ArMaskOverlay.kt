package com.sentinelvault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sentinelvault.ui.theme.AlertNeon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val AR_MASK_TEST_TAG: String = "ar_mask_overlay"
private val FACE_OK_COLOR: Color = Color(0xFF00E676) // Material Green A400 - high-contrast OK.

/**
 * Oval AR-style mask drawn over the camera preview (see guide.md §4 - mandatory composable).
 * Stroke color toggles between green (face well-framed) and the sentinel alert orange/red
 * when the [quality] flag is `false`.
 */
@Composable
fun ArMaskOverlay(
    quality: StateFlow<Boolean>,
    modifier: Modifier = Modifier
) {
    val ok by quality.collectAsState()
    ArMaskOverlay(ok = ok, modifier = modifier)
}

/** Stateless variant - useful for Compose previews and tests. */
@Composable
fun ArMaskOverlay(
    ok: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (ok) FACE_OK_COLOR else AlertNeon
    Canvas(modifier = modifier.testTag(AR_MASK_TEST_TAG)) {
        val ovalSize = Size(width = size.width * 0.7f, height = size.height * 0.55f)
        val topLeft = Offset(
            x = (size.width - ovalSize.width) / 2f,
            y = (size.height - ovalSize.height) / 2f
        )
        drawOval(
            color = color,
            topLeft = topLeft,
            size = ovalSize,
            style = Stroke(width = STROKE_PX)
        )
    }
}

private val STROKE_PX = 6.dp.value * 2.5f

/** Convenience flow used by previews / tests so we don't allocate a fresh StateFlow inline. */
fun rememberStaticQualityFlow(value: Boolean): StateFlow<Boolean> =
    MutableStateFlow(value)

/** Test-only helper kept here so the overlay file is fully self-contained. */
internal fun State<Boolean>.flatten(): Boolean = value
