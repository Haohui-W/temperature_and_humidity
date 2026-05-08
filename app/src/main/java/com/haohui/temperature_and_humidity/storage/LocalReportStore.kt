package com.haohui.temperature_and_humidity.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import com.haohui.temperature_and_humidity.model.NetworkDemoLog
import com.haohui.temperature_and_humidity.model.NetworkDemoStatus
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
    private val appContext = context.applicationContext
    private val database = ReportDatabase(appContext)

    fun saveDraft(draft: ReportDraft): ReportSaveResult {
        val now = clock()
        val diagnostics = draft.measurement.diagnostics
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
            status = ReportStatus.SAVED,
            diagnosticsSummary = diagnostics.compactSummary(),
            isDemoEstimate = diagnostics.isDemoEstimate
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
        val db = database.readableDatabase
        return db.query(
            ReportDatabase.TABLE_REPORTS,
            null,
            null,
            null,
            null,
            null,
            "${ReportDatabase.COL_CREATED_AT} DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    readRecord(cursor.getString(cursor.getColumnIndexOrThrow(ReportDatabase.COL_PAYLOAD)))?.let(::add)
                }
            }
        }
    }

    fun get(id: String): ReportRecord? {
        val db = database.readableDatabase
        return db.query(
            ReportDatabase.TABLE_REPORTS,
            arrayOf(ReportDatabase.COL_PAYLOAD),
            "${ReportDatabase.COL_ID} = ?",
            arrayOf(id),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                readRecord(cursor.getString(0))
            } else {
                null
            }
        }
    }

    fun saveNetworkDemoLog(log: NetworkDemoLog) {
        val db = database.writableDatabase
        db.insertWithOnConflict(
            ReportDatabase.TABLE_NETWORK_LOGS,
            null,
            ContentValues().apply {
                put(ReportDatabase.COL_ID, log.id)
                put(ReportDatabase.COL_REPORT_ID, log.reportId)
                put(ReportDatabase.COL_ENDPOINT, log.endpoint)
                put(ReportDatabase.COL_REQUEST_SUMMARY, log.requestSummary)
                put(ReportDatabase.COL_HTTP_STATUS, log.httpStatusCode)
                put(ReportDatabase.COL_SUCCESS, if (log.success) 1 else 0)
                put(ReportDatabase.COL_ERROR_SUMMARY, log.errorSummary)
                put(ReportDatabase.COL_RESPONSE_SUMMARY, log.responseSummary)
                put(ReportDatabase.COL_CREATED_AT, log.createdAtMillis)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
        updateNetworkStatus(log.reportId, log.status, log.displaySummary())
    }

    fun latestNetworkDemoLog(reportId: String): NetworkDemoLog? {
        val db = database.readableDatabase
        return db.query(
            ReportDatabase.TABLE_NETWORK_LOGS,
            null,
            "${ReportDatabase.COL_REPORT_ID} = ?",
            arrayOf(reportId),
            null,
            null,
            "${ReportDatabase.COL_CREATED_AT} DESC",
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                NetworkDemoLog(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(ReportDatabase.COL_ID)),
                    reportId = cursor.getString(cursor.getColumnIndexOrThrow(ReportDatabase.COL_REPORT_ID)),
                    endpoint = cursor.getString(cursor.getColumnIndexOrThrow(ReportDatabase.COL_ENDPOINT)),
                    requestSummary = cursor.getString(cursor.getColumnIndexOrThrow(ReportDatabase.COL_REQUEST_SUMMARY)),
                    httpStatusCode = cursor.getIntOrNull(ReportDatabase.COL_HTTP_STATUS),
                    success = cursor.getInt(cursor.getColumnIndexOrThrow(ReportDatabase.COL_SUCCESS)) == 1,
                    errorSummary = cursor.getString(cursor.getColumnIndexOrThrow(ReportDatabase.COL_ERROR_SUMMARY)).orEmpty(),
                    responseSummary = cursor.getString(cursor.getColumnIndexOrThrow(ReportDatabase.COL_RESPONSE_SUMMARY)).orEmpty(),
                    createdAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow(ReportDatabase.COL_CREATED_AT))
                )
            }
        }
    }

    private fun saveRecord(record: ReportRecord): ReportSaveResult {
        return try {
            val encrypted = cipher.encrypt(serialize(record))
            val db = database.writableDatabase
            db.insertWithOnConflict(
                ReportDatabase.TABLE_REPORTS,
                null,
                ContentValues().apply {
                    put(ReportDatabase.COL_ID, record.id)
                    put(ReportDatabase.COL_PAYLOAD, encrypted)
                    put(ReportDatabase.COL_MEASURED_AT, record.measuredAtMillis)
                    put(ReportDatabase.COL_CREATED_AT, record.createdAtMillis)
                    put(ReportDatabase.COL_UPDATED_AT, record.updatedAtMillis)
                    put(ReportDatabase.COL_STATUS, record.status.name)
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
            ReportSaveResult.Success(record)
        } catch (_: Exception) {
            ReportSaveResult.Failure(ReportSaveError.STORAGE_FAILED)
        }
    }

    private fun updateNetworkStatus(reportId: String, status: NetworkDemoStatus, summary: String) {
        val record = get(reportId) ?: return
        saveRecord(record.copy(networkDemoStatus = status, networkDemoSummary = summary, updatedAtMillis = clock()))
    }

    private fun readRecord(encryptedPayload: String): ReportRecord? {
        return runCatching { deserialize(cipher.decrypt(encryptedPayload)) }.getOrNull()
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
        record.status.name,
        encode(record.diagnosticsSummary),
        record.isDemoEstimate.toString(),
        record.networkDemoStatus.name,
        encode(record.networkDemoSummary)
    ).joinToString(DELIMITER)

    private fun deserialize(serialized: String): ReportRecord {
        val parts = serialized.split(DELIMITER)
        require(parts.size == 11 || parts.size == 12 || parts.size >= 16)
        val hasPressure = parts.size == 12 || parts.size >= 16
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
            status = ReportStatus.valueOf(parts[if (hasPressure) 11 else 10]),
            diagnosticsSummary = parts.getOrNull(12)?.let(::decode).orEmpty(),
            isDemoEstimate = parts.getOrNull(13)?.toBooleanStrictOrNull() ?: false,
            networkDemoStatus = parts.getOrNull(14)?.let { runCatching { NetworkDemoStatus.valueOf(it) }.getOrNull() } ?: NetworkDemoStatus.NOT_RUN,
            networkDemoSummary = parts.getOrNull(15)?.let(::decode).orEmpty()
        )
    }

    private fun encode(value: String): String = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun decode(value: String): String = String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)

    private fun android.database.Cursor.getIntOrNull(column: String): Int? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getInt(index)
    }

    private companion object {
        const val DELIMITER = "\u001F"
    }
}

