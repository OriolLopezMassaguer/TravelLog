package com.travellog.app.ui.screens.poi

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.travellog.app.ui.components.PoiCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiListScreen(
    navController: NavController,
    viewModel: PoiViewModel = hiltViewModel()
) {
    val nearbyPois   by viewModel.nearbyPois.collectAsStateWithLifecycle()
    val checkedIn    by viewModel.checkedInPois.collectAsStateWithLifecycle()
    val isLoading    by viewModel.isLoading.collectAsStateWithLifecycle()
    val error        by viewModel.error.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Nearby", "Checked In (${checkedIn.size})")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Places") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        snackbarHost = {
            error?.let { msg ->
                Snackbar(
                    action = {
                        TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                    }
                ) { Text(msg) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = { Text(title) }
                    )
                }
            }

            // Loading bar
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Content
            when (selectedTab) {
                0 -> NearbyTab(
                    pois      = nearbyPois,
                    isLoading = isLoading,
                    onCheckIn = viewModel::checkIn
                )
                1 -> CheckedInTab(pois = checkedIn)
            }
        }
    }
}

// ── Nearby tab ────────────────────────────────────────────────────────────────

@Composable
private fun NearbyTab(
    pois: List<PoiWithDistance>,
    isLoading: Boolean,
    onCheckIn: (Long) -> Unit
) {
    if (!isLoading && pois.isEmpty()) {
        EmptyState(
            message = "No places found nearby.\nTap ↻ to refresh or move to a different area."
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = pois, key = { it.poi.id }) { item ->
            PoiCard(
                poi           = item.poi,
                distanceMeters = item.distanceMeters.takeIf { it < Float.MAX_VALUE },
                onCheckIn     = { onCheckIn(item.poi.id) }
            )
        }
    }
}

// ── Checked-in tab ────────────────────────────────────────────────────────────

@Composable
private fun CheckedInTab(pois: List<com.travellog.app.data.db.entity.PointOfInterest>) {
    if (pois.isEmpty()) {
        EmptyState(message = "No check-ins yet today.\nFind a place and tap Check In!")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = pois, key = { it.id }) { poi ->
            PoiCard(poi = poi)
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(32.dp)
        )
    }
}
