package com.sentinelvault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sentinelvault.data.db.entity.EventLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Internal "endpoint" for the intrusion timeline (see guide.md §6).
 */
@Dao
interface EventLogDao {

    @Insert
    suspend fun insertBreach(log: EventLogEntity): Long

    @Query("SELECT * FROM event_log ORDER BY timestampMs DESC")
    fun getIncidentTimeline(): Flow<List<EventLogEntity>>

    @Query("SELECT * FROM event_log WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): EventLogEntity?

    @Query("SELECT COUNT(*) FROM event_log")
    suspend fun count(): Int

    @Query("DELETE FROM event_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM event_log")
    suspend fun deleteAll()
}
