package com.sentinelvault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 * Corner-bracket framing aid drawn over the camera preview. The overlay is intentionally NOT a
 * hard gate: it reacts to a face anywhere in the frame and avoids implying that detection only
 * works inside a central oval.
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
    val pulse = rememberInfiniteTransition(label = "ar_mask_pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ar_mask_alpha"
    )
    Canvas(modifier = modifier.testTag(AR_MASK_TEST_TAG)) {
        val inset = 24.dp.toPx()
        val arm = minOf(size.width, size.height) * 0.14f
        val stroke = Stroke(width = 3.dp.toPx())
        val rendered = if (ok) color.copy(alpha = alpha) else color

        drawBracket(Offset(inset, inset), Offset(inset + arm, inset), Offset(inset, inset + arm), rendered, stroke)
        drawBracket(
            Offset(size.width - inset, inset),
            Offset(size.width - inset - arm, inset),
            Offset(size.width - inset, inset + arm),
            rendered,
            stroke
        )
        drawBracket(
            Offset(inset, size.height - inset),
            Offset(inset + arm, size.height - inset),
            Offset(inset, size.height - inset - arm),
            rendered,
            stroke
        )
        drawBracket(
            Offset(size.width - inset, size.height - inset),
            Offset(size.width - inset - arm, size.height - inset),
            Offset(size.width - inset, size.height - inset - arm),
            rendered,
            stroke
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBracket(
    corner: Offset,
    horizontalEnd: Offset,
    verticalEnd: Offset,
    color: Color,
    stroke: Stroke
) {
    drawLine(color = color, start = corner, end = horizontalEnd, strokeWidth = stroke.width)
    drawLine(color = color, start = corner, end = verticalEnd, strokeWidth = stroke.width)
}

/** Convenience flow used by previews / tests so we don't allocate a fresh StateFlow inline. */
fun rememberStaticQualityFlow(value: Boolean): StateFlow<Boolean> =
    MutableStateFlow(value)

/** Test-only helper kept here so the overlay file is fully self-contained. */
internal fun State<Boolean>.flatten(): Boolean = value
