package com.parktimedetector.analytics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.parktimedetector.data.ParkingSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ParkingCsvExporter {

    data class ParkingAnalyticsSummary(
        val totalSessions: Int,
        val totalDurationMinutes: Long,
        val totalCost: Double,
        val thisMonthCost: Double,
        val thisMonthSessions: Int
    )

    fun calculateAnalytics(sessions: List<ParkingSession>): ParkingAnalyticsSummary {
        val now = System.currentTimeMillis()
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val currentMonthKey = monthFormat.format(Date(now))

        var totalDuration = 0L
        var totalCost = 0.0
        var thisMonthCost = 0.0
        var thisMonthSessions = 0

        for (session in sessions) {
            val durationMin = session.actualDurationMillis / (1000 * 60)
            totalDuration += durationMin

            val cost = parseCostToDouble(session.costOrRefundText ?: session.initialCostText)
            totalCost += cost

            val sessionMonthKey = monthFormat.format(Date(session.startTimeMillis))
            if (sessionMonthKey == currentMonthKey) {
                thisMonthCost += cost
                thisMonthSessions++
            }
        }

        return ParkingAnalyticsSummary(
            totalSessions = sessions.size,
            totalDurationMinutes = totalDuration,
            totalCost = totalCost,
            thisMonthCost = thisMonthCost,
            thisMonthSessions = thisMonthSessions
        )
    }

    fun parseCostToDouble(costStr: String?): Double {
        if (costStr.isNullOrBlank()) return 0.0
        // Clean "$", "CAD", "USD", commas
        val cleaned = costStr.replace(Regex("""[^0-9.]"""), "")
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    fun generateCsv(sessions: List<ParkingSession>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val sb = StringBuilder()
        // Header
        sb.append("Session ID,Date,Start Time,Scheduled Expiry,Actual Stop Time,Duration (Mins),Zone / Lot,Address,Spot / Level,Cost,Source,Stop Reason,Notes\n")

        for (session in sessions) {
            val dateStr = dateFormat.format(Date(session.startTimeMillis))
            val startStr = timeFormat.format(Date(session.startTimeMillis))
            val endStr = timeFormat.format(Date(session.endTimeMillis))
            val stopStr = session.actualStopTimeMillis?.let { timeFormat.format(Date(it)) } ?: ""
            val durationMinutes = (session.actualDurationMillis / (1000 * 60)).coerceAtLeast(0L)
            val cost = session.costOrRefundText ?: session.initialCostText ?: ""

            sb.append(escapeCsv(session.id.toString())).append(",")
            sb.append(escapeCsv(dateStr)).append(",")
            sb.append(escapeCsv(startStr)).append(",")
            sb.append(escapeCsv(endStr)).append(",")
            sb.append(escapeCsv(stopStr)).append(",")
            sb.append(durationMinutes).append(",")
            sb.append(escapeCsv(session.zoneOrLot ?: "")).append(",")
            sb.append(escapeCsv(session.locationAddress ?: "")).append(",")
            sb.append(escapeCsv(session.spotDetails ?: "")).append(",")
            sb.append(escapeCsv(cost)).append(",")
            sb.append(escapeCsv(session.source)).append(",")
            sb.append(escapeCsv(session.stopReason ?: "")).append(",")
            sb.append(escapeCsv(session.notesText ?: ""))
            sb.append("\n")
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    fun exportAndShareCsv(context: Context, sessions: List<ParkingSession>) {
        val csvContent = generateCsv(sessions)
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportDir, "parking_expenses_$dateStamp.csv")

        file.writeText(csvContent, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Parking Expenses Report - $dateStamp")
            putExtra(Intent.EXTRA_TEXT, "Attached is the parking expenses report for reimbursement.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Export Parking Expenses").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
