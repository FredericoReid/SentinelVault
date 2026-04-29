package com.sentinelvault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val LAYOUT: List<List<PinPadKey>> = listOf(
    listOf(PinPadKey.Digit('1'), PinPadKey.Digit('2'), PinPadKey.Digit('3')),
    listOf(PinPadKey.Digit('4'), PinPadKey.Digit('5'), PinPadKey.Digit('6')),
    listOf(PinPadKey.Digit('7'), PinPadKey.Digit('8'), PinPadKey.Digit('9')),
    listOf(PinPadKey.Empty, PinPadKey.Digit('0'), PinPadKey.Backspace)
)

sealed interface PinPadKey {
    data class Digit(val value: Char) : PinPadKey
    object Backspace : PinPadKey
    object Empty : PinPadKey
}

const val PIN_PAD_TEST_TAG: String = "pin_pad"
const val PIN_PAD_BACKSPACE_TAG: String = "pin_pad_backspace"

fun pinPadDigitTag(digit: Char): String = "pin_pad_digit_$digit"

@Composable
fun PinPadView(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth().testTag(PIN_PAD_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LAYOUT.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    when (key) {
                        is PinPadKey.Digit -> DigitKey(
                            value = key.value,
                            enabled = enabled,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDigit(key.value)
                            }
                        )
                        PinPadKey.Backspace -> BackspaceKey(
                            enabled = enabled,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBackspace()
                            }
                        )
                        PinPadKey.Empty -> Spacer(modifier = Modifier.size(KEY_SIZE))
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitKey(value: Char, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(KEY_SIZE)
            .testTag(pinPadDigitTag(value))
            .semantics { contentDescription = "PIN digit $value" },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
        enabled = enabled
    ) {
        Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BackspaceKey(enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(KEY_SIZE)
            .testTag(PIN_PAD_BACKSPACE_TAG)
            .semantics { contentDescription = "Backspace" }
    ) {
        Text(text = "\u232B", style = MaterialTheme.typography.headlineLarge)
    }
}

@Composable
fun PinDots(filled: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val active = index < filled
            Surface(
                modifier = Modifier.size(if (active) 14.dp else 12.dp),
                shape = CircleShape,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            ) {}
        }
    }
}

private val KEY_SIZE = 72.dp
