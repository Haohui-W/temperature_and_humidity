package com.haohui.temperature_and_humidity.reporting

import com.haohui.temperature_and_humidity.model.EstimateSource
import com.haohui.temperature_and_humidity.model.MeasurementResult
import com.haohui.temperature_and_humidity.model.QualityResult
import com.haohui.temperature_and_humidity.model.ReportRecord
import com.haohui.temperature_and_humidity.model.ReportSaveError
import com.haohui.temperature_and_humidity.model.ReportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class ReportWorkflowTest {
    @Test
    fun createDraft_rejectsEmptyPointName() {
        val result = ReportFactory().createDraft(" ", measurement())

        assertTrue(result is DraftResult.Failure)
        assertEquals(ReportSaveError.EMPTY_POINT, (result as DraftResult.Failure).error)
    }

    @Test
    fun createDraft_rejectsFailedQualityResult() {
        val result = ReportFactory().createDraft(
            pointName = "冷库 A",
            measurement = measurement(quality = QualityResult(passed = false, message = "失败"))
        )

        assertTrue(result is DraftResult.Failure)
        assertEquals(ReportSaveError.INVALID_MEASUREMENT, (result as DraftResult.Failure).error)
    }

    @Test
    fun copyTextBuilder_outputsStableReportText() {
        val builder = CopyTextBuilder(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA))
        val text = builder.build(record()).content

        assertTrue(text.contains("点位：冷库 A"))
        assertTrue(text.contains("温度：25.2℃"))
        assertTrue(text.contains("湿度：61%RH"))
        assertTrue(text.contains("气压：101.3 kPa"))
        assertTrue(text.contains("质控：质控通过"))
    }

    @Test
    fun copyTextBuilder_outputsPressureFallbackWhenMissing() {
        val builder = CopyTextBuilder(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA))
        val text = builder.build(record(pressureHpa = null)).content

        assertTrue(text.contains("气压：-- kPa"))
    }

    private fun measurement(
        quality: QualityResult = QualityResult(passed = true, message = "质控通过")
    ) = MeasurementResult(
        temperatureCelsius = 25.2,
        humidityRh = 61.0,
        pressureHpa = 1_013.2,
        confidence = 0.82,
        source = EstimateSource.FUSED,
        sourceSummary = "融合",
        quality = quality,
        measuredAtMillis = 100L,
        isDegraded = false
    )

    private fun record(pressureHpa: Double? = 1_013.2) = ReportRecord(
        id = "r1",
        pointName = "冷库 A",
        temperatureCelsius = 25.2,
        humidityRh = 61.0,
        pressureHpa = pressureHpa,
        confidence = 0.82,
        qualitySummary = "质控通过",
        sourceSummary = "融合",
        measuredAtMillis = 100L,
        createdAtMillis = 100L,
        updatedAtMillis = 100L,
        status = ReportStatus.SAVED
    )
}
