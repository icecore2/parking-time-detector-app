package com.parktimedetector

import com.parktimedetector.service.SessionNotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SessionNotificationParserTest {

    @Test
    fun testParseExplicit12HourWithZone() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 25, 14, 0, 0)
        }.timeInMillis

        val result = SessionNotificationParser.parse(
            title = "ParkedIn Session Active",
            text = "Parking in Zone 4022. Expires at 4:30 PM.",
            postTimeMillis = baseTime
        )

        assertNotNull(result)
        assertEquals("Zone 4022", result?.zoneOrLot)

        val cal = Calendar.getInstance().apply { timeInMillis = result!!.endTimeMillis }
        assertEquals(16, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testParse24HourWithLot() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 25, 10, 0, 0)
        }.timeInMillis

        val result = SessionNotificationParser.parse(
            title = "Parking Notification",
            text = "Active session at Lot B until 15:45",
            postTimeMillis = baseTime
        )

        assertNotNull(result)
        assertEquals("Lot B", result?.zoneOrLot)

        val cal = Calendar.getInstance().apply { timeInMillis = result!!.endTimeMillis }
        assertEquals(15, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testParseRelativeDuration() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 25, 12, 0, 0)
        }.timeInMillis

        val result = SessionNotificationParser.parse(
            title = "ParkedIn",
            text = "Duration: 2 hours and 15 mins in Stall 7",
            postTimeMillis = baseTime
        )

        assertNotNull(result)
        assertEquals("Stall 7", result?.zoneOrLot)

        val expectedMillis = baseTime + (((2 * 60) + 15) * 60 * 1000L)
        assertEquals(expectedMillis, result?.endTimeMillis)
    }

    @Test
    fun testParseMinutesOnly() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 25, 12, 0, 0)
        }.timeInMillis

        val result = SessionNotificationParser.parse(
            title = "ParkedIn Alert",
            text = "Session expires in 45 mins",
            postTimeMillis = baseTime
        )

        assertNotNull(result)
        val expectedMillis = baseTime + (45 * 60 * 1000L)
        assertEquals(expectedMillis, result?.endTimeMillis)
    }

    @Test
    fun testMidnightRollover() {
        // 11:55 PM
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 25, 23, 55, 0)
        }.timeInMillis

        // Expires at 12:30 AM (next day)
        val result = SessionNotificationParser.parse(
            title = "ParkedIn",
            text = "Parking valid until 12:30 AM",
            postTimeMillis = baseTime
        )

        assertNotNull(result)
        assertTrue(result!!.endTimeMillis > baseTime)

        val cal = Calendar.getInstance().apply { timeInMillis = result.endTimeMillis }
        assertEquals(26, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testParseMyParkingNotification() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 25, 13, 0, 0)
        }.timeInMillis

        val result = SessionNotificationParser.parse(
            title = "MyParking - Calgary Parking Authority",
            text = "Your parking session in Zone 1205 is active until 2:30 PM",
            postTimeMillis = baseTime
        )

        assertNotNull(result)
        assertEquals("Zone 1205", result?.zoneOrLot)

        val cal = Calendar.getInstance().apply { timeInMillis = result!!.endTimeMillis }
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testNonParkingNotificationReturnsNull() {
        val result = SessionNotificationParser.parse(
            title = "Welcome to ParkedIn",
            text = "Please verify your email address to proceed."
        )

        assertNull(result)
    }

    @Test
    fun testParseScreenTextWithSplitNodes() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 25, 14, 0, 0)
        }.timeInMillis

        val nodes = listOf("Zone", "1205", "Valid Until", "4:30 PM", "Plate: ABC-123")
        val result = SessionNotificationParser.parseScreenText(nodes, baseTime)

        assertNotNull(result)
        assertEquals("Zone 1205", result?.zoneOrLot)
        val cal = Calendar.getInstance().apply { timeInMillis = result!!.endTimeMillis }
        assertEquals(16, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testParseScreenTextWithCountdownFormat() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 25, 12, 0, 0)
        }.timeInMillis

        // 1 hour, 25 mins, 30 seconds
        val nodes = listOf("Calgary Parking Authority", "Zone 1205", "Time Remaining", "01:25:30")
        val result = SessionNotificationParser.parseScreenText(nodes, baseTime)

        assertNotNull(result)
        assertEquals("Zone 1205", result?.zoneOrLot)
        val expectedMillis = baseTime + (((1 * 3600) + (25 * 60) + 30) * 1000L)
        assertEquals(expectedMillis, result?.endTimeMillis)
    }

    @Test
    fun testParseScreenTextParkedInTwoPartCountdown() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 25, 10, 0, 0)
        }.timeInMillis

        // 45 minutes countdown
        val nodes = listOf("ParkedIn", "Active Session", "Zone 4022", "Remaining: 45:00")
        val result = SessionNotificationParser.parseScreenText(nodes, baseTime)

        assertNotNull(result)
        assertEquals("Zone 4022", result?.zoneOrLot)
        val expectedMillis = baseTime + (45 * 60 * 1000L)
        assertEquals(expectedMillis, result?.endTimeMillis)
    }

    @Test
    fun testParseScreenTextSuffixCountdown() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 25, 10, 0, 0)
        }.timeInMillis

        // Suffix "02:10:00 remaining"
        val nodes = listOf("Zone 881", "02:10:00 remaining")
        val result = SessionNotificationParser.parseScreenText(nodes, baseTime)

        assertNotNull(result)
        assertEquals("Zone 881", result?.zoneOrLot)
        val expectedMillis = baseTime + (((2 * 3600) + (10 * 60)) * 1000L)
        assertEquals(expectedMillis, result?.endTimeMillis)
    }

    @Test
    fun testParseScreenTextUnrelatedScreenReturnsNull() {
        val nodes = listOf("Welcome to MyParking", "Sign In", "Username", "Password", "Forgot Password?")
        val result = SessionNotificationParser.parseScreenText(nodes)

        assertNull(result)
    }

    @Test
    fun testIsStartOrActionClickMatching() {
        // Positive action matches
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("Start Parking"))
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("START"))
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("Pay Now"))
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("Confirm & Pay"))
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("Purchase Parking"))
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("Park Now"))
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("Renew Session"))
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("Extend Parking"))

        // Negative non-action matches
        org.junit.Assert.assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("Settings"))
        org.junit.Assert.assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("Terms of Service"))
        org.junit.Assert.assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("Help"))
    }

    @Test
    fun testPendingParkingDetectionDurationCalculation() {
        val start = 1000000L
        val end = start + (45 * 60 * 1000L) // +45 minutes

        val pending = com.parktimedetector.data.PendingParkingDetection(
            packageName = "com.cpa.accountManagement",
            appName = "MyParking",
            zoneOrLot = "Zone 1205",
            endTimeMillis = end,
            source = "MyParking (Screen Detected)",
            reason = "Test",
            detectedAtMillis = start
        )

        assertEquals(45L, pending.durationMinutes)
    }

    @Test
    fun testParseMyParkingReceiptScreenWithDateAndPeriods() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 26, 16, 10, 0)
        }.timeInMillis

        val nodes = listOf(
            "9058",
            "Lot 58 - 935 - 4 Av SW",
            "Duration",
            "13 hrs. 50 mins. ($3.00)",
            "End Time:",
            "Sunday, September 27 - 06:00 a.m.",
            "TAKE PHOTO",
            "(to remember where you parked)",
            "START",
            "Navigate up",
            "START PARKING SESSION"
        )

        val result = SessionNotificationParser.parseScreenText(nodes, baseTime)

        assertNotNull(result)
        assertEquals("Lot 58", result?.zoneOrLot)

        val cal = Calendar.getInstance().apply { timeInMillis = result!!.endTimeMillis }
        assertEquals(27, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, cal.get(Calendar.MONTH))
        assertEquals(6, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testDurationWithPeriodsFallback() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 26, 16, 10, 0)
        }.timeInMillis

        val nodes = listOf(
            "Lot 58",
            "Duration: 13 hrs. 50 mins."
        )

        val result = SessionNotificationParser.parseScreenText(nodes, baseTime)
        assertNotNull(result)
        val expected = baseTime + (((13 * 60) + 50) * 60 * 1000L)
        assertEquals(expected, result?.endTimeMillis)
    }

    @Test
    fun testParseMyParkingStopScreenWithCostAndDuration() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 26, 17, 0, 0)
        }.timeInMillis

        val nodes = listOf(
            "Calgary Parking Authority",
            "Session Ended",
            "Lot 58 - 935 - 4 Av SW",
            "Stopped at: 5:15 PM",
            "Total Time: 45 mins",
            "Total Cost: $2.50",
            "Receipt #9058",
            "TAKE PHOTO",
            "DONE"
        )

        val result = SessionNotificationParser.parseStopScreenText(nodes, baseTime)
        assertNotNull(result)
        assertEquals("Lot 58", result?.zoneOrLot)
        assertTrue(result?.costOrRefundText?.contains("2.50") == true)

        val cal = Calendar.getInstance().apply { timeInMillis = result!!.stopTimeMillis }
        assertEquals(17, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testParseParkedInStopScreen() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 26, 16, 0, 0)
        }.timeInMillis

        val nodes = listOf(
            "Precise ParkLink",
            "Parking Stopped",
            "Zone 4022",
            "Ended: 4:30 PM",
            "Duration: 1 hr 15 mins",
            "Total Charged: $4.00"
        )

        val result = SessionNotificationParser.parseStopScreenText(nodes, baseTime)
        assertNotNull(result)
        assertEquals("Zone 4022", result?.zoneOrLot)
        assertEquals("1h 15m", result?.durationParkedText)
        assertTrue(result?.costOrRefundText?.contains("4.00") == true)

        val cal = Calendar.getInstance().apply { timeInMillis = result!!.stopTimeMillis }
        assertEquals(16, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testParseStopNotification() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 26, 16, 0, 0)
        }.timeInMillis

        val result = SessionNotificationParser.parseStopNotification(
            title = "ParkedIn Session Ended",
            text = "Your parking session in Zone 4022 has ended at 4:30 PM. Duration: 45 mins.",
            postTimeMillis = baseTime
        )

        assertNotNull(result)
        assertEquals("Zone 4022", result?.zoneOrLot)
        val cal = Calendar.getInstance().apply { timeInMillis = result!!.stopTimeMillis }
        assertEquals(16, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testIsStopActionClickMatching() {
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("Stop Parking"))
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("STOP"))
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("End Session"))
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("Leave Parking"))
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("Finish Parking"))
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("Cancel Session"))
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("Stop Session"))

        // Negatives
        org.junit.Assert.assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("Start Parking"))
        org.junit.Assert.assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("Pay Now"))
        org.junit.Assert.assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("Extend Parking"))
        org.junit.Assert.assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("Help"))
    }

    @Test
    fun testNonStopScreenReturnsNull() {
        val nodes = listOf("Active Session", "Time Remaining: 01:25:00", "Zone 1205")
        val result = SessionNotificationParser.parseStopScreenText(nodes)
        assertNull(result)
    }

    @Test
    fun testActiveSessionWithStopButtonDoesNotTriggerStopScreen() {
        // Reproduces the exact user bug: MyParking active session dashboard has a "Stop Session" button
        val activeDashboardNodes = listOf(
            "MyParking",
            "Active Session",
            "Zone 1205",
            "Valid Until 4:30 PM",
            "Vehicle: ABC-123",
            "Stop Session"
        )

        // isStopScreen must return FALSE because it's an active dashboard with countdown/valid until
        org.junit.Assert.assertFalse(SessionNotificationParser.isStopScreen(activeDashboardNodes))
        assertNull(SessionNotificationParser.parseStopScreenText(activeDashboardNodes))

        // But containsStopActionButton must return TRUE so we can log [FLOW: SUPPRESSED_STOP]
        assertTrue(SessionNotificationParser.containsStopActionButton(activeDashboardNodes))
    }

    @Test
    fun testActualStopReceiptTriggersStopScreen() {
        val stopReceiptNodes = listOf(
            "MyParking",
            "Session Ended",
            "Zone 1205",
            "Stopped At: 3:15 PM",
            "Duration: 45 mins",
            "Total Charged: $2.50"
        )

        assertTrue(SessionNotificationParser.isStopScreen(stopReceiptNodes))
        val stopResult = SessionNotificationParser.parseStopScreenText(stopReceiptNodes)
        assertNotNull(stopResult)
        assertEquals("Zone 1205", stopResult?.zoneOrLot)
        assertEquals("45m", stopResult?.durationParkedText)
        assertEquals("$2.50", stopResult?.costOrRefundText)
    }

    @Test
    fun testPendingParkingStopDetectionDataModel() {
        val now = 1000000L
        val detection = com.parktimedetector.data.PendingParkingStopDetection(
            packageName = "com.cpa.accountManagement",
            appName = "MyParking",
            zoneOrLot = "Lot 58",
            stopTimeMillis = now,
            durationParkedText = "45m",
            costOrRefundText = "$2.50",
            source = "MyParking App (Screen Stop Detected)",
            reason = "Stop screen detected"
        )

        assertEquals("com.cpa.accountManagement", detection.packageName)
        assertEquals("MyParking", detection.appName)
        assertEquals("Lot 58", detection.zoneOrLot)
        assertEquals(now, detection.stopTimeMillis)
        assertEquals("45m", detection.durationParkedText)
        assertEquals("$2.50", detection.costOrRefundText)
    }

    @Test
    fun testMyParkingStartEndActiveSessionScreenParsing() {
        val nodes = listOf(
            "START/END SESSION",
            "END PARKING SESSION",
            "Remaining: 19 mins : 57 secs",
            "End Time: Monday, September 28 - 01:16 p.m.",
            "50 Av SW , Fr 6 St SW To ELBOW Dr SW",
            "Zone # 5586 20 mins. ($.00)",
            "Note: Please remember to end your parking session so that unused time can be credited to your account. This doesn't apply to flat-rate parking zones.",
            "LOCAL DEALS",
            "FIND MY CAR"
        )

        // 1. isStartEndActiveSessionScreen must be true
        assertTrue(SessionNotificationParser.isStartEndActiveSessionScreen(nodes))

        // 2. isStopScreen must be false (active countdown present)
        org.junit.Assert.assertFalse(SessionNotificationParser.isStopScreen(nodes))

        // 3. parseScreenText must extract all metadata correctly
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 28, 12, 56, 0)
        }.timeInMillis

        val result = SessionNotificationParser.parseScreenText(nodes, baseTime)
        assertNotNull(result)
        assertEquals("Zone 5586", result?.zoneOrLot)
        assertEquals("50 Av SW , Fr 6 St SW To ELBOW Dr SW", result?.locationAddress)
        assertEquals("20 mins.", result?.purchasedDurationText)
        assertEquals("$0.00", result?.costText)
        assertEquals("19 mins : 57 secs", result?.remainingTimeText)
        assertTrue(result?.notesText?.contains("Please remember to end your parking session") == true)

        val cal = Calendar.getInstance().apply { timeInMillis = result!!.endTimeMillis }
        assertEquals(13, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(16, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testMyParkingDeactivationStopScreenParsing() {
        val nodes = listOf(
            "START/END SESSION",
            "END PARKING SESSION",
            "Parking session ended successfully",
            "Zone # 5586",
            "50 Av SW , Fr 6 St SW To ELBOW Dr SW",
            "Total Charged: $0.00",
            "DONE"
        )

        val stopResult = SessionNotificationParser.parseStopScreenText(nodes)
        assertNotNull(stopResult)
        assertEquals("Zone 5586", stopResult?.zoneOrLot)
        assertEquals("50 Av SW , Fr 6 St SW To ELBOW Dr SW", stopResult?.locationAddress)
        assertEquals("$0.00", stopResult?.costOrRefundText)
    }

    @Test
    fun testGroupParkingSessionsMergingStartAndStop() {
        val baseTime = 1727546000000L
        val session1 = com.parktimedetector.data.ParkingSession(
            id = 1L,
            startTimeMillis = baseTime,
            endTimeMillis = baseTime + 20 * 60 * 1000L,
            actualStopTimeMillis = null,
            zoneOrLot = "Zone 5586",
            source = "MyParking Screen",
            packageName = "com.cpa.accountManagement",
            isActive = false,
            locationAddress = "50 Av SW , Fr 6 St SW To ELBOW Dr SW",
            purchasedDurationText = "20 mins.",
            initialCostText = "$0.00"
        )

        val session2 = com.parktimedetector.data.ParkingSession(
            id = 2L,
            startTimeMillis = baseTime + 10 * 60 * 1000L,
            endTimeMillis = baseTime + 20 * 60 * 1000L,
            actualStopTimeMillis = baseTime + 10 * 60 * 1000L,
            zoneOrLot = "Zone 5586",
            source = "MyParking Stop",
            packageName = "com.cpa.accountManagement",
            isActive = false,
            locationAddress = "50 Av SW , Fr 6 St SW To ELBOW Dr SW",
            costOrRefundText = "$0.00",
            stopReason = "User Deactivated"
        )

        val grouped = com.parktimedetector.ui.screens.groupParkingSessions(listOf(session1, session2))
        assertEquals(1, grouped.size)
        val group = grouped[0]
        assertEquals("Zone 5586", group.zoneOrLot)
        assertEquals("50 Av SW , Fr 6 St SW To ELBOW Dr SW", group.locationAddress)
        assertEquals("20 mins.", group.purchasedDuration)
        assertEquals("$0.00", group.initialCost)
        assertEquals("User Deactivated", group.stopReason)
        assertEquals(2, group.allSessions.size)
    }

    @Test
    fun testExtractRemainingCountdownVariousFormats() {
        // Discrete node with words format
        val nodes1 = listOf("START/END SESSION", "Remaining: 19 mins : 57 secs", "Zone 5586")
        assertEquals("19 mins : 57 secs", SessionNotificationParser.extractRemainingCountdown(nodes1, nodes1.joinToString(" ")))

        // Discrete node with colon format
        val nodes2 = listOf("Parking Dashboard", "Time Remaining: 01:24:30", "Zone 1205")
        assertEquals("01:24:30", SessionNotificationParser.extractRemainingCountdown(nodes2, nodes2.joinToString(" ")))

        // Combined fallback with words
        val combinedWords = "Active parking session Zone 5586 Remaining: 45 mins End Time: 2:00 PM"
        assertEquals("45 mins", SessionNotificationParser.extractRemainingCountdown(emptyList(), combinedWords))

        // Combined fallback with digital timer
        val combinedTimer = "Calgary Parking Authority Time Remaining 00:15:30 Valid Until 3:00 PM"
        assertEquals("00:15:30", SessionNotificationParser.extractRemainingCountdown(emptyList(), combinedTimer))

        // No countdown present
        val noCountdown = listOf("Calgary Parking Authority", "Zone 1205", "Valid Until 3:00 PM")
        org.junit.Assert.assertNull(SessionNotificationParser.extractRemainingCountdown(noCountdown, noCountdown.joinToString(" ")))
    }

    @Test
    fun testExtractPurchasedDurationAndCostRobustness() {
        val nodes = listOf("Zone # 5586 20 mins. ($.00)")
        val combined = nodes.joinToString(" ")

        val duration = SessionNotificationParser.extractPurchasedDuration(nodes, combined)
        assertEquals("20 mins.", duration)

        val cost = SessionNotificationParser.extractCostOrRate(nodes, combined)
        assertEquals("$0.00", cost)
    }

    @Test
    fun testExtractZoneNumberWithLabelPrefix() {
        // "Zone Number: 1198" should extract "Zone 1198", not "Zone Number"
        val text1 = "Zone Number: 1198 5 St SW , Fr 2 Av SW To 3 Av SW"
        assertEquals("Zone 1198", SessionNotificationParser.extractZone(text1))

        val text2 = "Zone Number 1198"
        assertEquals("Zone 1198", SessionNotificationParser.extractZone(text2))

        val text3 = "Zone # 1198"
        assertEquals("Zone 1198", SessionNotificationParser.extractZone(text3))

        val text4 = "Zone: 1198"
        assertEquals("Zone 1198", SessionNotificationParser.extractZone(text4))

        val text5 = "Zone 1198"
        assertEquals("Zone 1198", SessionNotificationParser.extractZone(text5))

        // Standalone "Zone Number" without actual identifier should return null
        val text6 = "Zone Number"
        assertNull(SessionNotificationParser.extractZone(text6))
    }

    @Test
    fun testParseMyParkingDeactivationDialog() {
        val nodes = listOf(
            "Your parking session has been deactivated. The cost for this session was $0.00.",
            "OK"
        )
        assertTrue(SessionNotificationParser.isStopScreen(nodes))

        val stopResult = SessionNotificationParser.parseStopScreenText(nodes)
        assertNotNull(stopResult)
        assertEquals("$0.00", stopResult?.costOrRefundText)

        val nodes2 = listOf(
            "Your parking session has been deactivated. The cost for this session was $0.02.",
            "OK"
        )
        val stopResult2 = SessionNotificationParser.parseStopScreenText(nodes2)
        assertNotNull(stopResult2)
        assertEquals("$0.02", stopResult2?.costOrRefundText)
    }

    @Test
    fun testParkingAccessibilityServiceActionClickClassification() {
        // Stop actions
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("END PARKING SESSION"))
        assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("END PARKING SESSION"))

        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("STOP PARKING"))
        assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("STOP PARKING"))

        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("DEACTIVATE SESSION"))
        assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("DEACTIVATE SESSION"))

        // Start actions
        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("START PARKING SESSION"))
        assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("START PARKING SESSION"))

        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("START"))
        assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("START"))

        assertTrue(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("PARK NOW"))
        assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("PARK NOW"))

        // Tabs/Headers should not trigger as either
        assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStartOrActionClick("START/END SESSION"))
        assertFalse(com.parktimedetector.service.ParkingAccessibilityService.isStopActionClick("START/END SESSION"))
    }

    @Test
    fun testExtractStartTimeExplicit() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 25, 12, 0, 0)
        }.timeInMillis

        val result = SessionNotificationParser.parse(
            title = "ParkedIn Session Active",
            text = "Started at 1:30 PM. Expires at 3:30 PM. Zone 4022.",
            postTimeMillis = baseTime
        )

        assertNotNull(result)
        assertNotNull(result?.startTimeMillis)
        val calStart = Calendar.getInstance().apply { timeInMillis = result!!.startTimeMillis!! }
        assertEquals(13, calStart.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calStart.get(Calendar.MINUTE))

        val calEnd = Calendar.getInstance().apply { timeInMillis = result!!.endTimeMillis }
        assertEquals(15, calEnd.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calEnd.get(Calendar.MINUTE))
    }

    @Test
    fun testExtractTimeRange() {
        val baseTime = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 25, 9, 0, 0)
        }.timeInMillis

        val nodes = listOf("Active Session", "Session time: 10:00 AM - 12:00 PM", "Lot 58")
        val result = SessionNotificationParser.parseScreenText(nodes, baseTime)

        assertNotNull(result)
        assertNotNull(result?.startTimeMillis)
        val calStart = Calendar.getInstance().apply { timeInMillis = result!!.startTimeMillis!! }
        assertEquals(10, calStart.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calStart.get(Calendar.MINUTE))

        val calEnd = Calendar.getInstance().apply { timeInMillis = result!!.endTimeMillis }
        assertEquals(12, calEnd.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calEnd.get(Calendar.MINUTE))
    }

    @Test
    fun testMyParkingMapScreenDetection() {
        val mapScreenTexts = listOf(
            "MyParking (Calgary Parking)",
            "🗺️ Interactive Map (Press Pin to Fetch)",
            "Lot 58 (9058)",
            "Zone 1008 (1008)",
            "Search zone number or lot (e.g. 9058)",
            "Search Suggestions / Recent Lots"
        )

        // Map screen with search suggestions should be recognized as a map screen
        assertTrue(SessionNotificationParser.isMyParkingMapScreen(mapScreenTexts, "MockMyParkingActivity"))
        assertTrue(SessionNotificationParser.isMyParkingMapScreen(mapScreenTexts, "com.google.android.gms.maps.MapView"))

        // Active session screen should NOT be recognized as a map screen
        val activeSessionTexts = listOf(
            "START/END SESSION",
            "Zone 9058",
            "Lot 58 - 935 - 4 Av SW",
            "Remaining: 01 hr : 20 mins",
            "End Parking Session"
        )
        assertFalse(SessionNotificationParser.isMyParkingMapScreen(activeSessionTexts, "MockMyParkingActivity"))

        // Stop session / receipt screen should NOT be recognized as a map screen
        val stopScreenTexts = listOf(
            "Parking Session Ended",
            "Stopped at 2:30 PM",
            "Total cost: $3.00",
            "Done"
        )
        assertFalse(SessionNotificationParser.isMyParkingMapScreen(stopScreenTexts, "ReceiptActivity"))
    }
}

