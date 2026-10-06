package com.parktimedetector

import com.parktimedetector.data.FavoriteZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteZoneTest {

    @Test
    fun testFavoriteZoneJsonSerialization() {
        val original = FavoriteZone(
            id = "fav_123",
            name = "Zone 4022",
            defaultDurationMinutes = 90,
            notes = "Level 2B, Meter #4",
            latitude = 51.0486,
            longitude = -114.0708
        )

        val json = original.toJson()
        val restored = FavoriteZone.fromJson(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.defaultDurationMinutes, restored.defaultDurationMinutes)
        assertEquals(original.notes, restored.notes)
        assertEquals(original.latitude, restored.latitude)
        assertEquals(original.longitude, restored.longitude)
    }

    @Test
    fun testFavoriteZoneListSerialization() {
        val list = listOf(
            FavoriteZone("1", "Zone A", 30),
            FavoriteZone("2", "Lot B", 120, "Near exit", 51.05, -114.08)
        )

        val jsonStr = FavoriteZone.listToJsonString(list)
        val restoredList = FavoriteZone.listFromJsonString(jsonStr)

        assertEquals(2, restoredList.size)
        assertEquals("Zone A", restoredList[0].name)
        assertEquals(30, restoredList[0].defaultDurationMinutes)
        assertEquals("Lot B", restoredList[1].name)
        assertEquals("Near exit", restoredList[1].notes)
    }

    @Test
    fun testDefaultFavoritesFallback() {
        val defaults = FavoriteZone.listFromJsonString(null)
        assertNotNull(defaults)
        assert(defaults.isNotEmpty())
    }
}
