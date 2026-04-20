package com.travellog.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.travellog.app.data.db.entity.TravelDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DaySelector(
    days: List<TravelDay>,
    selectedDayId: Long?,
    onDaySelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) return

    val listState = rememberLazyListState()
    val selectedIndex = days.indexOfFirst { it.id == selectedDayId }

    // Scroll to selected chip when day changes
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 4.dp
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = days, key = { it.id }) { day ->
                FilterChip(
                    selected = day.id == selectedDayId,
                    onClick = { onDaySelected(day.id) },
                    label = { Text(day.displayLabel()) }
                )
            }
        }
    }
}

private val shortDateFmt = DateTimeFormatter.ofPattern("MMM d")

private fun TravelDay.displayLabel(): String {
    val today     = LocalDate.now()
    val yesterday = today.minusDays(1)
    val dayDate   = LocalDate.parse(date)
    return when (dayDate) {
        today     -> "Today"
        yesterday -> "Yesterday"
        else      -> dayDate.format(shortDateFmt)
    }
}
