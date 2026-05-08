package com.haohui.temperature_and_humidity.measurement

import com.haohui.temperature_and_humidity.model.EstimateSource
import com.haohui.temperature_and_humidity.model.MeasurementErrorReason
import com.haohui.temperature_and_humidity.model.MeasurementResult
import com.haohui.temperature_and_humidity.model.QualityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityControlTest {
    private val qualityControl = QualityControl()

    @Test
    fun evaluate_passesNormalResult() {
        val result = qualityControl.evaluate(result())

        assertTrue(result.passed)
    }

    @Test
    fun evaluate_rejectsTemperatureOutOfRange() {
        val result = qualityControl.evaluate(result(temperature = 80.0))

        assertFalse(result.passed)
        assertEquals(MeasurementErrorReason.QUALITY_CHECK_FAILED, result.reason)
    }

    @Test
    fun evaluate_rejectsHumidityOutOfRange() {
        val result = qualityControl.evaluate(result(humidity = 120.0))

        assertFalse(result.passed)
        assertEquals(MeasurementErrorReason.QUALITY_CHECK_FAILED, result.reason)
    }

    @Test
    fun evaluate_rejectsLowConfidence() {
        val result = qualityControl.evaluate(result(confidence = 0.05))

        assertFalse(result.passed)
        assertEquals(MeasurementErrorReason.LOW_CONFIDENCE, result.reason)
    }

    private fun result(
        temperature: Double = 25.0,
        humidity: Double = 60.0,
        confidence: Double = 0.8
    ) = MeasurementResult(
        temperatureCelsius = temperature,
        humidityRh = humidity,
        confidence = confidence,
        source = EstimateSource.FUSED,
        sourceSummary = "融合",
        quality = QualityResult(passed = true),
        measuredAtMillis = 100L,
        isDegraded = false
    )
}
