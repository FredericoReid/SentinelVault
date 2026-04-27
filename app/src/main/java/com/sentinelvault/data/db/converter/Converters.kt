package com.sentinelvault.data.db.converter

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Room type-converters for primitive arrays not supported natively. */
class Converters {

    @TypeConverter
    fun floatArrayToBytes(value: FloatArray?): ByteArray? {
        if (value == null) return null
        val buf = ByteBuffer.allocate(value.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        for (f in value) buf.putFloat(f)
        return buf.array()
    }

    @TypeConverter
    fun bytesToFloatArray(value: ByteArray?): FloatArray? {
        if (value == null) return null
        val buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(value.size / Float.SIZE_BYTES)
        for (i in out.indices) out[i] = buf.float
        return out
    }
}
