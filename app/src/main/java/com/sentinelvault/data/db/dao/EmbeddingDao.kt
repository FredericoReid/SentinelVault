package com.sentinelvault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sentinelvault.data.db.entity.EmbeddingEntity

/**
 * Internal "endpoint" for the owner facial vector (see guide.md §6).
 */
@Dao
interface EmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EmbeddingEntity)

    @Query("SELECT vector FROM embedding WHERE id = :id LIMIT 1")
    suspend fun getOwnerVector(id: Long = EmbeddingEntity.OWNER_ID): FloatArray?

    @Query("SELECT * FROM embedding WHERE id = :id LIMIT 1")
    suspend fun getOwner(id: Long = EmbeddingEntity.OWNER_ID): EmbeddingEntity?

    @Query("DELETE FROM embedding")
    suspend fun deleteAll()
}
