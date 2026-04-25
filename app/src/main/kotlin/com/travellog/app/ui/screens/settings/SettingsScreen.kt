package com.travellog.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.travellog.app.R
import com.travellog.app.ui.screens.settings.VoskModelState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings   by viewModel.settings.collectAsStateWithLifecycle()
    val voskState  by viewModel.voskState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionLabel(stringResource(R.string.settings_section_gps))

            DropdownSetting(
                label   = stringResource(R.string.settings_gps_interval_label),
                options = listOf(
                    5  to stringResource(R.string.settings_gps_5s),
                    10 to stringResource(R.string.settings_gps_10s),
                    30 to stringResource(R.string.settings_gps_30s),
                ),
                current  = settings.gpsIntervalSeconds,
                onSelect = { viewModel.setGpsInterval(it) }
            )

            SwitchSetting(
                label    = stringResource(R.string.settings_adaptive_gps_label),
                subLabel = stringResource(R.string.settings_adaptive_gps_desc),
                checked  = settings.adaptiveGps,
                onChange = { viewModel.setAdaptiveGps(it) }
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.settings_section_schedule))

            DropdownSetting(
                label   = stringResource(R.string.settings_start_hour_label),
                options = (6..10).map { h -> h to "%02d:00".format(h) },
                current  = settings.trackingStartHour,
                onSelect = { viewModel.setTrackingStartHour(it) }
            )

            Text(
                stringResource(R.string.settings_tracking_stop_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.settings_section_transcription))

            VoskModelSetting(
                state       = voskState,
                onDownload  = viewModel::downloadVoskModel,
                onDelete    = viewModel::deleteVoskModel,
            )

            Spacer(Modifier.height(4.dp))

            ApiKeySetting(
                label    = stringResource(R.string.settings_openai_key_label),
                subLabel = stringResource(R.string.settings_openai_key_desc),
                value    = settings.openAiApiKey,
                onSave   = { viewModel.setOpenAiApiKey(it) }
            )
        }
    }
}

// ── Vosk model setting ────────────────────────────────────────────────────────

@Composable
private fun VoskModelSetting(
    state: VoskModelState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Vosk offline model (~50 MB)", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when (state) {
                        is VoskModelState.NotDownloaded -> "Not downloaded — transcription uses Whisper API"
                        is VoskModelState.Downloading   -> "Downloading… ${(state.progress * 100).toInt()}%"
                        is VoskModelState.Ready         -> "Ready — transcription works offline"
                        is VoskModelState.Error         -> "Download failed — tap to retry"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (state) {
                        is VoskModelState.Ready -> MaterialTheme.colorScheme.primary
                        is VoskModelState.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            when (state) {
                is VoskModelState.NotDownloaded, is VoskModelState.Error ->
                    TextButton(onClick = onDownload) { Text("Download") }
                is VoskModelState.Downloading ->
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                is VoskModelState.Ready ->
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete model",
                             tint = MaterialTheme.colorScheme.error)
                    }
            }
        }
        if (state is VoskModelState.Downloading) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Reusable setting components ───────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownSetting(
    label: String,
    options: List<Pair<T, String>>,
    current: T,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: current.toString()

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value         = currentLabel,
            onValueChange = {},
            readOnly      = true,
            label         = { Text(label) },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier      = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, display) ->
                DropdownMenuItem(
                    text    = { Text(display) },
                    onClick = { onSelect(value); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ApiKeySetting(
    label: String,
    subLabel: String,
    value: String,
    onSave: (String) -> Unit,
) {
    var text    by remember(value) { mutableStateOf(value) }
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value         = text,
        onValueChange = { text = it },
        label         = { Text(label) },
        placeholder   = { Text("sk-…") },
        supportingText = { Text(subLabel) },
        visualTransformation = if (visible) VisualTransformation.None
                               else PasswordVisualTransformation(),
        trailingIcon  = {
            Row {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(
                            if (visible) R.string.settings_hide_key_desc else R.string.settings_show_key_desc
                        )
                    )
                }
                TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) }
            }
        },
        singleLine    = true,
        modifier      = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SwitchSetting(
    label: String,
    subLabel: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(subLabel, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
