package com.haohui.temperature_and_humidity.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.haohui.temperature_and_humidity.model.ReportDraft
import com.haohui.temperature_and_humidity.model.ReportRecord
import com.haohui.temperature_and_humidity.model.ReportSaveError
import com.haohui.temperature_and_humidity.model.ReportSaveResult
import com.haohui.temperature_and_humidity.model.ReportStatus
import java.util.UUID

class LocalReportStore(
    context: Context,
    private val cipher: ReportCipher = ReportCipher(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveDraft(draft: ReportDraft): ReportSaveResult {
        val now = clock()
        val record = ReportRecord(
            id = UUID.randomUUID().toString(),
            pointName = draft.pointName,
            temperatureCelsius = draft.measurement.temperatureCelsius,
            humidityRh = draft.measurement.humidityRh,
            pressureHpa = draft.measurement.pressureHpa,
            confidence = draft.measurement.confidence,
            qualitySummary = draft.measurement.quality.message.ifBlank { "质控通过" },
            sourceSummary = draft.measurement.sourceSummary,
            measuredAtMillis = draft.measurement.measuredAtMillis,
            createdAtMillis = now,
            updatedAtMillis = now,
            status = ReportStatus.SAVED
        )
        return saveRecord(record)
    }

    fun markCopied(id: String): ReportRecord? {
        val record = get(id) ?: return null
        val updated = record.copy(status = ReportStatus.COPIED, updatedAtMillis = clock())
        saveRecord(updated)
        return updated
    }

    fun list(): List<ReportRecord> {
        return ids().mapNotNull(::get).sortedByDescending { it.createdAtMillis }
    }

    fun get(id: String): ReportRecord? {
        val encrypted = prefs.getString(keyFor(id), null) ?: return null
        return runCatching { deserialize(cipher.decrypt(encrypted)) }.getOrNull()
    }

    private fun saveRecord(record: ReportRecord): ReportSaveResult {
        return try {
            val encrypted = cipher.encrypt(serialize(record))
            val currentIds = ids().toMutableList()
            if (!currentIds.contains(record.id)) {
                currentIds.add(record.id)
            }
            prefs.edit()
                .putString(keyFor(record.id), encrypted)
                .putString(KEY_IDS, currentIds.joinToString(","))
                .apply()
            ReportSaveResult.Success(record)
        } catch (_: Exception) {
            ReportSaveResult.Failure(ReportSaveError.STORAGE_FAILED)
        }
    }

    private fun ids(): List<String> {
        return prefs.getString(KEY_IDS, "")
            .orEmpty()
            .split(",")
            .filter { it.isNotBlank() }
    }

    private fun serialize(record: ReportRecord): String = listOf(
        record.id,
        encode(record.pointName),
        record.temperatureCelsius.toString(),
        record.humidityRh.toString(),
        record.pressureHpa?.toString().orEmpty(),
        record.confidence.toString(),
        encode(record.qualitySummary),
        encode(record.sourceSummary),
        record.measuredAtMillis.toString(),
        record.createdAtMillis.toString(),
        record.updatedAtMillis.toString(),
        record.status.name
    ).joinToString(DELIMITER)

    private fun deserialize(serialized: String): ReportRecord {
        val parts = serialized.split(DELIMITER)
        require(parts.size == 11 || parts.size == 12)
        val hasPressure = parts.size == 12
        return ReportRecord(
            id = parts[0],
            pointName = decode(parts[1]),
            temperatureCelsius = parts[2].toDouble(),
            humidityRh = parts[3].toDouble(),
            pressureHpa = if (hasPressure) parts[4].toDoubleOrNull() else null,
            confidence = parts[if (hasPressure) 5 else 4].toDouble(),
            qualitySummary = decode(parts[if (hasPressure) 6 else 5]),
            sourceSummary = decode(parts[if (hasPressure) 7 else 6]),
            measuredAtMillis = parts[if (hasPressure) 8 else 7].toLong(),
            createdAtMillis = parts[if (hasPressure) 9 else 8].toLong(),
            updatedAtMillis = parts[if (hasPressure) 10 else 9].toLong(),
            status = ReportStatus.valueOf(parts[if (hasPressure) 11 else 10])
        )
    }

    private fun encode(value: String): String = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun decode(value: String): String = String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)

    private fun keyFor(id: String) = "report_$id"

    private companion object {
        const val PREFS_NAME = "cdc_temp_humidity_reports"
        const val KEY_IDS = "ids"
        const val DELIMITER = "\u001F"
    }
}
