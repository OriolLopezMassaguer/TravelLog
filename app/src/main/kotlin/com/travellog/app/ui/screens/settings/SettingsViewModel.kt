package com.travellog.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travellog.app.audio.VoskTranscriptionService
import com.travellog.app.data.settings.AppSettings
import com.travellog.app.data.settings.SettingsRepository
import com.travellog.app.scheduling.TrackingScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class VoskModelState {
    data object NotDownloaded : VoskModelState()
    data class Downloading(val progress: Float) : VoskModelState()
    data object Ready : VoskModelState()
    data object Error : VoskModelState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val trackingScheduler: TrackingScheduler,
    private val voskService: VoskTranscriptionService,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _voskState = MutableStateFlow<VoskModelState>(
        if (voskService.isModelReady()) VoskModelState.Ready else VoskModelState.NotDownloaded
    )
    val voskState: StateFlow<VoskModelState> = _voskState.asStateFlow()

    fun downloadVoskModel() {
        if (_voskState.value is VoskModelState.Downloading) return
        viewModelScope.launch {
            _voskState.value = VoskModelState.Downloading(0f)
            val ok = voskService.downloadModel { progress ->
                _voskState.value = VoskModelState.Downloading(progress)
            }
            _voskState.value = if (ok) VoskModelState.Ready else VoskModelState.Error
        }
    }

    fun deleteVoskModel() {
        voskService.deleteModel()
        _voskState.value = VoskModelState.NotDownloaded
    }

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

    fun setOpenAiApiKey(key: String) = viewModelScope.launch {
        settingsRepository.setOpenAiApiKey(key)
    }
}
