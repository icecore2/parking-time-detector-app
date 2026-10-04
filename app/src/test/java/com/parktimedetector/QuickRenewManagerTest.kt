package com.parktimedetector

import com.parktimedetector.service.QuickRenewManager
import com.parktimedetector.service.QuickRenewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuickRenewManagerTest {

    @Before
    fun setup() {
        QuickRenewManager.disarm()
    }

    @Test
    fun testExtractZoneDigitsExplicitZone() {
        val digits = QuickRenewManager.extractZoneDigits("Zone 4022")
        assertEquals("4022", digits)
    }

    @Test
    fun testExtractZoneDigitsMyParkingLot58() {
        // In Calgary Parking Authority, Lot 58 maps to Zone 9058
        val digits = QuickRenewManager.extractZoneDigits("Lot 58 - 935 - 4 Av SW")
        assertEquals("9058", digits)
    }

    @Test
    fun testExtractZoneDigitsNumericString() {
        val digits = QuickRenewManager.extractZoneDigits("9058")
        assertEquals("9058", digits)
    }

    @Test
    fun testExtractZoneDigitsFromLocationAddressFallback() {
        val digits = QuickRenewManager.extractZoneDigits(null, "Lot 58 - 935 - 4 Av SW")
        assertEquals("9058", digits)
    }

    @Test
    fun testExtractZoneDigitsLongZoneNumber() {
        val digits = QuickRenewManager.extractZoneDigits("Zone 1205")
        assertEquals("1205", digits)
    }

    @Test
    fun testExtractZoneDigitsFallback() {
        val digits = QuickRenewManager.extractZoneDigits("")
        assertEquals("4022", digits)
    }

    @Test
    fun testDisarmResetsState() {
        assertFalse(QuickRenewManager.isArmed())
        assertEquals(QuickRenewState.IDLE, QuickRenewManager.state.value)
        assertNull(QuickRenewManager.targetPackage)
    }
}
