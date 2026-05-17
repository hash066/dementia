package com.dementor.caregiver.data.mock

import com.dementor.caregiver.domain.model.EventEnvelope
import com.dementor.caregiver.domain.model.EventType
import com.dementor.caregiver.domain.model.Severity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*

class MockEventService {
    fun getMockEventsStream(): Flow<EventEnvelope> = flow {
        val descriptions = listOf(
            "Left bedroom at 8:05 AM",
            "Heart rate: 75 bpm (Normal)",
            "Medication 'Aspirin' taken",
            "Motion detected in kitchen",
            "Conversation detected: 'Good morning'"
        )

        while (true) {
            delay(10000) // New event every 10 seconds as requested
            val event = EventEnvelope(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                type = EventType.values().random(),
                priority = if (Math.random() > 0.9) Severity.CRITICAL else Severity.NORMAL,
                description = descriptions.random()
            )
            emit(event)
        }
    }

    fun getInitialMockData(): List<EventEnvelope> {
        return List(10) { i ->
            EventEnvelope(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis() - (i * 3600000),
                type = EventType.SPEECH,
                priority = Severity.NORMAL,
                description = "Historical event $i"
            )
        }
    }
}
