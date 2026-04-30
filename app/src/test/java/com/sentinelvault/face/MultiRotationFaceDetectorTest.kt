package com.sentinelvault.face

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import com.sentinelvault.security.MemorySanitizer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class MultiRotationFaceDetectorTest {

    private val sanitizer: MemorySanitizer = mockk(relaxed = true)
    private val source: Bitmap = mockk(relaxed = true)
    private val rotated90: Bitmap = mockk(relaxed = true)
    private val rotated180: Bitmap = mockk(relaxed = true)
    private val rotated270: Bitmap = mockk(relaxed = true)

    @Test
    fun `returns first hit without rotating when original frame matches`() {
        val face = FaceBox(RectF(0f, 0f, 1f, 1f), 0.9f)
        val delegate = mockk<FaceDetector> {
            every { detect(source) } returns face
        }

        val detector = MultiRotationFaceDetector(
            delegate = delegate,
            sanitizer = sanitizer,
            rotator = { _, _ -> error("rotation should not happen") }
        )

        assertThat(detector.detect(source)).isEqualTo(face)
        verify(exactly = 0) { sanitizer.recycle(source) }
        verify(exactly = 0) { sanitizer.recycle(rotated90) }
    }

    @Test
    fun `sweeps rotations until a face is found`() {
        val face = FaceBox(RectF(0f, 0f, 1f, 1f), 0.9f)
        val delegate = mockk<FaceDetector> {
            every { detect(source) } returns null
            every { detect(rotated90) } returns null
            every { detect(rotated180) } returns face
        }
        val detector = MultiRotationFaceDetector(
            delegate = delegate,
            sanitizer = sanitizer,
            rotator = { _, degrees ->
                when (degrees.toInt()) {
                    90 -> rotated90
                    180 -> rotated180
                    else -> rotated270
                }
            }
        )

        assertThat(detector.detect(source)).isEqualTo(face)
        verify { sanitizer.recycle(rotated90) }
        verify { sanitizer.recycle(rotated180) }
        verify(exactly = 0) { sanitizer.recycle(rotated270) }
    }

    @Test
    fun `stops sweeping when time budget is exhausted`() {
        val clock = object : MultiRotationFaceDetector.Clock {
            private val values = ArrayDeque(listOf(0L, 0L, 55L))
            override fun nowMs(): Long = values.removeFirst()
        }
        val delegate = mockk<FaceDetector> {
            every { detect(any()) } returns null
        }
        val detector = MultiRotationFaceDetector(
            delegate = delegate,
            sanitizer = sanitizer,
            clock = clock,
            rotator = { _, _ -> rotated90 }
        )

        assertThat(detector.detect(source)).isNull()
        verify(exactly = 1) { sanitizer.recycle(rotated90) }
        verify(exactly = 0) { sanitizer.recycle(rotated180) }
        verify(exactly = 0) { sanitizer.recycle(rotated270) }
    }
}