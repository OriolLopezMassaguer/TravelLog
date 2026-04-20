package com.travellog.app.data.export

sealed class TimelineEvent {
    abstract val recordedAt: Long
    abstract val latitude: Double?
    abstract val longitude: Double?

    data class CheckInEvent(
        override val recordedAt: Long,
        override val latitude: Double,
        override val longitude: Double,
        val poiName: String,
        val poiCategory: String,
        val poiAddress: String?,
    ) : TimelineEvent()

    data class PhotoEvent(
        override val recordedAt: Long,
        override val latitude: Double?,
        override val longitude: Double?,
        val filePath: String,
    ) : TimelineEvent()

    data class VideoEvent(
        override val recordedAt: Long,
        override val latitude: Double?,
        override val longitude: Double?,
        val durationSeconds: Int?,
    ) : TimelineEvent()

    data class VoiceNoteEvent(
        override val recordedAt: Long,
        override val latitude: Double?,
        override val longitude: Double?,
        val durationSeconds: Int,
        val transcription: String?,
    ) : TimelineEvent()
}
