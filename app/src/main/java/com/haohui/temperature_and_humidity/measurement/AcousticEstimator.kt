package com.haohui.temperature_and_humidity.measurement

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.haohui.temperature_and_humidity.model.Estimate
import com.haohui.temperature_and_humidity.model.EstimateSource
import com.haohui.temperature_and_humidity.model.MeasurementDiagnostics
import com.haohui.temperature_and_humidity.model.MeasurementErrorReason
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

interface AcousticEstimator {
    fun estimate(pressureHpa: Double? = null): Estimate
}

class AndroidAcousticEstimator(
    private val context: Context,
    private val audioSampler: AudioSampler = AudioSampler(),
    private val channelSplitter: StereoChannelSplitter = StereoChannelSplitter,
    private val tdoaEstimator: TdoaEstimator = NativeTdoaEstimator(),
    private val micCatalog: DeviceMicCatalog = DeviceMicCatalog(),
    private val acousticModel: AcousticDemoModel = AcousticDemoModel()
) : AcousticEstimator {

    override fun estimate(pressureHpa: Double?): Estimate {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return unavailable(MeasurementErrorReason.MICROPHONE_PERMISSION_DENIED)
        }

        val sample = audioSampler.sampleOneSecond() ?: return unavailable(MeasurementErrorReason.AUDIO_UNAVAILABLE)
        val split = channelSplitter.split(sample)
        if (split == null) {
            AudioBufferCleaner.clear(sample.buffer)
            return unavailable(MeasurementErrorReason.AUDIO_UNAVAILABLE)
        }

        val (left, right) = split
        return try {
            val signalDiagnostics = StereoSignalAnalyzer.analyze(left, right)
            val baseInputSummary = "声学采样 ${sample.frameCount} 帧，音频源 ${sample.audioSourceName}，${signalDiagnostics.compactSummary()}"
            val tdoa = tdoaEstimator.estimate(left, right, sample.sampleRate)
                ?: return legacyFallback(
                    left = left,
                    frameCount = sample.frameCount,
                    reason = MeasurementErrorReason.TDOA_UNAVAILABLE,
                    diagnostics = MeasurementDiagnostics(inputSummary = baseInputSummary)
                )
            if (!tdoa.isUsable) {
                return legacyFallback(
                    left = left,
                    frameCount = sample.frameCount,
                    reason = MeasurementErrorReason.TDOA_UNAVAILABLE,
                    diagnostics = MeasurementDiagnostics(
                        tdoaSummary = tdoa.summary(),
                        inputSummary = baseInputSummary
                    )
                )
            }
            val mic = micCatalog.currentDevice()
            val soundSpeed = mic.distanceMeters / abs(tdoa.deltaSeconds)
            val soundSpeedDiagnosis = soundSpeedDiagnosis(mic.distanceMeters, sample.sampleRate, tdoa, soundSpeed)
            val diagnostics = MeasurementDiagnostics(
                isDemoEstimate = false,
                deviceSummary = mic.summary(),
                tdoaSummary = tdoa.summary(),
                soundSpeedMetersPerSecond = soundSpeed,
                inputSummary = "$baseInputSummary，$soundSpeedDiagnosis"
            )
            val output = acousticModel.predict(
                AcousticModelInput(
                    soundSpeedMetersPerSecond = soundSpeed,
                    pressureHpa = pressureHpa,
                    inputQuality = tdoa.inputQuality
                )
            ) ?: return legacyFallback(
                left = left,
                frameCount = sample.frameCount,
                reason = MeasurementErrorReason.SOUND_SPEED_OUT_OF_RANGE,
                diagnostics = diagnostics.copy(
                    soundSpeedMetersPerSecond = null,
                    inputSummary = diagnostics.inputSummary + "，声速越界"
                )
            )

            val successDiagnostics = diagnostics.copy(
                isDemoEstimate = !output.metadata.calibrated,
                modelSummary = output.metadata.displaySummary,
            )
            Estimate(
                source = EstimateSource.ACOUSTIC,
                temperatureCelsius = output.temperatureCelsius,
                humidityRh = output.humidityRh,
                confidence = output.confidence,
                inputQuality = output.inputQuality,
                sourceSummary = "声学(${(output.confidence * 100).toInt()}%)，${successDiagnostics.compactSummary()}",
                diagnostics = successDiagnostics
            )
        } finally {
            AudioBufferCleaner.clearAll(sample.buffer, left, right)
        }
    }

    private fun unavailable(
        reason: MeasurementErrorReason,
        diagnostics: MeasurementDiagnostics = MeasurementDiagnostics()
    ): Estimate = Estimate(
        source = EstimateSource.ACOUSTIC,
        temperatureCelsius = 0.0,
        humidityRh = 0.0,
        confidence = 0.0,
        inputQuality = 0.0,
        sourceSummary = listOf(reason.userMessage, diagnostics.compactSummary())
            .filter { it.isNotBlank() }
            .joinToString("；"),
        unavailableReason = reason,
        diagnostics = diagnostics
    )

    private fun legacyFallback(
        left: ShortArray,
        frameCount: Int,
        reason: MeasurementErrorReason,
        diagnostics: MeasurementDiagnostics
    ): Estimate = LegacyAcousticMvpFallback.estimate(
        left = left,
        frameCount = frameCount,
        reason = reason,
        diagnostics = diagnostics
    )

    private fun soundSpeedDiagnosis(
        micDistanceMeters: Double,
        sampleRate: Int,
        tdoa: TdoaResult,
        soundSpeed: Double
    ): String {
        // Live device testing showed persistent 0-2 sample offsets with both native and Kotlin
        // TDOA, so treat tiny lag as an input/geometry/device-audio risk rather than a native bug.
        val expectedMinLag = micDistanceMeters / MAX_REASONABLE_SOUND_SPEED * sampleRate
        val expectedMaxLag = micDistanceMeters / MIN_REASONABLE_SOUND_SPEED * sampleRate
        val observedLag = abs(tdoa.sampleOffset)
        val angleOrStereoHint = if (observedLag < expectedMinLag * 0.75) {
            "实际lag偏小，可能是声源不在双麦轴线、环境漫反射或系统返回伪stereo"
        } else {
            "实际lag接近双麦轴线预期"
        }
        return "声速 %.1fm/s，实际lag %d，轴线预期lag %.1f-%.1f，%s".format(
            soundSpeed,
            observedLag,
            expectedMinLag,
            expectedMaxLag,
            angleOrStereoHint
        )
    }

    private companion object {
        const val MIN_REASONABLE_SOUND_SPEED = 300.0
        const val MAX_REASONABLE_SOUND_SPEED = 380.0
    }
}

