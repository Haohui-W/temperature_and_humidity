package com.haohui.temperature_and_humidity.model

enum class ReportStatus(val label: String) {
    DRAFT("草稿"),
    SAVED("已保存"),
    COPIED("已复制"),
    VOIDED("已作废")
}

data class ReportDraft(
    val pointName: String,
    val measurement: MeasurementResult
)

data class ReportRecord(
    val id: String,
    val pointName: String,
    val temperatureCelsius: Double,
    val humidityRh: Double,
    val pressureHpa: Double? = null,
    val confidence: Double,
    val qualitySummary: String,
    val sourceSummary: String,
    val measuredAtMillis: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val status: ReportStatus
) {
    fun displayTemperature(): String = "%.1f℃".format(temperatureCelsius)

    fun displayHumidity(): String = "%.0f%%RH".format(humidityRh)

    fun displayPressure(): String = pressureHpa?.let { "%.1f kPa".format(it / 10.0) } ?: "-- kPa"

    fun displayConfidence(): String = "%.0f%%".format(confidence * 100.0)
}

data class CopyText(
    val reportId: String,
    val content: String
)

sealed class ReportSaveResult {
    data class Success(val record: ReportRecord) : ReportSaveResult()
    data class Failure(val error: ReportSaveError) : ReportSaveResult()
}

enum class ReportSaveError(val userMessage: String) {
    EMPTY_POINT("请填写点位名称"),
    INVALID_MEASUREMENT("测量结果不可保存"),
    STORAGE_FAILED("本地保存失败，请重试"),
    ENCRYPTION_FAILED("本地加密失败，请重试")
}
