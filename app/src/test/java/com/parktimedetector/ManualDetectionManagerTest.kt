package com.parktimedetector

import com.parktimedetector.service.ManualDetectionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualDetectionManagerTest {

    @Test
    fun testStartAndResetDetectionWindow() {
        ManualDetectionManager.stopDetectionWindow(null)
        assertFalse(ManualDetectionManager.isDetectionWindowActive())
        assertEquals(0, ManualDetectionManager.remainingSeconds.value)

        // Start detection window
        ManualDetectionManager.startOrResetDetectionWindow(null, "Test")
        assertTrue(ManualDetectionManager.isDetectionWindowActive())
        assertEquals(ManualDetectionManager.DETECTION_WINDOW_SECONDS, ManualDetectionManager.remainingSeconds.value)

        // Start again -> starts time over
        ManualDetectionManager.startOrResetDetectionWindow(null, "Test Press Again")
        assertTrue(ManualDetectionManager.isDetectionWindowActive())
        assertEquals(ManualDetectionManager.DETECTION_WINDOW_SECONDS, ManualDetectionManager.remainingSeconds.value)

        // Stop manually
        ManualDetectionManager.stopDetectionWindow(null)
        assertFalse(ManualDetectionManager.isDetectionWindowActive())
        assertEquals(0, ManualDetectionManager.remainingSeconds.value)
    }
}
