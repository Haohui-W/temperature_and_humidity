package com.haohui.temperature_and_humidity.measurement

import com.haohui.temperature_and_humidity.model.Estimate
import com.haohui.temperature_and_humidity.model.EstimateSource
import com.haohui.temperature_and_humidity.model.MeasurementSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementServiceTest {
    @Test
    fun runMeasurement_includesValidPressureReading() {
        val state = service(pressureHpa = 1_013.2).runMeasurement(allowAcoustic = false)

        assertTrue(state is MeasurementSessionState.Completed)
        val result = (state as MeasurementSessionState.Completed).result
        assertEquals(1_013.2, result.pressureHpa ?: 0.0, 0.001)
        assertEquals("101.3", result.displayPressureValue())
        assertEquals("101.3 kPa", result.displayPressure())
    }

    @Test
    fun runMeasurement_keepsPressureUnavailableWhenReaderReturnsNull() {
        val state = service(pressureHpa = null).runMeasurement(allowAcoustic = false)

        assertTrue(state is MeasurementSessionState.Completed)
        val result = (state as MeasurementSessionState.Completed).result
        assertEquals(null, result.pressureHpa)
        assertEquals("--", result.displayPressureValue())
        assertEquals("-- kPa", result.displayPressure())
    }

    @Test
    fun runMeasurement_filtersInvalidPressureWithoutFailingMeasurement() {
        val state = service(pressureHpa = 1_200.0).runMeasurement(allowAcoustic = false)

        assertTrue(state is MeasurementSessionState.Completed)
        val result = (state as MeasurementSessionState.Completed).result
        assertEquals(null, result.pressureHpa)
        assertEquals(24.0, result.temperatureCelsius, 0.001)
        assertEquals(60.0, result.humidityRh, 0.001)
    }

    private fun service(pressureHpa: Double?) = MeasurementService(
        acousticEstimator = UnavailableAcousticEstimator(),
        thermalEstimator = FakeThermalEstimator,
        pressureReader = FakePressureReader(pressureHpa),
        clock = { 100L }
    )

    private object FakeThermalEstimator : ThermalEstimator {
        override fun estimate() = Estimate(
            source = EstimateSource.THERMAL,
            temperatureCelsius = 24.0,
            humidityRh = 60.0,
            confidence = 0.7,
            inputQuality = 0.7,
            sourceSummary = "fake thermal"
        )
    }

    private class FakePressureReader(private val pressureHpa: Double?) : PressureReader {
        override fun readPressureHpa(): Double? = pressureHpa
    }
}
