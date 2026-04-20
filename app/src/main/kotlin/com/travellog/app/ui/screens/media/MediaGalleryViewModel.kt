package com.travellog.app.ui.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travellog.app.data.db.entity.MediaItem
import com.travellog.app.data.db.entity.VoiceNote
import com.travellog.app.data.repository.MediaRepository
import com.travellog.app.data.repository.TravelDayRepository
import com.travellog.app.data.repository.VoiceNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaGalleryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val voiceNoteRepository: VoiceNoteRepository,
    private val travelDayRepository: TravelDayRepository,
) : ViewModel() {

    private val _currentDayId  = MutableStateFlow<Long?>(null)
    private val _isImporting   = MutableStateFlow(false)
    private val _importMessage = MutableStateFlow<String?>(null)

    val isImporting: StateFlow<Boolean>     = _isImporting.asStateFlow()
    val importMessage: StateFlow<String?>   = _importMessage.asStateFlow()

    /** Photos and videos (excludes voice_note type). */
    val mediaItems: StateFlow<List<MediaItem>> = _currentDayId
        .filterNotNull()
        .flatMapLatest { dayId ->
            mediaRepository.getPhotoVideoForDay(dayId)
                .map { items -> items.filter { it.type != "voice_note" } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Voice notes for the current day. */
    val voiceNotes: StateFlow<List<VoiceNote>> = _currentDayId
        .filterNotNull()
        .flatMapLatest { voiceNoteRepository.getVoiceNotesForDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val today = travelDayRepository.getOrCreateToday()
            _currentDayId.value = today.id
        }
    }

    fun importFromGallery() {
        viewModelScope.launch {
            val dayId = _currentDayId.value ?: return@launch
            val day   = travelDayRepository.getAllDays().first()
                .firstOrNull { it.id == dayId } ?: return@launch

            _isImporting.value = true
            _importMessage.value = null
            try {
                val count = mediaRepository.importMediaForDay(day)
                _importMessage.value = if (count > 0)
                    "$count item${if (count > 1) "s" else ""} imported"
                else
                    "No new media found for today"
            } catch (e: Exception) {
                _importMessage.value = "Import failed: ${e.message}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun clearMessage() { _importMessage.value = null }
}
