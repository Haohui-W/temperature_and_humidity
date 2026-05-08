package com.haohui.temperature_and_humidity.reporting

import com.haohui.temperature_and_humidity.model.MeasurementResult
import com.haohui.temperature_and_humidity.model.ReportDraft
import com.haohui.temperature_and_humidity.model.ReportSaveError

class ReportFactory {
    fun createDraft(pointName: String, measurement: MeasurementResult): DraftResult {
        val normalizedPoint = pointName.trim()
        if (normalizedPoint.isEmpty()) {
            return DraftResult.Failure(ReportSaveError.EMPTY_POINT)
        }
        if (!measurement.quality.passed) {
            return DraftResult.Failure(ReportSaveError.INVALID_MEASUREMENT)
        }
        return DraftResult.Success(ReportDraft(normalizedPoint, measurement))
    }
}

sealed class DraftResult {
    data class Success(val draft: ReportDraft) : DraftResult()
    data class Failure(val error: ReportSaveError) : DraftResult()
}
