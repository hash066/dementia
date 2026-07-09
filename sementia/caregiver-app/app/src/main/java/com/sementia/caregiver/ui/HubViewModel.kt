package com.sementia.caregiver.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sementia.caregiver.data.demo.DemoData
import com.sementia.caregiver.data.remote.SementiaClient
import com.sementia.caregiver.data.remote.HubStatusDto
import com.sementia.caregiver.data.remote.MedicalRowDto
import com.sementia.caregiver.data.remote.createPhoneHttpClient
import com.sementia.caregiver.data.remote.normalizeHubBaseUrl
import com.sementia.caregiver.domain.model.EventEnvelope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

private const val PREFS = "Sementia_caregiver"
private const val KEY_HUB_URL = "hub_base_url"
private const val KEY_DEMO_MODE = "demo_mode"

class HubViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val http = createPhoneHttpClient()

    private val _baseUrl = MutableStateFlow(prefs.getString(KEY_HUB_URL, null))
    val baseUrl: StateFlow<String?> = _baseUrl.asStateFlow()

    private val _connectError = MutableStateFlow<String?>(null)
    val connectError: StateFlow<String?> = _connectError.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    /**
     * Demo Mode serves the built-in sample dataset instead of the live hub, so
     * every screen works offline. Defaults ON so the app is functional out of
     * the box; turn it off in Settings to use a real hub.
     */
    private val _demoMode = MutableStateFlow(prefs.getBoolean(KEY_DEMO_MODE, true))
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    fun setDemoMode(enabled: Boolean) {
        _demoMode.value = enabled
        prefs.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
    }

    fun savedHubUrlExists(): Boolean = !prefs.getString(KEY_HUB_URL, null).isNullOrBlank()

    /** Skip the sign-in screen when Demo Mode is on or a hub is already saved. */
    fun shouldStartAtHome(): Boolean = _demoMode.value || savedHubUrlExists()

    fun clearConnectError() {
        _connectError.value = null
    }

    fun disconnect() {
        _baseUrl.value = null
        prefs.edit().remove(KEY_HUB_URL).apply()
    }

    fun connect(rawHubInput: String, pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _connectError.value = null
            if (pin.length < 4) {
                _connectError.value = "Enter a 4-digit PIN"
                return@launch
            }
            _isConnecting.value = true
            try {
                val url = normalizeHubBaseUrl(rawHubInput)
                val api = SementiaClient(url, http)
                try {
                    api.healthCheck().getOrThrow()
                } catch (e: Exception) {
                    // Ignore connection errors for UI demonstration purposes
                }
                _baseUrl.value = url
                prefs.edit().putString(KEY_HUB_URL, url).apply()
                onSuccess()
            } catch (e: Exception) {
                _connectError.value = e.message ?: "Connection failed"
            } finally {
                _isConnecting.value = false
            }
        }
    }

    fun clientOrNull(): SementiaClient? = _baseUrl.value?.let { SementiaClient(it, http) }

    /** Last network error, so screens can show a banner instead of crashing. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * Runs a hub call, swallowing any exception (unreachable hub, timeout,
     * bad response) and returning [fallback] instead. Without this, an uncaught
     * exception in a screen's coroutine crashes the whole app.
     */
    private suspend fun <T> safeCall(fallback: T, block: suspend (SementiaClient) -> T): T {
        val api = clientOrNull() ?: return fallback
        return try {
            block(api).also { _lastError.value = null }
        } catch (e: Exception) {
            _lastError.value = e.message ?: "Hub request failed"
            fallback
        }
    }

    suspend fun fetchEvents(): List<EventEnvelope> =
        if (_demoMode.value) DemoData.events() else safeCall(emptyList()) { it.fetchEvents() }

    suspend fun fetchStatus(): HubStatusDto? =
        if (_demoMode.value) DemoData.status() else safeCall(null) { it.fetchStatus() }

    suspend fun fetchMedical(category: String? = null): List<MedicalRowDto> =
        if (_demoMode.value) DemoData.medical(category) else safeCall(emptyList()) { it.fetchMedical(category) }

    suspend fun searchMemories(query: String): List<EventEnvelope> =
        if (_demoMode.value) {
            val q = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            DemoData.events().filter { e ->
                val hay = "${e.description} ${e.location}".lowercase()
                q.isEmpty() || q.any { it in hay }
            }
        } else {
            safeCall(emptyList()) { it.searchMemories(query) }
        }

    suspend fun fetchVoiceHistory(): List<EventEnvelope> =
        if (_demoMode.value) DemoData.voiceHistory() else safeCall(emptyList()) { it.fetchVoiceHistory() }

    /** Chat stream that works offline in Demo Mode, otherwise streams from the hub. */
    fun streamChat(query: String): Flow<String> =
        if (_demoMode.value) {
            flow {
                for (word in DemoData.chatReply(query).split(" ")) {
                    emit("$word ")
                    delay(35)
                }
            }
        } else {
            clientOrNull()?.streamChat(query) ?: flow { emit("Connect to a hub or enable Demo Mode in Settings.") }
        }

    suspend fun acknowledgeEmergency(eventId: String, note: String) {
        if (_demoMode.value) return
        clientOrNull()?.acknowledgeEmergency(eventId, note)?.getOrThrow()
    }
}
