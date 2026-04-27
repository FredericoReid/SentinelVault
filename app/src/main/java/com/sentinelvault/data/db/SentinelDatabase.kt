package com.sentinelvault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sentinelvault.data.db.converter.Converters
import com.sentinelvault.data.db.dao.EmbeddingDao
import com.sentinelvault.data.db.dao.EventLogDao
import com.sentinelvault.data.db.entity.EmbeddingEntity
import com.sentinelvault.data.db.entity.EventLogEntity

@Database(
    entities = [EmbeddingEntity::class, EventLogEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SentinelDatabase : RoomDatabase() {
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun eventLogDao(): EventLogDao

    companion object {
        const val NAME = "sentinel.db"
    }
}
