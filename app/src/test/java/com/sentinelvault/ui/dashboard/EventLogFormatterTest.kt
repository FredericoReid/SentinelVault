package com.sentinelvault.ui.dashboard

import com.google.common.truth.Truth.assertThat
import com.sentinelvault.data.db.entity.EventLogEntity
import org.junit.Test

class EventLogFormatterTest {

    @Test
    fun `every defined type code maps to a non-empty label`() {
        val typeCodes = listOf(
            EventLogEntity.Type.UNLOCK_VERIFIED,
            EventLogEntity.Type.UNLOCK_STRANGER,
            EventLogEntity.Type.SNATCH_DETECTED,
            EventLogEntity.Type.CONTEXT_BREACH,
            EventLogEntity.Type.BREACH_CONFIRMED,
            EventLogEntity.Type.LOCKDOWN_TRIGGERED,
            EventLogEntity.Type.FALSE_REJECT_RESOLVED
        )
        val labels = typeCodes.map(EventLogFormatter::typeLabel).toSet()
        assertThat(labels).hasSize(typeCodes.size)
        labels.forEach { assertThat(it).isNotEmpty() }
    }

    @Test
    fun `unknown type code falls back to the raw string`() {
        assertThat(EventLogFormatter.typeLabel("MADE_UP_TYPE")).isEqualTo("MADE_UP_TYPE")
    }

    @Test
    fun `timestamp label is rendered as UTC ISO-8601`() {
        // 2024-05-01T12:34:56Z = 1714566896000
        assertThat(EventLogFormatter.timestampLabel(1_714_566_896_000L))
            .isEqualTo("2024-05-01 12:34:56")
    }

    @Test
    fun `toUi copies every field through the formatter`() {
        val entity = EventLogEntity(
            id = 42L,
            timestampMs = 1_714_566_896_000L,
            type = EventLogEntity.Type.BREACH_CONFIRMED,
            severity = 2,
            foregroundPackage = "com.example.bank",
            evidencePath = "/files/evidence/breach_42.webp",
            notes = "mismatchStreak=3"
        )

        val ui = EventLogFormatter.toUi(entity)

        assertThat(ui.id).isEqualTo(42L)
        assertThat(ui.typeLabel).isEqualTo("Breach confirmed")
        assertThat(ui.timestampLabel).isEqualTo("2024-05-01 12:34:56")
        assertThat(ui.severity).isEqualTo(2)
        assertThat(ui.foregroundPackage).isEqualTo("com.example.bank")
        assertThat(ui.notes).isEqualTo("mismatchStreak=3")
    }
}
