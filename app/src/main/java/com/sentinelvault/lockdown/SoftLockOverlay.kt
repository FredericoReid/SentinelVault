package com.sentinelvault.lockdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sentinelvault.ui.theme.AlertNeon
import com.sentinelvault.ui.theme.DeepBlack
import com.sentinelvault.ui.theme.PureWhite
import com.sentinelvault.ui.theme.SentinelTheme

/**
 * Visual surface of the Epic 6 soft-lock. Pure stateless Composable so the overlay
 * controller can re-attach it deterministically and the preview tooling can render it.
 *
 * The colour palette is intentionally locked to the Sentinel design tokens (guide.md §4)
 * rather than `MaterialTheme.colorScheme` because the overlay is rendered inside a
 * `WindowManager` host that does NOT inherit the activity theme.
 */
@Composable
fun SoftLockOverlay(modifier: Modifier = Modifier) {
    SentinelTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DeepBlack),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "DEVICE LOCKED",
                    color = AlertNeon,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "SentinelVault detected an unrecognised user.",
                    color = PureWhite,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Return to the owner to unlock.",
                    color = PureWhite,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
