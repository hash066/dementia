package com.dementor.caregiver

import com.dementor.caregiver.domain.model.EventEnvelope
import com.dementor.caregiver.domain.model.EventType
import com.dementor.caregiver.domain.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Test

class EventModelTest {

    @Test
    fun `test event envelope formatting`() {
        val timestamp = 1715424000000L // Example timestamp
        val event = EventEnvelope(
            id = "test-uuid",
            timestamp = timestamp,
            type = EventType.FALL,
            priority = Severity.CRITICAL,
            description = "Test fall detected"
        )

        assertEquals("test-uuid", event.id)
        assertEquals(EventType.FALL, event.type)
        assertEquals(Severity.CRITICAL, event.priority)
        // Check if formattedTime contains the expected date/time structure
        assert(event.formattedTime.isNotEmpty())
    }
}
