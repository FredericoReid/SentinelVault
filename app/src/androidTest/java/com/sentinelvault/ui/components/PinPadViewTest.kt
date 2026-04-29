package com.sentinelvault.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class PinPadViewTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun digit_and_backspace_clicks_invoke_callbacks() {
        val typed = StringBuilder()
        var backspaces = 0
        rule.setContent {
            PinPadView(
                onDigit = { typed.append(it) },
                onBackspace = { backspaces++ }
            )
        }

        listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0').forEach { d ->
            rule.onNodeWithTag(pinPadDigitTag(d)).performClick()
        }
        rule.onNodeWithTag(PIN_PAD_BACKSPACE_TAG).performClick()
        rule.onNodeWithTag(PIN_PAD_BACKSPACE_TAG).performClick()

        assertThat(typed.toString()).isEqualTo("1234567890")
        assertThat(backspaces).isEqualTo(2)
    }

    @Test
    fun disabled_state_blocks_callbacks() {
        var calls = 0
        rule.setContent {
            PinPadView(
                onDigit = { calls++ },
                onBackspace = { calls++ },
                enabled = false
            )
        }
        rule.onNodeWithTag(pinPadDigitTag('5')).performClick()
        rule.onNodeWithTag(PIN_PAD_BACKSPACE_TAG).performClick()
        assertThat(calls).isEqualTo(0)
    }
}
