package com.travellog.app.ui.screens.media

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.travellog.app.audio.VoskTranscriptionService
import com.travellog.app.data.db.entity.MediaItem
import com.travellog.app.data.db.entity.VoiceNote
import com.travellog.app.data.repository.MediaRepository
import com.travellog.app.data.repository.TravelDayRepository
import com.travellog.app.data.repository.VoiceNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaGalleryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val voiceNoteRepository: VoiceNoteRepository,
    private val travelDayRepository: TravelDayRepository,
    private val voskService: VoskTranscriptionService,
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

    fun deleteVoiceNote(voiceNote: VoiceNote) {
        viewModelScope.launch {
            voiceNoteRepository.deleteVoiceNote(voiceNote.id)
        }
    }

    private val _transcribingIds = MutableStateFlow<Set<Long>>(emptySet())
    val transcribingIds: StateFlow<Set<Long>> = _transcribingIds.asStateFlow()

    fun transcribeVoiceNote(note: VoiceNote) {
        if (_transcribingIds.value.contains(note.id)) return
        viewModelScope.launch {
            _transcribingIds.update { it + note.id }
            if (voskService.isModelReady()) {
                voiceNoteRepository.transcribeWithVoskAndSave(note)
            } else {
                voiceNoteRepository.transcribeWithWhisperAndSave(note)
            }
            _transcribingIds.update { it - note.id }
        }
    }

    fun clearMessage() { _importMessage.value = null }

    // ── Playback ──────────────────────────────────────────────────────────────

    private val _playingNoteId = MutableStateFlow<Long?>(null)
    val playingNoteId: StateFlow<Long?> = _playingNoteId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().also { p ->
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        _playingNoteId.value = null
                        _isPlaying.value = false
                    }
                }
            })
        }
    }

    fun playOrPause(note: VoiceNote) {
        if (_playingNoteId.value == note.id) {
            if (player.isPlaying) player.pause() else player.play()
        } else {
            player.stop()
            player.setMediaItem(
                androidx.media3.common.MediaItem.fromUri(Uri.parse(note.filePath))
            )
            player.prepare()
            player.play()
            _playingNoteId.value = note.id
        }
    }

    fun stopPlayback() {
        player.stop()
        _playingNoteId.value = null
        _isPlaying.value = false
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
