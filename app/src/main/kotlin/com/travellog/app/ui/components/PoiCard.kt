package com.travellog.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travellog.app.data.db.entity.PointOfInterest

@Composable
fun PoiCard(
    poi: PointOfInterest,
    distanceMeters: Float? = null,
    onCheckIn: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon
            Icon(
                imageVector = poi.category.toIcon(),
                contentDescription = poi.category,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )

            Spacer(Modifier.width(12.dp))

            // Name + meta
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = poi.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = poi.category.displayName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                poi.address?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Right column: distance + check-in
            Column(horizontalAlignment = Alignment.End) {
                distanceMeters?.let {
                    Text(
                        text = it.formatDistance(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                }

                if (poi.checkedIn) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Checked in",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else if (onCheckIn != null) {
                    OutlinedButton(
                        onClick = onCheckIn,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Check in", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ── Formatting helpers ────────────────────────────────────────────────────────

private fun Float.formatDistance(): String = when {
    this >= 1000f -> "${"%.1f".format(this / 1000f)} km"
    else          -> "${this.toInt()} m"
}

private fun String.toIcon(): ImageVector = when (this) {
    "food"          -> Icons.Default.Restaurant
    "museum"        -> Icons.Default.Museum
    "attraction"    -> Icons.Default.Star
    "viewpoint"     -> Icons.Default.Landscape
    "accommodation" -> Icons.Default.Hotel
    "historic"      -> Icons.Default.AccountBalance
    "park"          -> Icons.Default.Park
    "entertainment" -> Icons.Default.TheaterComedy
    else            -> Icons.Default.Place
}

private fun String.displayName(): String = when (this) {
    "food"          -> "Food & Drink"
    "museum"        -> "Museum"
    "attraction"    -> "Attraction"
    "viewpoint"     -> "Viewpoint"
    "accommodation" -> "Accommodation"
    "historic"      -> "Historic site"
    "park"          -> "Park / Garden"
    "entertainment" -> "Entertainment"
    else            -> "Place"
}
