package com.parktimedetector.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.parktimedetector.data.FavoriteZone
import com.parktimedetector.data.ParkingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class DiscoveredZoneResult(
    val zoneOrLot: String,
    val address: String? = null,
    val distanceMeters: Double,
    val source: String,
    val defaultDurationMinutes: Int = 60
)

object ZoneDiscoveryManager {

    private data class KnownZone(
        val zoneOrLot: String,
        val address: String?,
        val latitude: Double,
        val longitude: Double,
        val defaultDurationMinutes: Int = 60
    )

    // Preloaded municipal/popular parking meters and lots
    private val PRELOADED_ZONES = listOf(
        KnownZone("Zone 4022", "8 Av SW & 4 St SW", 51.0486, -114.0708, 60),
        KnownZone("Lot 58", "935 4 Av SW", 51.0492, -114.0834, 120),
        KnownZone("Zone 1045", "50 Av SW near Elbow Dr", 51.0450, -114.0620, 30),
        KnownZone("Zone 3011", "17 Ave SW & 5 St SW", 51.0379, -114.0735, 90),
        KnownZone("Lot 83", "Centennial Lot - 11 Ave SW", 51.0435, -114.0690, 180),
        KnownZone("Zone 2050", "Kensington Rd & 10 St NW", 51.0532, -114.0865, 60)
    )

    // In-memory learned zone cache for real-time auto-captures during the session
    private val learnedZones = mutableListOf<KnownZone>()

    fun registerDiscoveredZone(zoneOrLot: String, address: String?, lat: Double, lng: Double, durationMinutes: Int = 60) {
        synchronized(learnedZones) {
            learnedZones.removeAll { it.zoneOrLot.equals(zoneOrLot, ignoreCase = true) }
            learnedZones.add(KnownZone(zoneOrLot, address, lat, lng, durationMinutes))
        }
    }

    /**
     * Finds the closest parking zone within the radius.
     */
    fun findNearestZone(
        currentLat: Double,
        currentLng: Double,
        maxRadiusMeters: Double = 350.0,
        pastSessions: List<ParkingSession> = emptyList(),
        favoriteZones: List<FavoriteZone> = emptyList()
    ): DiscoveredZoneResult? {
        val candidates = mutableListOf<DiscoveredZoneResult>()

        // 1. Check Favorite Zones
        for (fav in favoriteZones) {
            if (fav.latitude != null && fav.longitude != null) {
                val dist = LocationHelper.distanceInMeters(currentLat, currentLng, fav.latitude, fav.longitude)
                if (dist <= maxRadiusMeters) {
                    candidates.add(
                        DiscoveredZoneResult(
                            zoneOrLot = fav.name,
                            address = fav.notes,
                            distanceMeters = dist,
                            source = "Favorite Zone",
                            defaultDurationMinutes = fav.defaultDurationMinutes
                        )
                    )
                }
            }
        }

        // 2. Check Learned Real-Time Zones
        synchronized(learnedZones) {
            for (learned in learnedZones) {
                val dist = LocationHelper.distanceInMeters(currentLat, currentLng, learned.latitude, learned.longitude)
                if (dist <= maxRadiusMeters) {
                    candidates.add(
                        DiscoveredZoneResult(
                            zoneOrLot = learned.zoneOrLot,
                            address = learned.address,
                            distanceMeters = dist,
                            source = "Recently Detected Meter",
                            defaultDurationMinutes = learned.defaultDurationMinutes
                        )
                    )
                }
            }
        }

        // 3. Check Past Parking Sessions
        for (session in pastSessions) {
            val lat = session.parkedLatitude
            val lng = session.parkedLongitude
            val zone = session.zoneOrLot
            if (lat != null && lng != null && !zone.isNullOrBlank()) {
                val dist = LocationHelper.distanceInMeters(currentLat, currentLng, lat, lng)
                if (dist <= maxRadiusMeters) {
                    val durationMins = if (session.totalDurationMillis > 0) {
                        (session.totalDurationMillis / (60 * 1000L)).toInt().coerceIn(15, 360)
                    } else 60
                    candidates.add(
                        DiscoveredZoneResult(
                            zoneOrLot = zone,
                            address = session.locationAddress,
                            distanceMeters = dist,
                            source = "Previous Parking Spot",
                            defaultDurationMinutes = durationMins
                        )
                    )
                }
            }
        }

        // 4. Check Preloaded Municipal Registry
        for (known in PRELOADED_ZONES) {
            val dist = LocationHelper.distanceInMeters(currentLat, currentLng, known.latitude, known.longitude)
            if (dist <= maxRadiusMeters) {
                candidates.add(
                    DiscoveredZoneResult(
                        zoneOrLot = known.zoneOrLot,
                        address = known.address,
                        distanceMeters = dist,
                        source = "City Parking Registry",
                        defaultDurationMinutes = known.defaultDurationMinutes
                    )
                )
            }
        }

        return candidates.minByOrNull { it.distanceMeters }
    }

    /**
     * Resolves human-readable street address using native Android Geocoder.
     */
    suspend fun reverseGeocodeAddress(context: Context, lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var result: List<Address>? = null
                geocoder.getFromLocation(lat, lng, 1) { list -> result = list }
                result
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)
            }

            val address = addresses?.firstOrNull() ?: return@withContext null
            val thoroughfare = address.thoroughfare // e.g. "8 Ave SW"
            val subThoroughfare = address.subThoroughfare // e.g. "400"
            when {
                !subThoroughfare.isNullOrBlank() && !thoroughfare.isNullOrBlank() -> "$subThoroughfare $thoroughfare"
                !thoroughfare.isNullOrBlank() -> thoroughfare
                address.maxAddressLineIndex >= 0 -> address.getAddressLine(0)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Enriches zone discovery by fetching live MyParking rate information and max duration.
     */
    suspend fun fetchLiveZoneDetails(zoneOrLot: String): com.parktimedetector.network.ParkingZoneDetails {
        val cleanDigits = zoneOrLot.filter { it.isDigit() }
        val zoneNumber = if (cleanDigits.isNotEmpty()) cleanDigits else zoneOrLot
        return com.parktimedetector.network.MyParkingApiClient.getCombinedZoneDetails(zoneNumber, fallbackTitle = zoneOrLot)
    }
}
