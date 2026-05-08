package com.haohui.temperature_and_humidity.measurement

import com.haohui.temperature_and_humidity.model.MeasurementErrorReason
import com.haohui.temperature_and_humidity.model.MeasurementPhase
import com.haohui.temperature_and_humidity.model.MeasurementSessionState

class MeasurementService(
    private val acousticEstimator: AcousticEstimator,
    private val thermalEstimator: ThermalEstimator,
    private val pressureReader: PressureReader = UnavailablePressureReader,
    private val fusionEngine: FusionEngine = FusionEngine(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    var state: MeasurementSessionState = MeasurementSessionState.Idle
        private set

    fun runMeasurement(allowAcoustic: Boolean): MeasurementSessionState {
        state = MeasurementSessionState.Running(MeasurementPhase.CHECKING_PERMISSION, "正在检查权限")
        val acoustic = if (allowAcoustic) {
            state = MeasurementSessionState.Running(MeasurementPhase.COLLECTING_SIGNALS, "正在采集声学信号")
            acousticEstimator.estimate()
        } else {
            null
        }

        state = if (allowAcoustic) {
            MeasurementSessionState.Running(MeasurementPhase.COLLECTING_SIGNALS, "正在读取热特征")
        } else {
            MeasurementSessionState.Running(MeasurementPhase.THERMAL_ONLY_DELAY, "未授权麦克风，使用热特征模式")
        }
        val thermal = thermalEstimator.estimate()

        state = MeasurementSessionState.Running(MeasurementPhase.COLLECTING_SIGNALS, "正在读取气压")
        val pressureHpa = pressureReader.readValidPressureHpa()

        state = MeasurementSessionState.Running(MeasurementPhase.ESTIMATING, "正在估算温湿度")
        val outcome = fusionEngine.fuse(acoustic, thermal, clock(), pressureHpa)

        state = MeasurementSessionState.Running(MeasurementPhase.QUALITY_CHECK, "正在执行质控")
        state = when (outcome) {
            is FusionOutcome.Success -> MeasurementSessionState.Completed(outcome.result)
            is FusionOutcome.Failure -> MeasurementSessionState.Failed(
                reason = outcome.reason,
                message = outcome.reason.userMessage
            )
        }
        return state
    }

    fun forceThermalOnly(): MeasurementSessionState {
        return runMeasurement(allowAcoustic = false)
    }

    fun reset() {
        state = MeasurementSessionState.Idle
    }
}

class UnavailableAcousticEstimator(
    private val reason: MeasurementErrorReason = MeasurementErrorReason.AUDIO_UNAVAILABLE
) : AcousticEstimator {
    override fun estimate() = com.haohui.temperature_and_humidity.model.Estimate(
        source = com.haohui.temperature_and_humidity.model.EstimateSource.ACOUSTIC,
        temperatureCelsius = 0.0,
        humidityRh = 0.0,
        confidence = 0.0,
        inputQuality = 0.0,
        sourceSummary = reason.userMessage,
        unavailableReason = reason
    )
}
