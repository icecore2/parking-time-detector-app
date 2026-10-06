package com.parktimedetector

import com.parktimedetector.analytics.ParkingCsvExporter
import com.parktimedetector.data.ParkingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ParkingCsvExporterTest {

    @Test
    fun testParseCostToDouble() {
        assertEquals(4.50, ParkingCsvExporter.parseCostToDouble("$4.50"), 0.001)
        assertEquals(12.75, ParkingCsvExporter.parseCostToDouble("CAD 12.75"), 0.001)
        assertEquals(0.0, ParkingCsvExporter.parseCostToDouble(null), 0.001)
        assertEquals(0.0, ParkingCsvExporter.parseCostToDouble("FREE"), 0.001)
        assertEquals(1500.0, ParkingCsvExporter.parseCostToDouble("$1,500.00"), 0.001)
    }

    @Test
    fun testCalculateAnalytics() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            ParkingSession(
                id = 1L,
                packageName = "com.parkedin",
                source = "ParkedIn App",
                zoneOrLot = "Zone 4022",
                startTimeMillis = now - (60 * 60 * 1000L),
                endTimeMillis = now,
                actualStopTimeMillis = now,
                initialCostText = "$5.00"
            ),
            ParkingSession(
                id = 2L,
                packageName = "cpa.mobile",
                source = "MyParking App",
                zoneOrLot = "Lot 58",
                startTimeMillis = now - (120 * 60 * 1000L),
                endTimeMillis = now,
                actualStopTimeMillis = now,
                initialCostText = "$10.50"
            )
        )

        val analytics = ParkingCsvExporter.calculateAnalytics(sessions)
        assertEquals(2, analytics.totalSessions)
        assertEquals(15.50, analytics.totalCost, 0.01)
        assertEquals(180L, analytics.totalDurationMinutes)
        assertEquals(15.50, analytics.thisMonthCost, 0.01)
        assertEquals(2, analytics.thisMonthSessions)
    }

    @Test
    fun testGenerateCsvContentHeaderAndEscaping() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            ParkingSession(
                id = 101L,
                packageName = "com.parkedin",
                source = "ParkedIn App",
                zoneOrLot = "Zone 4022, North",
                locationAddress = "8th Ave, Calgary",
                startTimeMillis = now,
                endTimeMillis = now + (60 * 60 * 1000L),
                initialCostText = "$6.00",
                spotDetails = "Spot #4"
            )
        )

        val csv = ParkingCsvExporter.generateCsv(sessions)
        assertTrue(csv.contains("Session ID,Date,Start Time,Scheduled Expiry,Actual Stop Time,Duration (Mins),Zone / Lot,Address,Spot / Level,Cost,Source,Stop Reason,Notes"))
        assertTrue(csv.contains("\"Zone 4022, North\""))
        assertTrue(csv.contains("\"8th Ave, Calgary\""))
        assertTrue(csv.contains("Spot #4"))
    }
}
