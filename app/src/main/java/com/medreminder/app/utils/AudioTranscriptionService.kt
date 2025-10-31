package com.medreminder.app.utils

import android.content.Context
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.medreminder.app.transcription.SpeechToTextEngine
import com.medreminder.app.transcription.TranscriptionEngineFactory
import com.medreminder.app.transcription.models.TranscriptionOutcome
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Service for transcribing audio recordings and translating them using ML Kit.
 * Handles on-device speech recognition and translation for medication audio notes.
 * Uses a pluggable architecture to support multiple transcription engines (Whisper, Google Cloud, etc.)
 */
class AudioTranscriptionService(private val context: Context) {

    companion object {
        private const val TAG = "AudioTranscriptionService"

        // Language code mapping for ML Kit
        val SUPPORTED_LANGUAGES = mapOf(
            "en" to TranslateLanguage.ENGLISH,
            "hi" to TranslateLanguage.HINDI,
            "gu" to TranslateLanguage.GUJARATI,
            "mr" to TranslateLanguage.MARATHI
        )
    }

    // Lazy-initialized transcription engine
    private var engine: SpeechToTextEngine? = null

    /**
     * Transcribe audio file to text using the best available engine.
     * The engine is auto-selected by the factory based on device capabilities.
     *
     * Supported engines:
     * - Whisper Tiny (whisper.cpp): On-device, multilingual, ~75MB ggml model
     * - Google Cloud (future): Cloud-based, highest accuracy, requires API key
     * - NoOp: Graceful fallback when no engine available
     *
     * @param audioFilePath Path to the audio file
     * @return Pair of transcription text and detected language code, or null if failed
     */
    suspend fun transcribeAudio(audioFilePath: String): Pair<String, String>? {
        return try {
            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) {
                Log.e(TAG, "Audio file does not exist: $audioFilePath")
                return null
            }

            // Lazy initialize engine on first use
            if (engine == null) {
                Log.d(TAG, "Initializing transcription engine...")
                engine = TranscriptionEngineFactory.createEngine(context)

                // Initialize the selected engine
                when (val initResult = engine?.initialize(context)) {
                    is TranscriptionOutcome.Success -> {
                        Log.i(TAG, "Engine initialized: ${engine?.engineId}")
                    }
                    is TranscriptionOutcome.Failure -> {
                        Log.w(TAG, "Engine initialization failed: ${initResult.error}")
                        // Continue anyway - engine might work without full initialization
                    }
                    null -> {
                        Log.w(TAG, "No engine available")
                        return null
                    }
                }
            }

            // Transcribe using the selected engine
            Log.d(TAG, "Transcribing with engine: ${engine?.engineId}")
            when (val result = engine?.transcribe(audioFilePath)) {
                is TranscriptionOutcome.Success -> {
                    val transcriptionResult = result.data
                    Log.i(TAG, "Transcription successful: ${transcriptionResult.text.take(50)}...")
                    return transcriptionResult.text to transcriptionResult.languageCode
                }
                is TranscriptionOutcome.Failure -> {
                    Log.w(TAG, "Transcription failed: ${result.error}")
                    return null // Silent failure as requested
                }
                null -> {
                    Log.w(TAG, "No engine available for transcription")
                    return null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing audio", e)
            null // Silent failure
        }
    }

    /**
     * Detect language from audio file.
     * This is a placeholder - actual implementation would analyze audio content.
     * For now, we'll return English as default.
     *
     * @param audioFilePath Path to the audio file
     * @return Language code (e.g., "en", "hi", "gu", "mr")
     */
    private suspend fun detectLanguageFromAudio(audioFilePath: String): String {
        // TODO: Implement audio-based language detection
        // This would involve:
        // 1. First transcribing a portion of the audio
        // 2. Using ML Kit Language Identification on the transcribed text
        // For now, return English as default
        return "en"
    }

    /**
     * Detect language from text using ML Kit Language Identification.
     *
     * @param text Text to analyze
     * @return Language code (e.g., "en", "hi", "gu", "mr")
     */
    suspend fun detectLanguageFromText(text: String): String {
        return try {
            val languageIdentifier = LanguageIdentification.getClient(
                LanguageIdentificationOptions.Builder()
                    .setConfidenceThreshold(0.5f)
                    .build()
            )

            val languageCode = languageIdentifier.identifyLanguage(text).await()

            if (languageCode == "und") {
                Log.w(TAG, "Language could not be determined, defaulting to English")
                "en"
            } else {
                // Map ML Kit language codes to our supported languages
                when (languageCode) {
                    "en" -> "en"
                    "hi" -> "hi"
                    "gu" -> "gu"
                    "mr" -> "mr"
                    else -> {
                        Log.w(TAG, "Detected unsupported language: $languageCode, defaulting to English")
                        "en"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting language from text", e)
            "en" // Default to English on error
        }
    }

    /**
     * Translate text from source language to target language.
     * Downloads translation models on-demand if not already available.
     *
     * @param text Text to translate
     * @param sourceLanguage Source language code (e.g., "en", "hi")
     * @param targetLanguage Target language code (e.g., "en", "hi")
     * @return Translated text, or null if translation failed
     */
    suspend fun translateText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String? {
        if (sourceLanguage == targetLanguage) {
            return text // No translation needed
        }

        val sourceLang = SUPPORTED_LANGUAGES[sourceLanguage]
        val targetLang = SUPPORTED_LANGUAGES[targetLanguage]

        if (sourceLang == null || targetLang == null) {
            Log.e(TAG, "Unsupported language pair: $sourceLanguage -> $targetLanguage")
            return null
        }

        return try {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()

            val translator = Translation.getClient(options)

            // Download model if needed (only on WiFi to save data)
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()

            translator.downloadModelIfNeeded(conditions).await()

            // Translate the text
            val translatedText = translator.translate(text).await()

            Log.d(TAG, "Successfully translated from $sourceLanguage to $targetLanguage")
            translatedText
        } catch (e: Exception) {
            Log.e(TAG, "Error translating text from $sourceLanguage to $targetLanguage", e)
            null // Silent failure
        }
    }

    /**
     * Check if translation model is downloaded for a language pair.
     *
     * @param sourceLanguage Source language code
     * @param targetLanguage Target language code
     * @return True if model is downloaded, false otherwise
     */
    suspend fun isModelDownloaded(sourceLanguage: String, targetLanguage: String): Boolean {
        if (sourceLanguage == targetLanguage) return true

        val sourceLang = SUPPORTED_LANGUAGES[sourceLanguage] ?: return false
        val targetLang = SUPPORTED_LANGUAGES[targetLanguage] ?: return false

        return try {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()

            val translator = Translation.getClient(options)

            // This will return immediately if model is not downloaded
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()

            // Check if download is needed
            try {
                translator.downloadModelIfNeeded(conditions).await()
                true
            } catch (e: Exception) {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if model is downloaded", e)
            false
        }
    }

    /**
     * Delete translation models to free up storage space.
     *
     * @param sourceLanguage Source language code
     * @param targetLanguage Target language code
     */
    suspend fun deleteModel(sourceLanguage: String, targetLanguage: String) {
        val sourceLang = SUPPORTED_LANGUAGES[sourceLanguage] ?: return
        val targetLang = SUPPORTED_LANGUAGES[targetLanguage] ?: return

        try {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()

            val translator = Translation.getClient(options)
            translator.close()

            Log.d(TAG, "Deleted translation model: $sourceLanguage -> $targetLanguage")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting translation model", e)
        }
    }
}
