package com.haohui.temperature_and_humidity.measurement

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.haohui.temperature_and_humidity.model.Estimate
import com.haohui.temperature_and_humidity.model.EstimateSource
import com.haohui.temperature_and_humidity.model.MeasurementErrorReason
import java.io.File

interface ThermalEstimator {
    fun estimate(): Estimate
}

class AndroidThermalEstimator(
    private val context: Context,
    private val thermalModel: ThermalDemoModel = ThermalDemoModel()
) : ThermalEstimator {
    private var lastCpuLoad: Double? = null

    override fun estimate(): Estimate {
        val batteryTemperature = readBatteryTemperature()
        val cpuTemperature = readCpuTemperature()
        val cpuLoad = readCpuLoad()

        if (batteryTemperature == null && cpuTemperature == null) {
            return Estimate(
                source = EstimateSource.THERMAL,
                temperatureCelsius = 0.0,
                humidityRh = 0.0,
                confidence = 0.0,
                inputQuality = 0.0,
                sourceSummary = "热特征输入不可用",
                unavailableReason = MeasurementErrorReason.THERMAL_INPUT_UNAVAILABLE
            )
        }

        val output = thermalModel.predict(
            ThermalModelInput(
                batteryTemperatureCelsius = batteryTemperature,
                cpuTemperatureCelsius = cpuTemperature,
                cpuLoadPercent = cpuLoad,
                previousCpuLoadPercent = lastCpuLoad
            )
        ) ?: return Estimate(
            source = EstimateSource.THERMAL,
            temperatureCelsius = 0.0,
            humidityRh = 0.0,
            confidence = 0.0,
            inputQuality = 0.0,
            sourceSummary = "热特征输入不可用",
            unavailableReason = MeasurementErrorReason.THERMAL_INPUT_UNAVAILABLE
        )
        lastCpuLoad = cpuLoad
        val inputSummary = buildList {
            add("电池 ${batteryTemperature?.let { "%.1f℃".format(it) } ?: "缺失"}")
            add("CPU ${cpuTemperature?.let { "%.1f℃".format(it) } ?: "缺失"}")
            add(cpuLoadSummary(cpuLoad))
        }.joinToString("，")

        return Estimate(
            source = EstimateSource.THERMAL,
            temperatureCelsius = output.temperatureCelsius,
            humidityRh = output.humidityRh,
            confidence = output.confidence,
            inputQuality = output.inputQuality,
            sourceSummary = "$inputSummary，${output.metadata.displaySummary}",
            diagnostics = com.haohui.temperature_and_humidity.model.MeasurementDiagnostics(
                isDemoEstimate = !output.metadata.calibrated,
                modelSummary = output.metadata.displaySummary,
                inputSummary = inputSummary
            )
        )
    }

    private fun readBatteryTemperature(): Double? {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val tenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (tenths == Int.MIN_VALUE || tenths == 0) null else tenths / 10.0
    }

    private fun readCpuTemperature(): Double? {
        val zones = File("/sys/devices/virtual/thermal").listFiles { file ->
            file.name.startsWith("thermal_zone")
        } ?: return null

        return zones.asSequence()
            .mapNotNull { zone ->
                runCatching {
                    val raw = File(zone, "temp").readText().trim().toDouble()
                    if (raw > 1_000) raw / 1_000.0 else raw
                }.getOrNull()
            }
            .filter { it in -20.0..120.0 }
            .firstOrNull()
    }

    private fun readCpuLoad(): Double? {
        return runCatching {
            val parts = File("/proc/stat").readLines().first().split(Regex("\\s+")).drop(1).map { it.toDouble() }
            if (parts.size < 4) return null
            val idle = parts[3]
            val total = parts.sum()
            if (total <= 0.0) null else ((total - idle) / total * 100.0).coerceIn(0.0, 100.0)
        }.getOrNull()
    }

    private fun cpuLoadSummary(cpuLoad: Double?): String = cpuLoad?.let {
        "负载 %.0f%%".format(it)
    } ?: "负载缺失（系统限制或读取失败，模型使用默认20%）"
}
