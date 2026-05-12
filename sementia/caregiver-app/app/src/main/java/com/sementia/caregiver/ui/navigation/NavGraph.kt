package com.sementia.caregiver.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sementia.caregiver.ui.screens.*

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object Timeline : Screen("timeline")
    object Chat : Screen("chat")
    object Medical : Screen("medical")
    object Settings : Screen("settings")
}

@Composable
fun NavGraph(navController: NavHostController, startDestination: String = Screen.Home.route) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(onAuthenticated = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Auth.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToChat = { navController.navigate(Screen.Chat.route) },
                onNavigateToTimeline = { navController.navigate(Screen.Timeline.route) },
                onNavigateToMedical = { navController.navigate(Screen.Medical.route) }
            )
        }
        composable(Screen.Timeline.route) {
            TimelineScreen()
        }
        composable(Screen.Chat.route) {
            ChatScreen()
        }
        composable(Screen.Medical.route) {
            MedicalDashboardScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
