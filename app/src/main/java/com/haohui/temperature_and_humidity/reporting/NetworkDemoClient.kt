package com.haohui.temperature_and_humidity.reporting

import com.haohui.temperature_and_humidity.model.NetworkDemoLog
import com.haohui.temperature_and_humidity.model.ReportRecord
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class NetworkDemoPayload(
    val reportId: String,
    val temperatureCelsius: Double,
    val humidityRh: Double,
    val pressureHpa: Double?,
    val confidence: Double,
    val demoEstimate: Boolean,
    val qualitySummary: String,
    val sourceSummary: String,
    val diagnosticsSummary: String
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"reportId\":\"").append(reportId.escapeJson()).append("\",")
            append("\"temperatureCelsius\":").append(temperatureCelsius).append(",")
            append("\"humidityRh\":").append(humidityRh).append(",")
            append("\"pressureHpa\":").append(pressureHpa?.toString() ?: "null").append(",")
            append("\"confidence\":").append(confidence).append(",")
            append("\"demoEstimate\":").append(demoEstimate).append(",")
            append("\"qualitySummary\":\"").append(qualitySummary.escapeJson()).append("\",")
            append("\"sourceSummary\":\"").append(sourceSummary.escapeJson()).append("\",")
            append("\"diagnosticsSummary\":\"").append(diagnosticsSummary.escapeJson()).append("\"")
            append("}")
        }
    }

    private fun String.escapeJson(): String = replace("\\", "\\\\").replace("\"", "\\\"")
}

class NetworkDemoClient(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun postReport(record: ReportRecord): NetworkDemoLog {
        val payload = buildPayload(record)
        val requestSummary = "report=${record.id}, temp=${record.displayTemperature()}, humidity=${record.displayHumidity()}, pressure=${record.displayPressure()}"
        return try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            connection.outputStream.use { output ->
                output.write(payload.toJson().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            NetworkDemoLog(
                id = UUID.randomUUID().toString(),
                reportId = record.id,
                endpoint = endpoint,
                requestSummary = requestSummary,
                httpStatusCode = status,
                success = status in 200..299,
                errorSummary = if (status in 200..299) "" else "HTTP $status",
                responseSummary = response.take(MAX_RESPONSE_SUMMARY),
                createdAtMillis = clock()
            )
        } catch (error: Exception) {
            NetworkDemoLog(
                id = UUID.randomUUID().toString(),
                reportId = record.id,
                endpoint = endpoint,
                requestSummary = requestSummary,
                httpStatusCode = null,
                success = false,
                errorSummary = error.message ?: error.javaClass.simpleName,
                responseSummary = "",
                createdAtMillis = clock()
            )
        }
    }

    fun buildPayload(record: ReportRecord): NetworkDemoPayload {
        return NetworkDemoPayload(
            reportId = record.id,
            temperatureCelsius = record.temperatureCelsius,
            humidityRh = record.humidityRh,
            pressureHpa = record.pressureHpa,
            confidence = record.confidence,
            demoEstimate = record.isDemoEstimate,
            qualitySummary = record.qualitySummary,
            sourceSummary = record.sourceSummary,
            diagnosticsSummary = record.diagnosticsSummary
        )
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://httpbin.org/post"
        private const val TIMEOUT_MILLIS = 5_000
        private const val MAX_RESPONSE_SUMMARY = 500
    }
}
