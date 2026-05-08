package com.haohui.temperature_and_humidity.measurement

import com.haohui.temperature_and_humidity.model.MeasurementErrorReason
import com.haohui.temperature_and_humidity.model.MeasurementResult
import com.haohui.temperature_and_humidity.model.QualityResult

class QualityControl {
    fun evaluate(result: MeasurementResult): QualityResult {
        if (result.temperatureCelsius !in MIN_TEMPERATURE..MAX_TEMPERATURE) {
            return QualityResult(
                passed = false,
                reason = MeasurementErrorReason.QUALITY_CHECK_FAILED,
                message = "温度超出允许范围"
            )
        }
        if (result.humidityRh !in MIN_HUMIDITY..MAX_HUMIDITY) {
            return QualityResult(
                passed = false,
                reason = MeasurementErrorReason.QUALITY_CHECK_FAILED,
                message = "湿度超出允许范围"
            )
        }
        if (result.confidence < MIN_CONFIDENCE) {
            return QualityResult(
                passed = false,
                reason = MeasurementErrorReason.LOW_CONFIDENCE,
                message = "置信度过低"
            )
        }
        return QualityResult(passed = true, message = "质控通过")
    }

    private companion object {
        const val MIN_TEMPERATURE = -10.0
        const val MAX_TEMPERATURE = 50.0
        const val MIN_HUMIDITY = 0.0
        const val MAX_HUMIDITY = 100.0
        const val MIN_CONFIDENCE = 0.1
    }
}
