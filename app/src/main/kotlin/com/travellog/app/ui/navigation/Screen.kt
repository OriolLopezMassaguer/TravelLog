package com.travellog.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Map      : Screen("map",      "Map",      Icons.Default.Map)
    data object PoiList  : Screen("pois",     "Places",   Icons.Default.Place)
    data object Media    : Screen("media",    "Media",    Icons.Default.Photo)
    data object Export   : Screen("export",   "Export",   Icons.Default.Share)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    // Non-tab destinations
    data object PoiDetail : Screen("pois/{poiId}", "Place Detail", Icons.Default.Place) {
        fun route(poiId: Long) = "pois/$poiId"
    }
    data object MediaDetail : Screen("media/{mediaId}", "Media Detail", Icons.Default.Photo) {
        fun route(mediaId: Long) = "media/$mediaId"
    }
}

val bottomNavScreens = listOf(Screen.Map, Screen.PoiList, Screen.Media, Screen.Export, Screen.Settings)
