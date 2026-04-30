package com.sentinelvault.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sentinelvault.ui.components.SecurityButton
import kotlinx.coroutines.launch

const val ONBOARDING_TEST_TAG: String = "onboarding_root"
const val ONBOARDING_NEXT_TAG: String = "onboarding_next"
const val ONBOARDING_CTA_TAG: String = "onboarding_cta"

@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pages = remember { OnboardingPage.ordered }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableStateOf(0) }
    val statuses by remember(refreshTick) {
        derivedStateOf { collectStatuses(context) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshTick++ }
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshTick++ }
    val genericSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }

    LaunchedEffect(pagerState.currentPage) { refreshTick++ }

    Surface(modifier = modifier.fillMaxSize().testTag(ONBOARDING_TEST_TAG)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { index ->
                val page = pages[index]
                OnboardingPageContent(
                    page = page,
                    granted = isPageSatisfied(page, statuses),
                    extraBody = if (page == OnboardingPage.BatteryExemption)
                        OemBatteryInstructions.resolve(context) else null,
                    onCta = {
                        runCta(
                            page = page,
                            context = context,
                            requestCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                            requestNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationsLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                } else {
                                    refreshTick++
                                }
                            },
                            launchSettings = { intent -> genericSettingsLauncher.launch(intent) },
                            onFinished = onCompleted
                        )
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
            PageIndicator(total = pages.size, current = pagerState.currentPage)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
                    enabled = pagerState.currentPage > 0
                ) { Text("Back") }

                val isLast = pagerState.currentPage == pages.lastIndex
                SecurityButton(
                    text = if (isLast) "Finish" else "Next",
                    onClick = {
                        if (isLast) onCompleted()
                        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier.testTag(ONBOARDING_NEXT_TAG)
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    granted: Boolean,
    extraBody: String?,
    onCta: () -> Unit
) {
    val context = LocalContext.current
    val (title, body, ctaLabel) = when (page) {
        OnboardingPage.BatteryExemption -> Triple(
            context.getString(com.sentinelvault.R.string.onboarding_battery_title),
            context.getString(com.sentinelvault.R.string.onboarding_battery_body),
            context.getString(com.sentinelvault.R.string.onboarding_battery_cta)
        )
        OnboardingPage.Notifications -> Triple(
            context.getString(com.sentinelvault.R.string.onboarding_notifications_title),
            context.getString(com.sentinelvault.R.string.onboarding_notifications_body),
            context.getString(com.sentinelvault.R.string.onboarding_notifications_cta)
        )
        else -> Triple(page.title, page.body, page.ctaLabel)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (extraBody != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = extraBody,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
        if (ctaLabel.isNotEmpty()) {
            SecurityButton(
                text = if (granted) "Granted \u2713" else ctaLabel,
                onClick = onCta,
                enabled = !granted ||
                    page == OnboardingPage.RestrictedSettings ||
                    page == OnboardingPage.Done ||
                    page == OnboardingPage.BatteryExemption,
                modifier = Modifier.testTag(ONBOARDING_CTA_TAG)
            )
        }
    }
}

@Composable
private fun PageIndicator(total: Int, current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        repeat(total) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .size(if (active) 10.dp else 8.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                ) {}
            }
        }
    }
}
