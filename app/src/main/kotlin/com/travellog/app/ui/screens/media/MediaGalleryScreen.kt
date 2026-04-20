package com.travellog.app.ui.screens.media

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.travellog.app.R
import com.travellog.app.data.db.entity.MediaItem
import com.travellog.app.data.db.entity.VoiceNote
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    navController: NavController,
    viewModel: MediaGalleryViewModel = hiltViewModel(),
) {
    val mediaItems  by viewModel.mediaItems.collectAsStateWithLifecycle()
    val voiceNotes  by viewModel.voiceNotes.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val message     by viewModel.importMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Show snackbar when a message arrives
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message!!)
            viewModel.clearMessage()
        }
    }

    // Permission launcher
    val mediaPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) viewModel.importFromGallery()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.media_title)) })
                if (isImporting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { permissionLauncher.launch(mediaPermissions) }) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.media_import_desc))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    text     = { Text(stringResource(R.string.media_tab_photos_videos, mediaItems.size)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    text     = { Text(stringResource(R.string.media_tab_voice_notes, voiceNotes.size)) }
                )
            }

            when (selectedTab) {
                0 -> PhotoVideoGrid(mediaItems)
                1 -> VoiceNoteList(voiceNotes)
            }
        }
    }
}

// ── Photos & Videos ───────────────────────────────────────────────────────────

@Composable
private fun PhotoVideoGrid(items: List<MediaItem>) {
    if (items.isEmpty()) {
        EmptyState(stringResource(R.string.media_empty_photos))
        return
    }
    LazyVerticalGrid(
        columns            = GridCells.Fixed(3),
        contentPadding     = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement   = Arrangement.spacedBy(2.dp),
        modifier           = Modifier.fillMaxSize()
    ) {
        items(items, key = { it.id }) { item ->
            MediaCell(item)
        }
    }
}

@Composable
private fun MediaCell(item: MediaItem) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model             = item.filePath,
            contentDescription = null,
            contentScale      = ContentScale.Crop,
            modifier          = Modifier.fillMaxSize()
        )
        if (item.type == "video" && item.durationSeconds != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(topStart = 4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text  = formatDuration(item.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            Icon(
                imageVector        = Icons.Default.PlayCircle,
                contentDescription = null,
                tint               = Color.White.copy(alpha = 0.85f),
                modifier           = Modifier
                    .size(32.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

// ── Voice Notes ───────────────────────────────────────────────────────────────

@Composable
private fun VoiceNoteList(notes: List<VoiceNote>) {
    if (notes.isEmpty()) {
        EmptyState(stringResource(R.string.media_empty_voice_notes))
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(notes, key = { it.id }) { note ->
            VoiceNoteCard(note)
        }
    }
}

@Composable
private fun VoiceNoteCard(note: VoiceNote) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.Mic,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(36.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = formatTimestamp(note.recordedAt),
                    style    = MaterialTheme.typography.bodyMedium,
                )
                if (!note.transcription.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = note.transcription,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text  = formatDuration(note.durationSeconds),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text  = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm · MMM d")

private fun formatTimestamp(ms: Long): String =
    Instant.ofEpochMilli(ms)
        .atZone(ZoneId.systemDefault())
        .format(timeFormatter)
