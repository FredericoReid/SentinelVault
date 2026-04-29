package com.sentinelvault.face

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import com.sentinelvault.data.db.dao.EmbeddingDao
import com.sentinelvault.data.db.entity.EmbeddingEntity
import com.sentinelvault.security.MemorySanitizer
import io.mockk.Runs
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnrollmentRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun mockBitmap(): Bitmap = mockk(relaxed = true) {
        every { isRecycled } returns false
        every { isMutable } returns true
        every { eraseColor(0) } just Runs
        every { recycle() } just Runs
    }

    private fun newRepository(
        detector: FaceDetector,
        embedder: FaceEmbedder,
        dao: EmbeddingDao,
        sanitizer: MemorySanitizer = MemorySanitizer(),
        clock: EnrollmentRepository.Clock = EnrollmentRepository.Clock { 1_700_000_000L }
    ) = EnrollmentRepository(detector, embedder, dao, sanitizer, clock, dispatcher)

    @Test
    fun `enroll persists the owner vector and returns Success`() = runTest(dispatcher) {
        val bitmap = mockBitmap()
        val detector = mockk<FaceDetector>()
        val embedder = mockk<FaceEmbedder>()
        val dao = mockk<EmbeddingDao>(relaxed = true)
        val sanitizer = mockk<MemorySanitizer>(relaxed = true)
        every { detector.detect(bitmap) } returns FaceBox(RectF(0f, 0f, 1f, 1f), 0.9f)
        every { embedder.embed(bitmap) } returns FloatArray(EmbeddingEntity.VECTOR_SIZE) { it.toFloat() }
        coEvery { dao.upsert(any()) } just Runs

        val captured = slot<EmbeddingEntity>()
        val result = newRepository(detector, embedder, dao, sanitizer).enroll(bitmap)

        assertThat(result).isEqualTo(EnrollmentRepository.Result.Success)
        coVerify(exactly = 1) { dao.upsert(capture(captured)) }
        assertThat(captured.captured.vector.size).isEqualTo(EmbeddingEntity.VECTOR_SIZE)
        assertThat(captured.captured.createdAtMs).isEqualTo(1_700_000_000L)
        verify { sanitizer.zero(any<FloatArray>()) }
        verify { sanitizer.recycle(bitmap) }
    }

    @Test
    fun `enroll returns NoFaceDetected and recycles bitmap when detector misses`() = runTest(dispatcher) {
        val bitmap = mockBitmap()
        val detector = mockk<FaceDetector> { every { detect(bitmap) } returns null }
        val embedder = mockk<FaceEmbedder>(relaxed = true)
        val dao = mockk<EmbeddingDao>(relaxed = true)
        val sanitizer = mockk<MemorySanitizer>(relaxed = true)

        val result = newRepository(detector, embedder, dao, sanitizer).enroll(bitmap)

        assertThat(result).isEqualTo(EnrollmentRepository.Result.NoFaceDetected)
        coVerify(exactly = 0) { dao.upsert(any()) }
        verify { sanitizer.recycle(bitmap) }
    }

    @Test
    fun `enroll surfaces EmbedderUnavailable when the model is missing`() = runTest(dispatcher) {
        val bitmap = mockBitmap()
        val detector = mockk<FaceDetector> {
            every { detect(bitmap) } returns FaceBox(RectF(0f, 0f, 1f, 1f), 0.9f)
        }
        val embedder = mockk<FaceEmbedder> {
            every { embed(bitmap) } throws FaceEmbedderUnavailableException("missing model")
        }
        val dao = mockk<EmbeddingDao>(relaxed = true)
        val sanitizer = mockk<MemorySanitizer>(relaxed = true)

        val result = newRepository(detector, embedder, dao, sanitizer).enroll(bitmap)

        assertThat(result).isInstanceOf(EnrollmentRepository.Result.EmbedderUnavailable::class.java)
        coVerify(exactly = 0) { dao.upsert(any()) }
        verify { sanitizer.recycle(bitmap) }
    }

    @Test
    fun `enroll wraps unexpected throwables in Failure`() = runTest(dispatcher) {
        val bitmap = mockBitmap()
        val detector = mockk<FaceDetector> {
            every { detect(bitmap) } returns FaceBox(RectF(0f, 0f, 1f, 1f), 0.9f)
        }
        val embedder = mockk<FaceEmbedder> {
            every { embed(bitmap) } returns FloatArray(EmbeddingEntity.VECTOR_SIZE)
        }
        val dao = mockk<EmbeddingDao> { coEvery { upsert(any()) } throws RuntimeException("dao boom") }
        val sanitizer = mockk<MemorySanitizer>(relaxed = true)

        val result = newRepository(detector, embedder, dao, sanitizer).enroll(bitmap)

        assertThat(result).isInstanceOf(EnrollmentRepository.Result.Failure::class.java)
        verify { sanitizer.recycle(bitmap) }
    }

    @Test
    fun `isFaceWellFramed delegates to detector`() {
        val bitmap = mockBitmap()
        val detector = mockk<FaceDetector> {
            every { isFaceWellFramed(bitmap) } returns true
        }
        val repo = newRepository(detector, mockk(relaxed = true), mockk(relaxed = true))
        assertThat(repo.isFaceWellFramed(bitmap)).isTrue()
    }

    @Test
    fun `isEnrolled returns true when the dao yields an owner row`() = runTest(dispatcher) {
        val dao = mockk<EmbeddingDao> {
            coEvery { getOwner(EmbeddingEntity.OWNER_ID) } returns
                EmbeddingEntity(vector = FloatArray(EmbeddingEntity.VECTOR_SIZE), createdAtMs = 0L)
        }
        val repo = newRepository(mockk(relaxed = true), mockk(relaxed = true), dao)
        assertThat(repo.isEnrolled()).isTrue()
    }

    @Test
    fun `enroll throws when embedding size mismatches`() = runTest(dispatcher) {
        val bitmap = mockBitmap()
        val detector = mockk<FaceDetector> {
            every { detect(bitmap) } returns FaceBox(RectF(0f, 0f, 1f, 1f), 0.9f)
        }
        val embedder = mockk<FaceEmbedder> {
            every { embed(bitmap) } returns FloatArray(EmbeddingEntity.VECTOR_SIZE - 1)
        }
        val dao = mockk<EmbeddingDao>(relaxed = true)
        val result = newRepository(detector, embedder, dao).enroll(bitmap)
        assertThat(result).isInstanceOf(EnrollmentRepository.Result.Failure::class.java)
    }
}
