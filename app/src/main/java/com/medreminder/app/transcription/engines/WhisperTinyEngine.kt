package com.medreminder.app.transcription.engines

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.medreminder.app.transcription.SpeechToTextEngine
import com.medreminder.app.transcription.WhisperModelManager
import com.medreminder.app.transcription.models.TranscriptionError
import com.medreminder.app.transcription.models.TranscriptionOutcome
import com.medreminder.app.transcription.models.TranscriptionResult
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Whisper Tiny speech-to-text engine using whisper.cpp JNI bindings.
 * Based on the whisper.cpp C++ library: https://github.com/ggerganov/whisper.cpp
 * Using JNI bindings from: https://github.com/GiviMAD/whisper-jni
 *
 * Model Details:
 * - Model size: ~75MB (ggml-tiny.bin)
 * - Format: GGML (used by whisper.cpp)
 * - Languages: English, Hindi, Gujarati, Marathi, and 95+ others
 * - Speed: ~2 seconds for 30-second audio on modern devices
 *
 * Configuration:
 * - Model path: internal storage (downloads on first use)
 * - Requires: 1900MB+ RAM, WiFi for initial download
 * - Processing: Only runs when device is charging (WorkManager constraint)
 */
class WhisperTinyEngine(
    private val config: WhisperConfig = WhisperConfig.default()
) : SpeechToTextEngine {

    companion object {
        private const val TAG = "WhisperTinyEngine"

        // Minimum RAM required (in MB)
        // Set to 1900MB to allow testing on most emulators (which often have ~2GB)
        private const val MIN_RAM_MB = 1900
    }

    override val engineId = "whisper-tiny"
    override val engineName = "Whisper Tiny (whisper.cpp)"

    // Whisper.cpp JNI context (initialized when model is loaded)
    private var whisperContext: WhisperContext? = null

    /**
     * Check if this engine can run on the current device.
     * Requirements:
     * - Minimum 2GB RAM
     * - Model files downloaded or space/connectivity to download
     */
    override suspend fun isAvailable(context: Context): Boolean {
        return try {
            // Check RAM
            if (!hasMinimumRAM(context)) {
                Log.d(TAG, "Device has insufficient RAM for Whisper Tiny")
                return false
            }

            // Check if models are already downloaded
            if (WhisperModelManager.areModelsDownloaded(context)) {
                Log.d(TAG, "Whisper models already downloaded")
                return true
            }

            // Check if we can download models
            val downloadReadiness = WhisperModelManager.canDownloadModels(context)
            val canDownload = downloadReadiness == WhisperModelManager.DownloadReadiness.READY

            Log.d(TAG, "Whisper Tiny available: models not downloaded, can download: $canDownload ($downloadReadiness)")
            canDownload
        } catch (e: Exception) {
            Log.e(TAG, "Error checking availability", e)
            false
        }
    }

    /**
     * Initialize the engine by downloading models if needed.
     */
    override suspend fun initialize(context: Context): TranscriptionOutcome<Unit> {
        return try {
            Log.d(TAG, "Initializing Whisper Tiny engine...")

            // Check if models are already downloaded
            if (!WhisperModelManager.areModelsDownloaded(context)) {
                Log.i(TAG, "Models not found, attempting download...")

                // Download models with progress tracking
                when (val result = WhisperModelManager.downloadModels(context) { progress ->
                    Log.d(TAG, "Download progress: $progress%")
                }) {
                    is WhisperModelManager.DownloadResult.Success -> {
                        Log.i(TAG, "Models downloaded successfully")
                    }
                    is WhisperModelManager.DownloadResult.Failed -> {
                        Log.e(TAG, "Model download failed: ${result.reason}")
                        return TranscriptionOutcome.Failure(TranscriptionError.MODEL_NOT_DOWNLOADED)
                    }
                }
            } else {
                Log.d(TAG, "Models already downloaded")
            }

            // Initialize whisper.cpp JNI context
            val modelFile = WhisperModelManager.getModelFile(context)
            Log.d(TAG, "Initializing Whisper context from: ${modelFile.absolutePath}")

            whisperContext = WhisperContext.createContextFromFile(modelFile.absolutePath)
            Log.i(TAG, "Whisper Tiny engine initialized successfully")

            TranscriptionOutcome.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing engine", e)
            TranscriptionOutcome.Failure(TranscriptionError.INITIALIZATION_FAILED)
        }
    }

    /**
     * Transcribe an audio file to text.
     * The engine will automatically detect the language using ML Kit.
     */
    override suspend fun transcribe(audioPath: String): TranscriptionOutcome<TranscriptionResult> {
        return try {
            val audioFile = File(audioPath)
            if (!audioFile.exists()) {
                Log.e(TAG, "Audio file does not exist: $audioPath")
                return TranscriptionOutcome.Failure(TranscriptionError.AUDIO_FILE_INVALID)
            }

            Log.d(TAG, "Transcribing audio: $audioPath")

            // Check if context is initialized
            val context = whisperContext
            if (context == null) {
                Log.e(TAG, "Whisper context not initialized")
                return TranscriptionOutcome.Failure(TranscriptionError.INITIALIZATION_FAILED)
            }

            // Load and convert audio file to 16kHz mono FloatArray
            Log.d(TAG, "Loading audio file...")
            val audioData = com.medreminder.app.utils.AudioProcessor.decodeAudioFile(audioFile)
            Log.d(TAG, "Loaded ${audioData.size} audio samples (${audioData.size / 16} ms)")

            // Transcribe using whisper.cpp
            Log.d(TAG, "Starting transcription...")
            val startTime = System.currentTimeMillis()
            val transcription = context.transcribeData(audioData, printTimestamp = false)
            val elapsedTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "Transcription completed in ${elapsedTime}ms: $transcription")

            // Detect language using ML Kit
            val languageCode = detectLanguage(transcription)
            Log.d(TAG, "Detected language: $languageCode")

            return TranscriptionOutcome.Success(
                TranscriptionResult(
                    text = transcription.trim(),
                    languageCode = languageCode,
                    confidence = 1.0f, // Whisper doesn't provide confidence scores
                    engineId = engineId
                )
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid audio file format", e)
            TranscriptionOutcome.Failure(TranscriptionError.AUDIO_FILE_INVALID)
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)
            TranscriptionOutcome.Failure(TranscriptionError.UNKNOWN)
        }
    }

    /**
     * Detect language using ML Kit Language Identification.
     * Returns ISO 639-1 language code (e.g., "en", "hi", "gu", "mr").
     */
    private suspend fun detectLanguage(text: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            return@withContext "en" // Default to English for empty text
        }

        return@withContext try {
            val languageIdentifier = LanguageIdentification.getClient()
            val result = Tasks.await(languageIdentifier.identifyLanguage(text))

            if (result == "und") { // "und" = undetermined language
                Log.w(TAG, "Could not determine language, defaulting to English")
                "en"
            } else {
                Log.d(TAG, "Language identified: $result")
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting language, defaulting to English", e)
            "en"
        }
    }

    override fun cleanup() {
        try {
            // Clean up whisper.cpp JNI context
            whisperContext?.let {
                kotlinx.coroutines.runBlocking {
                    it.release()
                }
                whisperContext = null
            }
            Log.d(TAG, "Whisper Tiny engine cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    override fun estimateProcessingTime(audioDurationSeconds: Int): Int {
        // Whisper Tiny on modern devices: ~15x real-time (2s for 30s audio)
        // On low-end devices: ~30x real-time (1 minute for 30s audio)
        return audioDurationSeconds * 2 // Conservative estimate
    }

    /**
     * Check if device has minimum required RAM.
     */
    private fun hasMinimumRAM(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalMemoryMB = memoryInfo.totalMem / (1024 * 1024)
        return totalMemoryMB >= MIN_RAM_MB
    }

    /**
     * Configuration for Whisper Tiny engine.
     */
    data class WhisperConfig(
        val modelPath: String? = null,
        val vocabPath: String? = null,
        val multilingual: Boolean = true
    ) {
        companion object {
            fun default() = WhisperConfig()
        }
    }
}
