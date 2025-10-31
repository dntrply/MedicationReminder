package com.medreminder.app.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio processor for converting M4A/WAV files to 16kHz mono FloatArray.
 * Used to prepare audio files for Whisper transcription.
 */
object AudioProcessor {
    private const val TAG = "AudioProcessor"
    private const val TARGET_SAMPLE_RATE = 16000

    /**
     * Decode audio file and convert to 16kHz mono FloatArray.
     * Supports both M4A (AAC) and WAV formats.
     *
     * @param audioFile The audio file to process
     * @return FloatArray with normalized samples in range [-1.0, 1.0]
     * @throws Exception if file format is unsupported or decoding fails
     */
    fun decodeAudioFile(audioFile: File): FloatArray {
        if (!audioFile.exists()) {
            throw IllegalArgumentException("Audio file does not exist: ${audioFile.absolutePath}")
        }

        return when (audioFile.extension.lowercase()) {
            "wav" -> decodeWaveFile(audioFile)
            "m4a", "mp4", "aac" -> decodeM4aFile(audioFile)
            else -> throw IllegalArgumentException("Unsupported audio format: ${audioFile.extension}")
        }
    }

    /**
     * Decode WAV file to FloatArray.
     * Based on official whisper.cpp Android example implementation.
     */
    private fun decodeWaveFile(file: File): FloatArray {
        Log.d(TAG, "Decoding WAV file: ${file.name}")

        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Read WAV header
        val channels = buffer.getShort(22).toInt()
        val sampleRate = buffer.getInt(24)

        Log.d(TAG, "WAV format: $channels channels, $sampleRate Hz")

        // Skip to data section (standard WAV has 44-byte header)
        buffer.position(44)
        val shortBuffer = buffer.asShortBuffer()
        val shortArray = ShortArray(shortBuffer.limit())
        shortBuffer.get(shortArray)

        // Convert to mono float array
        val monoFloatArray = FloatArray(shortArray.size / channels) { index ->
            when (channels) {
                1 -> (shortArray[index] / 32767.0f).coerceIn(-1f..1f)
                else -> {
                    // Average stereo channels
                    val left = shortArray[2 * index]
                    val right = shortArray[2 * index + 1]
                    ((left + right) / 32767.0f / 2.0f).coerceIn(-1f..1f)
                }
            }
        }

        // Resample if needed
        return if (sampleRate != TARGET_SAMPLE_RATE) {
            Log.d(TAG, "Resampling from $sampleRate Hz to $TARGET_SAMPLE_RATE Hz")
            resampleAudio(monoFloatArray, sampleRate, TARGET_SAMPLE_RATE)
        } else {
            monoFloatArray
        }
    }

    /**
     * Decode M4A file using MediaCodec and convert to FloatArray.
     */
    private fun decodeM4aFile(file: File): FloatArray {
        Log.d(TAG, "Decoding M4A file: ${file.name}")

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                throw IllegalArgumentException("No audio track found in file")
            }

            extractor.selectTrack(audioTrackIndex)

            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)

            Log.d(TAG, "M4A format: $channelCount channels, $sampleRate Hz, mime: $mime")

            // Create decoder
            val codec = MediaCodec.createDecoderByType(mime ?: "audio/mp4a-latm")
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val pcmData = mutableListOf<Short>()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false

            try {
                while (!isEOS) {
                    // Feed input
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        inputBuffer?.clear()

                        val sampleSize = extractor.readSampleData(inputBuffer ?: ByteBuffer.allocate(0), 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }

                    // Get output
                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    if (outputBufferIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        outputBuffer?.let {
                            it.position(bufferInfo.offset)
                            it.limit(bufferInfo.offset + bufferInfo.size)

                            // Convert PCM bytes to short array
                            val shortBuffer = it.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            while (shortBuffer.hasRemaining()) {
                                pcmData.add(shortBuffer.get())
                            }
                        }

                        codec.releaseOutputBuffer(outputBufferIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isEOS = true
                        }
                    }
                }
            } finally {
                codec.stop()
                codec.release()
            }

            Log.d(TAG, "Decoded ${pcmData.size} PCM samples")

            // Convert to mono float array
            val shortArray = pcmData.toShortArray()
            val monoFloatArray = FloatArray(shortArray.size / channelCount) { index ->
                when (channelCount) {
                    1 -> (shortArray[index] / 32767.0f).coerceIn(-1f..1f)
                    else -> {
                        // Average all channels
                        var sum = 0f
                        for (ch in 0 until channelCount) {
                            sum += shortArray[index * channelCount + ch]
                        }
                        (sum / channelCount / 32767.0f).coerceIn(-1f..1f)
                    }
                }
            }

            // Resample if needed
            return if (sampleRate != TARGET_SAMPLE_RATE) {
                Log.d(TAG, "Resampling from $sampleRate Hz to $TARGET_SAMPLE_RATE Hz")
                resampleAudio(monoFloatArray, sampleRate, TARGET_SAMPLE_RATE)
            } else {
                monoFloatArray
            }

        } finally {
            extractor.release()
        }
    }

    /**
     * Resample audio using linear interpolation.
     * This is a simple resampler sufficient for speech processing.
     * For production, consider using a higher-quality resampler.
     */
    private fun resampleAudio(input: FloatArray, inputRate: Int, outputRate: Int): FloatArray {
        if (inputRate == outputRate) return input

        val ratio = inputRate.toDouble() / outputRate.toDouble()
        val outputSize = (input.size / ratio).toInt()
        val output = FloatArray(outputSize)

        for (i in output.indices) {
            val sourceIndex = i * ratio
            val index0 = sourceIndex.toInt()
            val index1 = (index0 + 1).coerceAtMost(input.size - 1)
            val fraction = sourceIndex - index0

            // Linear interpolation
            output[i] = (input[index0] * (1 - fraction) + input[index1] * fraction).toFloat()
        }

        Log.d(TAG, "Resampled: ${input.size} samples -> ${output.size} samples")
        return output
    }
}
