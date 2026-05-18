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

    suspend fun fetchEvents(): List<EventEnvelope> {
        val now = System.currentTimeMillis()
        return listOf(
            EventEnvelope(id = "1", type = com.dementor.caregiver.domain.model.EventType.EMERGENCY, description = "Left the geofenced area. Detected in Central Park.", location = "Central Park", timestamp = now, priority = com.dementor.caregiver.domain.model.Severity.CRITICAL),
            EventEnvelope(id = "2", type = com.dementor.caregiver.domain.model.EventType.SPEECH, description = "Asked 'Where are my spectacles?' - Detected stress.", location = "Living Room", timestamp = now - 7200000, priority = com.dementor.caregiver.domain.model.Severity.HIGH),
            EventEnvelope(id = "3", type = com.dementor.caregiver.domain.model.EventType.SYSTEM, description = "Completed morning walk.", location = "Neighborhood", timestamp = now - 86400000, priority = com.dementor.caregiver.domain.model.Severity.NORMAL),
            EventEnvelope(id = "4", type = com.dementor.caregiver.domain.model.EventType.REMINDER, description = "Took Donepezil 10mg.", location = "Kitchen", timestamp = now - 90000000, priority = com.dementor.caregiver.domain.model.Severity.NORMAL),
            EventEnvelope(id = "5", type = com.dementor.caregiver.domain.model.EventType.SPEECH, description = "Talked about past memories with daughter.", location = "Bedroom", timestamp = now - 172800000, priority = com.dementor.caregiver.domain.model.Severity.NORMAL)
        )
    }

    suspend fun fetchStatus(): HubStatusDto? = HubStatusDto(
        fsmState = "MONITORING",
        eventCountLastMin = 3,
        activeEmergency = false,
        lastEventTs = System.currentTimeMillis()
    )

    suspend fun fetchMedical(category: String? = null): List<MedicalRowDto> {
        val now = System.currentTimeMillis()
        return listOf(
            MedicalRowDto(label = "Donepezil 10mg", value = "Taken at 8:00 AM", category = "medication", ts = now - 90000000),
            MedicalRowDto(label = "Blood Pressure", value = "120/80 mmHg", category = "vitals", ts = now - 90000000),
            MedicalRowDto(label = "Heart Rate", value = "72 bpm", category = "vitals", ts = now - 90000000)
        )
    }

    suspend fun searchMemories(query: String): List<EventEnvelope> {
        val now = System.currentTimeMillis()
        return listOf(
            EventEnvelope(id = "m1", type = com.dementor.caregiver.domain.model.EventType.OBJECT, description = "Forgot spectacles on the dining table.", location = "Dining Room", timestamp = now - 36000000, priority = com.dementor.caregiver.domain.model.Severity.HIGH),
            EventEnvelope(id = "m2", type = com.dementor.caregiver.domain.model.EventType.EMERGENCY, description = "Got lost in the local park near Elm Street.", location = "Elm Street Park", timestamp = now - 432000000, priority = com.dementor.caregiver.domain.model.Severity.CRITICAL),
            EventEnvelope(id = "m3", type = com.dementor.caregiver.domain.model.EventType.SYSTEM, description = "Watered the garden plants.", location = "Backyard", timestamp = now - 518400000, priority = com.dementor.caregiver.domain.model.Severity.NORMAL)
        )
    }

    suspend fun fetchVoiceHistory(): List<EventEnvelope> {
        val now = System.currentTimeMillis()
        return listOf(
            EventEnvelope(id = "v1", type = com.dementor.caregiver.domain.model.EventType.SPEECH, description = "\"Who is coming to visit today?\"", location = "Living Room", timestamp = now - 18000000, priority = com.dementor.caregiver.domain.model.Severity.NORMAL),
            EventEnvelope(id = "v2", type = com.dementor.caregiver.domain.model.EventType.SPEECH, description = "\"I can't find my keys.\"", location = "Hallway", timestamp = now - 108000000, priority = com.dementor.caregiver.domain.model.Severity.HIGH)
        )
    }

    suspend fun acknowledgeEmergency(eventId: String, note: String) {
        // Mock successful acknowledgement
    }
}
