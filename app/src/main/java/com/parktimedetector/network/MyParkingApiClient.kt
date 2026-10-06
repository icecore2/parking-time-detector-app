package com.parktimedetector.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Client for fetching and parsing live parking information from Calgary Parking / MyParking
 * endpoints when pins are pressed on the map.
 */
object MyParkingApiClient {

    private const val TAG = "MyParkingApiClient"

    private const val VPM_COST_URL = "https://vpm.parkplus.ca/resources/parkingcosts?zoneNumber="
    private const val ON_STREET_GIS_URL =
        "https://services1.arcgis.com/AVP60cs0Q9PEA8rH/arcgis/rest/services/Calgary_Parking_Authority_On_Street_Parking_Zones_with_Rates/FeatureServer/0/query"
    private const val LOTS_GIS_URL =
        "https://services1.arcgis.com/AVP60cs0Q9PEA8rH/arcgis/rest/services/City_Parking_Lots/FeatureServer/0/query"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Parses the custom ISO-like timestamp used by MyParking VPM API:
     * e.g., "20261006T105720-0600" -> Epoch Milliseconds.
     */
    fun parseVpmTimestamp(raw: String): Long? {
        return try {
            val format = SimpleDateFormat("yyyyMMdd'T'HHmmssZ", Locale.US)
            val date = format.parse(raw)
            date?.time
        } catch (_: Exception) {
            // Fallback: try parsing with colon in timezone offset if format varies
            try {
                val cleaned = if (raw.length == 22 && raw[19] == ':') {
                    raw.substring(0, 19) + raw.substring(20)
                } else raw
                val format = SimpleDateFormat("yyyyMMdd'T'HHmmssZ", Locale.US)
                format.parse(cleaned)?.time
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Parses the VPM /resources/parkingcosts JSON response body.
     */
    fun parseVpmResponse(jsonString: String): Result<Pair<String, List<CostDuration>>> {
        return try {
            if (jsonString.contains("Zone not found", ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("Zone not found."))
            }

            val json = JSONObject(jsonString)
            val address = json.optString("zoneAddress", "")
            val costArray = json.optJSONArray("costDurations") ?: JSONArray()
            val durations = mutableListOf<CostDuration>()

            for (i in 0 until costArray.length()) {
                val item = costArray.getJSONObject(i)
                val cost = item.optDouble("cost", 0.0)
                val duration = item.optInt("duration", 0)
                val endTimeRaw = item.optString("endTime", "")
                val parsedMillis = parseVpmTimestamp(endTimeRaw)

                durations.add(
                    CostDuration(
                        cost = cost,
                        durationMinutes = duration,
                        endTimeRaw = endTimeRaw,
                        parsedEndTimeMillis = parsedMillis
                    )
                )
            }

            Result.success(Pair(address, durations))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cleans and structures the HTML rate schedule string returned by the CPA GIS layer:
     * e.g., "<b>Mon-Fri 9:00 AM to 11:00 AM</b><br><br>$4.00 per Hour<br><br>..."
     */
    fun parseHtmlZoneRate(html: String?): List<String> {
        if (html.isNullOrBlank()) return emptyList()

        // Replace breaks with newlines and clean tags
        val text = html
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&nbsp;", " ")
            .trim()

        val rawLines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val structured = mutableListOf<String>()

        var pendingHeader: String? = null
        for (line in rawLines) {
            if (line.startsWith("$") || line.contains("free", ignoreCase = true) || line.contains("no charge", ignoreCase = true)) {
                if (pendingHeader != null) {
                    val header = pendingHeader.trim().trimEnd(':')
                    structured.add("$header: $line")
                    pendingHeader = null
                } else {
                    structured.add(line)
                }
            } else if (line.contains("Mon", ignoreCase = true) ||
                line.contains("Sat", ignoreCase = true) ||
                line.contains("Sun", ignoreCase = true) ||
                line.contains("Holiday", ignoreCase = true) ||
                line.contains("Free parking to", ignoreCase = true)
            ) {
                if (pendingHeader != null) {
                    structured.add(pendingHeader)
                }
                pendingHeader = line
            } else {
                if (pendingHeader != null) {
                    structured.add("$pendingHeader ($line)")
                    pendingHeader = null
                } else {
                    structured.add(line)
                }
            }
        }
        if (pendingHeader != null) {
            structured.add(pendingHeader)
        }
        return structured
    }

    /**
     * Parses the ArcGIS FeatureServer query JSON response.
     */
    fun parseArcGisFeatures(jsonString: String): Result<ZoneGisMetadata?> {
        return try {
            val json = JSONObject(jsonString)
            val features = json.optJSONArray("features") ?: return Result.success(null)
            if (features.length() == 0) return Result.success(null)

            val feature = features.getJSONObject(0)
            val attrs = feature.optJSONObject("attributes") ?: return Result.success(null)

            val zoneNumber = attrs.opt("PARKING_ZONE")?.toString()
                ?: attrs.optString("LOTNUMBER", "")
            val address = attrs.optString("ADDRESS_DESC", attrs.optString("ADDRESS1", ""))
            val maxTime = if (attrs.has("MAX_TIME") && !attrs.isNull("MAX_TIME")) attrs.getInt("MAX_TIME") else null
            val enforceableTime = attrs.optString("ENFORCEABLE_TIME", "")
            val stallType = attrs.optString("STALL_TYPE", attrs.optString("TYPE", ""))
            val zoneType = attrs.optString("ZONE_TYPE", "")
            val rawHtmlRate = attrs.optString("HTML_ZONE_RATE", "")

            val parsedSchedule = parseHtmlZoneRate(rawHtmlRate)
            val rateSummary = parsedSchedule.firstOrNull { it.contains("$") }

            Result.success(
                ZoneGisMetadata(
                    parkingZone = zoneNumber,
                    addressDesc = address,
                    maxTimeMinutes = maxTime,
                    enforceableTime = enforceableTime.ifBlank { null },
                    stallType = stallType.ifBlank { null },
                    zoneType = zoneType.ifBlank { null },
                    rawHtmlRate = rawHtmlRate.ifBlank { null },
                    parsedRatesSummary = rateSummary
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executes GET request to MyParking VPM costs API.
     */
    suspend fun fetchZoneCost(zoneNumber: String): Result<Pair<String, List<CostDuration>>> = withContext(Dispatchers.IO) {
        try {
            val url = "$VPM_COST_URL$zoneNumber"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ParkingTimeDetector-Android/1.0")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("VPM API returned HTTP ${response.code}: $body")
                    )
                }
                parseVpmResponse(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching VPM costs for zone $zoneNumber: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Executes GET request to Calgary Parking Authority ArcGIS FeatureServer for zone metadata.
     */
    suspend fun fetchArcGisMetadata(zoneNumber: String): Result<ZoneGisMetadata?> = withContext(Dispatchers.IO) {
        try {
            // 1. Try On-Street feature layer
            val cleanZone = zoneNumber.filter { it.isDigit() }
            val onStreetUrl = "$ON_STREET_GIS_URL?where=PARKING_ZONE%3D$cleanZone&outFields=*&f=json"
            val req1 = Request.Builder().url(onStreetUrl).build()

            val onStreetResult = try {
                httpClient.newCall(req1).execute().use { res ->
                    if (res.isSuccessful) parseArcGisFeatures(res.body?.string().orEmpty()).getOrNull() else null
                }
            } catch (_: Exception) {
                null
            }

            if (onStreetResult != null) {
                return@withContext Result.success(onStreetResult)
            }

            // 2. Try Off-Street Lots feature layer (e.g. 9058 -> lot 58)
            val lotQueryNum = if (cleanZone.startsWith("90") && cleanZone.length == 4) {
                cleanZone.substring(2)
            } else {
                cleanZone
            }

            val lotUrl = "$LOTS_GIS_URL?where=LOTNUMBER%3D%27$lotQueryNum%27&outFields=*&f=json"
            val req2 = Request.Builder().url(lotUrl).build()

            val lotResult = httpClient.newCall(req2).execute().use { res ->
                if (res.isSuccessful) parseArcGisFeatures(res.body?.string().orEmpty()).getOrNull() else null
            }

            Result.success(lotResult)
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching ArcGIS metadata for zone $zoneNumber: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetches and merges live MyParking information for a pin press,
     * providing transparent fallback if the network is unavailable.
     */
    suspend fun getCombinedZoneDetails(
        zoneNumber: String,
        fallbackTitle: String? = null,
        fallbackAddress: String? = null
    ): ParkingZoneDetails = withContext(Dispatchers.IO) {
        val costResult = fetchZoneCost(zoneNumber)
        val gisResult = fetchArcGisMetadata(zoneNumber).getOrNull()

        if (costResult.isSuccess) {
            val (vpmAddress, durations) = costResult.getOrThrow()
            val resolvedAddress = vpmAddress.ifBlank { gisResult?.addressDesc ?: fallbackAddress ?: "Calgary, AB" }
            val hourlyEst = durations.firstOrNull { it.durationMinutes in 30..60 }?.let {
                val rate = (it.cost / (it.durationMinutes / 60.0))
                String.format(Locale.US, "$%.2f / hr", rate)
            } ?: gisResult?.parsedRatesSummary ?: "$3.00 / hr"

            val maxTime = gisResult?.maxTimeMinutes ?: durations.maxOfOrNull { it.durationMinutes } ?: 120
            val schedule = parseHtmlZoneRate(gisResult?.rawHtmlRate)

            ParkingZoneDetails(
                zoneNumber = zoneNumber,
                nameOrTitle = fallbackTitle ?: "Zone $zoneNumber",
                address = resolvedAddress,
                hourlyRateEstimate = hourlyEst,
                maxTimeMinutes = maxTime,
                enforceableTime = gisResult?.enforceableTime ?: "09:00 - 18:00 MON-SAT",
                stallType = gisResult?.stallType,
                costDurations = durations,
                rateScheduleLines = schedule,
                source = "MyParking Live (VPM + CPA GIS)",
                isFallback = false
            )
        } else {
            // Generate fallback data gracefully for offline / mock testing
            createFallbackZoneDetails(zoneNumber, fallbackTitle, fallbackAddress, gisResult)
        }
    }

    /**
     * Fallback generator when device is offline or simulated in air-gapped environments.
     */
    private fun createFallbackZoneDetails(
        zoneNumber: String,
        fallbackTitle: String?,
        fallbackAddress: String?,
        gisMeta: ZoneGisMetadata?
    ): ParkingZoneDetails {
        val now = System.currentTimeMillis()
        val defaultRates = listOf(
            CostDuration(1.50, 30, "30m", now + 30 * 60 * 1000L),
            CostDuration(3.00, 60, "60m", now + 60 * 60 * 1000L),
            CostDuration(6.00, 120, "120m", now + 120 * 60 * 1000L)
        )

        val (addr, title, maxTime, estRate) = when (zoneNumber) {
            "9058", "58" -> Quad(
                "Lot 58 - 935 - 4 Av SW",
                "Lot 58 (West Downtown)",
                120,
                "$3.00 / hr"
            )
            "1008" -> Quad(
                "Eau Claire Av SW, Fr 4 St SW To 5 St SW",
                "Zone 1008 (Eau Claire)",
                180,
                "$4.00 / hr"
            )
            "1205" -> Quad(
                "Kensington Rd & 10 St NW",
                "Zone 1205 (Kensington)",
                180,
                "$3.00 / hr"
            )
            "4022" -> Quad(
                "8 Av SW & 4 St SW (Downtown 8th Ave)",
                "Zone 4022 (Downtown)",
                120,
                "$4.25 / hr"
            )
            else -> Quad(
                gisMeta?.addressDesc ?: fallbackAddress ?: "Calgary Zone $zoneNumber",
                fallbackTitle ?: "Zone $zoneNumber",
                gisMeta?.maxTimeMinutes ?: 120,
                gisMeta?.parsedRatesSummary ?: "$3.00 / hr"
            )
        }

        return ParkingZoneDetails(
            zoneNumber = zoneNumber,
            nameOrTitle = title,
            address = addr,
            hourlyRateEstimate = estRate,
            maxTimeMinutes = maxTime,
            enforceableTime = gisMeta?.enforceableTime ?: "09:00 - 18:00 MON-SAT",
            stallType = gisMeta?.stallType ?: "Parallel",
            costDurations = defaultRates,
            rateScheduleLines = listOf("Standard Calgary Parking Rates apply", "Mon-Sat: $estRate", "Sun/Holidays: Free"),
            source = "MyParking Local Cache (Offline Fallback)",
            isFallback = true
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