object LegacyAcousticMvpFallback {
    fun estimate(
        left: ShortArray,
        frameCount: Int,
        reason: MeasurementErrorReason,
        diagnostics: MeasurementDiagnostics
    ): Estimate {
        if (left.isEmpty() || frameCount <= 0) {
            return Estimate(
                source = EstimateSource.ACOUSTIC,
                temperatureCelsius = 0.0,
                humidityRh = 0.0,
                confidence = 0.0,
                inputQuality = 0.0,
                sourceSummary = reason.userMessage,
                unavailableReason = reason,
                diagnostics = diagnostics
            )
        }

        var energy = 0.0
        var peak = 0
        var zeroCrossings = 0
        var previous = left.first().toInt()
        for (sample in left) {
            val value = sample.toInt()
            energy += value.toDouble() * value.toDouble()
            peak = maxOf(peak, abs(value))
            if ((value >= 0) != (previous >= 0)) {
                zeroCrossings++
            }
            previous = value
        }

        val rms = sqrt(energy / left.size.toDouble()).coerceAtLeast(1.0)
        val normalizedRms = (ln(rms) / ln(Short.MAX_VALUE.toDouble())).coerceIn(0.0, 1.0)
        val zeroCrossingRate = zeroCrossings.toDouble() / left.size.toDouble()
        val legacyConfidence = ((normalizedRms * 0.7) + ((1.0 - zeroCrossingRate).coerceIn(0.0, 1.0) * 0.3))
            .coerceIn(0.1, 1.0)
        val confidence = (legacyConfidence * 0.35).coerceIn(0.12, 0.35)
        val temperature = (20.0 + (normalizedRms - 0.5) * 8.0).coerceIn(-10.0, 50.0)
        val humidity = (55.0 + (zeroCrossingRate - 0.05) * 120.0).coerceIn(0.0, 100.0)
        val fallbackSummary = "旧版MVP声学启发式兜底，峰值 $peak，过零率 %.3f".format(zeroCrossingRate)
        val fallbackDiagnostics = diagnostics.copy(
            isDemoEstimate = true,
            modelSummary = "旧版MVP声学启发式兜底（未标定）",
            inputSummary = listOf(diagnostics.inputSummary, fallbackSummary)
                .filter { it.isNotBlank() }
                .joinToString("，")
        )

        return Estimate(
            source = EstimateSource.ACOUSTIC,
            temperatureCelsius = temperature,
            humidityRh = humidity,
            confidence = confidence,
            inputQuality = confidence,
            sourceSummary = "声学兜底(${(confidence * 100).toInt()}%)，${fallbackDiagnostics.compactSummary()}",
            degradationReason = reason,
            diagnostics = fallbackDiagnostics
        )
    }
}
