package com.travellog.app.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travellog.app.data.db.entity.MediaItem
import com.travellog.app.data.db.entity.PointOfInterest
import com.travellog.app.data.db.entity.TravelDay
import com.travellog.app.data.db.entity.TrackPoint
import com.travellog.app.data.db.entity.VoiceNote
import com.travellog.app.data.repository.MediaRepository
import com.travellog.app.data.repository.PoiRepository
import com.travellog.app.data.repository.TravelDayRepository
import com.travellog.app.data.repository.TrackingRepository
import com.travellog.app.data.repository.VoiceNoteRepository
import com.travellog.app.service.GpsTrackingService
import com.travellog.app.service.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val travelDayRepository: TravelDayRepository,
    private val trackingRepository: TrackingRepository,
    private val poiRepository: PoiRepository,
    private val voiceNoteRepository: VoiceNoteRepository,
    private val mediaRepository: MediaRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _selectedDayId = MutableStateFlow<Long?>(null)
    val selectedDayId: StateFlow<Long?> = _selectedDayId.asStateFlow()

    val days: StateFlow<List<TravelDay>> = travelDayRepository.getAllDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedDay: StateFlow<TravelDay?> = combine(days, _selectedDayId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val trackPoints: StateFlow<List<TrackPoint>> = _selectedDayId
        .filterNotNull()
        .flatMapLatest { dayId -> trackingRepository.getPointsForDay(dayId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val checkedInPois: StateFlow<List<PointOfInterest>> = _selectedDayId
        .filterNotNull()
        .flatMapLatest { dayId -> poiRepository.getCheckedInPoisForDay(dayId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _availablePois = MutableStateFlow<List<PointOfInterest>>(emptyList())
    val availablePois: StateFlow<List<PointOfInterest>> = _availablePois.asStateFlow()

    val voiceNotes: StateFlow<List<VoiceNote>> = _selectedDayId
        .filterNotNull()
        .flatMapLatest { dayId -> voiceNoteRepository.getVoiceNotesForDay(dayId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mediaItems: StateFlow<List<MediaItem>> = _selectedDayId
        .filterNotNull()
        .flatMapLatest { dayId -> mediaRepository.getMediaForDay(dayId) }
        .map { items -> items.filter { it.type == "photo" || it.type == "video" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isTrackingActive: StateFlow<Boolean> = GpsTrackingService.isRunning

    init {
        viewModelScope.launch {
            val today = travelDayRepository.getOrCreateToday()
            _selectedDayId.compareAndSet(null, today.id)
        }
    }

    fun selectDay(dayId: Long) {
        _selectedDayId.value = dayId
    }

    fun refreshPois() {
        viewModelScope.launch {
            val dayId    = _selectedDayId.value ?: return@launch
            val location = locationProvider.getLastLocation() ?: return@launch
            runCatching {
                _availablePois.value = poiRepository.fetchNearbyPois(location.latitude, location.longitude, dayId)
            }
        }
    }

    fun checkIn(poi: PointOfInterest) {
        viewModelScope.launch {
            val dayId    = _selectedDayId.value ?: return@launch
            val location = locationProvider.getLastLocation()
            poiRepository.checkIn(poi, dayId, location)
            _availablePois.update { current -> current.filter { it.externalId != poi.externalId } }
        }
    }
}
