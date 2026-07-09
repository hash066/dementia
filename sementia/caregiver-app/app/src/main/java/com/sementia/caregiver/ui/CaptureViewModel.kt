package com.sementia.caregiver.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sementia.caregiver.data.capture.AudioCaptureManager
import com.sementia.caregiver.data.capture.LocationProvider
import com.sementia.caregiver.data.remote.IntakeResult
import com.sementia.caregiver.data.remote.SementiaClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

private const val CHUNK_SECONDS = 10
private const val MAX_LOG_LINES = 200

/**
 * Drives live capture: records audio in chunks, stamps each chunk with the
 * current GPS fix, and posts it to the hub as an AUDIO event. Every step is
 * written to a visible debug log so failures are easy to localize.
 */
class CaptureViewModel(application: Application) : AndroidViewModel(application) {

    data class LogEntry(val time: String, val message: String, val isError: Boolean = false)

    private val audio = AudioCaptureManager()
    val location = LocationProvider(application)

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    private val _sentCount = MutableStateFlow(0)
    val sentCount: StateFlow<Int> = _sentCount.asStateFlow()

    private val _failCount = MutableStateFlow(0)
    val failCount: StateFlow<Int> = _failCount.asStateFlow()

    private val _lastPeak = MutableStateFlow(0)
    val lastPeak: StateFlow<Int> = _lastPeak.asStateFlow()

    private var captureJob: Job? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun appendLog(message: String, isError: Boolean = false) {
        val entry = LogEntry(timeFormat.format(Date()), message, isError)
        _log.value = (listOf(entry) + _log.value).take(MAX_LOG_LINES)
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    /** Starts the continuous record→locate→post loop. */
    fun startCapture(client: SementiaClient?) {
        if (_isCapturing.value) return
        if (client == null) {
            appendLog("No hub connected — sign in with the hub address first", isError = true)
            return
        }
        _isCapturing.value = true
        location.start()
        appendLog("Capture started: ${CHUNK_SECONDS}s chunks, 16kHz mono PCM audio + GPS")
        captureJob = viewModelScope.launch {
            var chunkNo = 0
            while (isActive && _isCapturing.value) {
                chunkNo++
                val chunk = try {
                    audio.recordChunk(CHUNK_SECONDS)
                } catch (e: Exception) {
                    appendLog("Mic error on chunk #$chunkNo: ${e.message}", isError = true)
                    stopCapture()
                    break
                }
                _lastPeak.value = chunk.peakAmplitude
                val fix = location.lastFix.value
                val locString = fix?.asPayloadString()
                if (locString == null) {
                    appendLog("Chunk #$chunkNo: no GPS fix yet — sending without location")
                }
                sendChunk(client, chunkNo, chunk, locString)
            }
        }
    }

    private suspend fun sendChunk(
        client: SementiaClient,
        chunkNo: Int,
        chunk: AudioCaptureManager.Chunk,
        locString: String?,
    ) {
        val b64 = Base64.getEncoder().encodeToString(chunk.pcmBytes)
        val kb = chunk.pcmBytes.size / 1024
        when (val result = client.postAudioEvent(b64, chunk.durationSec, locString)) {
            is IntakeResult.Accepted -> {
                _sentCount.value++
                appendLog(
                    "Chunk #$chunkNo sent (${kb}KB, peak=${chunk.peakAmplitude}" +
                        (locString?.let { ", gps=$it" } ?: "") + ") → accepted"
                )
            }
            is IntakeResult.Duplicate -> {
                appendLog("Chunk #$chunkNo → hub says duplicate event_id (409)", isError = true)
            }
            is IntakeResult.Rejected -> {
                _failCount.value++
                appendLog(
                    "Chunk #$chunkNo rejected: HTTP ${result.httpStatus} ${result.body}",
                    isError = true,
                )
            }
            is IntakeResult.TransportError -> {
                _failCount.value++
                appendLog(
                    "Chunk #$chunkNo network error: ${result.message} — check hub URL/Wi-Fi",
                    isError = true,
                )
            }
        }
    }

    /**
     * Posts a tiny silent audio event immediately — isolates connectivity and
     * validation problems from mic problems.
     */
    fun sendTestPing(client: SementiaClient?) {
        if (client == null) {
            appendLog("No hub connected — sign in with the hub address first", isError = true)
            return
        }
        viewModelScope.launch {
            location.start()
            val fix = location.lastFix.value
            // 0.1s of silence @16kHz mono s16le
            val silent = ByteArray(3200)
            appendLog("Sending test ping (silent 0.1s audio, gps=${fix?.asPayloadString() ?: "none"})…")
            sendChunk(
                client,
                chunkNo = 0,
                chunk = AudioCaptureManager.Chunk(silent, 0.1, 0),
                locString = fix?.asPayloadString(),
            )
        }
    }

    fun stopCapture() {
        if (captureJob != null || _isCapturing.value) {
            appendLog("Capture stopped")
        }
        _isCapturing.value = false
        captureJob?.cancel()
        captureJob = null
        location.stop()
    }

    override fun onCleared() {
        stopCapture()
        super.onCleared()
    }
}
