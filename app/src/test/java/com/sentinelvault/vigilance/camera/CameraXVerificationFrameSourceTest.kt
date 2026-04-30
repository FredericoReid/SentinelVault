package com.sentinelvault.vigilance.camera

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * JVM test for [CameraXVerificationFrameSource] (Task 9.3). The wrapper is intentionally
 * thin — verify it forwards the suspend call exactly once and propagates whatever the
 * session returns (including the timeout-induced `null`).
 */
class CameraXVerificationFrameSourceTest {

    @Test
    fun `capture forwards to session next and returns the bitmap`() = runTest {
        val bitmap = mockk<Bitmap>(relaxed = true)
        val session = mockk<HeadlessCameraSession>()
        coEvery { session.next(any()) } returns bitmap
        val source = CameraXVerificationFrameSource(session)

        val result = source.capture()

        assertThat(result).isSameInstanceAs(bitmap)
        coVerify(exactly = 1) { session.next(any()) }
    }

    @Test
    fun `capture propagates null on session timeout`() = runTest {
        val session = mockk<HeadlessCameraSession>()
        coEvery { session.next(any()) } returns null
        val source = CameraXVerificationFrameSource(session)

        val result = source.capture()

        assertThat(result).isNull()
    }
}
