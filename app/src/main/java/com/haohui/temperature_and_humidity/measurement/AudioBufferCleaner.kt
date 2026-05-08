package com.haohui.temperature_and_humidity.measurement

import java.util.Arrays

object AudioBufferCleaner {
    fun clear(buffer: ShortArray?) {
        if (buffer != null) {
            Arrays.fill(buffer, 0)
        }
    }

    fun clearAll(vararg buffers: ShortArray?) {
        buffers.forEach(::clear)
    }
}
