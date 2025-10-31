package com.medreminder.app.transcription

import android.content.Context
import android.util.Log
import com.medreminder.app.BuildConfig
import com.medreminder.app.transcription.engines.NoOpEngine
import com.medreminder.app.transcription.engines.WhisperTinyEngine

/**
 * Factory for creating speech-to-text transcription engines.
 * Automatically selects the best available engine based on device capabilities,
 * network status, and configuration.
 */
object TranscriptionEngineFactory {

    private const val TAG = "TranscriptionEngineFactory"

    /**
     * Auto-select the best available transcription engine.
     * Selection priority:
     * 1. Google Cloud (if API key configured and network available)
     * 2. Whisper Tiny (if model downloaded or downloadable)
     * 3. NoOpEngine (graceful fallback)
     *
     * @param context Application context
     * @return The best available engine
     */
    suspend fun createEngine(context: Context): SpeechToTextEngine {
        Log.d(TAG, "Auto-selecting transcription engine...")

        // Try engines in priority order
        val engines = listOf(
            // Future: Google Cloud engine
            // createGoogleCloudEngineIfConfigured(),

            // Current: Whisper Tiny engine
            createWhisperEngineIfAvailable(context),

            // Fallback: No-op engine
            createNoOpEngine()
        )

        // Return first available engine
        for (engine in engines.filterNotNull()) {
            if (engine.isAvailable(context)) {
                Log.i(TAG, "Selected engine: ${engine.engineId} (${engine.engineName})")
                return engine
            }
        }

        // Should never reach here due to NoOpEngine always being available
        Log.w(TAG, "No engine available, using NoOpEngine")
        return NoOpEngine()
    }

    /**
     * Create a specific engine by ID.
     * Useful for testing or forced engine selection.
     *
     * @param engineId Engine identifier (e.g., "whisper-tiny", "google-cloud")
     * @return The requested engine, or null if not found/available
     */
    suspend fun createEngineById(engineId: String, context: Context): SpeechToTextEngine? {
        return when (engineId) {
            "whisper-tiny" -> createWhisperEngineIfAvailable(context)
            // Future engines:
            // "google-cloud" -> createGoogleCloudEngineIfConfigured()
            "noop" -> NoOpEngine()
            else -> {
                Log.w(TAG, "Unknown engine ID: $engineId")
                null
            }
        }
    }

    /**
     * Get list of all available engine IDs.
     * Useful for configuration or debugging.
     */
    suspend fun getAvailableEngineIds(context: Context): List<String> {
        val engines = mutableListOf<String>()

        // Check which engines are available
        createWhisperEngineIfAvailable(context)?.let { engines.add(it.engineId) }
        // Future: createGoogleCloudEngineIfConfigured()?.let { engines.add(it.engineId) }

        return engines
    }

    /**
     * Create Whisper Tiny engine if available.
     * Checks for sufficient device resources.
     */
    private suspend fun createWhisperEngineIfAvailable(context: Context): SpeechToTextEngine? {
        return try {
            val engine = WhisperTinyEngine()
            if (engine.isAvailable(context)) {
                Log.d(TAG, "Whisper Tiny engine is available")
                engine
            } else {
                Log.d(TAG, "Whisper Tiny engine not available on this device")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Whisper engine", e)
            null
        }
    }

    /**
     * Create Google Cloud engine if API key is configured.
     * Checks BuildConfig for API key.
     */
    private fun createGoogleCloudEngineIfConfigured(): SpeechToTextEngine? {
        return try {
            // Check if API key is configured in BuildConfig
            val apiKey = getGoogleCloudApiKey()
            if (apiKey.isNullOrEmpty()) {
                Log.d(TAG, "Google Cloud API key not configured")
                return null
            }

            // TODO: Uncomment when GoogleCloudEngine is implemented
            // GoogleCloudEngine(GoogleCloudConfig(apiKey = apiKey))

            // Temporary: Return null until implementation is complete
            Log.d(TAG, "Google Cloud engine not yet implemented")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Google Cloud engine", e)
            null
        }
    }

    /**
     * Create fallback no-op engine.
     */
    private fun createNoOpEngine(): SpeechToTextEngine {
        return NoOpEngine()
    }

    /**
     * Get Google Cloud API key from BuildConfig.
     * Returns null if not configured.
     */
    private fun getGoogleCloudApiKey(): String? {
        return try {
            // Will be configured in build.gradle.kts
            val field = BuildConfig::class.java.getDeclaredField("GOOGLE_CLOUD_API_KEY")
            field.get(null) as? String
        } catch (e: NoSuchFieldException) {
            // Field doesn't exist yet
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading API key from BuildConfig", e)
            null
        }
    }
}
