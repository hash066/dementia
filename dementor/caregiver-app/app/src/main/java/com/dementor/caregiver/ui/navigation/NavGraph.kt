package com.dementor.caregiver.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dementor.caregiver.ui.HubViewModel
import com.dementor.caregiver.ui.screens.*

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object Timeline : Screen("timeline")
    object Chat : Screen("chat")
    object Medical : Screen("medical")
    object Settings : Screen("settings")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    hubViewModel: HubViewModel,
    startDestination: String,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                hubViewModel = hubViewModel,
                onAuthenticated = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                hubViewModel = hubViewModel,
                onNavigateToChat = { navController.navigate(Screen.Chat.route) },
                onNavigateToTimeline = { navController.navigate(Screen.Timeline.route) },
                onNavigateToMedical = { navController.navigate(Screen.Medical.route) },
            )
        }
        composable(Screen.Timeline.route) {
            TimelineScreen(hubViewModel = hubViewModel)
        }
        composable(Screen.Chat.route) {
            ChatScreen(hubViewModel = hubViewModel)
        }
        composable(Screen.Medical.route) {
            MedicalDashboardScreen(hubViewModel = hubViewModel)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                hubViewModel = hubViewModel,
                onForgotHub = {
                    hubViewModel.disconnect()
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(navController.graph.id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
