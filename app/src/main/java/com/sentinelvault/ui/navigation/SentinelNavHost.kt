package com.sentinelvault.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sentinelvault.ui.dashboard.DashboardScreen
import com.sentinelvault.ui.enrollment.EnrollmentScreen
import com.sentinelvault.ui.gatekeeper.GatekeeperScreen
import com.sentinelvault.ui.incident.IncidentDetailScreen
import com.sentinelvault.ui.intrudertest.IntruderTestScreen
import com.sentinelvault.ui.onboarding.OnboardingScreen
import com.sentinelvault.ui.settings.VigilanceSettingsScreen

@Composable
fun SentinelNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.GATEKEEPER
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.GATEKEEPER) {
            GatekeeperScreen(
                onAuthenticated = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.GATEKEEPER) { inclusive = true }
                    }
                },
                onPinCreated = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.GATEKEEPER) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onCompleted = {
                    navController.navigate(Routes.ENROLLMENT) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ENROLLMENT) {
            EnrollmentScreen(
                onCompleted = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ENROLLMENT) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onIncidentClick = { incidentId ->
                    navController.navigate(Routes.incidentDetail(incidentId))
                },
                onLaunchIntruderTest = { navController.navigate(Routes.INTRUDER_TEST) },
                onOpenVigilanceSettings = { navController.navigate(Routes.VIGILANCE_SETTINGS) }
            )
        }
        composable(
            route = Routes.INCIDENT_DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.INCIDENT_ID_ARG) { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.INCIDENT_ID_ARG) ?: 0L
            IncidentDetailScreen(incidentId = id)
        }
        composable(Routes.INTRUDER_TEST) {
            IntruderTestScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.VIGILANCE_SETTINGS) {
            VigilanceSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
