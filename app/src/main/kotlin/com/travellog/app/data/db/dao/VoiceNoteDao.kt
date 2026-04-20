package com.travellog.app.data.db.dao

import androidx.room.*
import com.travellog.app.data.db.entity.VoiceNote
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceNoteDao {

    @Query("SELECT * FROM voice_notes WHERE day_id = :dayId ORDER BY recorded_at ASC")
    fun getVoiceNotesForDay(dayId: Long): Flow<List<VoiceNote>>

    @Query("SELECT * FROM voice_notes WHERE poi_id = :poiId ORDER BY recorded_at ASC")
    fun getVoiceNotesForPoi(poiId: Long): Flow<List<VoiceNote>>

    @Query("SELECT * FROM voice_notes WHERE id = :id")
    suspend fun getById(id: Long): VoiceNote?

    @Query("SELECT * FROM voice_notes WHERE day_id = :dayId ORDER BY recorded_at ASC")
    suspend fun getVoiceNotesForDayOnce(dayId: Long): List<VoiceNote>

    @Insert
    suspend fun insert(voiceNote: VoiceNote): Long

    @Update
    suspend fun update(voiceNote: VoiceNote)

    @Query("UPDATE voice_notes SET poi_id = :poiId WHERE id = :id")
    suspend fun associateToPoi(id: Long, poiId: Long?)

    @Query("UPDATE voice_notes SET transcription = :text WHERE id = :id")
    suspend fun setTranscription(id: Long, text: String)

    @Delete
    suspend fun delete(voiceNote: VoiceNote)
}
