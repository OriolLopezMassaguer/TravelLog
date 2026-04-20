package com.travellog.app.ui.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.travellog.app.data.db.entity.PointOfInterest
import com.travellog.app.data.db.entity.TrackPoint
import com.travellog.app.data.db.entity.VoiceNote
import com.travellog.app.ui.components.DaySelector
import com.travellog.app.ui.components.VoiceRecorderSheet
import com.travellog.app.ui.screens.voicenote.VoiceNoteViewModel
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// OpenFreeMap Liberty — free OSM vector tiles, no API key
private const val STYLE_URL  = "https://tiles.openfreemap.org/styles/liberty"
private const val TRACK_SOURCE = "track-source"
private const val TRACK_LAYER  = "track-layer"
private const val POI_SOURCE      = "poi-source"
private const val POI_LAYER       = "poi-layer"
private const val POI_LABEL_LAYER = "poi-label-layer"
private const val VN_SOURCE            = "vn-source"
private const val VN_LAYER             = "vn-layer"
private const val AVAILABLE_POI_SOURCE = "available-poi-source"
private const val AVAILABLE_POI_LAYER  = "available-poi-layer"
private const val AVAILABLE_POI_LABEL  = "available-poi-label-layer"

@Composable
fun MapScreen(
    navController: NavController,
    viewModel: MapViewModel = hiltViewModel(),
    voiceNoteViewModel: VoiceNoteViewModel = hiltViewModel()
) {
    val context      = LocalContext.current
    val lifecycle    = LocalLifecycleOwner.current

    val days           by viewModel.days.collectAsStateWithLifecycle()
    val selectedDayId  by viewModel.selectedDayId.collectAsStateWithLifecycle()
    val trackPoints    by viewModel.trackPoints.collectAsStateWithLifecycle()
    val checkedInPois  by viewModel.checkedInPois.collectAsStateWithLifecycle()
    val availablePois  by viewModel.availablePois.collectAsStateWithLifecycle()
    val voiceNotes     by viewModel.voiceNotes.collectAsStateWithLifecycle()

    var showRecorder by remember { mutableStateOf(false) }
    // Holds the POI the user tapped on the map, triggering the check-in dialog
    val checkinDialogPoi = remember { mutableStateOf<PointOfInterest?>(null) }

    // ── Permissions ──────────────────────────────────────────────────────────
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { grants ->
        locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                          grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        if (!locationGranted) {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // ── MapLibre setup ───────────────────────────────────────────────────────
    MapLibre.getInstance(context)

    val mapView = remember {
        MapView(context).also { it.onCreate(null) }
    }

    var mapRef   by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }

    // Forward lifecycle events to MapView
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    // Initialise map and style once
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapRef = map
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.isLogoEnabled = false
            map.setStyle(STYLE_URL) { style ->
                styleRef = style
                initTrackLayer(style)
                initAvailablePoiLayer(style)
                initPoiLayer(style)
                initVoiceNoteLayer(style)
            }
            map.addOnMapClickListener { latLng ->
                val screenPoint = map.projection.toScreenLocation(latLng)
                val features = map.queryRenderedFeatures(screenPoint, AVAILABLE_POI_LAYER)
                if (features.isNotEmpty()) {
                    val id = features[0].getNumberProperty("id")?.toLong()
                        ?: return@addOnMapClickListener false
                    checkinDialogPoi.value = viewModel.availablePois.value.find { it.id == id }
                    checkinDialogPoi.value != null
                } else false
            }
        }
    }

    // Enable location dot whenever both style and permission are ready
    LaunchedEffect(styleRef, locationGranted) {
        val m = mapRef   ?: return@LaunchedEffect
        val s = styleRef ?: return@LaunchedEffect
        if (locationGranted) enableLocationComponent(context, m, s)
    }

    // Update track polyline whenever style or points change
    LaunchedEffect(styleRef, trackPoints) {
        val s = styleRef ?: return@LaunchedEffect
        updateTrackPolyline(s, trackPoints)
    }

    // Update available (not yet checked-in) POI markers
    LaunchedEffect(styleRef, availablePois) {
        val s = styleRef ?: return@LaunchedEffect
        updateAvailablePoiMarkers(s, availablePois)
    }

    // Update checked-in POI markers whenever style or checked-in POIs change
    LaunchedEffect(styleRef, checkedInPois) {
        val s = styleRef ?: return@LaunchedEffect
        updatePoiMarkers(s, checkedInPois)
    }

    // Update voice note pins
    LaunchedEffect(styleRef, voiceNotes) {
        val s = styleRef ?: return@LaunchedEffect
        updateVoiceNoteMarkers(s, voiceNotes)
    }

    // Animate camera to fit the track when day or points change
    LaunchedEffect(selectedDayId, trackPoints) {
        val map = mapRef ?: return@LaunchedEffect
        if (trackPoints.size < 2) return@LaunchedEffect
        val bounds = LatLngBounds.Builder().apply {
            trackPoints.forEach { include(LatLng(it.latitude, it.longitude)) }
        }.build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Day selector at the top
        DaySelector(
            days = days,
            selectedDayId = selectedDayId,
            onDaySelected = viewModel::selectDay,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Voice note FAB
        FloatingActionButton(
            onClick = { showRecorder = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Record voice note")
        }

        // Voice recorder bottom sheet
        if (showRecorder) {
            VoiceRecorderSheet(
                viewModel = voiceNoteViewModel,
                onDismiss = { showRecorder = false }
            )
        }

        // Check-in dialog shown when user taps an available POI marker
        checkinDialogPoi.value?.let { poi ->
            AlertDialog(
                onDismissRequest = { checkinDialogPoi.value = null },
                title = { Text(poi.name) },
                text  = { Text(poi.category + if (poi.address != null) "\n${poi.address}" else "") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.checkIn(poi.id)
                        checkinDialogPoi.value = null
                    }) { Text("Check In") }
                },
                dismissButton = {
                    TextButton(onClick = { checkinDialogPoi.value = null }) { Text("Cancel") }
                }
            )
        }

        // My location
        SmallFloatingActionButton(
            onClick = {
                mapRef?.locationComponent?.lastKnownLocation?.let { loc ->
                    mapRef?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(loc.latitude, loc.longitude), 15.0
                        )
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 88.dp, end = 16.dp)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "My location")
        }
    }
}

