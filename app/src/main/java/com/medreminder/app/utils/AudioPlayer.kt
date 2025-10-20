package com.medreminder.app.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Utility class for playing audio notes
 * Handles audio focus and playback state
 */
class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var _isPlaying = false
    private var onCompletionListener: (() -> Unit)? = null
    private var onErrorListener: (() -> Unit)? = null

    companion object {
        private const val TAG = "AudioPlayer"
    }

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /**
     * Start playing audio from the given file path
     * @param audioPath Path to the audio file
     * @param onCompletion Callback when playback completes
     * @param onError Callback when an error occurs
     * @return true if playback started successfully
     */
    fun play(
        audioPath: String?,
        onCompletion: (() -> Unit)? = null,
        onError: (() -> Unit)? = null
    ): Boolean {
        Log.d(TAG, "=== PLAY AUDIO DEBUG ===")
        Log.d(TAG, "Audio path: $audioPath")

        if (audioPath.isNullOrEmpty()) {
            Log.w(TAG, "Audio path is null or empty")
            onError?.invoke()
            return false
        }

        val file = File(audioPath)
        Log.d(TAG, "File exists: ${file.exists()}")
        Log.d(TAG, "File absolute path: ${file.absolutePath}")
        Log.d(TAG, "File size: ${if (file.exists()) file.length() else 0} bytes")

        if (!file.exists()) {
            Log.e(TAG, "Audio file does not exist: $audioPath")
            onError?.invoke()
            return false
        }

        this.onCompletionListener = onCompletion
        this.onErrorListener = onError

        // Stop current playback if any
        stop()

        // Request audio focus
        if (!requestAudioFocus()) {
            Log.w(TAG, "Failed to gain audio focus")
            onError?.invoke()
            return false
        }

        return try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                setOnCompletionListener {
                    Log.d(TAG, "Playback completed")
                    _isPlaying = false
                    abandonAudioFocus()
                    this@AudioPlayer.onCompletionListener?.invoke()
                }

                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "Playback error: what=$what, extra=$extra")
                    _isPlaying = false
                    abandonAudioFocus()
                    this@AudioPlayer.onErrorListener?.invoke()
                    true
                }

                prepare()
                start()
            }

            _isPlaying = true
            Log.d(TAG, "Started playing: $audioPath")
            true
        } catch (e: IOException) {
            Log.e(TAG, "IOException while playing audio", e)
            abandonAudioFocus()
            onError?.invoke()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while playing audio", e)
            abandonAudioFocus()
            onError?.invoke()
            false
        }
    }

    /**
     * Stop playback
     */
    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying()) {
                    stop()
                }
                reset()
                release()
            }
            mediaPlayer = null
            _isPlaying = false
            abandonAudioFocus()
            Log.d(TAG, "Stopped playback")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }

    /**
     * Pause playback
     */
    fun pause() {
        try {
            mediaPlayer?.apply {
                if (isPlaying()) {
                    pause()
                    _isPlaying = false
                    Log.d(TAG, "Paused playback")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback", e)
        }
    }

    /**
     * Resume playback
     */
    fun resume() {
        try {
            mediaPlayer?.apply {
                if (!isPlaying()) {
                    start()
                    _isPlaying = true
                    Log.d(TAG, "Resumed playback")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming playback", e)
        }
    }

    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean = _isPlaying

    /**
     * Get current playback position in milliseconds
     */
    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get total duration in milliseconds
     */
    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Request audio focus
     */
    private fun requestAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        handleAudioFocusChange(focusChange)
                    }
                    .build()

                val result = audioManager?.requestAudioFocus(audioFocusRequest!!)
                result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager?.requestAudioFocus(
                    { focusChange -> handleAudioFocusChange(focusChange) },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
                result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus", e)
            false
        }
    }

    /**
     * Abandon audio focus
     */
    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let {
                    audioManager?.abandonAudioFocusRequest(it)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus { }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus", e)
        }
    }

    /**
     * Handle audio focus changes
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Lost focus permanently, stop playback
                stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Lost focus temporarily, pause playback
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lost focus but can duck, lower volume
                mediaPlayer?.setVolume(0.3f, 0.3f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Gained focus, restore volume and resume if paused
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
        }
    }

    /**
     * Release resources
     */
    fun release() {
        stop()
        mediaPlayer = null
        audioManager = null
        onCompletionListener = null
        onErrorListener = null
    }
}
