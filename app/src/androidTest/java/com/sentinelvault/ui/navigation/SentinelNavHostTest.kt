package com.sentinelvault.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Validates the route wiring without booting the real (Hilt-backed) screens. The graph below
 * mirrors [SentinelNavHost] route-for-route but substitutes placeholder composables, so we can
 * assert that the [Routes] constants drive correct transitions.
 */
class SentinelNavHostTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun start_destination_is_gatekeeper() {
        rule.setContent { TestNavHost() }
        rule.onNodeWithTag(tagFor(Routes.GATEKEEPER)).assertIsDisplayed()
    }

    @Test
    fun navigate_to_onboarding_then_enrollment() {
        lateinit var controller: NavHostController
        rule.setContent {
            controller = rememberNavController()
            TestNavHost(navController = controller)
        }
        rule.runOnUiThread { controller.navigate(Routes.ONBOARDING) }
        rule.onNodeWithTag(tagFor(Routes.ONBOARDING)).assertIsDisplayed()

        rule.runOnUiThread { controller.navigate(Routes.ENROLLMENT) }
        rule.onNodeWithTag(tagFor(Routes.ENROLLMENT)).assertIsDisplayed()
    }

    @Test
    fun incident_detail_route_passes_id_argument() {
        lateinit var controller: NavHostController
        var captured: Long = -1L
        rule.setContent {
            controller = rememberNavController()
            NavHost(navController = controller, startDestination = Routes.GATEKEEPER) {
                composable(Routes.GATEKEEPER) { Stub(Routes.GATEKEEPER) }
                composable(
                    route = Routes.INCIDENT_DETAIL_PATTERN,
                    arguments = listOf(navArgument(Routes.INCIDENT_ID_ARG) { type = NavType.LongType })
                ) { entry ->
                    captured = entry.arguments?.getLong(Routes.INCIDENT_ID_ARG) ?: -1L
                    Stub(route = "incident")
                }
            }
        }
        rule.runOnUiThread { controller.navigate(Routes.incidentDetail(42L)) }
        rule.onNodeWithTag(tagFor("incident")).assertIsDisplayed()
        assertThat(captured).isEqualTo(42L)
    }
}

private fun tagFor(route: String): String = "stub_$route"

@Composable
private fun Stub(route: String) {
    Text(text = route, modifier = androidx.compose.ui.Modifier.testTag(tagFor(route)))
}

@Composable
private fun TestNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.GATEKEEPER) {
        composable(Routes.GATEKEEPER) { Stub(Routes.GATEKEEPER) }
        composable(Routes.ONBOARDING) { Stub(Routes.ONBOARDING) }
        composable(Routes.ENROLLMENT) { Stub(Routes.ENROLLMENT) }
        composable(Routes.DASHBOARD) { Stub(Routes.DASHBOARD) }
        composable(
            route = Routes.INCIDENT_DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.INCIDENT_ID_ARG) { type = NavType.LongType })
        ) { Stub("incident") }
    }
}
