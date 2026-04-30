package com.sentinelvault.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Owner facial embedding (192-d MobileFaceNet vector). The original photo is destroyed
 * immediately after extraction (see [com.sentinelvault.security] / Epic 3).
 */
@Entity(tableName = "embedding")
data class EmbeddingEntity(
    @PrimaryKey val id: Long = OWNER_ID,
    val vector: FloatArray,
    val createdAtMs: Long
) {
    companion object {
        const val OWNER_ID: Long = 1L
        const val VECTOR_SIZE: Int = 192
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingEntity) return false
        return id == other.id &&
            createdAtMs == other.createdAtMs &&
            vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + createdAtMs.hashCode()
        return result
    }
}
