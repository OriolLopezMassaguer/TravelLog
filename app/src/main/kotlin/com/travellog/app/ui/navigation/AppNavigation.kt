package com.travellog.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.res.stringResource
import com.travellog.app.ui.screens.export.ExportScreen
import com.travellog.app.ui.screens.map.MapScreen
import com.travellog.app.ui.screens.media.MediaGalleryScreen
import com.travellog.app.ui.screens.poi.PoiListScreen
import com.travellog.app.ui.screens.settings.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavScreens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = stringResource(screen.labelResId)) },
                        label = { Text(stringResource(screen.labelResId)) },
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Map.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Map.route) {
                MapScreen(navController = navController)
            }
            composable(Screen.PoiList.route) {
                PoiListScreen(navController = navController)
            }
            composable(Screen.Media.route) {
                MediaGalleryScreen(navController = navController)
            }
            composable(Screen.Export.route) {
                ExportScreen(navController = navController)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }
        }
    }
}
