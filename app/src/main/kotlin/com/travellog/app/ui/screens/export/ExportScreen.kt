package com.travellog.app.ui.screens.export

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.content.ContextWrapper
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import android.widget.Toast
import android.view.ViewGroup
import android.view.View
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.travellog.app.R
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.export_title)) }) }
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
                    value            = selectedDay?.let { formatDayLabel(it) }
                        ?: stringResource(R.string.export_select_day_placeholder),
                    onValueChange    = {},
                    readOnly         = true,
                    label            = { Text(stringResource(R.string.export_travel_day_label)) },
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
                        Text(stringResource(R.string.export_build_report))
                    }
                }
                is ExportViewModel.ExportState.Building -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.export_building),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ExportViewModel.ExportState.Ready -> {
                    Text(
                        stringResource(R.string.export_ready),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick  = { triggerPrint(context, state.html, state.day.date) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_print_pdf))
                    }
                    OutlinedButton(
                        onClick  = { shareHtml(context, state.html, state.day.date) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_share_html))
                    }

                    // Dual GPX Share (Track and POIs)
                    OutlinedButton(
                        onClick  = {
                            shareGpxFiles(
                                context,
                                state.day.date,
                                state.trackGpx,
                                state.poisGpx
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_share_gpx))
                    }

                    // Unified GPX share
                    OutlinedButton(
                        onClick  = { shareUnifiedGpx(context, state.unifiedGpx, state.day.date) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_share_unified_gpx))
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
                    ) { Text(stringResource(R.string.export_try_again)) }
                }
            }

            HorizontalDivider()
        }
    }
}

// ── Print helper ──────────────────────────────────────────────────────────────

// Keep a static reference to prevent GC during the print process
private var activeWebView: WebView? = null

private fun triggerPrint(context: Context, html: String, jobName: String) {
    val activity = context.findActivity() ?: return
    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: run {
        Toast.makeText(context, "Print service not available", Toast.LENGTH_SHORT).show()
        return
    }

    Log.d("ExportScreen", "Triggering print for $jobName. HTML size: ${html.length}")
    Toast.makeText(context, R.string.export_building, Toast.LENGTH_SHORT).show()

    // Create WebView on the UI thread
    val webView = WebView(activity)
    activeWebView = webView
    
    // On some devices, WebView must be attached to a Window to print correctly
    val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
    webView.layoutParams = ViewGroup.LayoutParams(1, 1)
    webView.visibility = View.INVISIBLE
    rootView.addView(webView)

    webView.settings.javaScriptEnabled = true
    
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            Log.d("ExportScreen", "WebView page finished. Starting print job: $jobName")
            
            try {
                // Use the version that doesn't take a name if on older APIs, 
                // but for SDK 35 createPrintDocumentAdapter(String) is the way.
                val adapter = view.createPrintDocumentAdapter(jobName)
                
                printManager.print(
                    jobName,
                    adapter,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .build()
                )
            } catch (e: Exception) {
                Log.e("ExportScreen", "Error during print() call", e)
                Toast.makeText(activity, "Print error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                // Cleanup after some time. We can't remove it immediately 
                // because the PrintManager might still be talking to it.
                view.postDelayed({
                    rootView.removeView(view)
                    if (activeWebView == view) activeWebView = null
                }, 10000)
            }
        }

        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
            Log.e("ExportScreen", "WebView error ($errorCode): $description")
            Toast.makeText(activity, "WebView error: $description", Toast.LENGTH_LONG).show()
            rootView.removeView(view)
            activeWebView = null
        }
    }

    // Use a simpler base URL
    webView.loadDataWithBaseURL("https://app.travellog", html, "text/html", "UTF-8", null)
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

// ── GPX share helper ──────────────────────────────────────────────────────────

private fun shareGpxFiles(context: Context, date: String, trackGpx: String, poisGpx: String) {
    val exportDir = File(context.cacheDir, "export").also { it.mkdirs() }
    
    val trackFile = File(exportDir, "track-$date.gpx").apply { writeText(trackGpx) }
    val poisFile  = File(exportDir, "pois-$date.gpx").apply { writeText(poisGpx) }

    val uris = listOf(trackFile, poisFile).map { file ->
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "application/gpx+xml"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_share_gpx)))
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
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_share_html_subject, date))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_share_html)))
}

private fun shareUnifiedGpx(context: Context, gpxData: String, date: String) {
    val exportDir = File(context.cacheDir, "export").also { it.mkdirs() }
    val gpxFile   = File(exportDir, "trip-$date.gpx")
    gpxFile.writeText(gpxData)

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        gpxFile
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_share_unified_gpx_subject, date))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_share_unified_gpx_chooser)))
}

// ── Formatting ────────────────────────────────────────────────────────────────

private val labelFormatter = DateTimeFormatter.ofPattern("EEE, MMM d yyyy")

private fun formatDayLabel(day: TravelDay): String {
    val date = LocalDate.parse(day.date).format(labelFormatter)
    return if (day.title != null) "${day.title} · $date" else date
}
