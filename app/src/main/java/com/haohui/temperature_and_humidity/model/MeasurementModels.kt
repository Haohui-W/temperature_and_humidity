package com.haohui.temperature_and_humidity.model

enum class MeasurementPhase {
    IDLE,
    CHECKING_PERMISSION,
    COLLECTING_SIGNALS,
    THERMAL_ONLY_DELAY,
    ESTIMATING,
    QUALITY_CHECK,
    SUCCESS,
    DEGRADED_SUCCESS,
    NEEDS_RETRY,
    FAILED
}

enum class EstimateSource(val label: String) {
    ACOUSTIC("声学"),
    THERMAL("热特征"),
    FUSED("融合")
}

enum class MeasurementErrorReason(val userMessage: String) {
    MICROPHONE_PERMISSION_DENIED("未授权麦克风，已使用热特征降级模式"),
    AUDIO_UNAVAILABLE("设备音频输入不可用，已使用热特征降级模式"),
    TDOA_UNAVAILABLE("声学 TDOA 未检测到有效双麦延迟，已使用热特征降级模式"),
    SOUND_SPEED_OUT_OF_RANGE("声学 TDOA 已返回，但反推声速超出空气声速范围，已使用热特征降级模式"),
    THERMAL_INPUT_UNAVAILABLE("热特征输入不可用"),
    LOW_CONFIDENCE("当前环境信号不佳，请重新测量"),
    QUALITY_CHECK_FAILED("测量结果异常，请重新测量"),
    UNKNOWN("测量失败，请重试")
}

data class ModelMetadata(
    val modelId: String,
    val version: String,
    val calibrated: Boolean,
    val summary: String
) {
    val displaySummary: String
        get() = if (calibrated) {
            "$summary（$version，已标定）"
        } else {
            "$summary（$version）"
        }
}

data class MeasurementDiagnostics(
    val isDemoEstimate: Boolean = false,
    val modelSummary: String = "",
    val deviceSummary: String = "",
    val tdoaSummary: String = "",
    val soundSpeedMetersPerSecond: Double? = null,
    val inputSummary: String = ""
) {
    fun compactSummary(): String = listOf(
        modelSummary,
        deviceSummary,
        tdoaSummary,
        inputSummary
    ).filter { it.isNotBlank() }.distinct().joinToString("；")
}

data class Estimate(
    val source: EstimateSource,
    val temperatureCelsius: Double,
    val humidityRh: Double,
    val confidence: Double,
    val inputQuality: Double,
    val sourceSummary: String,
    val unavailableReason: MeasurementErrorReason? = null,
    val degradationReason: MeasurementErrorReason? = null,
    val diagnostics: MeasurementDiagnostics = MeasurementDiagnostics()
) {
    val isAvailable: Boolean
        get() = unavailableReason == null
}

data class SignalSnapshot(
    val batteryTemperatureCelsius: Double?,
    val cpuTemperatureCelsius: Double?,
    val cpuLoadPercent: Double?,
    val pressureHpa: Double?,
    val audioSampleCount: Int,
    val missingInputs: List<String>
)

data class QualityResult(
    val passed: Boolean,
    val reason: MeasurementErrorReason? = null,
    val message: String = ""
)

data class MeasurementResult(
    val temperatureCelsius: Double,
    val humidityRh: Double,
    val pressureHpa: Double? = null,
    val confidence: Double,
    val source: EstimateSource,
    val sourceSummary: String,
    val quality: QualityResult,
    val measuredAtMillis: Long,
    val isDegraded: Boolean,
    val degradationReason: MeasurementErrorReason? = null,
    val diagnostics: MeasurementDiagnostics = MeasurementDiagnostics()
) {
    fun displayTemperature(): String = "%.1f℃".format(temperatureCelsius)

    fun displayHumidity(): String = "%.0f%%RH".format(humidityRh)

    fun displayPressureValue(): String = pressureHpa?.let { "%.1f".format(it / 10.0) } ?: "--"

    fun displayPressure(): String = "${displayPressureValue()} kPa"

    fun displayConfidence(): String = "%.0f%%".format(confidence * 100.0)
}

sealed class MeasurementSessionState {
    object Idle : MeasurementSessionState()
    data class Running(val phase: MeasurementPhase, val message: String) : MeasurementSessionState()
    data class Completed(val result: MeasurementResult) : MeasurementSessionState()
    data class Failed(val reason: MeasurementErrorReason, val message: String) : MeasurementSessionState()
}
