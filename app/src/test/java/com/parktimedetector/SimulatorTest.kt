package com.parktimedetector

import com.parktimedetector.receiver.SimulationReceiver
import com.parktimedetector.service.SessionNotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SimulatorTest {

    @Test
    fun testSimulationBroadcastActionConstant() {
        assertEquals("com.parktimedetector.action.SIMULATE", SimulationReceiver.ACTION_SIMULATE)
    }

    @Test
    fun testSimulated1MinuteSessionParsing() {
        val now = 1700000000000L // arbitrary fixed timestamp
        val futureTime = now + 65_000L
        val timeStr = SimpleDateFormat("h:mm:ss a", Locale.US).format(Date(futureTime))
        val text = "Parking active in Zone 999. Expires at $timeStr"

        val parsed = SessionNotificationParser.parse(
            title = "ParkedIn Session Started",
            text = text,
            subText = null,
            postTimeMillis = now
        )

        assertNotNull("1-Minute simulated text should parse successfully", parsed)
        assertEquals("Zone 999", parsed?.zoneOrLot)
        assertTrue("End time must be after reference start time", (parsed?.endTimeMillis ?: 0) > now)
    }

    @Test
    fun testSimulatedParkedIn30MinParsing() {
        val now = 1700000000000L
        val futureTime = now + 30 * 60 * 1000L
        val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(futureTime))
        val text = "Zone 4022 active. Expires at $timeStr"

        val parsed = SessionNotificationParser.parse(
            title = "ParkedIn Session Started",
            text = text,
            subText = null,
            postTimeMillis = now
        )

        assertNotNull(parsed)
        assertEquals("Zone 4022", parsed?.zoneOrLot)
    }

    @Test
    fun testSimulatedMyParkingScreenSplitNodes() {
        val now = 1700000000000L
        val futureTime = now + 45 * 60 * 1000L
        val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(futureTime))
        val screenNodes = listOf(
            "Calgary Parking Authority",
            "Active Session",
            "Zone",
            "1205",
            "Valid Until",
            timeStr,
            "Vehicle: AB-1234"
        )

        val parsed = SessionNotificationParser.parseScreenText(screenNodes, now)
        assertNotNull("Split screen nodes should parse zone and end time", parsed)
        assertEquals("Zone 1205", parsed?.zoneOrLot)
    }

    @Test
    fun testSimulatedScreenCountdownTimer() {
        val now = 1700000000000L
        val screenNodes = listOf(
            "Precise ParkLink",
            "ParkedIn Session",
            "Zone 4022",
            "Time Remaining: 01:15:00"
        )

        val parsed = SessionNotificationParser.parseScreenText(screenNodes, now)
        assertNotNull(parsed)
        assertEquals("Zone 4022", parsed?.zoneOrLot)
        val expectedDurationMillis = (1 * 3600 + 15 * 60) * 1000L
        assertEquals(now + expectedDurationMillis, parsed?.endTimeMillis)
    }

    @Test
    fun testSimulatedMyParkingStopScreenParsing() {
        val now = 1700000000000L
        val screenNodes = listOf(
            "Calgary Parking Authority",
            "Session Ended",
            "Lot 58 - 935 - 4 Av SW",
            "Stopped at: 5:15 PM",
            "Total Time: 45 mins",
            "Total Cost: $2.50",
            "Receipt #9058",
            "DONE"
        )

        val parsed = SessionNotificationParser.parseStopScreenText(screenNodes, now)
        assertNotNull(parsed)
        assertEquals("Lot 58", parsed?.zoneOrLot)
        assertEquals("45m", parsed?.durationParkedText)
        assertTrue(parsed?.costOrRefundText?.contains("2.50") == true)
    }

    @Test
    fun testSimulatedParkedInStopScreenParsing() {
        val now = 1700000000000L
        val screenNodes = listOf(
            "Precise ParkLink",
            "Parking Stopped",
            "Zone 4022",
            "Ended: 4:30 PM",
            "Duration: 1 hr 15 mins",
            "Session Summary"
        )

        val parsed = SessionNotificationParser.parseStopScreenText(screenNodes, now)
        assertNotNull(parsed)
        assertEquals("Zone 4022", parsed?.zoneOrLot)
        assertEquals("1h 15m", parsed?.durationParkedText)
    }
}
