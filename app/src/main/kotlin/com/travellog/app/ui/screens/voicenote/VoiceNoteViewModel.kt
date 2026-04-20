package com.travellog.app.ui.screens.voicenote

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travellog.app.audio.VoiceRecordingManager
import com.travellog.app.data.db.entity.PointOfInterest
import com.travellog.app.data.repository.PoiRepository
import com.travellog.app.data.repository.TravelDayRepository
import com.travellog.app.data.repository.VoiceNoteRepository
import com.travellog.app.service.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── State machine ─────────────────────────────────────────────────────────────

sealed class RecordingState {
    data object Idle : RecordingState()

    data class Recording(
        val filePath: String,
        val startedAtMs: Long,
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val heading: Float?,
        val nearbyPois: List<PointOfInterest>,
        val nearestPoiName: String?,
    ) : RecordingState()

    data class Saved(
        val voiceNoteId: Long,
        val durationSeconds: Int,
        val associatedPoiName: String?,
    ) : RecordingState()

    data class Error(val message: String) : RecordingState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class VoiceNoteViewModel @Inject constructor(
    private val recorder: VoiceRecordingManager,
    private val voiceNoteRepository: VoiceNoteRepository,
    private val poiRepository: PoiRepository,
    private val travelDayRepository: TravelDayRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    fun startRecording() {
        viewModelScope.launch {
            val location = locationProvider.getLastLocation()
            val day      = travelDayRepository.getOrCreateToday()

            // Trigger POI fetch concurrently — we want the snapshot but won't block UI
            val nearbyDeferred = async {
                if (location != null)
                    poiRepository.fetchNearbyPois(location.latitude, location.longitude, day.id)
                else emptyList()
            }

            try {
                val file     = recorder.startRecording(day.date)
                val nearby   = nearbyDeferred.await()
                val nearest  = location?.nearestPoi(nearby)

                _state.value = RecordingState.Recording(
                    filePath       = file.absolutePath,
                    startedAtMs    = System.currentTimeMillis(),
                    latitude       = location?.latitude  ?: 0.0,
                    longitude      = location?.longitude ?: 0.0,
                    altitude       = location?.altitude  ?: 0.0,
                    heading        = location?.bearing?.takeIf { location.hasBearing() },
                    nearbyPois     = nearby,
                    nearestPoiName = nearest?.name
                )
            } catch (e: Exception) {
                _state.value = RecordingState.Error("Could not start recording: ${e.message}")
            }
        }
    }

    fun stopRecording() {
        val current = _state.value as? RecordingState.Recording ?: return

        viewModelScope.launch {
            val result = recorder.stopRecording()
            if (result == null) {
                _state.value = RecordingState.Error("Recording failed — please try again")
                return@launch
            }

            val day  = travelDayRepository.getOrCreateToday()
            val note = voiceNoteRepository.save(
                dayId           = day.id,
                filePath        = result.file.absolutePath,
                durationSeconds = result.durationSeconds,
                latitude        = current.latitude,
                longitude       = current.longitude,
                altitude        = current.altitude,
                heading         = current.heading,
                nearbyPoiIds    = current.nearbyPois.map { it.id },
                recordedAt      = current.startedAtMs,
            )

            _state.value = RecordingState.Saved(
                voiceNoteId       = note.id,
                durationSeconds   = result.durationSeconds,
                associatedPoiName = current.nearestPoiName
            )
        }
    }

    fun cancelRecording() {
        recorder.cancelRecording()
        _state.value = RecordingState.Idle
    }

    fun reset() {
        _state.value = RecordingState.Idle
    }

    override fun onCleared() {
        recorder.cancelRecording()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Location.nearestPoi(pois: List<PointOfInterest>): PointOfInterest? {
        val result = FloatArray(1)
        return pois.minByOrNull { poi ->
            Location.distanceBetween(latitude, longitude, poi.latitude, poi.longitude, result)
            result[0]
        }
    }
}
