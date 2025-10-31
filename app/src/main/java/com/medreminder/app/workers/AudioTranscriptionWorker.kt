package com.medreminder.app.workers

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.medreminder.app.data.MedicationDatabase
import com.medreminder.app.data.TranscriptionStats
import com.medreminder.app.utils.AudioTranscriptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Background worker for transcribing audio recordings of medication notes.
 * Runs asynchronously to avoid blocking the UI.
 */
class AudioTranscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AudioTranscriptionWorker"

        // Input data keys
        const val KEY_MEDICATION_ID = "medication_id"
        const val KEY_AUDIO_PATH = "audio_path"

        // Output data keys
        const val KEY_SUCCESS = "success"
        const val KEY_TRANSCRIPTION = "transcription"
        const val KEY_LANGUAGE = "language"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var statsId: Long? = null

        try {
            val medicationId = inputData.getLong(KEY_MEDICATION_ID, -1L)
            val audioPath = inputData.getString(KEY_AUDIO_PATH)

            if (medicationId == -1L || audioPath.isNullOrEmpty()) {
                Log.e(TAG, "Invalid input data: medicationId=$medicationId, audioPath=$audioPath")
                return@withContext Result.failure()
            }

            Log.d(TAG, "Starting transcription for medication $medicationId")

            // Get database and transcription service
            val database = MedicationDatabase.getDatabase(applicationContext)
            val transcriptionService = AudioTranscriptionService(applicationContext)
            val statsDao = database.transcriptionStatsDao()

            // Get medication details
            val medication = database.medicationDao().getMedicationById(medicationId)
            if (medication == null) {
                Log.e(TAG, "Medication $medicationId not found in database")
                return@withContext Result.failure()
            }

            // Get audio file metadata
            val audioFile = File(audioPath)
            val audioFileSizeBytes = if (audioFile.exists()) audioFile.length() else 0L
            val audioDurationSeconds = getAudioDuration(audioPath)

            // Check if a pending stats entry already exists for this medication and audio path
            val existingStats = statsDao.getStatsByMedicationIdAndPath(medicationId, audioPath)

            if (existingStats != null) {
                // Use existing stats entry
                statsId = existingStats.id
                Log.d(TAG, "Found existing stats entry $statsId for medication $medicationId")
            } else {
                // Create initial stats entry with "pending" status
                val pendingStats = TranscriptionStats(
                    medicationId = medicationId,
                    medicationName = medication.name,
                    status = "pending",
                    startTime = startTime,
                    audioFilePath = audioPath,
                    audioFileSizeBytes = audioFileSizeBytes,
                    audioDurationSeconds = audioDurationSeconds
                )
                statsId = statsDao.insertStats(pendingStats)
                Log.d(TAG, "Created new stats entry $statsId for medication $medicationId")
            }

            // Transcribe the audio
            val transcriptionResult = transcriptionService.transcribeAudio(audioPath)
            val endTime = System.currentTimeMillis()
            val durationMs = endTime - startTime

            if (transcriptionResult != null) {
                val (transcription, language) = transcriptionResult

                // Calculate processing speed ratio (lower is faster)
                val processingSpeedRatio = if (audioDurationSeconds != null && audioDurationSeconds > 0) {
                    (durationMs / 1000f) / audioDurationSeconds
                } else null

                // Update medication with transcription
                val updatedMedication = medication.copy(
                    audioTranscription = transcription,
                    audioTranscriptionLanguage = language
                )
                database.medicationDao().update(updatedMedication)

                // Update stats with success
                val successStats = TranscriptionStats(
                    id = statsId,
                    medicationId = medicationId,
                    medicationName = medication.name,
                    status = "success",
                    startTime = startTime,
                    endTime = endTime,
                    durationMs = durationMs,
                    audioFilePath = audioPath,
                    audioFileSizeBytes = audioFileSizeBytes,
                    audioDurationSeconds = audioDurationSeconds,
                    transcriptionText = transcription,
                    transcriptionLength = transcription.length,
                    detectedLanguage = language,
                    engineId = "whisper-tiny",
                    processingSpeedRatio = processingSpeedRatio
                )
                statsDao.updateStats(successStats)

                Log.i(TAG, "Successfully transcribed audio for medication $medicationId: ${transcription.length} chars in ${durationMs}ms (speed ratio: $processingSpeedRatio)")

                return@withContext Result.success(
                    workDataOf(
                        KEY_SUCCESS to true,
                        KEY_TRANSCRIPTION to transcription,
                        KEY_LANGUAGE to language
                    )
                )
            } else {
                // Transcription failed - record failure stats
                val endTime = System.currentTimeMillis()
                val durationMs = endTime - startTime

                val failureStats = TranscriptionStats(
                    id = statsId,
                    medicationId = medicationId,
                    medicationName = medication.name,
                    status = "failed",
                    startTime = startTime,
                    endTime = endTime,
                    durationMs = durationMs,
                    audioFilePath = audioPath,
                    audioFileSizeBytes = audioFileSizeBytes,
                    audioDurationSeconds = audioDurationSeconds,
                    errorMessage = "Transcription returned null",
                    engineId = "whisper-tiny"
                )
                statsDao.updateStats(failureStats)

                Log.d(TAG, "Transcription returned null for medication $medicationId (silent failure)")
                return@withContext Result.success(
                    workDataOf(KEY_SUCCESS to false)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription work", e)

            // Record failure stats
            try {
                if (statsId != null) {
                    val medicationId = inputData.getLong(KEY_MEDICATION_ID, -1L)
                    val audioPath = inputData.getString(KEY_AUDIO_PATH) ?: ""
                    val database = MedicationDatabase.getDatabase(applicationContext)
                    val medication = database.medicationDao().getMedicationById(medicationId)

                    if (medication != null) {
                        val audioFile = File(audioPath)
                        val endTime = System.currentTimeMillis()

                        val failureStats = TranscriptionStats(
                            id = statsId,
                            medicationId = medicationId,
                            medicationName = medication.name,
                            status = "failed",
                            startTime = startTime,
                            endTime = endTime,
                            durationMs = endTime - startTime,
                            audioFilePath = audioPath,
                            audioFileSizeBytes = if (audioFile.exists()) audioFile.length() else 0L,
                            errorMessage = e.message ?: e.javaClass.simpleName,
                            engineId = "whisper-tiny"
                        )
                        database.transcriptionStatsDao().updateStats(failureStats)
                    }
                }
            } catch (statsError: Exception) {
                Log.e(TAG, "Error recording failure stats", statsError)
            }

            // Silent failure - don't retry
            return@withContext Result.success(
                workDataOf(KEY_SUCCESS to false)
            )
        }
    }

    /**
     * Get audio file duration in seconds using MediaMetadataRetriever.
     */
    private fun getAudioDuration(audioPath: String): Float? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(audioPath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()

            durationStr?.toLongOrNull()?.let { it / 1000f }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get audio duration for $audioPath", e)
            null
        }
    }
}
