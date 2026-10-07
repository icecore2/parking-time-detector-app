package com.parktimedetector.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheapestPriceParserTest {

    @Test
    fun testParseArcGisFeatureAttributesAllKeys() {
        val attrJson = JSONObject("""
            {
                "ADDRESS_DESC": "Eau Claire Av SW , Fr 4 St SW To 5 St SW",
                "PARKING_ZONE": 1008,
                "ZONE_TYPE": "Parking Zone",
                "STALL_TYPE": "Parallel",
                "MAX_TIME": 180,
                "ENFORCEABLE_TIME": "0910-1750 MON-SAT",
                "HTML_ZONE_RATE": "<b>Mon-Fri 9:00 AM to 11:00 AM</b><br><br>$4.00 per Hour<br><br><b>Saturday:</b><br><br>$1.00 per Hour",
                "ZONE_CAP": 12,
                "SEG_CAP": 6,
                "ZONE_LENGTH": 60.5,
                "SEG_LENGTH": 30.2,
                "PRICE_ZONE": "Core Downtown",
                "BRZ_NAME": "Downtown Association",
                "BLOCK_SIDE": "N",
                "STATUS": "Active",
                "COMMENTS": "Construction nearby",
                "PARKING_RESTRICT_TIME": "0700-0900",
                "PARKING_RESTRICT_TYPE": "No Parking",
                "HOME_PAGE": "https://www.calgaryparking.com",
                "OBJECTID": 999,
                "GLOBALID": "{ABC-123}",
                "DOT": "Calgary Roads",
                "CAMERA": "Camera 04"
            }
        """.trimIndent())

        val meta = MyParkingApiClient.parseArcGisFeatureAttributes(attrJson)
        assertNotNull(meta)
        assertEquals("1008", meta.parkingZone)
        assertEquals("Eau Claire Av SW , Fr 4 St SW To 5 St SW", meta.addressDesc)
        assertEquals("Parking Zone", meta.zoneType)
        assertEquals("Parallel", meta.stallType)
        assertEquals(180, meta.maxTimeMinutes)
        assertEquals("0910-1750 MON-SAT", meta.enforceableTime)
        assertEquals(12, meta.zoneCapacity)
        assertEquals(6, meta.segmentCapacity)
        assertEquals(60.5, meta.zoneLengthMeters ?: 0.0, 0.001)
        assertEquals(30.2, meta.segmentLengthMeters ?: 0.0, 0.001)
        assertEquals("Core Downtown", meta.priceZone)
        assertEquals("Downtown Association", meta.brzName)
        assertEquals("N", meta.blockSide)
        assertEquals("Active", meta.status)
        assertEquals("Construction nearby", meta.comments)
        assertEquals("0700-0900", meta.parkingRestrictTime)
        assertEquals("No Parking", meta.parkingRestrictType)
        assertEquals("https://www.calgaryparking.com", meta.homePageUrl)
        assertEquals(999L, meta.objectId)
        assertEquals("{ABC-123}", meta.globalId)
        assertEquals("Calgary Roads", meta.dot)
        assertEquals("Camera 04", meta.camera)
    }

    @Test
    fun testExtractCheapestPriceForLoadingZone() {
        val attrJson = JSONObject("""
            {
                "PARKING_ZONE": 2001,
                "ADDRESS_DESC": "8th Ave SW loading area",
                "ZONE_TYPE": "Loading zone",
                "MAX_TIME": 30
            }
        """.trimIndent())

        val meta = MyParkingApiClient.parseArcGisFeatureAttributes(attrJson)
        val cheapest = MyParkingApiClient.extractCheapestPrice(meta, emptyList())

        assertTrue(cheapest.isFree)
        assertEquals(0.0, cheapest.numericRatePerHour, 0.001)
        assertEquals("FREE", cheapest.displayPrice)
    }

    @Test
    fun testExtractCheapestPriceFromHtmlRates() {
        val attrJson = JSONObject("""
            {
                "PARKING_ZONE": 1008,
                "ADDRESS_DESC": "Eau Claire Av",
                "ZONE_TYPE": "Parking Zone",
                "MAX_TIME": 180,
                "HTML_ZONE_RATE": "<b>Mon-Fri 9am-4pm</b><br>$4.50 per Hour<br><b>Evenings / Weekends</b><br>$1.25 per Hour"
            }
        """.trimIndent())

        val meta = MyParkingApiClient.parseArcGisFeatureAttributes(attrJson)
        val cheapest = MyParkingApiClient.extractCheapestPrice(meta, emptyList())

        assertFalse(cheapest.isFree)
        assertEquals(1.25, cheapest.numericRatePerHour, 0.001)
        assertEquals("$1.25 / hr", cheapest.displayPrice)
    }

    @Test
    fun testExtractCheapestPriceFromVpmCostDurations() {
        val attrJson = JSONObject("""
            {
                "PARKING_ZONE": 9058,
                "ADDRESS_DESC": "Lot 58 Downtown West",
                "MAX_TIME": 120
            }
        """.trimIndent())

        val meta = MyParkingApiClient.parseArcGisFeatureAttributes(attrJson)
        val vpmOptions = listOf(
            CostDuration(cost = 1.00, durationMinutes = 30, endTimeRaw = "test"),
            CostDuration(cost = 3.00, durationMinutes = 60, endTimeRaw = "test"),
            CostDuration(cost = 6.00, durationMinutes = 120, endTimeRaw = "test")
        )

        val cheapest = MyParkingApiClient.extractCheapestPrice(meta, vpmOptions)
        assertFalse(cheapest.isFree)
        // 1.00 for 30m = 2.00/hr, 3.00 for 60m = 3.00/hr -> min is 2.00/hr
        assertEquals(2.0, cheapest.numericRatePerHour, 0.001)
        assertEquals("$2.00 / hr", cheapest.displayPrice)
    }

    @Test
    fun testFallbackNearbyZonesSortingOrder() {
        val zones = MyParkingApiClient.getFallbackNearbyZonesSorted()
        assertTrue(zones.isNotEmpty())

        // Free zones (numericRatePerHour == 0.0) should be at the very top
        val firstZone = zones.first()
        assertTrue(firstZone.cheapestPrice.isFree)
        assertEquals(0.0, firstZone.cheapestPrice.numericRatePerHour, 0.001)

        // Ensure array is strictly sorted in ascending order of numericRatePerHour
        for (i in 0 until zones.size - 1) {
            val curr = zones[i].cheapestPrice.numericRatePerHour
            val next = zones[i + 1].cheapestPrice.numericRatePerHour
            assertTrue("Expected curr ($curr) <= next ($next)", curr <= next)
        }
    }

    @Test
    fun testParseArcGisFeaturesListMultiFeatures() {
        val jsonString = """
            {
                "features": [
                    {
                        "attributes": {
                            "PARKING_ZONE": 1008,
                            "ADDRESS_DESC": "Eau Claire Av SW",
                            "ZONE_TYPE": "Parking Zone",
                            "MAX_TIME": 180,
                            "ZONE_CAP": 10,
                            "HTML_ZONE_RATE": "$3.00 per Hour"
                        }
                    },
                    {
                        "attributes": {
                            "PARKING_ZONE": 2005,
                            "ADDRESS_DESC": "Free 15-min loading bay",
                            "ZONE_TYPE": "Loading zone",
                            "MAX_TIME": 15,
                            "ZONE_CAP": 2
                        }
                    }
                ]
            }
        """.trimIndent()

        val result = MyParkingApiClient.parseArcGisFeaturesList(jsonString)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(2, list.size)
        assertEquals("1008", list[0].parkingZone)
        assertEquals("2005", list[1].parkingZone)
        assertEquals("Loading zone", list[1].zoneType)
    }
}
