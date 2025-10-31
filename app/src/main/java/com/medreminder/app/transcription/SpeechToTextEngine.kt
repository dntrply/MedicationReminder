package com.medreminder.app.transcription

import android.content.Context
import com.medreminder.app.transcription.models.TranscriptionOutcome
import com.medreminder.app.transcription.models.TranscriptionResult

/**
 * Interface for speech-to-text transcription engines.
 * All transcription engines must implement this interface to be pluggable.
 */
interface SpeechToTextEngine {
    /**
     * Unique identifier for this engine (e.g., "whisper-tiny", "google-cloud")
     */
    val engineId: String

    /**
     * Human-readable name for this engine
     */
    val engineName: String

    /**
     * Check if this engine can run on the current device/environment.
     * This includes checking for:
     * - Required device capabilities (RAM, CPU)
     * - Network availability (if needed)
     * - Model availability
     * - API keys (if needed)
     *
     * @param context Application context
     * @return true if engine can be used, false otherwise
     */
    suspend fun isAvailable(context: Context): Boolean

    /**
     * Initialize the engine. This may include:
     * - Downloading models
     * - Validating API keys
     * - Loading native libraries
     *
     * @param context Application context
     * @return Success if initialized, Failure with error code
     */
    suspend fun initialize(context: Context): TranscriptionOutcome<Unit>

    /**
     * Transcribe an audio file to text.
     * The engine should automatically detect the language.
     *
     * @param audioPath Absolute path to the audio file
     * @return Success with TranscriptionResult, or Failure with error code
     */
    suspend fun transcribe(audioPath: String): TranscriptionOutcome<TranscriptionResult>

    /**
     * Clean up any resources held by this engine.
     * Called when engine is no longer needed.
     */
    fun cleanup()

    /**
     * Get estimated processing time in seconds for a given audio duration.
     * Useful for UI progress indication.
     *
     * @param audioDurationSeconds Duration of audio in seconds
     * @return Estimated processing time in seconds
     */
    fun estimateProcessingTime(audioDurationSeconds: Int): Int {
        // Default: assume real-time processing
        return audioDurationSeconds
    }
}
