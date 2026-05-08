package com.haohui.temperature_and_humidity.measurement

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceMicCatalogTest {
    private val catalog = DeviceMicCatalog()

    @Test
    fun match_returnsBuiltInDistanceForHuaweiP50() {
        val match = catalog.match("Huawei", "P50 Pro")

        assertEquals(0.05, match.distanceMeters, 0.0001)
        assertEquals(MicDistanceSource.BUILT_IN_BRAND_FAMILY, match.source)
    }

    @Test
    fun match_returnsBuiltInDistanceForRedmiNote12() {
        val match = catalog.match("Xiaomi", "Redmi Note 12")

        assertEquals(0.047, match.distanceMeters, 0.0001)
    }

    @Test
    fun match_usesDefaultForUnknownDevice() {
        val match = catalog.match("unknown", "mystery")

        assertEquals(0.05, match.distanceMeters, 0.0001)
        assertEquals(MicDistanceSource.DEFAULT_UNKNOWN, match.source)
    }
}
