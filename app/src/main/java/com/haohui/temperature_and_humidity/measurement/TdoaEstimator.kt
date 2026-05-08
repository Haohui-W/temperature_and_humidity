package com.haohui.temperature_and_humidity.measurement

import kotlin.math.abs
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
        val boundedLag = minOf(maxLag, usableLength - 1)
        var bestLag = 0
        var bestCorrelation = Double.NEGATIVE_INFINITY
        var bestQuality = 0.0

        for (lag in -boundedLag..boundedLag) {
            var corr = 0.0
            var leftEnergy = 0.0
            var rightEnergy = 0.0
            for (i in 0 until usableLength) {
                val rightIndex = i + lag
                if (rightIndex !in 0 until usableLength) {
                    continue
                }
                val l = left[i].toDouble()
                val r = right[rightIndex].toDouble()
                corr += l * r
                leftEnergy += l * l
                rightEnergy += r * r
            }
            if (leftEnergy <= 0.0 || rightEnergy <= 0.0) {
                continue
            }
            val normalized = corr / sqrt(leftEnergy * rightEnergy)
            val quality = abs(normalized).coerceIn(0.0, 1.0)
            if (quality > bestQuality || normalized > bestCorrelation) {
                bestLag = lag
                bestCorrelation = normalized
                bestQuality = quality
            }
        }

        if (!bestCorrelation.isFinite() || bestQuality <= 0.0) {
            return null
        }
        return TdoaResult(
            sampleOffset = bestLag,
            deltaSeconds = bestLag.toDouble() / sampleRate.toDouble(),
            correlationPeak = bestCorrelation,
            inputQuality = bestQuality,
            isNative = false
        )
    }
}
