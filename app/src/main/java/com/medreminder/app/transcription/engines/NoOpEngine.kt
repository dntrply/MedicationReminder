package com.medreminder.app.transcription.engines

import android.content.Context
import android.util.Log
import com.medreminder.app.transcription.SpeechToTextEngine
import com.medreminder.app.transcription.models.TranscriptionError
import com.medreminder.app.transcription.models.TranscriptionOutcome
import com.medreminder.app.transcription.models.TranscriptionResult

/**
 * No-operation transcription engine that gracefully fails.
 * Used as a fallback when no real engine is available.
 * Ensures the app continues to function even without transcription capability.
 */
class NoOpEngine : SpeechToTextEngine {

    companion object {
        private const val TAG = "NoOpEngine"
    }

    override val engineId = "noop"
    override val engineName = "No Transcription"

    override suspend fun isAvailable(context: Context): Boolean {
        // Always available as a fallback
        return true
    }

    override suspend fun initialize(context: Context): TranscriptionOutcome<Unit> {
        Log.d(TAG, "NoOpEngine initialized - transcription disabled")
        return TranscriptionOutcome.Success(Unit)
    }

    override suspend fun transcribe(audioPath: String): TranscriptionOutcome<TranscriptionResult> {
        Log.d(TAG, "NoOpEngine: Transcription requested but no engine available")
        return TranscriptionOutcome.Failure(TranscriptionError.MODEL_NOT_DOWNLOADED)
    }

    override fun cleanup() {
        // Nothing to clean up
    }

    override fun estimateProcessingTime(audioDurationSeconds: Int): Int {
        return 0 // Instant "processing"
    }
}
