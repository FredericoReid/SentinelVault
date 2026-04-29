package com.sentinelvault.ui.enrollment

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.sentinelvault.face.EnrollmentRepository
import com.sentinelvault.security.MemorySanitizer
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnrollmentViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: EnrollmentRepository
    private lateinit var sanitizer: MemorySanitizer

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        sanitizer = mockk(relaxed = true)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(): EnrollmentViewModel =
        EnrollmentViewModel(repository, sanitizer, analysisDispatcher = dispatcher)

    private fun mockBitmap(): Bitmap = mockk(relaxed = true) {
        every { isRecycled } returns false
    }

    @Test
    fun `initial state is Idle without face`() = runTest(dispatcher) {
        val vm = newViewModel()
        assertThat(vm.state.value.status).isEqualTo(EnrollmentStatus.Idle)
        assertThat(vm.state.value.hasFace).isFalse()
        assertThat(vm.events.value).isNull()
    }

    @Test
    fun `processFrame updates quality flow and recycles the bitmap`() = runTest(dispatcher) {
        val vm = newViewModel()
        val bitmap = mockBitmap()
        every { repository.isFaceWellFramed(bitmap) } returns true
        every { sanitizer.recycle(bitmap) } just Runs

        vm.processFrame(bitmap)
        advanceUntilIdle()

        assertThat(vm.state.value.hasFace).isTrue()
        assertThat(vm.quality.value).isTrue()
        verify(exactly = 1) { sanitizer.recycle(bitmap) }
    }

    @Test
    fun `processFrame recycles the bitmap even when detection throws`() = runTest(dispatcher) {
        val vm = newViewModel()
        val bitmap = mockBitmap()
        every { repository.isFaceWellFramed(bitmap) } throws RuntimeException("detector boom")

        vm.processFrame(bitmap)
        advanceUntilIdle()

        assertThat(vm.state.value.hasFace).isFalse()
        verify(exactly = 1) { sanitizer.recycle(bitmap) }
    }

    @Test
    fun `capture transitions to Saved and emits EnrollmentCompleted on success`() = runTest(dispatcher) {
        val vm = newViewModel()
        val bitmap = mockBitmap()
        coEvery { repository.enroll(bitmap) } returns EnrollmentRepository.Result.Success

        vm.capture(bitmap)
        advanceUntilIdle()

        assertThat(vm.state.value.status).isEqualTo(EnrollmentStatus.Saved)
        assertThat(vm.events.value).isEqualTo(EnrollmentEvent.EnrollmentCompleted)
    }

    @Test
    fun `capture maps NoFaceDetected to NoFace status`() = runTest(dispatcher) {
        val vm = newViewModel()
        val bitmap = mockBitmap()
        coEvery { repository.enroll(bitmap) } returns EnrollmentRepository.Result.NoFaceDetected

        vm.capture(bitmap)
        advanceUntilIdle()

        assertThat(vm.state.value.status).isEqualTo(EnrollmentStatus.NoFace)
        assertThat(vm.events.value).isNull()
    }

    @Test
    fun `capture maps EmbedderUnavailable to status with reason`() = runTest(dispatcher) {
        val vm = newViewModel()
        val bitmap = mockBitmap()
        coEvery { repository.enroll(bitmap) } returns
            EnrollmentRepository.Result.EmbedderUnavailable("model missing")

        vm.capture(bitmap)
        advanceUntilIdle()

        assertThat(vm.state.value.status).isEqualTo(EnrollmentStatus.EmbedderUnavailable)
        assertThat(vm.state.value.errorMessage).isEqualTo("model missing")
    }

    @Test
    fun `capture maps Failure to Error status`() = runTest(dispatcher) {
        val vm = newViewModel()
        val bitmap = mockBitmap()
        coEvery { repository.enroll(bitmap) } returns
            EnrollmentRepository.Result.Failure(IllegalStateException("dao boom"))

        vm.capture(bitmap)
        advanceUntilIdle()

        assertThat(vm.state.value.status).isEqualTo(EnrollmentStatus.Error)
        assertThat(vm.state.value.errorMessage).isEqualTo("dao boom")
    }

    @Test
    fun `consumeEvent clears the latest event`() = runTest(dispatcher) {
        val vm = newViewModel()
        val bitmap = mockBitmap()
        coEvery { repository.enroll(bitmap) } returns EnrollmentRepository.Result.Success

        vm.capture(bitmap); advanceUntilIdle()
        vm.consumeEvent()

        assertThat(vm.events.value).isNull()
    }

    @Test
    fun `resetError moves error states back to Idle`() = runTest(dispatcher) {
        val vm = newViewModel()
        val bitmap = mockBitmap()
        coEvery { repository.enroll(bitmap) } returns EnrollmentRepository.Result.NoFaceDetected

        vm.capture(bitmap); advanceUntilIdle()
        assertThat(vm.state.value.status).isEqualTo(EnrollmentStatus.NoFace)
        vm.resetError()
        assertThat(vm.state.value.status).isEqualTo(EnrollmentStatus.Idle)
    }

    @Test
    fun `concurrent capture is dropped while one is in flight`() = runTest(dispatcher) {
        val vm = newViewModel()
        val first = mockBitmap()
        val second = mockBitmap()
        coEvery { repository.enroll(first) } returns EnrollmentRepository.Result.Success

        vm.capture(first)
        vm.capture(second) // dropped because status flips to Capturing synchronously
        advanceUntilIdle()

        verify(exactly = 1) { second.recycle() }
    }
}
