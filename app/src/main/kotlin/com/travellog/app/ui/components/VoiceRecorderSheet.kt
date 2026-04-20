package com.travellog.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.travellog.app.R
import com.travellog.app.ui.screens.voicenote.RecordingState
import com.travellog.app.ui.screens.voicenote.VoiceNoteViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecorderSheet(
    viewModel: VoiceNoteViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state   by viewModel.state.collectAsState()

    // ── Microphone permission ─────────────────────────────────────────────────
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        micGranted = granted
        if (granted) viewModel.startRecording()
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.cancelRecording()
            onDismiss()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val s = state) {
                is RecordingState.Idle -> IdleContent(
                    onStart = {
                        if (micGranted) viewModel.startRecording()
                        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )

                is RecordingState.Recording -> RecordingContent(
                    state    = s,
                    onStop   = viewModel::stopRecording,
                    onCancel = { viewModel.cancelRecording(); onDismiss() }
                )

                is RecordingState.Saved -> SavedContent(
                    state  = s,
                    onDone = { viewModel.reset(); onDismiss() }
                )

                is RecordingState.Error -> ErrorContent(
                    message   = s.message,
                    onRetry   = viewModel::reset,
                    onDismiss = { viewModel.reset(); onDismiss() }
                )
            }
        }
    }
}

// ── Idle ──────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(onStart: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.recorder_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.recorder_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(32.dp))
    FilledIconButton(
        onClick = onStart,
        modifier = Modifier.size(80.dp)
    ) {
        Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.recorder_title), modifier = Modifier.size(40.dp))
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.recorder_tap_to_start), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
}

// ── Recording ─────────────────────────────────────────────────────────────────

@Composable
private fun RecordingContent(
    state: RecordingState.Recording,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    // Elapsed timer
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1_000); elapsed++ }
    }

    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue  = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.recorder_recording), style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)

    state.nearestPoiName?.let { name ->
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.recorder_near_place, name), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Spacer(Modifier.height(32.dp))

    // Pulsing mic icon
    Box(contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick  = {},
            enabled  = false,
            modifier = Modifier.size(80.dp).scale(scale),
            colors   = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(40.dp))
        }
    }

    Spacer(Modifier.height(16.dp))
    Text(
        elapsed.formatDuration(),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold
    )

    Spacer(Modifier.height(32.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.action_cancel))
        }
        Button(onClick = onStop) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.action_stop))
        }
    }
    Spacer(Modifier.height(16.dp))
}

// ── Saved ─────────────────────────────────────────────────────────────────────

@Composable
private fun SavedContent(state: RecordingState.Saved, onDone: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        tint     = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp)
    )
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.recorder_saved_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.recorder_duration, state.durationSeconds.formatDuration()),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    state.associatedPoiName?.let { name ->
        Text(
            stringResource(R.string.recorder_linked_to, name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Spacer(Modifier.height(12.dp))
    when {
        state.isTranscribing -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.recorder_transcribing), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.transcription != null -> {
            Text(stringResource(R.string.recorder_transcript_label), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                state.transcription,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
    Spacer(Modifier.height(16.dp))
}

// ── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.recorder_error_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(message, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
    Spacer(Modifier.height(16.dp))
}

// ── Formatting ────────────────────────────────────────────────────────────────

private fun Int.formatDuration(): String = "%d:%02d".format(this / 60, this % 60)
