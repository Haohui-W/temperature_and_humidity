package com.haohui.temperature_and_humidity.measurement

import com.haohui.temperature_and_humidity.model.MeasurementDiagnostics
import com.haohui.temperature_and_humidity.model.MeasurementErrorReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TdoaEstimatorTest {
    @Test
    fun kotlinReferenceEstimator_detectsSyntheticOffset() {
        val left = ShortArray(256) { index -> if (index == 80) 10_000 else 0 }
        val right = ShortArray(256) { index -> if (index == 87) 10_000 else 0 }

        val result = KotlinReferenceTdoaEstimator().estimate(left, right, sampleRate = 48_000)

        assertNotNull(result)
        assertEquals(7, result?.sampleOffset)
        assertEquals(7.0 / 48_000.0, result?.deltaSeconds ?: 0.0, 0.000001)
    }

    @Test
    fun kotlinReferenceEstimator_detectsOffsetWithSharedDcBias() {
        val left = ShortArray(512) { 1_000 }
        val right = ShortArray(512) { 1_000 }
        left[160] = 12_000
        right[167] = 12_000

        val result = KotlinReferenceTdoaEstimator().estimate(left, right, sampleRate = 48_000)

        assertNotNull(result)
        assertEquals(7, result?.sampleOffset)
        assertTrue((result?.correlationPeak ?: 0.0) >= 0.7)
    }

    @Test
    fun kotlinReferenceEstimator_rejectsLowCorrelationSignal() {
        val left = ShortArray(256) { index -> ((index * 37) % 201 - 100).toShort() }
        val right = ShortArray(256) { index -> ((index * 19 + 11) % 201 - 100).toShort() }

        val result = KotlinReferenceTdoaEstimator().estimate(left, right, sampleRate = 48_000)

        assertNull(result)
    }

    @Test
    fun splitter_deinterleavesStereoBuffer() {
        val sample = StereoPcmSample(
            buffer = shortArrayOf(1, 10, 2, 20, 3, 30),
            readSamples = 6,
            sampleRate = 48_000
        )

        val channels = StereoChannelSplitter.split(sample)

        assertEquals(listOf<Short>(1, 2, 3), channels?.first?.toList())
        assertEquals(listOf<Short>(10, 20, 30), channels?.second?.toList())
    }

    @Test
    fun signalAnalyzer_detectsNearlyIdenticalChannels() {
        val left = shortArrayOf(0, 100, 200, 100, 0)
        val right = shortArrayOf(0, 100, 200, 100, 0)

        val diagnostics = StereoSignalAnalyzer.analyze(left, right)

        assertEquals(1.0, diagnostics.zeroLagSimilarity, 0.001)
        assertTrue(diagnostics.compactSummary().contains("左右声道近似相同"))
    }

    @Test
    fun legacyMvpFallback_returnsLowConfidenceDegradedAcousticEstimate() {
        val estimate = LegacyAcousticMvpFallback.estimate(
            left = ShortArray(256) { index -> if (index % 20 < 10) 1_000 else -1_000 },
            frameCount = 256,
            reason = MeasurementErrorReason.SOUND_SPEED_OUT_OF_RANGE,
            diagnostics = MeasurementDiagnostics(inputSummary = "声速越界")
        )

        assertNull(estimate.unavailableReason)
        assertEquals(MeasurementErrorReason.SOUND_SPEED_OUT_OF_RANGE, estimate.degradationReason)
        assertTrue(estimate.confidence in 0.12..0.35)
        assertTrue(estimate.diagnostics.isDemoEstimate)
        assertTrue(estimate.sourceSummary.contains("旧版MVP声学启发式兜底"))
    }
}
