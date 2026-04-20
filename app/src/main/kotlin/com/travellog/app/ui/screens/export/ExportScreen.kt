package com.travellog.app.ui.screens.export

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.travellog.app.data.db.entity.TravelDay
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    navController: NavController,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val days        by viewModel.days.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val context     = LocalContext.current

    var dropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Export") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Day selector
            ExposedDropdownMenuBox(
                expanded  = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value            = selectedDay?.let { formatDayLabel(it) } ?: "Select a day",
                    onValueChange    = {},
                    readOnly         = true,
                    label            = { Text("Travel day") },
                    trailingIcon     = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownExpanded) },
                    modifier         = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded  = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    days.forEach { day ->
                        DropdownMenuItem(
                            text    = { Text(formatDayLabel(day)) },
                            onClick = {
                                viewModel.selectDay(day.id)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Status / actions
            when (val state = exportState) {
                is ExportViewModel.ExportState.Idle -> {
                    Button(
                        onClick  = { viewModel.buildReport() },
                        enabled  = selectedDay != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Build Report")
                    }
                }
                is ExportViewModel.ExportState.Building -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "Building report…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ExportViewModel.ExportState.Ready -> {
                    Text(
                        "Report ready.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick  = { triggerPrint(context, state.html, state.day.date) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Print / Save PDF")
                    }
                    OutlinedButton(
                        onClick  = { shareHtml(context, state.html, state.day.date) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share HTML")
                    }
                }
                is ExportViewModel.ExportState.Error -> {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick  = { viewModel.resetState() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Try Again") }
                }
            }

            HorizontalDivider()

            // GPX sharing — available once a day is selected regardless of export state
            selectedDay?.let { day ->
                OutlinedButton(
                    onClick  = { shareGpxFiles(context, day) },
                    enabled  = day.gpxTrackPath != null || day.gpxPoiPath != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share GPX Files")
                }
                if (day.gpxTrackPath == null && day.gpxPoiPath == null) {
                    Text(
                        "No GPX files recorded for this day yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Print helper ──────────────────────────────────────────────────────────────

private fun triggerPrint(context: Context, html: String, jobName: String) {
    val webView = WebView(context)
    webView.settings.allowFileAccess = true
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val adapter      = view.createPrintDocumentAdapter(jobName)
            printManager.print(
                jobName, adapter,
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .build()
            )
        }
    }
    webView.loadDataWithBaseURL("file:///", html, "text/html", "UTF-8", null)
}

// ── GPX share helper ──────────────────────────────────────────────────────────

private fun shareGpxFiles(context: Context, day: TravelDay) {
    val uris = listOfNotNull(day.gpxTrackPath, day.gpxPoiPath)
        .map { path ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(path)
            )
        }
    if (uris.isEmpty()) return

    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "application/gpx+xml"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share GPX Files"))
}

// ── HTML share helper ─────────────────────────────────────────────────────────

private fun shareHtml(context: Context, html: String, date: String) {
    val reportsDir = File(context.cacheDir, "reports").also { it.mkdirs() }
    val htmlFile   = File(reportsDir, "travellog-$date.html")
    htmlFile.writeText(html)

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        htmlFile
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/html"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "TravelLog — $date")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share HTML Report"))
}

// ── Formatting ────────────────────────────────────────────────────────────────

private val labelFormatter = DateTimeFormatter.ofPattern("EEE, MMM d yyyy")

private fun formatDayLabel(day: TravelDay): String {
    val date = LocalDate.parse(day.date).format(labelFormatter)
    return if (day.title != null) "${day.title} · $date" else date
}
