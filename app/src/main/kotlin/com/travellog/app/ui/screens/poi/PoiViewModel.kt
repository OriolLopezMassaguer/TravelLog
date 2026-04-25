package com.travellog.app.ui.screens.poi

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travellog.app.data.db.entity.PointOfInterest
import com.travellog.app.data.repository.PoiRepository
import com.travellog.app.data.repository.TravelDayRepository
import com.travellog.app.service.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PoiWithDistance(val poi: PointOfInterest, val distanceMeters: Float)

@HiltViewModel
class PoiViewModel @Inject constructor(
    private val poiRepository: PoiRepository,
    private val travelDayRepository: TravelDayRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _currentDayId    = MutableStateFlow<Long?>(null)
    private val _currentLocation = MutableStateFlow<Location?>(null)
    private val _isLoading       = MutableStateFlow(false)
    private val _error           = MutableStateFlow<String?>(null)
    private val _nearbyPois      = MutableStateFlow<List<PointOfInterest>>(emptyList())
    private val _selectedCategory = MutableStateFlow<String?>(null)

    val isLoading: StateFlow<Boolean>    = _isLoading.asStateFlow()
    val error: StateFlow<String?>        = _error.asStateFlow()
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    /** Nearby POIs (fetched from Overpass, held in memory) sorted by distance, filtered by category. */
    val nearbyPois: StateFlow<List<PoiWithDistance>> = combine(
        _nearbyPois,
        _currentLocation,
        _selectedCategory
    ) { pois, location, category ->
        pois
            .filter { category == null || it.category == category }
            .map { poi -> PoiWithDistance(poi, location?.distanceTo(poi) ?: Float.MAX_VALUE) }
            .sortedBy { it.distanceMeters }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Distinct categories available in the current nearby list. */
    val availableCategories: StateFlow<List<String>> = _nearbyPois
        .map { pois -> pois.map { it.category }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Today's checked-in POIs in check-in order. */
    val checkedInPois: StateFlow<List<PointOfInterest>> = _currentDayId
        .filterNotNull()
        .flatMapLatest { poiRepository.getCheckedInPoisForDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val today = travelDayRepository.getOrCreateToday()
            _currentDayId.value = today.id
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val dayId    = _currentDayId.value ?: return@launch
            val location = locationProvider.getLastLocation()
            _currentLocation.value = location

            if (location == null) {
                _error.value = "Location unavailable — showing cached places"
                return@launch
            }

            _isLoading.value = true
            _error.value = null
            try {
                _nearbyPois.value = poiRepository.fetchNearbyPois(location.latitude, location.longitude, dayId)
            } catch (e: Exception) {
                _error.value = "Could not fetch nearby places"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun checkIn(poi: PointOfInterest) {
        viewModelScope.launch {
            val dayId = _currentDayId.value ?: return@launch
            poiRepository.checkIn(poi, dayId, _currentLocation.value)
            _nearbyPois.update { current -> current.filter { it.externalId != poi.externalId } }
        }
    }

    fun deleteCheckIn(poi: PointOfInterest) {
        viewModelScope.launch {
            poiRepository.deleteCheckIn(poi.id)
            refresh()
        }
    }

    fun setCategory(category: String?) { _selectedCategory.value = category }

    fun clearError() { _error.value = null }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Location.distanceTo(poi: PointOfInterest): Float {
        val result = FloatArray(1)
        Location.distanceBetween(latitude, longitude, poi.latitude, poi.longitude, result)
        return result[0]
    }
}
