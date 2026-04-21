package com.travellog.app.ui.screens.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travellog.app.data.db.dao.MediaItemDao
import com.travellog.app.data.db.dao.PoiDao
import com.travellog.app.data.db.dao.TrackPointDao
import com.travellog.app.data.db.dao.TravelDayDao
import com.travellog.app.data.db.dao.VoiceNoteDao
import com.travellog.app.data.db.entity.TravelDay
import com.travellog.app.data.export.DayReport
import com.travellog.app.data.export.HtmlReportBuilder
import com.travellog.app.data.repository.TravelDayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val travelDayRepository: TravelDayRepository,
    private val travelDayDao: TravelDayDao,
    private val mediaItemDao: MediaItemDao,
    private val voiceNoteDao: VoiceNoteDao,
    private val poiDao: PoiDao,
    private val trackPointDao: TrackPointDao,
    private val htmlBuilder: HtmlReportBuilder,
    private val gpxBuilder: com.travellog.app.data.gpx.GpxExportBuilder,
) : ViewModel() {

    sealed class ExportState {
        data object Idle     : ExportState()
        data object Building : ExportState()
        data class  Ready(
            val html: String,
            val unifiedGpx: String,
            val trackGpx: String,
            val poisGpx: String,
            val day: TravelDay
        ) : ExportState()
        data class  Error(val message: String) : ExportState()
    }

    val days: StateFlow<List<TravelDay>> = travelDayRepository.getAllDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedDayId = MutableStateFlow<Long?>(null)
    val selectedDayId: StateFlow<Long?> = _selectedDayId.asStateFlow()

    val selectedDay: StateFlow<TravelDay?> = combine(days, _selectedDayId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun selectDay(dayId: Long) {
        _selectedDayId.value = dayId
        _exportState.value   = ExportState.Idle
    }

    fun buildReport() {
        val dayId = _selectedDayId.value ?: return
        viewModelScope.launch {
            _exportState.value = ExportState.Building
            try {
                val report = withContext(Dispatchers.IO) { gatherReport(dayId) }
                val unifiedGpx = gpxBuilder.build(report)
                val trackGpx   = gpxBuilder.buildTrackOnly(report)
                val poisGpx    = gpxBuilder.buildPoisOnly(report)
                val html       = htmlBuilder.build(report, unifiedGpx, trackGpx, poisGpx)

                _exportState.value = ExportState.Ready(html, unifiedGpx, trackGpx, poisGpx, report.day)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Export failed")
            }
        }
    }

    fun resetState() { _exportState.value = ExportState.Idle }

    private suspend fun gatherReport(dayId: Long): DayReport {
        val day         = travelDayDao.getDayById(dayId)
            ?: error("Day $dayId not found")
        val pois        = poiDao.getPoisForDayOnce(dayId).filter { it.checkedIn }
        val media       = mediaItemDao.getPhotoVideoForDayOnce(dayId)
        val voiceNotes  = voiceNoteDao.getVoiceNotesForDayOnce(dayId)
        val trackPoints = trackPointDao.getPointsForDayOnce(dayId)
        return DayReport(day, pois, media, voiceNotes, trackPoints)
    }
}
