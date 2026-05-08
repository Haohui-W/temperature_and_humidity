package com.haohui.temperature_and_humidity.measurement

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs
import kotlin.math.sqrt

data class StereoPcmSample(
    val buffer: ShortArray,
    val readSamples: Int,
    val sampleRate: Int
) {
    val frameCount: Int
        get() = readSamples / CHANNEL_COUNT

    companion object {
        const val CHANNEL_COUNT = 2
    }
}

class AudioSampler(
    private val sampleRate: Int = 48_000
) {
    @SuppressLint("MissingPermission")
    fun sampleOneSecond(): StereoPcmSample? {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            return null
        }

        val sampleCount = sampleRate * StereoPcmSample.CHANNEL_COUNT
        val bufferSizeBytes = maxOf(minBuffer, sampleCount * BYTES_PER_SAMPLE)
        for (source in AUDIO_SOURCES) {
            val sample = sampleFromSource(source, sampleCount, bufferSizeBytes)
            if (sample != null) {
                return sample
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun sampleFromSource(
        audioSource: Int,
        sampleCount: Int,
        bufferSizeBytes: Int
    ): StereoPcmSample? {
        val buffer = ShortArray(sampleCount)
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(audioSource)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeBytes)
                .build()
        } catch (_: RuntimeException) {
            return null
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }

        return try {
            record.startRecording()
            var totalRead = 0
            while (totalRead < buffer.size) {
                val read = record.read(buffer, totalRead, buffer.size - totalRead)
                if (read <= 0) {
                    break
                }
                totalRead += read
            }
            if (totalRead > 0) StereoPcmSample(buffer, totalRead, sampleRate) else null
        } catch (_: RuntimeException) {
            null
        } finally {
            try {
                record.stop()
            } catch (_: RuntimeException) {
                // Some devices stop recording during failure paths.
            }
            record.release()
        }
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        val AUDIO_SOURCES = intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )
    }
}

object StereoChannelSplitter {
    fun split(sample: StereoPcmSample): Pair<ShortArray, ShortArray>? {
        val frames = sample.frameCount
        if (frames <= 0) {
            return null
        }
        val left = ShortArray(frames)
        val right = ShortArray(frames)
        var frame = 0
        var index = 0
        while (index + 1 < sample.readSamples && frame < frames) {
            left[frame] = sample.buffer[index]
            right[frame] = sample.buffer[index + 1]
            frame++
            index += StereoPcmSample.CHANNEL_COUNT
        }
        return left to right
    }
}

data class StereoSignalDiagnostics(
    val leftRms: Double,
    val rightRms: Double,
    val zeroLagSimilarity: Double,
    val leftPeak: Int,
    val rightPeak: Int
) {
    fun compactSummary(): String {
        val notes = buildList {
            if (maxOf(leftRms, rightRms) < LOW_RMS_THRESHOLD) {
                add("采样能量低")
            }
            if (zeroLagSimilarity > MONO_SIMILARITY_THRESHOLD) {
                add("左右声道近似相同")
            }
        }.joinToString("，")
        val base = "L/R rms %.0f/%.0f，相似 %.2f".format(leftRms, rightRms, zeroLagSimilarity)
        return if (notes.isBlank()) base else "$base，$notes"
    }

    companion object {
        private const val LOW_RMS_THRESHOLD = 30.0
        private const val MONO_SIMILARITY_THRESHOLD = 0.98
    }
}

object StereoSignalAnalyzer {
    fun analyze(left: ShortArray, right: ShortArray): StereoSignalDiagnostics {
        val usableLength = minOf(left.size, right.size)
        if (usableLength <= 0) {
            return StereoSignalDiagnostics(0.0, 0.0, 0.0, 0, 0)
        }

        var leftEnergy = 0.0
        var rightEnergy = 0.0
        var dot = 0.0
        var leftPeak = 0
        var rightPeak = 0
        for (index in 0 until usableLength) {
            val l = left[index].toDouble()
            val r = right[index].toDouble()
            leftEnergy += l * l
            rightEnergy += r * r
            dot += l * r
            leftPeak = maxOf(leftPeak, abs(left[index].toInt()))
            rightPeak = maxOf(rightPeak, abs(right[index].toInt()))
        }

        val similarity = if (leftEnergy <= 0.0 || rightEnergy <= 0.0) {
            0.0
        } else {
            (dot / sqrt(leftEnergy * rightEnergy)).coerceIn(-1.0, 1.0)
        }
        return StereoSignalDiagnostics(
            leftRms = sqrt(leftEnergy / usableLength),
            rightRms = sqrt(rightEnergy / usableLength),
            zeroLagSimilarity = similarity,
            leftPeak = leftPeak,
            rightPeak = rightPeak
        )
    }
}
