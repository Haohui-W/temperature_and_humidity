package com.haohui.temperature_and_humidity.measurement

import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sqrt

data class TdoaResult(
    val sampleOffset: Int,
    val deltaSeconds: Double,
    val correlationPeak: Double,
    val inputQuality: Double,
    val isNative: Boolean
) {
    val isUsable: Boolean
        get() = deltaSeconds != 0.0 && inputQuality > 0.0

    fun summary(): String {
        val engine = if (isNative) "JNI" else "Kotlin"
        return "$engine TDOA ${sampleOffset} samples, %.3f ms, peak %.2f".format(deltaSeconds * 1000.0, correlationPeak)
    }
}

interface TdoaEstimator {
    fun estimate(left: ShortArray, right: ShortArray, sampleRate: Int, maxLag: Int = DEFAULT_MAX_LAG): TdoaResult?

    companion object {
        const val DEFAULT_MAX_LAG = 100
    }
}

class NativeTdoaEstimator : TdoaEstimator {
    private val nativeAvailable: Boolean = runCatching {
        System.loadLibrary("tdoa")
        true
    }.getOrDefault(false)

    override fun estimate(left: ShortArray, right: ShortArray, sampleRate: Int, maxLag: Int): TdoaResult? {
        if (!nativeAvailable || left.isEmpty() || right.isEmpty() || sampleRate <= 0) {
            return null
        }
        val values = runCatching { nativeEstimate(left, right, sampleRate, maxLag) }.getOrNull() ?: return null
        if (values.size < 4) {
            return null
        }
        return TdoaResult(
            sampleOffset = values[0].toInt(),
            deltaSeconds = values[1].toDouble(),
            correlationPeak = values[2].toDouble(),
            inputQuality = values[3].toDouble().coerceIn(0.0, 1.0),
            isNative = true
        ).takeIf { it.inputQuality > 0.0 }
    }

    private external fun nativeEstimate(left: ShortArray, right: ShortArray, sampleRate: Int, maxLag: Int): FloatArray
}

class KotlinReferenceTdoaEstimator : TdoaEstimator {
    override fun estimate(left: ShortArray, right: ShortArray, sampleRate: Int, maxLag: Int): TdoaResult? {
        val usableLength = minOf(left.size, right.size)
        if (usableLength <= 1 || sampleRate <= 0) {
            return null
        }
        val processedLeft = TdoaSignalPreprocessor.highPassNormalize(left, usableLength, sampleRate)
        val processedRight = TdoaSignalPreprocessor.highPassNormalize(right, usableLength, sampleRate)
        val boundedLag = minOf(maxLag, usableLength - 1)
        var bestLag = 0
        var bestCorrelation = Double.NEGATIVE_INFINITY

        for (lag in -boundedLag..boundedLag) {
            val startLeft = if (lag < 0) -lag else 0
            val startRight = if (lag > 0) lag else 0
            val validLength = usableLength - abs(lag)
            if (validLength < MIN_OVERLAP_SAMPLES) {
                continue
            }

            var meanLeft = 0.0
            var meanRight = 0.0
            for (i in 0 until validLength) {
                meanLeft += processedLeft[startLeft + i]
                meanRight += processedRight[startRight + i]
            }
            meanLeft /= validLength.toDouble()
            meanRight /= validLength.toDouble()

            var cross = 0.0
            var leftVariance = 0.0
            var rightVariance = 0.0
            for (i in 0 until validLength) {
                val l = processedLeft[startLeft + i] - meanLeft
                val r = processedRight[startRight + i] - meanRight
                cross += l * r
                leftVariance += l * l
                rightVariance += r * r
            }
            if (leftVariance <= 0.0 || rightVariance <= 0.0) {
                continue
            }
            val normalized = cross / sqrt(leftVariance * rightVariance)
            if (normalized > bestCorrelation) {
                bestLag = lag
                bestCorrelation = normalized
            }
        }

        if (!bestCorrelation.isFinite() || bestCorrelation < MIN_CORRELATION_PEAK) {
            return null
        }
        return TdoaResult(
            sampleOffset = bestLag,
            deltaSeconds = bestLag.toDouble() / sampleRate.toDouble(),
            correlationPeak = bestCorrelation,
            inputQuality = bestCorrelation.coerceIn(0.0, 1.0),
            isNative = false
        )
    }

    private companion object {
        const val MIN_OVERLAP_SAMPLES = 100
        const val MIN_CORRELATION_PEAK = 0.7
    }
}

private object TdoaSignalPreprocessor {
    fun highPassNormalize(input: ShortArray, usableLength: Int, sampleRate: Int): DoubleArray {
        val alpha = 1.0 / (1.0 + 2.0 * PI * HIGH_PASS_CUTOFF_HZ / sampleRate.toDouble())
        val output = DoubleArray(usableLength)
        var previousInput = 0.0
        var peak = 0.0
        for (index in 0 until usableLength) {
            val currentInput = input[index].toDouble()
            val filtered = alpha * (currentInput - previousInput)
            output[index] = filtered
            peak = maxOf(peak, abs(filtered))
            previousInput = currentInput
        }
        if (peak <= MIN_NORMALIZATION_PEAK) {
            return output
        }
        for (index in output.indices) {
            output[index] /= peak
        }
        return output
    }

    private const val HIGH_PASS_CUTOFF_HZ = 500.0
    private const val MIN_NORMALIZATION_PEAK = 1e-6
}
