package com.medreminder.app.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Utility class for recording audio notes with a 60-second limit
 * Uses AAC codec for better quality-to-size ratio
 */
class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var recordingStartTime = 0L

    companion object {
        private const val TAG = "AudioRecorder"
        const val MAX_RECORDING_DURATION_MS = 60000 // 60 seconds
        private const val AUDIO_DIR = "audio"
    }

    /**
     * Start recording audio
     * @return File where audio is being recorded, or null if failed
     */
    fun startRecording(): File? {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return null
        }

        try {
            // Create audio directory if it doesn't exist
            val audioDir = File(context.filesDir, AUDIO_DIR)
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }

            // Create unique audio file
            val timestamp = System.currentTimeMillis()
            audioFile = File(audioDir, "audio_note_$timestamp.m4a")

            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setMaxDuration(MAX_RECORDING_DURATION_MS)
                setOutputFile(audioFile?.absolutePath)

                // Set listener for max duration reached
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        Log.d(TAG, "Max duration reached, stopping recording")
                        stopRecording()
                    }
                }

                prepare()
                start()
            }

            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            Log.d(TAG, "Started recording to: ${audioFile?.absolutePath}")
            return audioFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting recording", e)
            cleanup()
            return null
        }
    }

    /**
     * Stop recording and return the recorded file
     * @return File containing the recording, or null if recording failed
     */
    fun stopRecording(): File? {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording")
            return null
        }

        return try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val recordingDuration = System.currentTimeMillis() - recordingStartTime
            Log.d(TAG, "Stopped recording. Duration: ${recordingDuration}ms")

            // Validate file exists and has content
            if (audioFile?.exists() == true && (audioFile?.length() ?: 0) > 0) {
                Log.d(TAG, "Recording saved: ${audioFile?.absolutePath}, size: ${audioFile?.length()} bytes")
                audioFile
            } else {
                Log.e(TAG, "Recording file is empty or doesn't exist")
                audioFile?.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            cleanup()
            null
        }
    }

    /**
     * Cancel recording and delete the file
     */
    fun cancelRecording() {
        try {
            if (isRecording) {
                mediaRecorder?.apply {
                    stop()
                    reset()
                    release()
                }
                mediaRecorder = null
                isRecording = false
            }

            // Delete the file
            audioFile?.delete()
            audioFile = null
            Log.d(TAG, "Recording cancelled and file deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling recording", e)
            cleanup()
        }
    }

    /**
     * Get the current recording duration in milliseconds
     */
    fun getRecordingDuration(): Long {
        return if (isRecording) {
            System.currentTimeMillis() - recordingStartTime
        } else {
            0L
        }
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Release resources and cleanup
     */
    private fun cleanup() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        }
        mediaRecorder = null
        isRecording = false
        audioFile = null
    }

    /**
     * Delete an audio file at the given path
     */
    fun deleteAudioFile(audioPath: String?): Boolean {
        if (audioPath.isNullOrEmpty()) return false

        return try {
            val file = File(audioPath)
            val deleted = file.delete()
            if (deleted) {
                Log.d(TAG, "Deleted audio file: $audioPath")
            } else {
                Log.w(TAG, "Failed to delete audio file: $audioPath")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting audio file: $audioPath", e)
            false
        }
    }
}
