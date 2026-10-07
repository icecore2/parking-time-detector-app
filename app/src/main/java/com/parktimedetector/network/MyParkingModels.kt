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
 * Metadata retrieved from Calgary Parking Authority ArcGIS FeatureServer GIS layers,
 * parsing all keys returned by On-Street and Off-Street municipal layers.
 */
data class ZoneGisMetadata(
    val parkingZone: String,
    val addressDesc: String? = null,
    val zoneType: String? = null,
    val stallType: String? = null,
    val maxTimeMinutes: Int? = null,
    val enforceableTime: String? = null,
    val rawHtmlRate: String? = null,
    val parsedRatesSummary: String? = null,
    val zoneCapacity: Int? = null,
    val segmentCapacity: Int? = null,
    val zoneLengthMeters: Double? = null,
    val segmentLengthMeters: Double? = null,
    val priceZone: String? = null,
    val brzName: String? = null,
    val blockSide: String? = null,
    val status: String? = null,
    val comments: String? = null,
    val parkingRestrictTime: String? = null,
    val parkingRestrictType: String? = null,
    val homePageUrl: String? = null,
    val objectId: Long? = null,
    val globalId: String? = null,
    val dot: String? = null,
    val camera: String? = null
)

/**
 * Extracted cheapest price information for a parking zone.
 */
data class CheapestPriceInfo(
    val displayPrice: String,
    val numericRatePerHour: Double,
    val isFree: Boolean = false,
    val rateDescription: String? = null
)

/**
 * Clean data model representing a parking zone with its computed cheapest price and full attributes.
 */
data class ZoneWithPrice(
    val zoneNumber: String,
    val nameOrTitle: String,
    val address: String,
    val zoneType: String? = null,
    val stallType: String? = null,
    val maxTimeMinutes: Int? = null,
    val enforceableTime: String? = null,
    val cheapestPrice: CheapestPriceInfo,
    val zoneCapacity: Int? = null,
    val brzName: String? = null,
    val blockSide: String? = null,
    val distanceMeters: Int? = null,
    val isLot: Boolean = false,
    val rawMetadata: ZoneGisMetadata? = null
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
    val zoneType: String? = null,
    val zoneCapacity: Int? = null,
    val brzName: String? = null,
    val costDurations: List<CostDuration> = emptyList(),
    val rateScheduleLines: List<String> = emptyList(),
    val cheapestPrice: CheapestPriceInfo? = null,
    val gisMetadata: ZoneGisMetadata? = null,
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
    val isLot: Boolean = false,
    val cheapestPrice: CheapestPriceInfo? = null
)
