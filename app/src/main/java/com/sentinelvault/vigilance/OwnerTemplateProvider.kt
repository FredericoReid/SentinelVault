package com.sentinelvault.vigilance

import com.sentinelvault.data.db.dao.EmbeddingDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single read access point for the owner's facial template. Wraps [EmbeddingDao] behind a
 * narrow interface so the verification engine — and its unit tests — never touch SQLCipher
 * directly. The provider returns `null` when enrollment hasn't happened yet.
 */
fun interface OwnerTemplateProvider {
    suspend fun load(): FloatArray?
}

@Singleton
class DaoOwnerTemplateProvider @Inject constructor(
    private val embeddingDao: EmbeddingDao
) : OwnerTemplateProvider {
    override suspend fun load(): FloatArray? = embeddingDao.getOwnerVector()
}
