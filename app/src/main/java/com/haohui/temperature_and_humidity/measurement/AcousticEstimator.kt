package com.haohui.temperature_and_humidity.measurement

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.haohui.temperature_and_humidity.model.Estimate
import com.haohui.temperature_and_humidity.model.EstimateSource
import com.haohui.temperature_and_humidity.model.MeasurementErrorReason
import kotlin.math.abs
import kotlin.math.ln

interface AcousticEstimator {
    fun estimate(): Estimate
}

class AndroidAcousticEstimator(
    private val context: Context,
    private val sampleRate: Int = 48_000
) : AcousticEstimator {

    @SuppressLint("MissingPermission")
    override fun estimate(): Estimate {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return unavailable(MeasurementErrorReason.MICROPHONE_PERMISSION_DENIED)
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            return unavailable(MeasurementErrorReason.AUDIO_UNAVAILABLE)
        }

        val oneSecondStereoSamples = sampleRate * CHANNEL_COUNT
        val buffer = ShortArray(oneSecondStereoSamples)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, oneSecondStereoSamples * BYTES_PER_SAMPLE)
            )
        } catch (_: RuntimeException) {
            return unavailable(MeasurementErrorReason.AUDIO_UNAVAILABLE)
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return unavailable(MeasurementErrorReason.AUDIO_UNAVAILABLE)
        }

        return try {
            record.startRecording()
            val read = record.read(buffer, 0, buffer.size)
            if (read <= 0) {
                unavailable(MeasurementErrorReason.AUDIO_UNAVAILABLE)
            } else {
                estimateFromBuffer(buffer, read)
            }
        } catch (_: RuntimeException) {
            unavailable(MeasurementErrorReason.AUDIO_UNAVAILABLE)
        } finally {
            try {
                record.stop()
            } catch (_: RuntimeException) {
                // Recording may already be stopped on some emulator/device failures.
            }
            record.release()
            AudioBufferCleaner.clear(buffer)
        }
    }

    private fun estimateFromBuffer(buffer: ShortArray, read: Int): Estimate {
        val usableFrames = read / CHANNEL_COUNT
        if (usableFrames <= 0) {
            return unavailable(MeasurementErrorReason.AUDIO_UNAVAILABLE)
        }

        var energy = 0.0
        var peak = 0
        var zeroCrossings = 0
        var previous = 0
        for (i in 0 until read step CHANNEL_COUNT) {
            val sample = buffer[i].toInt()
            energy += sample.toDouble() * sample.toDouble()
            peak = maxOf(peak, abs(sample))
            if (i > 0 && (sample >= 0) != (previous >= 0)) {
                zeroCrossings++
            }
            previous = sample
        }

        val rms = kotlin.math.sqrt(energy / usableFrames).coerceAtLeast(1.0)
        val normalizedRms = (ln(rms) / ln(Short.MAX_VALUE.toDouble())).coerceIn(0.0, 1.0)
        val zeroCrossingRate = zeroCrossings.toDouble() / usableFrames.toDouble()
        val confidence = ((normalizedRms * 0.7) + ((1.0 - zeroCrossingRate).coerceIn(0.0, 1.0) * 0.3))
            .coerceIn(0.1, 1.0)

        val temperature = 20.0 + (normalizedRms - 0.5) * 8.0
        val humidity = 55.0 + (zeroCrossingRate - 0.05) * 120.0

        return Estimate(
            source = EstimateSource.ACOUSTIC,
            temperatureCelsius = temperature.coerceIn(-10.0, 50.0),
            humidityRh = humidity.coerceIn(0.0, 100.0),
            confidence = confidence,
            inputQuality = confidence,
            sourceSummary = "声学采样 ${usableFrames} 帧，峰值 $peak"
        )
    }

    private fun unavailable(reason: MeasurementErrorReason): Estimate = Estimate(
        source = EstimateSource.ACOUSTIC,
        temperatureCelsius = 0.0,
        humidityRh = 0.0,
        confidence = 0.0,
        inputQuality = 0.0,
        sourceSummary = reason.userMessage,
        unavailableReason = reason
    )

    private companion object {
        const val CHANNEL_COUNT = 2
        const val BYTES_PER_SAMPLE = 2
    }
}
