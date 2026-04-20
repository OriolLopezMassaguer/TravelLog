package com.travellog.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector
import com.travellog.app.R

sealed class Screen(val route: String, @StringRes val labelResId: Int, val icon: ImageVector) {
    data object Map      : Screen("map",      R.string.nav_map,      Icons.Default.Map)
    data object PoiList  : Screen("pois",     R.string.nav_places,   Icons.Default.Place)
    data object Media    : Screen("media",    R.string.nav_media,    Icons.Default.Photo)
    data object Export   : Screen("export",   R.string.nav_export,   Icons.Default.Share)
    data object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)

    // Non-tab destinations
    data object PoiDetail : Screen("pois/{poiId}", R.string.nav_places, Icons.Default.Place) {
        fun route(poiId: Long) = "pois/$poiId"
    }
    data object MediaDetail : Screen("media/{mediaId}", R.string.nav_media, Icons.Default.Photo) {
        fun route(mediaId: Long) = "media/$mediaId"
    }
}

val bottomNavScreens = listOf(Screen.Map, Screen.PoiList, Screen.Media, Screen.Export, Screen.Settings)
