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

    @Query("SELECT * FROM embedding WHERE id = :id LIMIT 1")
    suspend fun getOwner(id: Long = EmbeddingEntity.OWNER_ID): EmbeddingEntity?

    /**
     * Convenience accessor for the raw 128-d vector. Implemented as a default method to
     * sidestep Room 2.7's primitive-array unwrap heuristic, which would otherwise treat a
     * direct `SELECT vector` query as `Array<Float>` (one Float per row) rather than as a
     * BLOB column passed through [com.sentinelvault.data.db.converter.Converters].
     */
    suspend fun getOwnerVector(id: Long = EmbeddingEntity.OWNER_ID): FloatArray? =
        getOwner(id)?.vector

    @Query("DELETE FROM embedding")
    suspend fun deleteAll()
}
