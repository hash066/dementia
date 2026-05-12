package com.sementia.caregiver.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sementia.caregiver.domain.model.EventEnvelope
import com.sementia.caregiver.domain.model.EventType
import com.sementia.caregiver.domain.model.Severity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

class TimelineViewModel : ViewModel() {
    private val _events = MutableStateFlow<List<EventEnvelope>>(emptyList())
    val events: StateFlow<List<EventEnvelope>> = _events.asStateFlow()

    init {
        // Mocking real-time SSE stream for now
        viewModelScope.launch {
            while (true) {
                delay(5000)
                val newEvent = EventEnvelope(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    type = EventType.values().random(),
                    priority = Severity.values().random(),
                    description = "Automatic update from monitoring system"
                )
                _events.value = listOf(newEvent) + _events.value
            }
        }
    }
}
