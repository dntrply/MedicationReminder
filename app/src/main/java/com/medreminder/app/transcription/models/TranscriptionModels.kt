package com.medreminder.app.transcription.models

/**
 * Result of a transcription operation
 */
data class TranscriptionResult(
    val text: String,
    val languageCode: String,
    val confidence: Float = 1.0f,
    val engineId: String = "unknown"
)

/**
 * Sealed class for transcription operation results
 */
sealed class TranscriptionOutcome<out T> {
    data class Success<T>(val data: T) : TranscriptionOutcome<T>()
    data class Failure<T>(val error: TranscriptionError) : TranscriptionOutcome<T>()
}

/**
 * Possible transcription errors
 */
enum class TranscriptionError {
    /** Model not downloaded or available */
    MODEL_NOT_DOWNLOADED,

    /** Network unavailable when required */
    NETWORK_UNAVAILABLE,

    /** Audio file is invalid or corrupted */
    AUDIO_FILE_INVALID,

    /** API key missing or invalid */
    API_KEY_MISSING,

    /** API quota exceeded */
    QUOTA_EXCEEDED,

    /** Device lacks required capabilities (RAM, CPU) */
    DEVICE_INCOMPATIBLE,

    /** Engine initialization failed */
    INITIALIZATION_FAILED,

    /** Unknown error occurred */
    UNKNOWN
}
