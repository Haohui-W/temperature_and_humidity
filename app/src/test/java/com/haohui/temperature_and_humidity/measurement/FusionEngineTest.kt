package com.haohui.temperature_and_humidity.measurement

import com.haohui.temperature_and_humidity.model.Estimate
import com.haohui.temperature_and_humidity.model.EstimateSource
import com.haohui.temperature_and_humidity.model.MeasurementErrorReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FusionEngineTest {
    private val engine = FusionEngine()

    @Test
    fun fuse_usesWeightedAverageWhenBothSourcesAvailable() {
        val acoustic = estimate(EstimateSource.ACOUSTIC, temperature = 20.0, humidity = 50.0, confidence = 0.8)
        val thermal = estimate(EstimateSource.THERMAL, temperature = 30.0, humidity = 70.0, confidence = 0.2)

        val outcome = engine.fuse(acoustic, thermal, measuredAtMillis = 100L)

        assertTrue(outcome is FusionOutcome.Success)
        val result = (outcome as FusionOutcome.Success).result
        assertEquals(22.0, result.temperatureCelsius, 0.001)
        assertEquals(54.0, result.humidityRh, 0.001)
        assertEquals(EstimateSource.FUSED, result.source)
        assertEquals(false, result.isDegraded)
    }

    @Test
    fun fuse_returnsDegradedResultWhenOnlyOneSourceAvailable() {
        val acoustic = Estimate(
            source = EstimateSource.ACOUSTIC,
            temperatureCelsius = 0.0,
            humidityRh = 0.0,
            confidence = 0.0,
            inputQuality = 0.0,
            sourceSummary = "no audio",
            unavailableReason = MeasurementErrorReason.AUDIO_UNAVAILABLE
        )
        val thermal = estimate(EstimateSource.THERMAL, temperature = 24.0, humidity = 60.0, confidence = 0.6)

        val outcome = engine.fuse(acoustic, thermal, measuredAtMillis = 100L)

        assertTrue(outcome is FusionOutcome.Success)
        val result = (outcome as FusionOutcome.Success).result
        assertEquals(24.0, result.temperatureCelsius, 0.001)
        assertEquals(true, result.isDegraded)
        assertEquals(MeasurementErrorReason.AUDIO_UNAVAILABLE, result.degradationReason)
    }

    @Test
    fun fuse_failsWhenNoSourceIsAvailable() {
        val outcome = engine.fuse(null, null, measuredAtMillis = 100L)

        assertTrue(outcome is FusionOutcome.Failure)
        assertEquals(MeasurementErrorReason.LOW_CONFIDENCE, (outcome as FusionOutcome.Failure).reason)
    }

    private fun estimate(
        source: EstimateSource,
        temperature: Double,
        humidity: Double,
        confidence: Double
    ) = Estimate(
        source = source,
        temperatureCelsius = temperature,
        humidityRh = humidity,
        confidence = confidence,
        inputQuality = confidence,
        sourceSummary = source.label
    )
}
