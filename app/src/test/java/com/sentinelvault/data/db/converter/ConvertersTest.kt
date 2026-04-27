package com.sentinelvault.data.db.converter

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `floatArray round-trip preserves values`() {
        val source = FloatArray(128) { i -> (i.toFloat() / 128f) - 0.5f }
        val bytes = converters.floatArrayToBytes(source)
        val back = converters.bytesToFloatArray(bytes)
        assertThat(back).isNotNull()
        assertThat(back!!.toList()).isEqualTo(source.toList())
    }

    @Test
    fun `null in null out`() {
        assertThat(converters.floatArrayToBytes(null)).isNull()
        assertThat(converters.bytesToFloatArray(null)).isNull()
    }
}
