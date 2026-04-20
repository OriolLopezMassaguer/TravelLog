package com.travellog.app.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travellog.app.data.db.entity.PointOfInterest
import com.travellog.app.data.db.entity.TravelDay
import com.travellog.app.data.db.entity.TrackPoint
import com.travellog.app.data.db.entity.VoiceNote
import com.travellog.app.data.repository.PoiRepository
import com.travellog.app.data.repository.TravelDayRepository
import com.travellog.app.data.repository.TrackingRepository
import com.travellog.app.data.repository.VoiceNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.location.Location
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val travelDayRepository: TravelDayRepository,
    private val trackingRepository: TrackingRepository,
    private val poiRepository: PoiRepository,
    private val voiceNoteRepository: VoiceNoteRepository,
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

    val availablePois: StateFlow<List<PointOfInterest>> = _selectedDayId
        .filterNotNull()
        .flatMapLatest { dayId -> poiRepository.getPoisForDay(dayId) }
        .map { pois -> pois.filter { !it.checkedIn } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val voiceNotes: StateFlow<List<VoiceNote>> = _selectedDayId
        .filterNotNull()
        .flatMapLatest { dayId -> voiceNoteRepository.getVoiceNotesForDay(dayId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val today = travelDayRepository.getOrCreateToday()
            _selectedDayId.compareAndSet(null, today.id)
        }
    }

    fun selectDay(dayId: Long) {
        _selectedDayId.value = dayId
    }

    fun checkIn(poiId: Long) {
        viewModelScope.launch {
            val dayId = _selectedDayId.value ?: return@launch
            poiRepository.checkIn(poiId, dayId, null)
        }
    }
}
