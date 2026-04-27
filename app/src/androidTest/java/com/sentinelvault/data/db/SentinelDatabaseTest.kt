package com.sentinelvault.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sentinelvault.data.db.entity.EmbeddingEntity
import com.sentinelvault.data.db.entity.EventLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SentinelDatabaseTest {

    private lateinit var db: SentinelDatabase

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val factory = SupportOpenHelperFactory("test-passphrase".toByteArray())
        db = Room.inMemoryDatabaseBuilder(ctx, SentinelDatabase::class.java)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun owner_embedding_round_trip() = runBlocking {
        val dao = db.embeddingDao()
        val vector = FloatArray(EmbeddingEntity.VECTOR_SIZE) { it.toFloat() / 128f }
        dao.upsert(EmbeddingEntity(vector = vector, createdAtMs = 42L))

        val stored = dao.getOwnerVector()
        assertThat(stored).isNotNull()
        assertThat(stored!!.toList()).isEqualTo(vector.toList())
    }

    @Test
    fun event_log_insert_and_timeline_flow() = runBlocking {
        val dao = db.eventLogDao()
        dao.insertBreach(
            EventLogEntity(
                timestampMs = 1L,
                type = EventLogEntity.Type.UNLOCK_STRANGER,
                severity = 2,
                foregroundPackage = "com.example.bank",
                evidencePath = null,
                notes = null
            )
        )
        dao.insertBreach(
            EventLogEntity(
                timestampMs = 2L,
                type = EventLogEntity.Type.BREACH_CONFIRMED,
                severity = 3,
                foregroundPackage = null,
                evidencePath = "vault/intruder1.webp",
                notes = "intruder confirmed"
            )
        )

        val timeline = dao.getIncidentTimeline().first()
        assertThat(timeline).hasSize(2)
        assertThat(timeline.first().timestampMs).isEqualTo(2L)
        assertThat(dao.count()).isEqualTo(2)
    }

    @Test
    fun event_log_delete_by_id() = runBlocking {
        val dao = db.eventLogDao()
        val id = dao.insertBreach(
            EventLogEntity(
                timestampMs = 10L,
                type = EventLogEntity.Type.SNATCH_DETECTED,
                severity = 1,
                foregroundPackage = null,
                evidencePath = null,
                notes = null
            )
        )
        assertThat(dao.findById(id)).isNotNull()
        dao.deleteById(id)
        assertThat(dao.findById(id)).isNull()
    }
}
