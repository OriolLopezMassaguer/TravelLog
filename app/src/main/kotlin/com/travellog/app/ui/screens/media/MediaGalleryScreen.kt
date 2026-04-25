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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
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
    val mediaItems      by viewModel.mediaItems.collectAsStateWithLifecycle()
    val voiceNotes      by viewModel.voiceNotes.collectAsStateWithLifecycle()
    val isImporting     by viewModel.isImporting.collectAsStateWithLifecycle()
    val message         by viewModel.importMessage.collectAsStateWithLifecycle()
    val transcribingIds by viewModel.transcribingIds.collectAsStateWithLifecycle()
    val playingNoteId   by viewModel.playingNoteId.collectAsStateWithLifecycle()
    val isPlaying       by viewModel.isPlaying.collectAsStateWithLifecycle()

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
                1 -> VoiceNoteList(
                    notes           = voiceNotes,
                    transcribingIds = transcribingIds,
                    playingNoteId   = playingNoteId,
                    isPlaying       = isPlaying,
                    onDelete        = viewModel::deleteVoiceNote,
                    onTranscribe    = viewModel::transcribeVoiceNote,
                    onPlayPause     = viewModel::playOrPause
                )
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
private fun VoiceNoteList(
    notes: List<VoiceNote>,
    transcribingIds: Set<Long>,
    playingNoteId: Long?,
    isPlaying: Boolean,
    onDelete: (VoiceNote) -> Unit,
    onTranscribe: (VoiceNote) -> Unit,
    onPlayPause: (VoiceNote) -> Unit,
) {
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
            VoiceNoteCard(
                note           = note,
                isTranscribing = note.id in transcribingIds,
                isThisPlaying  = note.id == playingNoteId && isPlaying,
                isThisLoaded   = note.id == playingNoteId,
                onDelete       = { onDelete(note) },
                onTranscribe   = { onTranscribe(note) },
                onPlayPause    = { onPlayPause(note) },
            )
        }
    }
}

@Composable
private fun VoiceNoteCard(
    note: VoiceNote,
    isTranscribing: Boolean,
    isThisPlaying: Boolean,
    isThisLoaded: Boolean,
    onDelete: () -> Unit,
    onTranscribe: () -> Unit,
    onPlayPause: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text(stringResource(R.string.delete_voice_note_title)) },
            text    = { Text(stringResource(R.string.delete_voice_note_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isThisPlaying) Icons.Default.PauseCircle
                                  else Icons.Default.PlayCircle,
                    contentDescription = if (isThisPlaying) "Pause" else "Play",
                    tint = if (isThisLoaded) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
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
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text  = formatDuration(note.durationSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (note.transcription.isNullOrBlank()) {
                    if (isTranscribing) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = onTranscribe) {
                            Icon(
                                imageVector        = Icons.Default.RecordVoiceOver,
                                contentDescription = "Transcribe",
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
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
