package com.sentinelvault.di

import android.content.Context
import androidx.room.Room
import com.sentinelvault.data.db.DatabaseKeyProvider
import com.sentinelvault.data.db.SentinelDatabase
import com.sentinelvault.data.db.dao.EmbeddingDao
import com.sentinelvault.data.db.dao.EventLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider
    ): SentinelDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = keyProvider.getOrCreatePassphrase()
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, SentinelDatabase::class.java, SentinelDatabase.NAME)
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideEmbeddingDao(db: SentinelDatabase): EmbeddingDao = db.embeddingDao()

    @Provides
    fun provideEventLogDao(db: SentinelDatabase): EventLogDao = db.eventLogDao()
}
