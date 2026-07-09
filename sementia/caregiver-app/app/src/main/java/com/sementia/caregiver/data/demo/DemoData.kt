package com.sementia.caregiver.data.demo

import com.sementia.caregiver.data.remote.HubStatusDto
import com.sementia.caregiver.data.remote.MedicalRowDto
import com.sementia.caregiver.domain.model.EventEnvelope
import com.sementia.caregiver.domain.model.EventType
import com.sementia.caregiver.domain.model.Severity
import java.util.UUID

/**
 * Self-contained sample dataset so every screen has realistic content without a
 * reachable hub. Powers Demo Mode — a labelled toggle in Settings — so the app
 * is fully functional offline for walkthroughs and presentations.
 */
object DemoData {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    private fun ago(ms: Long) = System.currentTimeMillis() - ms

    fun status(): HubStatusDto = HubStatusDto(
        lastEventTs = ago(3 * MINUTE),
        eventCountLastMin = 3,
        fsmState = "MONITORING",
        activeEmergency = false,
    )

    fun events(): List<EventEnvelope> = listOf(
        event(
            EventType.SPEECH, Severity.NORMAL, ago(4 * MINUTE),
            "Said \"Good morning\" and asked what day it is",
            location = "Bedroom",
        ),
        event(
            EventType.AUDIO, Severity.NORMAL, ago(22 * MINUTE),
            "Calm conversation about breakfast plans",
            location = "Kitchen",
        ),
        event(
            EventType.OBJECT, Severity.NORMAL, ago(48 * MINUTE),
            "Reading glasses seen on the side table",
            location = "Living room",
        ),
        event(
            EventType.REMINDER, Severity.NORMAL, ago(70 * MINUTE),
            "Reminder acknowledged: take morning medication",
        ),
        event(
            EventType.IMAGE, Severity.NORMAL, ago(95 * MINUTE),
            "Kitchen scene — stove is off, area is clear",
            location = "Kitchen",
        ),
        event(
            EventType.VITALS, Severity.NORMAL, ago(2 * HOUR),
            "Heart rate 76 bpm (normal range)",
        ),
        event(
            EventType.FALL, Severity.HIGH, ago(3 * HOUR + 10 * MINUTE),
            "Possible stumble near the hallway — no button press, resolved",
            location = "Hallway",
        ),
        event(
            EventType.SPEECH, Severity.NORMAL, ago(4 * HOUR),
            "Talked about a walk in the garden yesterday",
            location = "Living room",
        ),
    )

    fun voiceHistory(): List<EventEnvelope> = events().filter {
        it.type == EventType.SPEECH || it.type == EventType.AUDIO
    }

    fun medical(category: String?): List<MedicalRowDto> {
        val all = listOf(
            medRow("medication", "Aspirin 75mg", "Taken 8:10 AM", ago(5 * HOUR)),
            medRow("medication", "Donepezil 5mg", "Taken 9:00 AM", ago(4 * HOUR)),
            medRow("medication", "Vitamin D", "Taken 9:00 AM", ago(4 * HOUR)),
            medRow("symptom", "Mild confusion", "Asked the date twice this morning", ago(4 * MINUTE)),
            medRow("symptom", "Appetite", "Ate a full breakfast", ago(90 * MINUTE)),
            medRow("vitals", "Heart rate", "76 bpm", ago(2 * HOUR)),
        )
        return if (category == null) all else all.filter { it.category == category }
    }

    /** Keyword-matched caregiver answer grounded in the seeded memories. */
    fun chatReply(query: String): String {
        val q = query.lowercase()
        return when {
            listOf("medic", "pill", "aspirin", "donepezil", "drug", "dose").any { it in q } ->
                "Today's medication is on track: Aspirin 75mg was taken at 8:10 AM, and Donepezil 5mg " +
                    "plus Vitamin D at 9:00 AM. All logged as taken."

            listOf("eat", "food", "breakfast", "appetite", "meal", "drink").any { it in q } ->
                "Breakfast went well — a full meal around 8:30 AM, and a calm conversation about " +
                    "breakfast plans in the kitchen. Appetite looks normal today."

            listOf("glass", "where", "keys", "found", "lost", "object").any { it in q } ->
                "The reading glasses were last seen on the side table in the living room about 48 minutes ago."

            listOf("fall", "hurt", "safe", "stumble", "emergency").any { it in q } ->
                "There was a possible stumble near the hallway about 3 hours ago, but there was no " +
                    "emergency button press and it resolved on its own. No active emergencies right now."

            listOf("sleep", "night", "morning", "day", "how", "mood", "feel").any { it in q } ->
                "It's been a calm morning: woke up and said good morning around 4 minutes ago, took all " +
                    "medication, ate a full breakfast, and chatted about a garden walk. Heart rate 76 bpm."

            else ->
                "Based on today's memories: a calm morning overall — greeted the day, took morning " +
                    "medication, ate breakfast, and had a few short conversations. One minor stumble in the " +
                    "hallway earlier, now resolved. Ask me about medication, meals, or where an item was last seen."
        }
    }

    private fun event(
        type: EventType,
        priority: Severity,
        ts: Long,
        description: String,
        location: String? = null,
    ) = EventEnvelope(
        id = UUID.randomUUID().toString(),
        timestamp = ts,
        type = type,
        priority = priority,
        description = description,
        location = location,
    )

    private fun medRow(category: String, label: String, value: String, ts: Long) = MedicalRowDto(
        eventId = UUID.randomUUID().toString(),
        category = category,
        label = label,
        value = value,
        ts = ts,
    )
}
