package com.haohui.temperature_and_humidity.measurement

import android.os.Build

enum class MicDistanceSource(val label: String) {
    BUILT_IN_EXACT("内置机型表"),
    BUILT_IN_BRAND_FAMILY("内置机型族"),
    DEFAULT_UNKNOWN("未知机型默认值")
}

data class MicDistanceMatch(
    val distanceMeters: Double,
    val source: MicDistanceSource,
    val manufacturer: String,
    val model: String
) {
    fun summary(): String = "${source.label} %.1fcm ($manufacturer $model)".format(distanceMeters * 100.0)
}

class DeviceMicCatalog(
    private val entries: List<Entry> = DEFAULT_ENTRIES
) {
    fun currentDevice(): MicDistanceMatch = match(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty())

    fun match(manufacturer: String, model: String): MicDistanceMatch {
        val normalizedManufacturer = manufacturer.lowercase()
        val normalizedModel = model.lowercase()
        val modelOnlyEntry = entries.firstOrNull { candidate ->
            normalizedModel.contains(candidate.manufacturer.lowercase()) &&
                candidate.models.any { normalizedModel.contains(it.lowercase()) }
        }
        val entry = modelOnlyEntry ?: entries.firstOrNull { candidate ->
            normalizedManufacturer.contains(candidate.manufacturer.lowercase()) &&
                candidate.models.any { normalizedModel.contains(it.lowercase()) }
        }
        return if (entry != null) {
            MicDistanceMatch(
                distanceMeters = entry.distanceCentimeters / 100.0,
                source = MicDistanceSource.BUILT_IN_BRAND_FAMILY,
                manufacturer = manufacturer.ifBlank { entry.manufacturer },
                model = model.ifBlank { entry.models.first() }
            )
        } else {
            MicDistanceMatch(
                distanceMeters = DEFAULT_DISTANCE_CENTIMETERS / 100.0,
                source = MicDistanceSource.DEFAULT_UNKNOWN,
                manufacturer = manufacturer.ifBlank { "unknown" },
                model = model.ifBlank { "unknown" }
            )
        }
    }

    data class Entry(
        val manufacturer: String,
        val models: List<String>,
        val distanceCentimeters: Double
    )

    companion object {
        const val DEFAULT_DISTANCE_CENTIMETERS = 5.0
        val DEFAULT_ENTRIES = listOf(
            Entry("huawei", listOf("p50", "p40", "mate40"), 5.0),
            Entry("redmi", listOf("note12", "note 12", "note11", "note 11"), 4.7),
            Entry("xiaomi", listOf("13", "12", "11"), 4.8),
            Entry("oppo", listOf("find x5", "reno8"), 4.9),
            Entry("vivo", listOf("x80", "x70", "s15"), 5.1),
            Entry("honor", listOf("magic4", "60", "50"), 4.9)
        )
    }
}
