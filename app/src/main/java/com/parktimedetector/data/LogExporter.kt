package com.parktimedetector.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.parktimedetector.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object LogExporter {

    data class DiagnosticStatus(
        val hasNotifPermission: Boolean = false,
        val isNotifConnected: Boolean = false,
        val hasAccessibilityPermission: Boolean = false,
        val isAccessibilityConnected: Boolean = false,
        val logAllNotifications: Boolean = false
    )

    fun formatLogsAsText(
        logs: List<LogEntry>,
        status: DiagnosticStatus = DiagnosticStatus(),
        exportTimeMillis: Long = System.currentTimeMillis()
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val exportDateStr = dateFormat.format(Date(exportTimeMillis))

        return buildString {
            appendLine("================================================================================")
            appendLine("                      PARKING TIME DETECTOR - SYSTEM LOGS                      ")
            appendLine("================================================================================")
            appendLine("Export Timestamp            : $exportDateStr ($exportTimeMillis)")
            appendLine("App Version                 : ${BuildConfig.VERSION_NAME} (Code: ${BuildConfig.VERSION_CODE})")
            appendLine("Package                     : ${BuildConfig.APPLICATION_ID}")
            appendLine("Device Model                : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android OS                  : Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Notification Listener Perm  : ${if (status.hasNotifPermission) "GRANTED" else "DENIED"}")
            appendLine("Notification Service Active : ${if (status.isNotifConnected) "CONNECTED" else "DISCONNECTED"}")
            appendLine("Accessibility Perm          : ${if (status.hasAccessibilityPermission) "GRANTED" else "DENIED"}")
            appendLine("Accessibility Service Active: ${if (status.isAccessibilityConnected) "CONNECTED" else "DISCONNECTED"}")
            appendLine("Log All Notifications       : ${if (status.logAllNotifications) "ENABLED" else "DISABLED"}")
            appendLine("Total Log Entries           : ${logs.size}")
            appendLine("================================================================================")
            appendLine()

            if (logs.isEmpty()) {
                appendLine("No logs recorded.")
            } else {
                logs.forEachIndexed { index, log ->
                    val timeStr = dateFormat.format(Date(log.timestampMillis))
                    appendLine("[#${index + 1}] [$timeStr] [${log.level.padEnd(7)}] [${log.tag}]")
                    if (!log.packageName.isNullOrBlank()) {
                        appendLine("Package: ${log.packageName}")
                    }
                    appendLine("Message: ${log.message}")
                    if (!log.rawData.isNullOrBlank()) {
                        appendLine("Raw Data:")
                        log.rawData.lineSequence().forEach { line ->
                            appendLine("    $line")
                        }
                    }
                    appendLine("--------------------------------------------------------------------------------")
                }
            }
        }
    }

    private fun escapeJson(str: String): String {
        val out = StringBuilder()
        for (c in str) {
            when (c) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        out.append(String.format("\\u%04x", c.code))
                    } else {
                        out.append(c)
                    }
                }
            }
        }
        return out.toString()
    }

    fun formatLogsAsJson(
        logs: List<LogEntry>,
        status: DiagnosticStatus = DiagnosticStatus(),
        exportTimeMillis: Long = System.currentTimeMillis()
    ): String {
        val exportDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(exportTimeMillis))
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        return buildString {
            appendLine("{")
            appendLine("  \"metadata\": {")
            appendLine("    \"exportTimeMillis\": $exportTimeMillis,")
            appendLine("    \"exportTimestamp\": \"${escapeJson(exportDateStr)}\",")
            appendLine("    \"appVersion\": \"${escapeJson(BuildConfig.VERSION_NAME)}\",")
            appendLine("    \"versionCode\": ${BuildConfig.VERSION_CODE},")
            appendLine("    \"applicationId\": \"${escapeJson(BuildConfig.APPLICATION_ID)}\",")
            appendLine("    \"deviceManufacturer\": \"${escapeJson(Build.MANUFACTURER ?: "")}\",")
            appendLine("    \"deviceModel\": \"${escapeJson(Build.MODEL ?: "")}\",")
            appendLine("    \"androidRelease\": \"${escapeJson(Build.VERSION.RELEASE ?: "")}\",")
            appendLine("    \"androidSdk\": ${Build.VERSION.SDK_INT},")
            appendLine("    \"notificationPermissionGranted\": ${status.hasNotifPermission},")
            appendLine("    \"notificationServiceConnected\": ${status.isNotifConnected},")
            appendLine("    \"accessibilityPermissionGranted\": ${status.hasAccessibilityPermission},")
            appendLine("    \"accessibilityServiceConnected\": ${status.isAccessibilityConnected},")
            appendLine("    \"logAllNotifications\": ${status.logAllNotifications},")
            appendLine("    \"totalLogs\": ${logs.size}")
            appendLine("  },")
            appendLine("  \"logs\": [")
            logs.forEachIndexed { index, log ->
                val timeStr = dateFormat.format(Date(log.timestampMillis))
                val comma = if (index < logs.size - 1) "," else ""
                appendLine("    {")
                appendLine("      \"id\": ${log.id},")
                appendLine("      \"timestampMillis\": ${log.timestampMillis},")
                appendLine("      \"timestamp\": \"${escapeJson(timeStr)}\",")
                appendLine("      \"level\": \"${escapeJson(log.level)}\",")
                appendLine("      \"tag\": \"${escapeJson(log.tag)}\",")
                val pkgStr = if (log.packageName != null) "\"${escapeJson(log.packageName)}\"" else "null"
                appendLine("      \"packageName\": $pkgStr,")
                appendLine("      \"message\": \"${escapeJson(log.message)}\",")
                val rawStr = if (log.rawData != null) "\"${escapeJson(log.rawData)}\"" else "null"
                appendLine("      \"rawData\": $rawStr")
                appendLine("    }$comma")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }

    suspend fun createLogsZip(
        context: Context,
        logs: List<LogEntry>,
        status: DiagnosticStatus = DiagnosticStatus()
    ): File = withContext(Dispatchers.IO) {
        val logsDir = File(context.cacheDir, "logs").apply { mkdirs() }

        // Clean up previous export zips to avoid unbounded cache growth
        logsDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("parking_logs_") && file.name.endsWith(".zip")) {
                file.delete()
            }
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val zipFile = File(logsDir, "parking_logs_$timestamp.zip")

        val textContent = formatLogsAsText(logs, status)
        val jsonContent = formatLogsAsJson(logs, status)

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
            // Entry 1: text log
            val textEntry = ZipEntry("detection_logs.txt")
            zipOut.putNextEntry(textEntry)
            zipOut.write(textContent.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // Entry 2: json log
            val jsonEntry = ZipEntry("detection_logs.json")
            zipOut.putNextEntry(jsonEntry)
            zipOut.write(jsonContent.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }

        zipFile
    }

    suspend fun shareLogs(
        context: Context,
        logs: List<LogEntry>,
        status: DiagnosticStatus = DiagnosticStatus()
    ) {
        try {
            val zipFile = createLogsZip(context, logs, status)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Parking Time Detector Logs (${zipFile.name})")
                putExtra(Intent.EXTRA_TEXT, "Attached diagnostic logs from Parking Time Detector (${logs.size} log entries).")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Logs Archive").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooser)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to export logs: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
