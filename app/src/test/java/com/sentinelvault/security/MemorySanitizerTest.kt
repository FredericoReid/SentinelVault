package com.sentinelvault.security

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.nio.ByteBuffer
import org.junit.Test

class MemorySanitizerTest {

    private val sanitizer = MemorySanitizer()

    @Test
    fun `zero overwrites every float in place`() {
        val array = floatArrayOf(0.1f, 0.5f, -0.7f, 1.0f)
        sanitizer.zero(array)
        assertThat(array.toList()).containsExactly(0f, 0f, 0f, 0f).inOrder()
    }

    @Test
    fun `zero on empty float array is a no-op`() {
        val empty = FloatArray(0)
        sanitizer.zero(empty) // must not throw
        assertThat(empty.size).isEqualTo(0)
    }

    @Test
    fun `zero overwrites every byte in place`() {
        val array = byteArrayOf(1, 2, 3, 4, 5)
        sanitizer.zero(array)
        assertThat(array.toList()).containsExactly(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte()).inOrder()
    }

    @Test
    fun `zero rewinds and wipes the byte buffer`() {
        val buffer = ByteBuffer.allocate(8).apply { put(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) }
        sanitizer.zero(buffer)
        assertThat(buffer.position()).isEqualTo(0)
        val read = ByteArray(buffer.remaining())
        buffer.get(read)
        assertThat(read.toList()).containsExactly(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(),
            0.toByte(), 0.toByte(), 0.toByte(), 0.toByte()).inOrder()
    }

    @Test
    fun `recycle erases mutable bitmap and releases the native handle`() {
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { bitmap.isRecycled } returns false
        every { bitmap.isMutable } returns true
        every { bitmap.eraseColor(0) } just Runs
        every { bitmap.recycle() } just Runs

        sanitizer.recycle(bitmap)

        verify(exactly = 1) { bitmap.eraseColor(0) }
        verify(exactly = 1) { bitmap.recycle() }
    }

    @Test
    fun `recycle skips immutable bitmap eraseColor but still recycles`() {
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { bitmap.isRecycled } returns false
        every { bitmap.isMutable } returns false
        every { bitmap.recycle() } just Runs

        sanitizer.recycle(bitmap)

        verify(exactly = 0) { bitmap.eraseColor(any()) }
        verify(exactly = 1) { bitmap.recycle() }
    }

    @Test
    fun `recycle is a no-op for null and already-recycled bitmaps`() {
        sanitizer.recycle(null)
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { bitmap.isRecycled } returns true
        sanitizer.recycle(bitmap)
        verify(exactly = 0) { bitmap.recycle() }
    }

    @Test
    fun `close releases the camera image proxy`() {
        val image = mockk<ImageProxy>(relaxed = true)
        sanitizer.close(image)
        verify(exactly = 1) { image.close() }
    }

    @Test
    fun `close swallows exceptions thrown by already-closed proxies`() {
        val image = mockk<ImageProxy>()
        every { image.close() } throws IllegalStateException("already closed")
        sanitizer.close(image) // must not throw
    }
}
