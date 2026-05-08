package com.haohui.temperature_and_humidity

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.haohui.temperature_and_humidity.model.EstimateSource
import com.haohui.temperature_and_humidity.model.MeasurementResult
import com.haohui.temperature_and_humidity.model.QualityResult
import com.haohui.temperature_and_humidity.model.ReportDraft
import com.haohui.temperature_and_humidity.model.ReportSaveResult
import com.haohui.temperature_and_humidity.model.ReportStatus
import com.haohui.temperature_and_humidity.storage.LocalReportStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalReportStoreInstrumentedTest {
    @Test
    fun saveListAndMarkCopied_roundTripsEncryptedReport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalReportStore(context, clock = { 1_000L })
        val draft = ReportDraft("模拟器点位", measurement())

        val result = store.saveDraft(draft)

        assertTrue(result is ReportSaveResult.Success)
        val saved = (result as ReportSaveResult.Success).record
        val loaded = store.get(saved.id)
        assertEquals("模拟器点位", loaded?.pointName)
        assertEquals(1_013.2, loaded?.pressureHpa ?: 0.0, 0.001)
        assertEquals(ReportStatus.SAVED, loaded?.status)

        val copied = store.markCopied(saved.id)
        assertEquals(ReportStatus.COPIED, copied?.status)
    }

    private fun measurement() = MeasurementResult(
        temperatureCelsius = 24.0,
        humidityRh = 60.0,
        pressureHpa = 1_013.2,
        confidence = 0.8,
        source = EstimateSource.FUSED,
        sourceSummary = "融合",
        quality = QualityResult(passed = true, message = "质控通过"),
        measuredAtMillis = 100L,
        isDegraded = false
    )
}
