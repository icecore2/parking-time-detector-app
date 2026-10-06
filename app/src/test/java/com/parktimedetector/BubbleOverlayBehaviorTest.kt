package com.parktimedetector

import com.parktimedetector.data.PendingParkingDetection
import com.parktimedetector.data.PendingParkingStopDetection
import com.parktimedetector.service.DetectionApprovalManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BubbleOverlayBehaviorTest {

    @Before
    fun setUp() {
        DetectionApprovalManager.overlayCallback = null
    }

    @Test
    fun testBubbleCollapsedStateReporting() {
        assertFalse(
            "Default without callback should report bubble not collapsed",
            DetectionApprovalManager.isBubbleCollapsed()
        )

        var bubbleCollapsed = false
        val mockCallback = object : DetectionApprovalManager.OverlayCallback {
            override fun showOverlay(detection: PendingParkingDetection) {}
            override fun showStopOverlay(detection: PendingParkingStopDetection) {}
            override fun hideOverlay() {}
            override fun isBubbleCollapsed(): Boolean = bubbleCollapsed
        }

        DetectionApprovalManager.overlayCallback = mockCallback
        assertFalse(DetectionApprovalManager.isBubbleCollapsed())

        bubbleCollapsed = true
        assertTrue(
            "Should reflect that bubble is collapsed",
            DetectionApprovalManager.isBubbleCollapsed()
        )
    }

    @Test
    fun testNewDetectionUpdatesPendingDetectionWhileInBubble() {
        var overlayShownCount = 0
        var lastDetectionReceived: PendingParkingDetection? = null

        val mockCallback = object : DetectionApprovalManager.OverlayCallback {
            var bubbleCollapsed = false

            override fun showOverlay(detection: PendingParkingDetection) {
                overlayShownCount++
                lastDetectionReceived = detection
            }

            override fun showStopOverlay(detection: PendingParkingStopDetection) {}
            override fun hideOverlay() {}
            override fun isBubbleCollapsed(): Boolean = bubbleCollapsed
        }

        DetectionApprovalManager.overlayCallback = mockCallback

        val initialDetection = PendingParkingDetection(
            packageName = "com.calgaryparking.myparking",
            appName = "MyParking",
            zoneOrLot = "Lot 58 - 935 - 4 Av SW",
            endTimeMillis = System.currentTimeMillis() + 3600_000L,
            source = "MyParking App",
            reason = "Test initial detection",
            rawData = "Lot 58"
        )

        // Initial detection shows overlay
        mockCallback.showOverlay(initialDetection)
        assertEquals(1, overlayShownCount)
        assertEquals("Lot 58 - 935 - 4 Av SW", lastDetectionReceived?.zoneOrLot)

        // User collapses dialog to bubble
        mockCallback.bubbleCollapsed = true
        assertTrue(DetectionApprovalManager.isBubbleCollapsed())

        // User changes parking selection to Zone 4022 while bubble is collapsed
        val updatedDetection = PendingParkingDetection(
            packageName = "com.calgaryparking.myparking",
            appName = "MyParking",
            zoneOrLot = "Zone 4022 - Downtown 8th Ave",
            endTimeMillis = System.currentTimeMillis() + 7200_000L,
            source = "MyParking App",
            reason = "Test updated selection",
            rawData = "Zone 4022"
        )

        // New detection is delivered to callback while bubble is collapsed
        mockCallback.showOverlay(updatedDetection)
        assertEquals(2, overlayShownCount)
        assertEquals("Zone 4022 - Downtown 8th Ave", lastDetectionReceived?.zoneOrLot)
        assertTrue("Bubble should remain collapsed without raising full dialog", mockCallback.bubbleCollapsed)
    }

    @Test
    fun testBubbleLotTextFormatting() {
        fun formatLotForBubble(zoneOrLot: String?): String {
            return zoneOrLot?.substringBefore(" -") ?: "Parking"
        }

        assertEquals("Lot 58", formatLotForBubble("Lot 58 - 935 - 4 Av SW"))
        assertEquals("Zone 4022", formatLotForBubble("Zone 4022 - Downtown 8th Ave"))
        assertEquals("Lot 25", formatLotForBubble("Lot 25 - City Hall P1"))
        assertEquals("Zone 1205", formatLotForBubble("Zone 1205"))
        assertEquals("Parking", formatLotForBubble(null))
    }

    @Test
    fun testDetectionDeduplicationLogic() {
        val baseTime = 1700000000000L

        fun isExactSameDetection(
            newEndTime: Long,
            newZone: String?,
            newDuration: String?,
            newCost: String?,
            lastEndTime: Long,
            lastZone: String?,
            lastDuration: String?,
            lastCost: String?
        ): Boolean {
            return Math.abs(newEndTime - lastEndTime) < 60_000L &&
                newZone == lastZone &&
                newDuration == lastDuration &&
                newCost == lastCost
        }

        // Exact same detection -> suppressed as duplicate
        assertTrue(
            isExactSameDetection(
                newEndTime = baseTime,
                newZone = "Lot 58",
                newDuration = "1 hour",
                newCost = "$3.00",
                lastEndTime = baseTime,
                lastZone = "Lot 58",
                lastDuration = "1 hour",
                lastCost = "$3.00"
            )
        )

        // Zone changed while end time unchanged -> NOT duplicate (should update bubble)
        assertFalse(
            "Different zone must not be treated as duplicate",
            isExactSameDetection(
                newEndTime = baseTime,
                newZone = "Zone 4022",
                newDuration = "1 hour",
                newCost = "$3.00",
                lastEndTime = baseTime,
                lastZone = "Lot 58",
                lastDuration = "1 hour",
                lastCost = "$3.00"
            )
        )

        // Duration changed -> NOT duplicate (should update bubble)
        assertFalse(
            "Different duration must not be treated as duplicate",
            isExactSameDetection(
                newEndTime = baseTime + 3600_000L,
                newZone = "Lot 58",
                newDuration = "2 hours",
                newCost = "$6.00",
                lastEndTime = baseTime,
                lastZone = "Lot 58",
                lastDuration = "1 hour",
                lastCost = "$3.00"
            )
        )
    }
}
