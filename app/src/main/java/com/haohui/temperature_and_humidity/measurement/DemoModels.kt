package com.haohui.temperature_and_humidity.measurement

import com.haohui.temperature_and_humidity.model.ModelMetadata
import kotlin.math.abs

data class AcousticModelInput(
    val soundSpeedMetersPerSecond: Double,
    val pressureHpa: Double?,
    val inputQuality: Double
)

data class ThermalModelInput(
    val batteryTemperatureCelsius: Double?,
    val cpuTemperatureCelsius: Double?,
    val cpuLoadPercent: Double?,
    val previousCpuLoadPercent: Double?
)

data class DemoModelOutput(
    val temperatureCelsius: Double,
    val humidityRh: Double,
    val confidence: Double,
    val inputQuality: Double,
    val metadata: ModelMetadata,
    val summary: String
)

class AcousticDemoModel(
    private val metadata: ModelMetadata = ModelMetadata(
        modelId = "acoustic-demo",
        version = "v0",
        calibrated = false,
        summary = "声学未标定演示模型"
    )
) {
    fun predict(input: AcousticModelInput): DemoModelOutput? {
        val speed = input.soundSpeedMetersPerSecond
        if (!speed.isFinite() || speed !in MIN_SOUND_SPEED..MAX_SOUND_SPEED) {
            return null
        }
        val pressureAdjustment = ((input.pressureHpa ?: STANDARD_PRESSURE_HPA) - STANDARD_PRESSURE_HPA) * 0.002
        val temperature = ((speed - 331.3) / 0.606 + pressureAdjustment).coerceIn(-10.0, 50.0)
        val humidity = (55.0 + (input.pressureHpa ?: STANDARD_PRESSURE_HPA - STANDARD_PRESSURE_HPA) * 0.01)
            .coerceIn(0.0, 100.0)
        val confidence = input.inputQuality.coerceIn(0.1, 0.85)
        return DemoModelOutput(
            temperatureCelsius = temperature,
            humidityRh = humidity,
            confidence = confidence,
            inputQuality = input.inputQuality.coerceIn(0.0, 1.0),
            metadata = metadata,
            summary = metadata.displaySummary
        )
    }

    private companion object {
        const val MIN_SOUND_SPEED = 300.0
        const val MAX_SOUND_SPEED = 380.0
        const val STANDARD_PRESSURE_HPA = 1_013.25
    }
}

class ThermalDemoModel(
    private val metadata: ModelMetadata = ModelMetadata(
        modelId = "thermal-demo",
        version = "v0",
        calibrated = false,
        summary = "热特征未标定演示模型"
    )
) {
    fun predict(input: ThermalModelInput): DemoModelOutput? {
        val load = input.cpuLoadPercent ?: DEFAULT_CPU_LOAD_PERCENT
        val estimates = buildList {
            input.batteryTemperatureCelsius?.let { add(estimateFromBattery(it)) }
            input.cpuTemperatureCelsius?.let { add(estimateFromCpu(it, load)) }
        }
        if (estimates.isEmpty()) {
            return null
        }
        val estimatedTemperature = weightedThermalEstimate(
            batteryEstimate = input.batteryTemperatureCelsius?.let(::estimateFromBattery),
            cpuEstimate = input.cpuTemperatureCelsius?.let { estimateFromCpu(it, load) },
            fallback = estimates.average()
        ).coerceIn(-10.0, 50.0)
        val stabilityBonus = input.previousCpuLoadPercent?.let { previous ->
            if (abs(load - previous) < 5.0) 0.1 else 0.0
        } ?: 0.0
        val availableInputs = listOfNotNull(
            input.batteryTemperatureCelsius,
            input.cpuTemperatureCelsius,
            input.cpuLoadPercent
        ).size
        val confidence = (when (availableInputs) {
            3 -> 0.72
            2 -> 0.55
            else -> 0.35
        } + stabilityBonus).coerceIn(0.1, 0.82)
        val estimatedHumidity = (62.0 - (estimatedTemperature - 24.0) * 1.0).coerceIn(0.0, 100.0)
        return DemoModelOutput(
            temperatureCelsius = estimatedTemperature,
            humidityRh = estimatedHumidity,
            confidence = confidence,
            inputQuality = confidence,
            metadata = metadata,
            summary = metadata.displaySummary
        )
    }

    private fun estimateFromBattery(value: Double): Double {
        val deviceHeatBias = ((value - 25.0) * 0.55).coerceIn(0.0, 8.0)
        return value - deviceHeatBias
    }

    private fun estimateFromCpu(value: Double, load: Double): Double {
        val loadBias = (load.coerceIn(0.0, 100.0) - DEFAULT_CPU_LOAD_PERCENT).coerceAtLeast(0.0) * 0.04
        return value - 12.0 - loadBias
    }

    private fun weightedThermalEstimate(
        batteryEstimate: Double?,
        cpuEstimate: Double?,
        fallback: Double
    ): Double = when {
        batteryEstimate != null && cpuEstimate != null -> batteryEstimate * 0.7 + cpuEstimate * 0.3
        batteryEstimate != null -> batteryEstimate
        cpuEstimate != null -> cpuEstimate
        else -> fallback
    }

    private companion object {
        const val DEFAULT_CPU_LOAD_PERCENT = 20.0
    }
}
