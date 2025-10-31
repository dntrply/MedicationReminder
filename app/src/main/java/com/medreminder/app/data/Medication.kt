package com.medreminder.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medications",
    indices = [Index(value = ["profileId"])]
)
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long, // Foreign key to Profile
    val name: String,
    val photoUri: String? = null,
    val audioNotePath: String? = null, // Path to audio instruction file
    val audioTranscription: String? = null, // Transcribed text from audio
    val audioTranscriptionLanguage: String? = null, // Language code of transcription (e.g., "en", "hi", "gu", "mr")
    val dosage: String? = null,
    val notes: String? = null,
    val reminderTimesJson: String? = null, // JSON string of List<ReminderTime>
    val createdAt: Long = System.currentTimeMillis()
)
