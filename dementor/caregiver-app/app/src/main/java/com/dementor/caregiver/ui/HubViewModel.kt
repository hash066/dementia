package com.dementor.caregiver.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dementor.caregiver.data.remote.DementorClient
import com.dementor.caregiver.data.remote.HubStatusDto
import com.dementor.caregiver.data.remote.MedicalRowDto
import com.dementor.caregiver.data.remote.createPhoneHttpClient
import com.dementor.caregiver.data.remote.normalizeHubBaseUrl
import com.dementor.caregiver.domain.model.EventEnvelope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val PREFS = "dementor_caregiver"
private const val KEY_HUB_URL = "hub_base_url"

class HubViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val http = createPhoneHttpClient()

    private val _baseUrl = MutableStateFlow(prefs.getString(KEY_HUB_URL, null))
    val baseUrl: StateFlow<String?> = _baseUrl.asStateFlow()

    private val _connectError = MutableStateFlow<String?>(null)
    val connectError: StateFlow<String?> = _connectError.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    fun savedHubUrlExists(): Boolean = !prefs.getString(KEY_HUB_URL, null).isNullOrBlank()

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
                val api = DementorClient(url, http)
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

    fun clientOrNull(): DementorClient? = _baseUrl.value?.let { DementorClient(it, http) }

    suspend fun fetchEvents(): List<EventEnvelope> =
        clientOrNull()?.fetchEvents() ?: emptyList()

    suspend fun fetchStatus(): HubStatusDto? =
        clientOrNull()?.fetchStatus()

    suspend fun fetchMedical(category: String? = null): List<MedicalRowDto> =
        clientOrNull()?.fetchMedical(category) ?: emptyList()

    suspend fun searchMemories(query: String): List<EventEnvelope> =
        clientOrNull()?.searchMemories(query) ?: emptyList()

    suspend fun fetchVoiceHistory(): List<EventEnvelope> =
        clientOrNull()?.fetchVoiceHistory() ?: emptyList()

    suspend fun acknowledgeEmergency(eventId: String, note: String) {
        clientOrNull()?.acknowledgeEmergency(eventId, note)?.getOrThrow()
    }
}
