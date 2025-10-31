package com.medreminder.app.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.medreminder.app.data.MedicationDatabase
import com.medreminder.app.data.TranscriptionStats
import com.medreminder.app.workers.AudioTranscriptionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Helper class to schedule audio transcription work.
 */
object TranscriptionScheduler {

    private const val WORK_NAME_PREFIX = "audio_transcription_"

    /**
     * Schedule background transcription for a medication's audio note.
     * Creates a pending stats entry immediately, then schedules the actual transcription work.
     *
     * @param context Application context
     * @param medicationId ID of the medication
     * @param audioPath Path to the audio file
     */
    fun scheduleTranscription(
        context: Context,
        medicationId: Long,
        audioPath: String
    ) {
        // Create pending stats entry immediately in a coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = MedicationDatabase.getDatabase(context)
                val statsDao = database.transcriptionStatsDao()
                val medicationDao = database.medicationDao()

                // Get medication details
                val medication = medicationDao.getMedicationById(medicationId)
                if (medication == null) {
                    return@launch
                }

                // Get audio file metadata
                val audioFile = File(audioPath)
                val audioFileSizeBytes = if (audioFile.exists()) audioFile.length() else 0L
                val audioDurationSeconds = getAudioDuration(audioPath)

                // Create pending stats entry
                val pendingStats = TranscriptionStats(
                    medicationId = medicationId,
                    medicationName = medication.name,
                    status = "pending",
                    startTime = System.currentTimeMillis(),
                    audioFilePath = audioPath,
                    audioFileSizeBytes = audioFileSizeBytes,
                    audioDurationSeconds = audioDurationSeconds
                )
                statsDao.insertStats(pendingStats)
            } catch (e: Exception) {
                // Log error but don't fail - the worker will create stats if this fails
                android.util.Log.e("TranscriptionScheduler", "Error creating pending stats", e)
            }
        }
        val inputData = workDataOf(
            AudioTranscriptionWorker.KEY_MEDICATION_ID to medicationId,
            AudioTranscriptionWorker.KEY_AUDIO_PATH to audioPath
        )

        // Set constraints for background work
        // IMPORTANT: Require charging for Whisper Tiny to avoid battery drain on low-end devices
        // Whisper Tiny is CPU-intensive and can take 30-60 seconds per audio clip
        val constraints = Constraints.Builder()
            .setRequiresCharging(true) // Only run when device is plugged in and charging
            .setRequiresBatteryNotLow(true) // Additional safety: don't run if battery is low
            .build()

        val transcriptionWork = OneTimeWorkRequestBuilder<AudioTranscriptionWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setInitialDelay(5, TimeUnit.SECONDS) // Small delay to ensure app responsiveness
            .build()

        // Use unique work name for each medication to avoid duplicate transcriptions
        val workName = WORK_NAME_PREFIX + medicationId

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE, // Replace any existing work for this medication
            transcriptionWork
        )
    }

    /**
     * Cancel pending transcription work for a medication.
     *
     * @param context Application context
     * @param medicationId ID of the medication
     */
    fun cancelTranscription(context: Context, medicationId: Long) {
        val workName = WORK_NAME_PREFIX + medicationId
        WorkManager.getInstance(context).cancelUniqueWork(workName)
    }

    /**
     * Get audio file duration in seconds.
     * Returns null if duration cannot be determined.
     */
    private fun getAudioDuration(audioPath: String): Float? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(audioPath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            durationMs?.let { it / 1000f }
        } catch (e: Exception) {
            android.util.Log.e("TranscriptionScheduler", "Error getting audio duration", e)
            null
        }
    }
}
