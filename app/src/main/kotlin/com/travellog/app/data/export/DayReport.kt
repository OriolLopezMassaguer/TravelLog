package com.travellog.app.data.export

import com.travellog.app.data.db.entity.MediaItem
import com.travellog.app.data.db.entity.PointOfInterest
import com.travellog.app.data.db.entity.TravelDay
import com.travellog.app.data.db.entity.VoiceNote

data class DayReport(
    val day: TravelDay,
    val checkedInPois: List<PointOfInterest>,
    val mediaItems: List<MediaItem>,         // photos + videos only (no voice_note)
    val voiceNotes: List<VoiceNote>,
)
