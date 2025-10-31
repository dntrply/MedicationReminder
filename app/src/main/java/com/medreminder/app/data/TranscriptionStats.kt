package com.medreminder.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity to track statistics for audio transcription operations.
 * Records detailed metrics about transcription success, failures, and performance.
 */
@Entity(
    tableName = "transcription_stats",
    indices = [
        Index(value = ["medicationId"]),
        Index(value = ["status"])
    ]
)
data class TranscriptionStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Reference to medication
    val medicationId: Long,
    val medicationName: String,

    // Transcription status
    val status: String, // "success", "failed", "pending"

    // Timing information
    val startTime: Long, // Timestamp when transcription started
    val endTime: Long? = null, // Timestamp when transcription completed (null if pending)
    val durationMs: Long? = null, // Duration in milliseconds (null if pending)

    // Audio file information
    val audioFilePath: String,
    val audioFileSizeBytes: Long,
    val audioDurationSeconds: Float? = null, // Duration of audio in seconds

    // Transcription result
    val transcriptionText: String? = null,
    val transcriptionLength: Int? = null, // Length of transcribed text in characters
    val detectedLanguage: String? = null,

    // Error information
    val errorMessage: String? = null,

    // Engine information
    val engineId: String? = null, // Which engine was used (e.g., "whisper-tiny")

    // Performance metrics
    val processingSpeedRatio: Float? = null // Duration / Audio length (lower is faster)
)
