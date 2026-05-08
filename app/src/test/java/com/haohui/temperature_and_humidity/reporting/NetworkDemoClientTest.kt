package com.haohui.temperature_and_humidity.reporting

import com.haohui.temperature_and_humidity.model.NetworkDemoStatus
import com.haohui.temperature_and_humidity.model.ReportRecord
import com.haohui.temperature_and_humidity.model.ReportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDemoClientTest {
    @Test
    fun buildPayload_excludesPlainPointNameAndIncludesMeasurementValues() {
        val record = record()

        val payload = NetworkDemoClient().buildPayload(record)

        assertEquals("r1", payload.reportId)
        assertEquals(24.5, payload.temperatureCelsius, 0.001)
        assertEquals(61.0, payload.humidityRh, 0.001)
        assertEquals(1_013.2, payload.pressureHpa ?: 0.0, 0.001)
        assertTrue(payload.demoEstimate)
        assertFalse(payload.toJson().contains("冷库 A"))
    }

    private fun record() = ReportRecord(
        id = "r1",
        pointName = "冷库 A",
        temperatureCelsius = 24.5,
        humidityRh = 61.0,
        pressureHpa = 1_013.2,
        confidence = 0.7,
        qualitySummary = "质控通过",
        sourceSummary = "融合",
        measuredAtMillis = 100L,
        createdAtMillis = 100L,
        updatedAtMillis = 100L,
        status = ReportStatus.SAVED,
        diagnosticsSummary = "未标定演示估算",
        isDemoEstimate = true,
        networkDemoStatus = NetworkDemoStatus.NOT_RUN
    )
}
