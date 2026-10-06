package com.parktimedetector.network

/**
 * Represents an individual parking option with duration in minutes, cost in dollars,
 * and expiration timestamp returned by the MyParking VPM service.
 */
data class CostDuration(
    val cost: Double,
    val durationMinutes: Int,
    val endTimeRaw: String,
    val parsedEndTimeMillis: Long? = null
)

/**
 * Metadata retrieved from Calgary Parking Authority ArcGIS FeatureServer GIS layers.
 */
data class ZoneGisMetadata(
    val parkingZone: String,
    val addressDesc: String? = null,
    val maxTimeMinutes: Int? = null,
    val enforceableTime: String? = null,
    val stallType: String? = null,
    val zoneType: String? = null,
    val rawHtmlRate: String? = null,
    val parsedRatesSummary: String? = null
)

/**
 * Unified parking zone and rate details shown when pressing a pin on the map.
 */
data class ParkingZoneDetails(
    val zoneNumber: String,
    val nameOrTitle: String,
    val address: String,
    val hourlyRateEstimate: String? = null,
    val maxTimeMinutes: Int? = null,
    val enforceableTime: String? = null,
    val stallType: String? = null,
    val costDurations: List<CostDuration> = emptyList(),
    val rateScheduleLines: List<String> = emptyList(),
    val source: String = "MyParking Live",
    val isBlocked: Boolean = false,
    val isFallback: Boolean = false
)

/**
 * Map pin definition for UI rendering in interactive maps.
 */
data class ParkingMapPin(
    val zoneNumber: String,
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val isLot: Boolean = false
)
