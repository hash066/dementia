package com.dementor.caregiver.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dementor.caregiver.domain.model.EventEnvelope
import com.dementor.caregiver.ui.HubViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimelineViewModel(
    private val hub: HubViewModel,
) : ViewModel() {

    private val _events = MutableStateFlow<List<EventEnvelope>>(emptyList())
    val events: StateFlow<List<EventEnvelope>> = _events.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                if (hub.baseUrl.value != null) {
                    try {
                        _events.value = hub.fetchEvents()
                        _error.value = null
                    } catch (e: Exception) {
                        _error.value = e.message ?: "Failed to load events"
                    }
                } else {
                    _events.value = emptyList()
                }
                delay(5000)
            }
        }
    }

    fun acknowledge(event: EventEnvelope) {
        viewModelScope.launch {
            try {
                hub.acknowledgeEmergency(event.id, note = "Acknowledged from caregiver app")
                _events.value = hub.fetchEvents()
            } catch (e: Exception) {
                _error.value = e.message ?: "Ack failed"
            }
        }
    }
}

class TimelineViewModelFactory(
    private val hub: HubViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(TimelineViewModel::class.java)) {
            error("Unknown ViewModel: ${modelClass.name}")
        }
        return TimelineViewModel(hub) as T
    }
}
