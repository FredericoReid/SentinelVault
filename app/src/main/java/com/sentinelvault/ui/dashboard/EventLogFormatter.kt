package com.sentinelvault.ui.dashboard

import com.sentinelvault.data.db.entity.EventLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure formatter that maps [EventLogEntity.type] / [EventLogEntity.timestampMs] into the
 * human-readable strings shown in [TimelineRowUi]. Kept separate from the view-model so the
 * unit tests can drive every type code through a single deterministic seam.
 */
object EventLogFormatter {

    private val isoFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun typeLabel(typeCode: String): String = when (typeCode) {
        EventLogEntity.Type.UNLOCK_VERIFIED -> "Unlock verified"
        EventLogEntity.Type.UNLOCK_STRANGER -> "Unknown unlock"
        EventLogEntity.Type.SNATCH_DETECTED -> "Snatch detected"
        EventLogEntity.Type.CONTEXT_BREACH -> "Context breach"
        EventLogEntity.Type.BREACH_CONFIRMED -> "Breach confirmed"
        EventLogEntity.Type.LOCKDOWN_TRIGGERED -> "Lockdown triggered"
        EventLogEntity.Type.FALSE_REJECT_RESOLVED -> "False reject resolved"
        else -> typeCode
    }

    fun timestampLabel(timestampMs: Long): String = isoFormat.format(Date(timestampMs))

    fun toUi(entity: EventLogEntity): TimelineRowUi = TimelineRowUi(
        id = entity.id,
        typeLabel = typeLabel(entity.type),
        timestampLabel = timestampLabel(entity.timestampMs),
        severity = entity.severity,
        foregroundPackage = entity.foregroundPackage,
        notes = entity.notes
    )
}
