package com.travellog.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travellog.app.data.settings.AppSettings
import com.travellog.app.data.settings.SettingsRepository
import com.travellog.app.scheduling.TrackingScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val trackingScheduler: TrackingScheduler,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setGpsInterval(seconds: Int) = viewModelScope.launch {
        settingsRepository.setGpsInterval(seconds)
    }

    fun setAdaptiveGps(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAdaptiveGps(enabled)
    }

    fun setTrackingStartHour(hour: Int) = viewModelScope.launch {
        settingsRepository.setTrackingStartHour(hour)
        trackingScheduler.schedule(startHour = hour)
    }
}
