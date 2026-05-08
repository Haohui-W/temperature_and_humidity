package com.haohui.temperature_and_humidity.measurement

import com.haohui.temperature_and_humidity.model.Estimate
import com.haohui.temperature_and_humidity.model.EstimateSource
import com.haohui.temperature_and_humidity.model.MeasurementErrorReason
import com.haohui.temperature_and_humidity.model.MeasurementResult
import com.haohui.temperature_and_humidity.model.QualityResult

class FusionEngine(
    private val qualityControl: QualityControl = QualityControl()
) {
    fun fuse(acoustic: Estimate?, thermal: Estimate?, measuredAtMillis: Long): FusionOutcome {
        val available = listOfNotNull(acoustic, thermal)
            .filter { it.isAvailable && it.confidence > 0.0 }

        if (available.isEmpty()) {
            return FusionOutcome.Failure(MeasurementErrorReason.LOW_CONFIDENCE)
        }

        val totalConfidence = available.sumOf { it.confidence }
        if (totalConfidence < MIN_TOTAL_CONFIDENCE) {
            return FusionOutcome.Failure(MeasurementErrorReason.LOW_CONFIDENCE)
        }

        val temperature = available.sumOf { it.temperatureCelsius * it.confidence } / totalConfidence
        val humidity = available.sumOf { it.humidityRh * it.confidence } / totalConfidence
        val confidence = (totalConfidence / available.size).coerceIn(0.0, 1.0)
        val isDegraded = available.size < 2
        val degradationReason = if (isDegraded) {
            acoustic?.unavailableReason ?: thermal?.unavailableReason ?: MeasurementErrorReason.AUDIO_UNAVAILABLE
        } else {
            null
        }
        val source = if (available.size > 1) EstimateSource.FUSED else available.first().source
        val sourceSummary = available.joinToString(" + ") { "${it.source.label}(${(it.confidence * 100).toInt()}%)" }

        val result = MeasurementResult(
            temperatureCelsius = temperature,
            humidityRh = humidity,
            confidence = confidence,
            source = source,
            sourceSummary = sourceSummary,
            quality = QualityResult(passed = true),
            measuredAtMillis = measuredAtMillis,
            isDegraded = isDegraded,
            degradationReason = degradationReason
        )

        val quality = qualityControl.evaluate(result)
        return if (quality.passed) {
            FusionOutcome.Success(result.copy(quality = quality))
        } else {
            FusionOutcome.Failure(quality.reason ?: MeasurementErrorReason.QUALITY_CHECK_FAILED)
        }
    }

    private companion object {
        const val MIN_TOTAL_CONFIDENCE = 0.1
    }
}

sealed class FusionOutcome {
    data class Success(val result: MeasurementResult) : FusionOutcome()
    data class Failure(val reason: MeasurementErrorReason) : FusionOutcome()
}
