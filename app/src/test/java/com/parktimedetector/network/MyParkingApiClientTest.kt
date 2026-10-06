package com.parktimedetector.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyParkingApiClientTest {

    @Test
    fun testParseVpmTimestamp() {
        val raw = "20261006T105720-0600"
        val parsed = MyParkingApiClient.parseVpmTimestamp(raw)
        assertNotNull(parsed)
        assertTrue(parsed!! > 0L)

        // Invalid timestamp should return null without crashing
        val invalid = MyParkingApiClient.parseVpmTimestamp("invalid-timestamp")
        assertNull(invalid)
    }

    @Test
    fun testParseVpmResponseSuccess() {
        val json = """
            {
                "zoneNumber": "1008",
                "zoneAddress": "Eau Claire Av - 4 St to 5 St SW - N side",
                "costDurations": [
                    {
                        "cost": 1.00,
                        "duration": 16,
                        "endTime": "20261006T104308-0600"
                    },
                    {
                        "cost": 2.00,
                        "duration": 30,
                        "endTime": "20261006T105708-0600"
                    },
                    {
                        "cost": 12.62,
                        "duration": 180,
                        "endTime": "20261006T132708-0600"
                    }
                ],
                "startDateTime": "20261006T102708-0600"
            }
        """.trimIndent()

        val result = MyParkingApiClient.parseVpmResponse(json)
        assertTrue(result.isSuccess)

        val (address, durations) = result.getOrThrow()
        assertEquals("Eau Claire Av - 4 St to 5 St SW - N side", address)
        assertEquals(3, durations.size)

        val first = durations[0]
        assertEquals(1.00, first.cost, 0.001)
        assertEquals(16, first.durationMinutes)
        assertEquals("20261006T104308-0600", first.endTimeRaw)
        assertNotNull(first.parsedEndTimeMillis)

        val last = durations[2]
        assertEquals(12.62, last.cost, 0.001)
        assertEquals(180, last.durationMinutes)
    }

    @Test
    fun testParseVpmResponseZoneNotFound() {
        val json = "\"404 Not Found on GET request for http://ppapp.ho.cpa/resources/parkingcosts: Zone not found.\""
        val result = MyParkingApiClient.parseVpmResponse(json)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testParseHtmlZoneRate() {
        val html = "<b>Mon-Fri 9:00 AM to 11:00 AM</b><br><br>$4.00 per Hour<br><br><b>Mon-Fri 11:00 AM to 1:30 PM</b><br><br>$4.25 per Hour<br><br><b>Saturday: 9:00 AM to 6:00 PM</b><br><br>$1.00 per Hour<br><br><b>Sunday/Holidays:</b><br><br>Free parking<br><br>"

        val schedule = MyParkingApiClient.parseHtmlZoneRate(html)
        assertTrue(schedule.isNotEmpty())

        assertTrue(schedule.any { it.contains("Mon-Fri 9:00 AM to 11:00 AM: $4.00 per Hour") })
        assertTrue(schedule.any { it.contains("Saturday: 9:00 AM to 6:00 PM: $1.00 per Hour") })
        assertTrue(schedule.any { it.contains("Sunday/Holidays: Free parking") })
    }

    @Test
    fun testParseArcGisFeaturesOnStreet() {
        val json = """
            {
                "features": [
                    {
                        "attributes": {
                            "OBJECTID": 2,
                            "BLOCK_SIDE": "N",
                            "ADDRESS_DESC": "Eau Claire Av SW , Fr 4 St SW To 5 St SW",
                            "PARKING_ZONE": 1008,
                            "ZONE_TYPE": "Parking Zone",
                            "STALL_TYPE": "Parallel",
                            "MAX_TIME": 180,
                            "ENFORCEABLE_TIME": "0910-1750 MON-SAT",
                            "HTML_ZONE_RATE": "<b>Mon-Fri 9:00 AM to 11:00 AM</b><br><br>$4.00 per Hour"
                        }
                    }
                ]
            }
        """.trimIndent()

        val result = MyParkingApiClient.parseArcGisFeatures(json)
        assertTrue(result.isSuccess)

        val meta = result.getOrThrow()
        assertNotNull(meta)
        assertEquals("1008", meta!!.parkingZone)
        assertEquals("Eau Claire Av SW , Fr 4 St SW To 5 St SW", meta.addressDesc)
        assertEquals(180, meta.maxTimeMinutes)
        assertEquals("0910-1750 MON-SAT", meta.enforceableTime)
        assertEquals("Parallel", meta.stallType)
        assertEquals("Parking Zone", meta.zoneType)
        assertTrue(meta.parsedRatesSummary!!.contains("$4.00"))
    }

    @Test
    fun testParseArcGisFeaturesEmpty() {
        val json = """{ "features": [] }"""
        val result = MyParkingApiClient.parseArcGisFeatures(json)
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun testFallbackZoneDetails() {
        // Query for Lot 58 (9058)
        val details9058 = kotlinx.coroutines.runBlocking {
            MyParkingApiClient.getCombinedZoneDetails("9058")
        }
        assertNotNull(details9058)
        assertEquals("9058", details9058.zoneNumber)
        assertTrue(details9058.address.contains("Lot 58") || details9058.address.contains("4 Av SW"))
        assertTrue(details9058.maxTimeMinutes != null && details9058.maxTimeMinutes!! >= 120)
        assertTrue(details9058.costDurations.isNotEmpty())

        // Query for Zone 1008 (Eau Claire)
        val details1008 = kotlinx.coroutines.runBlocking {
            MyParkingApiClient.getCombinedZoneDetails("1008")
        }
        assertNotNull(details1008)
        assertEquals("1008", details1008.zoneNumber)
        assertTrue(details1008.address.contains("Eau Claire") || details1008.address.contains("4 St"))
        assertTrue(details1008.maxTimeMinutes != null && details1008.maxTimeMinutes!! >= 60)

        // Query for non-existent zone ensures offline fallback path works smoothly
        val detailsUnknown = kotlinx.coroutines.runBlocking {
            MyParkingApiClient.getCombinedZoneDetails("0000")
        }
        assertNotNull(detailsUnknown)
        assertEquals("0000", detailsUnknown.zoneNumber)
        assertTrue(detailsUnknown.isFallback)
        assertTrue(detailsUnknown.costDurations.isNotEmpty())
    }
}