// ── Map helpers ───────────────────────────────────────────────────────────────

private fun initTrackLayer(style: Style) {
    style.addSource(GeoJsonSource(TRACK_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        LineLayer(TRACK_LAYER, TRACK_SOURCE).withProperties(
            PropertyFactory.lineColor("#1565C0"),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
}

private fun updateTrackPolyline(style: Style, points: List<TrackPoint>) {
    val source = style.getSource(TRACK_SOURCE) as? GeoJsonSource ?: return
    if (points.size < 2) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val coords = points.map { Point.fromLngLat(it.longitude, it.latitude) }
    source.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(coords)))
}

private fun initAvailablePoiLayer(style: Style) {
    style.addSource(GeoJsonSource(AVAILABLE_POI_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer(AVAILABLE_POI_LAYER, AVAILABLE_POI_SOURCE).withProperties(
            PropertyFactory.circleColor("#1565C0"),
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleOpacity(0.75f)
        )
    )
    style.addLayer(
        SymbolLayer(AVAILABLE_POI_LABEL, AVAILABLE_POI_SOURCE).withProperties(
            PropertyFactory.textField("{name}"),
            PropertyFactory.textSize(10f),
            PropertyFactory.textColor("#0D47A1"),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textOffset(arrayOf(0f, -1.8f)),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_BOTTOM)
        )
    )
}

private fun updateAvailablePoiMarkers(style: Style, pois: List<PointOfInterest>) {
    val source = style.getSource(AVAILABLE_POI_SOURCE) as? GeoJsonSource ?: return
    val features = pois.map { poi ->
        Feature.fromGeometry(Point.fromLngLat(poi.longitude, poi.latitude)).apply {
            addStringProperty("name", poi.name)
            addStringProperty("category", poi.category)
            addNumberProperty("id", poi.id)
        }
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun initPoiLayer(style: Style) {
    style.addSource(GeoJsonSource(POI_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    // Filled green circle for each checked-in POI
    style.addLayer(
        CircleLayer(POI_LAYER, POI_SOURCE).withProperties(
            PropertyFactory.circleColor("#2E7D32"),
            PropertyFactory.circleRadius(10f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f)
        )
    )
    // Name label above the circle
    style.addLayer(
        SymbolLayer(POI_LABEL_LAYER, POI_SOURCE).withProperties(
            PropertyFactory.textField("{name}"),
            PropertyFactory.textSize(11f),
            PropertyFactory.textColor("#1B5E20"),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textOffset(arrayOf(0f, -1.8f)),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_BOTTOM)
        )
    )
}

private fun updatePoiMarkers(style: Style, pois: List<PointOfInterest>) {
    val source = style.getSource(POI_SOURCE) as? GeoJsonSource ?: return
    val features = pois.map { poi ->
        Feature.fromGeometry(Point.fromLngLat(poi.longitude, poi.latitude)).apply {
            addStringProperty("name", poi.name)
            addStringProperty("category", poi.category)
            addNumberProperty("id", poi.id)
        }
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun initVoiceNoteLayer(style: Style) {
    style.addSource(GeoJsonSource(VN_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer(VN_LAYER, VN_SOURCE).withProperties(
            PropertyFactory.circleColor("#E65100"),     // deep orange
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f)
        )
    )
}

private fun updateVoiceNoteMarkers(style: Style, notes: List<VoiceNote>) {
    val source = style.getSource(VN_SOURCE) as? GeoJsonSource ?: return
    val features = notes
        .filter { it.latitude != 0.0 || it.longitude != 0.0 }
        .map { note ->
            Feature.fromGeometry(Point.fromLngLat(note.longitude, note.latitude)).apply {
                addNumberProperty("id", note.id)
                addNumberProperty("duration", note.durationSeconds)
            }
        }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}

@SuppressLint("MissingPermission")
private fun enableLocationComponent(
    context: android.content.Context,
    map: MapLibreMap,
    style: Style
) {
    with(map.locationComponent) {
        activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style)
                .useDefaultLocationEngine(true)
                .build()
        )
        isLocationComponentEnabled = true
        cameraMode  = CameraMode.NONE
        renderMode  = RenderMode.COMPASS
    }
}
