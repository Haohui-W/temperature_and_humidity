package com.haohui.temperature_and_humidity.reporting

import com.haohui.temperature_and_humidity.model.CopyText
import com.haohui.temperature_and_humidity.model.ReportRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CopyTextBuilder(
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
) {
    fun build(record: ReportRecord): CopyText {
        val measuredAt = dateFormat.format(Date(record.measuredAtMillis))
        val content = listOf(
            "点位：${record.pointName}",
            "温度：${record.displayTemperature()}",
            "湿度：${record.displayHumidity()}",
            "气压：${record.displayPressure()}",
            "测量时间：$measuredAt",
            "置信度：${record.displayConfidence()}",
            "来源：${record.sourceSummary}",
            "质控：${record.qualitySummary}",
            "估算状态：${if (record.isDemoEstimate) "未标定演示估算" else "已标定"}",
            "校准摘要：${record.diagnosticsSummary.ifBlank { "无验证证据" }}"
        ).joinToString("\n")
        return CopyText(record.id, content)
    }
}
