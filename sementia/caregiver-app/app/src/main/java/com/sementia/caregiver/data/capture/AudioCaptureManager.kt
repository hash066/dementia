package com.sementia.caregiver.data.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Records microphone audio in the exact format the hub's AUDIO intake payload
 * expects: PCM signed 16-bit little-endian, 16 kHz, mono.
 */
class AudioCaptureManager {

    data class Chunk(
        val pcmBytes: ByteArray,
        val durationSec: Double,
        val peakAmplitude: Int,
    )

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val ENCODING_NAME = "pcm_s16le"
        const val CHANNELS = 1

        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    /**
     * Records a single chunk of [durationSec] seconds. Suspends until done.
     * Throws IllegalStateException when the mic cannot be opened (permission
     * missing or device busy) so callers can surface the reason in the debug log.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordChunk(durationSec: Int): Chunk = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuffer <= 0) {
            throw IllegalStateException("AudioRecord.getMinBufferSize failed ($minBuffer) — mic unsupported at ${SAMPLE_RATE_HZ}Hz?")
        }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBuffer * 4,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Mic init failed — is RECORD_AUDIO permission granted and the mic free?")
        }

        val totalBytes = SAMPLE_RATE_HZ * 2 * durationSec // 16-bit mono
        val out = ByteArray(totalBytes)
        var offset = 0
        var peak = 0
        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("Mic did not start recording (state=${recorder.recordingState})")
            }
            while (offset < totalBytes && isActive) {
                val read = recorder.read(out, offset, totalBytes - offset)
                if (read < 0) {
                    throw IllegalStateException("AudioRecord.read error code $read")
                }
                // Track peak amplitude for the UI level meter (LE 16-bit samples).
                var i = offset
                while (i + 1 < offset + read) {
                    val sample = (out[i].toInt() and 0xFF) or (out[i + 1].toInt() shl 8)
                    val amp = abs(sample.toShort().toInt())
                    if (amp > peak) peak = amp
                    i += 2
                }
                offset += read
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        Chunk(
            pcmBytes = if (offset == totalBytes) out else out.copyOf(offset),
            durationSec = offset / (SAMPLE_RATE_HZ * 2.0),
            peakAmplitude = peak,
        )
    }
}
