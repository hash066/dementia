package com.dementor.caregiver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dementor.caregiver.ui.HubViewModel
import com.dementor.caregiver.ui.navigation.NavGraph
import com.dementor.caregiver.ui.navigation.Screen
import com.dementor.caregiver.ui.theme.DementorTheme

class MainActivity : ComponentActivity() {

    private val hubViewModel: HubViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DementorTheme {
                val navController = rememberNavController()
                val startDestination = remember {
                    if (hubViewModel.savedHubUrlExists()) Screen.Home.route else Screen.Auth.route
                }
                val items = listOf(
                    Screen.Home to Icons.Default.Home,
                    Screen.Timeline to Icons.Default.Timeline,
                    Screen.Chat to Icons.Default.Chat,
                    Screen.Medical to Icons.Default.MedicalServices,
                    Screen.Settings to Icons.Default.Settings
                )

                Scaffold(
                    bottomBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination

                        // Only show bottom bar if NOT on Auth screen
                        if (currentDestination?.route != Screen.Auth.route) {
                            NavigationBar {
                                items.forEach { (screen, icon) ->
                                    NavigationBarItem(
                                        icon = { Icon(icon, contentDescription = null) },
                                        label = { Text(screen.route.replaceFirstChar { it.uppercase() }) },
                                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        NavGraph(
                            navController = navController,
                            hubViewModel = hubViewModel,
                            startDestination = startDestination,
                        )
                    }
                }
            }
        }
    }
}
