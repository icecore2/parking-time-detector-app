package com.parktimedetector

import com.parktimedetector.data.FavoriteZone
import com.parktimedetector.location.LocationHelper
import com.parktimedetector.location.ZoneDiscoveryManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneDiscoveryManagerTest {

    @Test
    fun testHaversineDistanceAccuracy() {
        // Distance between Calgary Tower (51.0443, -114.0631) and Peace Bridge (51.0543, -114.0792) is approx 1.57 km
        val dist = LocationHelper.distanceInMeters(51.0443, -114.0631, 51.0543, -114.0792)
        assertTrue(dist in 1400.0..1700.0)
    }

    @Test
    fun testFindNearestZoneFromPreloaded() = runBlocking {
        // Query right beside preloaded Zone 4022 (51.0486, -114.0708)
        val result = ZoneDiscoveryManager.findNearestZone(
            currentLat = 51.0487,
            currentLng = -114.0709,
            maxRadiusMeters = 200.0,
            pastSessions = emptyList(),
            favoriteZones = emptyList()
        )

        assertNotNull(result)
        assertEquals("Zone 4022", result?.zoneOrLot)
        assertTrue(result!!.distanceMeters < 50.0)
    }

    @Test
    fun testFindNearestZonePrioritizesFavorite() = runBlocking {
        // Query near a custom favorite zone
        val fav = FavoriteZone(
            id = "fav_test",
            name = "Office Underground",
            latitude = 51.0450,
            longitude = -114.0620,
            defaultDurationMinutes = 120
        )

        val result = ZoneDiscoveryManager.findNearestZone(
            currentLat = 51.0451,
            currentLng = -114.0621,
            maxRadiusMeters = 150.0,
            pastSessions = emptyList(),
            favoriteZones = listOf(fav)
        )

        assertNotNull(result)
        assertEquals("Office Underground", result?.zoneOrLot)
        assertEquals("Favorite Zone", result?.source)
    }
}