private class ReportDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_REPORTS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_PAYLOAD TEXT NOT NULL,
                $COL_MEASURED_AT INTEGER NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_STATUS TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_NETWORK_LOGS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_REPORT_ID TEXT NOT NULL,
                $COL_ENDPOINT TEXT NOT NULL,
                $COL_REQUEST_SUMMARY TEXT NOT NULL,
                $COL_HTTP_STATUS INTEGER,
                $COL_SUCCESS INTEGER NOT NULL,
                $COL_ERROR_SUMMARY TEXT NOT NULL,
                $COL_RESPONSE_SUMMARY TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        const val DB_NAME = "cdc_temp_humidity_reports.db"
        const val DB_VERSION = 1
        const val TABLE_REPORTS = "reports"
        const val TABLE_NETWORK_LOGS = "network_demo_logs"
        const val COL_ID = "id"
        const val COL_PAYLOAD = "payload"
        const val COL_MEASURED_AT = "measured_at"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_STATUS = "status"
        const val COL_REPORT_ID = "report_id"
        const val COL_ENDPOINT = "endpoint"
        const val COL_REQUEST_SUMMARY = "request_summary"
        const val COL_HTTP_STATUS = "http_status"
        const val COL_SUCCESS = "success"
        const val COL_ERROR_SUMMARY = "error_summary"
        const val COL_RESPONSE_SUMMARY = "response_summary"
    }
}
