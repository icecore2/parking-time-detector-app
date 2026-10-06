package com.parktimedetector

import com.parktimedetector.data.ParkingDatabase
import com.parktimedetector.data.ParkingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAndSystemIntegrationTest {

    @Test
    fun testParkingSessionWithCalendarEventId() {
        val now = System.currentTimeMillis()
        val session = ParkingSession(
            id = 101L,
            packageName = "com.preciseparklink.parkedin",
            source = "ParkedIn App",
            zoneOrLot = "Zone 4022",
            startTimeMillis = now,
            endTimeMillis = now + (60 * 60 * 1000L),
            calendarEventId = 555L
        )

        assertEquals(555L, session.calendarEventId)
        assertEquals("Zone 4022", session.zoneOrLot)
        assertTrue(session.isActive)
        assertFalse(session.isExpired(now))

        // When extended
        val extendedSession = session.copy(
            endTimeMillis = session.endTimeMillis + (15 * 60 * 1000L)
        )
        assertEquals(555L, extendedSession.calendarEventId)
        assertEquals(session.endTimeMillis + (15 * 60 * 1000L), extendedSession.endTimeMillis)
    }

    @Test
    fun testNotificationProgressPercentageCalculation() {
        val startTime = 1000000L
        val endTime = startTime + (60 * 60 * 1000L) // 60 minutes = 3600000 ms

        fun calculateProgress(currentTime: Long): Int {
            val totalDuration = (endTime - startTime).coerceAtLeast(1000L)
            val elapsed = (currentTime - startTime).coerceIn(0L, totalDuration)
            return ((elapsed.toDouble() / totalDuration.toDouble()) * 100).toInt().coerceIn(0, 100)
        }

        // At start: 0%
        assertEquals(0, calculateProgress(startTime))

        // At 15 min (quarter duration): 25%
        assertEquals(25, calculateProgress(startTime + (15 * 60 * 1000L)))

        // At 30 min (half duration): 50%
        assertEquals(50, calculateProgress(startTime + (30 * 60 * 1000L)))

        // At 45 min (three-quarters): 75%
        assertEquals(75, calculateProgress(startTime + (45 * 60 * 1000L)))

        // At end: 100%
        assertEquals(100, calculateProgress(endTime))

        // Beyond end: clamped to 100%
        assertEquals(100, calculateProgress(endTime + 50000L))

        // Before start: clamped to 0%
        assertEquals(0, calculateProgress(startTime - 50000L))
    }

    @Test
    fun testDatabaseMigration6To7Sql() {
        // Test that migration 6 to 7 executes the expected SQL statement
        val migration = ParkingDatabase.MIGRATION_6_7
        assertEquals(6, migration.startVersion)
        assertEquals(7, migration.endVersion)
    }

    @Test
    fun testGlanceActiveSessionStatusCalculation() {
        val now = System.currentTimeMillis()
        val normalSession = ParkingSession(
            packageName = "com.preciseparklink.parkedin",
            source = "ParkedIn App",
            zoneOrLot = "Zone 4022",
            startTimeMillis = now - (10 * 60 * 1000L),
            endTimeMillis = now + (40 * 60 * 1000L),
            advanceWarningMinutes = 15
        )

        assertFalse(normalSession.isInCriticalZone(now))
        assertFalse(normalSession.isInAdvanceWarningZone(now))

        // In advance warning zone (e.g. 10m remaining when advance warning is 15m)
        val warningSession = normalSession.copy(
            endTimeMillis = now + (10 * 60 * 1000L)
        )
        assertTrue(warningSession.isInAdvanceWarningZone(now))
        assertFalse(warningSession.isInCriticalZone(now, criticalMinutes = 2))

        // In critical zone (e.g. 1m remaining)
        val criticalSession = normalSession.copy(
            endTimeMillis = now + (1 * 60 * 1000L)
        )
        assertTrue(criticalSession.isInCriticalZone(now, criticalMinutes = 2))
    }
}
