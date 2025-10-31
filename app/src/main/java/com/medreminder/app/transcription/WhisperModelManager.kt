package com.medreminder.app.transcription

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manager for downloading and managing Whisper model files.
 * Handles model download from Hugging Face with WiFi checks, storage checks, and retry logic.
 */
object WhisperModelManager {

    private const val TAG = "WhisperModelManager"

    // Model URL from Hugging Face (whisper.cpp repository)
    private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"

    // Local file name
    private const val MODEL_FILENAME = "whisper-tiny.bin"

    // Download requirements
    private const val REQUIRED_STORAGE_MB = 100L // Need 100MB free space
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val DOWNLOAD_TIMEOUT_MS = 300000 // 5 minutes (increased for slower connections)

    /**
     * Check if Whisper model is downloaded and ready.
     */
    fun areModelsDownloaded(context: Context): Boolean {
        val modelFile = getModelFile(context)
        return modelFile.exists() && modelFile.length() > 0
    }

    /**
     * Check if device meets requirements for downloading models.
     * Checks: WiFi connection, storage space.
     */
    fun canDownloadModels(context: Context): DownloadReadiness {
        // Check WiFi
        if (!isWiFiConnected(context)) {
            return DownloadReadiness.NO_WIFI
        }

        // Check storage space
        if (!hasEnoughStorage(context)) {
            return DownloadReadiness.INSUFFICIENT_STORAGE
        }

        return DownloadReadiness.READY
    }

    /**
     * Download Whisper model from Hugging Face.
     * Auto-retries up to MAX_RETRY_ATTEMPTS times on failure.
     *
     * @param context Application context
     * @param progressCallback Called with download progress (0-100)
     * @return DownloadResult indicating success or failure
     */
    suspend fun downloadModels(
        context: Context,
        progressCallback: ((Int) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting Whisper model download...")

        var attempt = 0
        var lastError: Exception? = null

        while (attempt < MAX_RETRY_ATTEMPTS) {
            attempt++
            Log.d(TAG, "Download attempt $attempt of $MAX_RETRY_ATTEMPTS")

            try {
                // Check readiness before each attempt
                val readiness = canDownloadModels(context)
                if (readiness != DownloadReadiness.READY) {
                    Log.w(TAG, "Cannot download: $readiness")
                    return@withContext DownloadResult.Failed(readiness.toString())
                }

                // Download model file (75MB)
                progressCallback?.invoke(0)
                val modelSuccess = downloadFile(
                    url = MODEL_URL,
                    outputFile = getModelFile(context),
                    progressCallback = progressCallback
                )

                if (!modelSuccess) {
                    throw Exception("Model file download failed")
                }

                progressCallback?.invoke(100)
                Log.i(TAG, "Model downloaded successfully!")
                return@withContext DownloadResult.Success

            } catch (e: Exception) {
                Log.e(TAG, "Download attempt $attempt failed", e)
                lastError = e

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    // Wait before retry (exponential backoff: 2s, 4s, 8s)
                    val delayMs = (1000L * (1 shl attempt))
                    Log.d(TAG, "Retrying in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }

        // All attempts failed
        Log.e(TAG, "Model download failed after $MAX_RETRY_ATTEMPTS attempts")
        DownloadResult.Failed(lastError?.message ?: "Unknown error")
    }

    /**
     * Download a single file from URL with progress tracking.
     */
    private fun downloadFile(
        url: String,
        outputFile: File,
        progressCallback: ((Int) -> Unit)? = null
    ): Boolean {
        var connection: HttpURLConnection? = null
        var outputStream: FileOutputStream? = null

        try {
            Log.d(TAG, "Downloading: $url")

            val urlConnection = URL(url)
            connection = urlConnection.openConnection() as HttpURLConnection
            connection.connectTimeout = DOWNLOAD_TIMEOUT_MS
            connection.readTimeout = DOWNLOAD_TIMEOUT_MS
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error: ${connection.responseCode}")
                return false
            }

            val fileLength = connection.contentLength
            val inputStream = connection.inputStream
            outputStream = FileOutputStream(outputFile)

            val buffer = ByteArray(8192)
            var totalRead = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead

                // Update progress
                if (fileLength > 0) {
                    val progress = (totalRead * 100 / fileLength).toInt()
                    progressCallback?.invoke(progress)
                }
            }

            outputStream.flush()
            Log.d(TAG, "Downloaded: ${outputFile.name} (${totalRead / 1024 / 1024}MB)")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Download error for $url", e)
            // Delete partial file on error
            if (outputFile.exists()) {
                outputFile.delete()
            }
            return false

        } finally {
            outputStream?.close()
            connection?.disconnect()
        }
    }

    /**
     * Delete downloaded model to free up storage.
     */
    fun deleteModels(context: Context): Boolean {
        return try {
            val modelFile = getModelFile(context)

            val deleted = if (modelFile.exists()) {
                modelFile.delete()
            } else {
                true
            }

            Log.i(TAG, "Model deleted: $deleted")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model", e)
            false
        }
    }

    /**
     * Get total size of downloaded model in MB.
     */
    fun getModelSize(context: Context): Long {
        val modelFile = getModelFile(context)
        val totalBytes = if (modelFile.exists()) modelFile.length() else 0
        return totalBytes / 1024 / 1024 // Convert to MB
    }

    /**
     * Get model file path.
     */
    fun getModelFile(context: Context): File {
        return File(context.filesDir, MODEL_FILENAME)
    }

    /**
     * Check if WiFi is connected.
     */
    private fun isWiFiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Check if device has enough storage space.
     */
    private fun hasEnoughStorage(context: Context): Boolean {
        val filesDir = context.filesDir
        val stat = StatFs(filesDir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val availableMB = availableBytes / 1024 / 1024
        return availableMB >= REQUIRED_STORAGE_MB
    }

    /**
     * Download readiness states.
     */
    enum class DownloadReadiness {
        READY,
        NO_WIFI,
        INSUFFICIENT_STORAGE
    }

    /**
     * Download result.
     */
    sealed class DownloadResult {
        object Success : DownloadResult()
        data class Failed(val reason: String) : DownloadResult()
    }
}
