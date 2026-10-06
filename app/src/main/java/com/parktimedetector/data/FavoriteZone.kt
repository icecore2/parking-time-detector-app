package com.parktimedetector.data

import org.json.JSONArray
import org.json.JSONObject

data class FavoriteZone(
    val id: String,
    val name: String,
    val defaultDurationMinutes: Int = 60,
    val notes: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("defaultDurationMinutes", defaultDurationMinutes)
            if (notes != null) put("notes", notes)
            if (latitude != null) put("latitude", latitude)
            if (longitude != null) put("longitude", longitude)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): FavoriteZone {
            return FavoriteZone(
                id = json.optString("id", System.currentTimeMillis().toString()),
                name = json.optString("name", "Zone"),
                defaultDurationMinutes = json.optInt("defaultDurationMinutes", 60),
                notes = if (json.has("notes")) json.optString("notes") else null,
                latitude = if (json.has("latitude")) json.optDouble("latitude") else null,
                longitude = if (json.has("longitude")) json.optDouble("longitude") else null
            )
        }

        fun listToJsonString(list: List<FavoriteZone>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJsonString(jsonStr: String?): List<FavoriteZone> {
            if (jsonStr.isNullOrBlank()) return defaultFavorites()
            val result = mutableListOf<FavoriteZone>()
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    result.add(fromJson(arr.getJSONObject(i)))
                }
            } catch (_: Exception) {
                return defaultFavorites()
            }
            return if (result.isEmpty()) defaultFavorites() else result
        }

        fun defaultFavorites(): List<FavoriteZone> {
            return listOf(
                FavoriteZone(
                    id = "fav_4022",
                    name = "Zone 4022",
                    defaultDurationMinutes = 60,
                    notes = "Downtown Core Meter",
                    latitude = 51.0486,
                    longitude = -114.0708
                ),
                FavoriteZone(
                    id = "fav_lot58",
                    name = "Lot 58",
                    defaultDurationMinutes = 120,
                    notes = "935 4 Av SW Lot",
                    latitude = 51.0492,
                    longitude = -114.0834
                ),
                FavoriteZone(
                    id = "fav_1045",
                    name = "Zone 1045",
                    defaultDurationMinutes = 30,
                    notes = "Express Street Meter",
                    latitude = 51.0450,
                    longitude = -114.0620
                )
            )
        }
    }
}
