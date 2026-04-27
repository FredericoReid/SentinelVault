package com.sentinelvault.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Records a single security event in the encrypted vault. */
@Entity(tableName = "event_log")
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMs: Long,
    val type: String,
    val severity: Int,
    val foregroundPackage: String?,
    val evidencePath: String?,
    val notes: String?
) {
    object Type {
        const val UNLOCK_VERIFIED = "UNLOCK_VERIFIED"
        const val UNLOCK_STRANGER = "UNLOCK_STRANGER"
        const val SNATCH_DETECTED = "SNATCH_DETECTED"
        const val CONTEXT_BREACH = "CONTEXT_BREACH"
        const val BREACH_CONFIRMED = "BREACH_CONFIRMED"
        const val LOCKDOWN_TRIGGERED = "LOCKDOWN_TRIGGERED"
    }
}
