package com.haohui.temperature_and_humidity.measurement

import com.haohui.temperature_and_humidity.model.Estimate
import com.haohui.temperature_and_humidity.model.EstimateSource
import com.haohui.temperature_and_humidity.model.MeasurementErrorReason
import com.haohui.temperature_and_humidity.model.MeasurementResult
import com.haohui.temperature_and_humidity.model.QualityResult

class FusionEngine(
    private val qualityControl: QualityControl = QualityControl()
) {
    fun fuse(
        acoustic: Estimate?,
        thermal: Estimate?,
        measuredAtMillis: Long,
        pressureHpa: Double? = null
    ): FusionOutcome {
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
        val degradationReason = available.firstNotNullOfOrNull { it.degradationReason } ?: if (available.size < 2) {
            acoustic?.unavailableReason ?: thermal?.unavailableReason ?: MeasurementErrorReason.AUDIO_UNAVAILABLE
        } else {
            null
        }
        val isDegraded = degradationReason != null
        val source = if (available.size > 1) EstimateSource.FUSED else available.first().source
        val sourceSummary = available.joinToString(" + ") { estimateSourceLabel(it) }
        val diagnostics = com.haohui.temperature_and_humidity.model.MeasurementDiagnostics(
            isDemoEstimate = available.any { it.diagnostics.isDemoEstimate },
            modelSummary = available.mapNotNull { it.diagnostics.modelSummary.takeIf(String::isNotBlank) }.distinct().joinToString(" + "),
            deviceSummary = available.mapNotNull { it.diagnostics.deviceSummary.takeIf(String::isNotBlank) }.distinct().joinToString(" + "),
            tdoaSummary = available.mapNotNull { it.diagnostics.tdoaSummary.takeIf(String::isNotBlank) }.distinct().joinToString(" + "),
            soundSpeedMetersPerSecond = available.firstNotNullOfOrNull { it.diagnostics.soundSpeedMetersPerSecond },
            inputSummary = available.mapNotNull { it.diagnostics.inputSummary.takeIf(String::isNotBlank) }.distinct().joinToString(" + ")
        )

        val result = MeasurementResult(
            temperatureCelsius = temperature,
            humidityRh = humidity,
            pressureHpa = pressureHpa,
            confidence = confidence,
            source = source,
            sourceSummary = sourceSummary,
            quality = QualityResult(passed = true),
            measuredAtMillis = measuredAtMillis,
            isDegraded = isDegraded,
            degradationReason = degradationReason,
            diagnostics = diagnostics
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

    private fun estimateSourceLabel(estimate: Estimate): String {
        val label = if (estimate.source == EstimateSource.ACOUSTIC && estimate.degradationReason != null) {
            "声学兜底"
        } else {
            estimate.source.label
        }
        return "$label(${(estimate.confidence * 100).toInt()}%)"
    }
}

sealed class FusionOutcome {
    data class Success(val result: MeasurementResult) : FusionOutcome()
    data class Failure(val reason: MeasurementErrorReason) : FusionOutcome()
}
