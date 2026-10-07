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
 * endpoints when pins are pressed on the map, with full attribute parsing and cheapest-rate discovery.
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
     * Extracts the lowest/cheapest price per zone from its rate schedule, zone type, and VPM ladder.
     */
    fun extractCheapestPrice(
        rawHtmlRate: String?,
        costDurations: List<CostDuration> = emptyList(),
        zoneType: String? = null
    ): CheapestPriceInfo {
        val lowerType = zoneType?.lowercase() ?: ""
        val lowerRate = rawHtmlRate?.lowercase() ?: ""

        // 1. Check for Free / Loading Zone
        if (lowerType.contains("loading") ||
            lowerRate.contains("free 20") ||
            lowerRate.contains("free 1 h") ||
            lowerRate.contains("free 2 h") ||
            lowerRate.contains("free 3 h") ||
            lowerRate.contains("no charge")
        ) {
            val desc = when {
                lowerRate.contains("free 20") -> "Free 20m Loading Zone"
                lowerRate.contains("free 2 h") -> "Free 2 Hours"
                lowerRate.contains("free 1 h") -> "Free 1 Hour"
                lowerRate.contains("no charge") -> "Free / No Charge"
                else -> "Free Loading Period"
            }
            return CheapestPriceInfo(
                displayPrice = "FREE",
                numericRatePerHour = 0.0,
                isFree = true,
                rateDescription = desc
            )
        }

        // 2. Extract numeric dollar rates per hour from HTML schedule (e.g. "$1.00 per Hour", "$3.50/hr", "$0.75")
        val hourlyRegex = Regex("""\$(\d+(?:\.\d{2})?)\s*(?:per\s*(?:Hour|hr)|\/hr)""", RegexOption.IGNORE_CASE)
        val dollarRates = hourlyRegex.findAll(rawHtmlRate ?: "")
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .toList()

        if (dollarRates.isNotEmpty()) {
            val minRate = dollarRates.minOrNull() ?: 3.00
            val formatted = String.format(Locale.US, "$%.2f / hr", minRate)
            return CheapestPriceInfo(
                displayPrice = formatted,
                numericRatePerHour = minRate,
                isFree = minRate == 0.0,
                rateDescription = if (minRate <= 1.50) "Weekend / Off-Peak Rate" else "Standard Rate"
            )
        }

        // 3. Fallback to VPM minimum cost or duration rates
        if (costDurations.isNotEmpty()) {
            val valid = costDurations.filter { it.durationMinutes > 0 }
            val minHourly = valid.map { (it.cost / (it.durationMinutes / 60.0)) }.minOrNull()
            if (minHourly != null) {
                val formatted = String.format(Locale.US, "$%.2f / hr", minHourly)
                return CheapestPriceInfo(
                    displayPrice = formatted,
                    numericRatePerHour = minHourly,
                    isFree = minHourly == 0.0,
                    rateDescription = "Computed Live Rate"
                )
            }
        }

        return CheapestPriceInfo(
            displayPrice = "$3.00 / hr",
            numericRatePerHour = 3.00,
            isFree = false,
            rateDescription = "Standard Rate"
        )
    }

    /**
     * Overload that extracts cheapest price directly from a [ZoneGisMetadata] object.
     */
    fun extractCheapestPrice(
        meta: ZoneGisMetadata,
        costDurations: List<CostDuration> = emptyList()
    ): CheapestPriceInfo {
        return extractCheapestPrice(
            rawHtmlRate = meta.rawHtmlRate ?: meta.parsedRatesSummary,
            costDurations = costDurations,
            zoneType = meta.zoneType
        )
    }

    /**
     * Parses all target keys from an ArcGIS feature attributes JSON object:
     * ADDRESS_DESC, PARKING_ZONE, ZONE_TYPE, STALL_TYPE, MAX_TIME, ENFORCEABLE_TIME,
     * HTML_ZONE_RATE, ZONE_CAP, SEG_CAP, ZONE_LENGTH, SEG_LENGTH, PRICE_ZONE, BRZ_NAME,
     * BLOCK_SIDE, STATUS, COMMENTS, HOME_PAGE, OBJECTID, GLOBALID, etc.
     */
    fun parseArcGisFeatureAttributes(attrs: JSONObject): ZoneGisMetadata {
        val zoneNumber = attrs.opt("PARKING_ZONE")?.toString()
            ?: attrs.optString("LOTNUMBER", "")
        val address = attrs.optString("ADDRESS_DESC", attrs.optString("ADDRESS1", ""))
        val maxTime = if (attrs.has("MAX_TIME") && !attrs.isNull("MAX_TIME")) attrs.getInt("MAX_TIME") else null
        val enforceableTime = attrs.optString("ENFORCEABLE_TIME", "")
        val stallType = attrs.optString("STALL_TYPE", attrs.optString("TYPE", ""))
        val zoneType = attrs.optString("ZONE_TYPE", "")
        val rawHtmlRate = attrs.optString("HTML_ZONE_RATE", "")

        val zoneCap = if (attrs.has("ZONE_CAP") && !attrs.isNull("ZONE_CAP")) attrs.getInt("ZONE_CAP") else null
        val segCap = if (attrs.has("SEG_CAP") && !attrs.isNull("SEG_CAP")) attrs.getInt("SEG_CAP") else null
        val zoneLen = if (attrs.has("ZONE_LENGTH") && !attrs.isNull("ZONE_LENGTH")) attrs.getDouble("ZONE_LENGTH") else null
        val segLen = if (attrs.has("SEG_LENGTH") && !attrs.isNull("SEG_LENGTH")) attrs.getDouble("SEG_LENGTH") else null

        val priceZone = attrs.optString("PRICE_ZONE", "")
        val brzName = attrs.optString("BRZ_NAME", "")
        val blockSide = attrs.optString("BLOCK_SIDE", "")
        val status = attrs.optString("STATUS", "")
        val comments = attrs.optString("COMMENTS", "")
        val restrictTime = attrs.optString("PARKING_RESTRICT_TIME", "")
        val restrictType = attrs.optString("PARKING_RESTRICT_TYPE", "")
        val homePage = attrs.optString("HOME_PAGE", "")
        val objId = if (attrs.has("OBJECTID") && !attrs.isNull("OBJECTID")) attrs.getLong("OBJECTID") else null
        val globalId = attrs.optString("GLOBALID", attrs.optString("GLOBALID_GUID", ""))
        val dot = attrs.optString("DOT", "")
        val camera = attrs.optString("CAMERA", "")

        val parsedSchedule = parseHtmlZoneRate(rawHtmlRate)
        val rateSummary = parsedSchedule.firstOrNull { it.contains("$") }

        return ZoneGisMetadata(
            parkingZone = zoneNumber,
            addressDesc = address.ifBlank { null },
            zoneType = zoneType.ifBlank { null },
            stallType = stallType.ifBlank { null },
            maxTimeMinutes = maxTime,
            enforceableTime = enforceableTime.ifBlank { null },
            rawHtmlRate = rawHtmlRate.ifBlank { null },
            parsedRatesSummary = rateSummary,
            zoneCapacity = zoneCap,
            segmentCapacity = segCap,
            zoneLengthMeters = zoneLen,
            segmentLengthMeters = segLen,
            priceZone = priceZone.ifBlank { null },
            brzName = brzName.ifBlank { null },
            blockSide = blockSide.ifBlank { null },
            status = status.ifBlank { null },
            comments = comments.ifBlank { null },
            parkingRestrictTime = restrictTime.ifBlank { null },
            parkingRestrictType = restrictType.ifBlank { null },
            homePageUrl = homePage.ifBlank { null },
            objectId = objId,
            globalId = globalId.ifBlank { null },
            dot = dot.ifBlank { null },
            camera = camera.ifBlank { null }
        )
    }

    /**
     * Parses the ArcGIS FeatureServer query JSON response into a list of ZoneGisMetadata.
     */
    fun parseArcGisFeaturesList(jsonString: String): Result<List<ZoneGisMetadata>> {
        return try {
            val json = JSONObject(jsonString)
            val features = json.optJSONArray("features") ?: return Result.success(emptyList())
            val list = mutableListOf<ZoneGisMetadata>()
            for (i in 0 until features.length()) {
                val f = features.getJSONObject(i)
                val attrs = f.optJSONObject("attributes") ?: continue
                list.add(parseArcGisFeatureAttributes(attrs))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Backward-compatible helper to parse the first feature from ArcGIS response.
     */
    fun parseArcGisFeatures(jsonString: String): Result<ZoneGisMetadata?> {
        val listResult = parseArcGisFeaturesList(jsonString)
        return listResult.map { it.firstOrNull() }
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
            val cheapest = extractCheapestPrice(gisResult?.rawHtmlRate, durations, gisResult?.zoneType)
            val hourlyEst = cheapest.displayPrice

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
                zoneType = gisResult?.zoneType,
                zoneCapacity = gisResult?.zoneCapacity,
                brzName = gisResult?.brzName,
                costDurations = durations,
                rateScheduleLines = schedule,
                cheapestPrice = cheapest,
                gisMetadata = gisResult,
                source = "MyParking Live (VPM + CPA GIS)",
                isFallback = false
            )
        } else {
            createFallbackZoneDetails(zoneNumber, fallbackTitle, fallbackAddress, gisResult)
        }
    }

    /**
     * Fetches nearby parking zones from Calgary Parking Authority ArcGIS FeatureServer,
     * calculates the cheapest price for each, and returns the list sorted by cheapest price.
     */
    suspend fun fetchNearbyZonesWithPrices(
        lat: Double = 51.0486,
        lng: Double = -114.0708,
        radiusMeters: Int = 450
    ): List<ZoneWithPrice> = withContext(Dispatchers.IO) {
        val spatialUrl = "$ON_STREET_GIS_URL?geometry=$lng,$lat&geometryType=esriGeometryPoint&inSR=4326&spatialRel=esriSpatialRelIntersects&distance=$radiusMeters&units=esriSRUnit_Meter&outFields=*&resultRecordCount=20&f=json"
        val req = Request.Builder().url(spatialUrl).build()

        val parsedGisList = try {
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) parseArcGisFeaturesList(res.body?.string().orEmpty()).getOrNull() ?: emptyList()
                else emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

        if (parsedGisList.isNotEmpty()) {
            val zones = parsedGisList.map { meta ->
                val cheapest = extractCheapestPrice(meta.rawHtmlRate, emptyList(), meta.zoneType)
                ZoneWithPrice(
                    zoneNumber = meta.parkingZone,
                    nameOrTitle = meta.zoneType ?: "Zone ${meta.parkingZone}",
                    address = meta.addressDesc ?: "Calgary Street Parking",
                    zoneType = meta.zoneType,
                    stallType = meta.stallType,
                    maxTimeMinutes = meta.maxTimeMinutes,
                    enforceableTime = meta.enforceableTime,
                    cheapestPrice = cheapest,
                    zoneCapacity = meta.zoneCapacity,
                    brzName = meta.brzName,
                    blockSide = meta.blockSide,
                    isLot = false,
                    rawMetadata = meta
                )
            }
            // Sort by Cheapest price first (FREE first, then ascending rate)
            return@withContext zones.sortedWith(
                compareBy<ZoneWithPrice> { it.cheapestPrice.numericRatePerHour }
                    .thenBy { it.zoneNumber }
            )
        }

        // Return offline fallback zones sorted by cheapest price
        return@withContext getFallbackNearbyZonesSorted()
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

        val (addr, title, maxTime, estRate, zType, cap, brz) = when (zoneNumber) {
            "1747" -> Sept(
                "5 Av SW, Fr 3 St SW To 4 St SW",
                "Loading zone",
                20,
                "FREE",
                "Loading zone",
                3,
                "Downtown"
            )
            "9058", "58" -> Sept(
                "Lot 58 - 935 - 4 Av SW",
                "Lot 58 (West Downtown)",
                120,
                "$3.00 / hr",
                "Surface Lot",
                85,
                "Downtown West"
            )
            "1008" -> Sept(
                "Eau Claire Av SW, Fr 4 St SW To 5 St SW",
                "Zone 1008 (Eau Claire)",
                180,
                "$4.00 / hr",
                "Parking Zone",
                11,
                "Eau Claire"
            )
            "1205" -> Sept(
                "Kensington Rd & 10 St NW",
                "Zone 1205 (Kensington)",
                180,
                "$3.00 / hr",
                "Parking Zone",
                14,
                "Kensington"
            )
            "4022" -> Sept(
                "8 Av SW & 4 St SW (Downtown 8th Ave)",
                "Zone 4022 (Downtown)",
                120,
                "$4.25 / hr",
                "Parking Zone",
                8,
                "Downtown"
            )
            else -> Sept(
                gisMeta?.addressDesc ?: fallbackAddress ?: "Calgary Zone $zoneNumber",
                fallbackTitle ?: "Zone $zoneNumber",
                gisMeta?.maxTimeMinutes ?: 120,
                gisMeta?.parsedRatesSummary ?: "$3.00 / hr",
                gisMeta?.zoneType ?: "Parking Zone",
                gisMeta?.zoneCapacity ?: 10,
                gisMeta?.brzName ?: "Calgary"
            )
        }

        val cheapest = extractCheapestPrice(estRate, defaultRates, zType)

        return ParkingZoneDetails(
            zoneNumber = zoneNumber,
            nameOrTitle = title,
            address = addr,
            hourlyRateEstimate = estRate,
            maxTimeMinutes = maxTime,
            enforceableTime = gisMeta?.enforceableTime ?: "09:00 - 18:00 MON-SAT",
            stallType = gisMeta?.stallType ?: "Parallel",
            zoneType = zType,
            zoneCapacity = cap,
            brzName = brz,
            costDurations = defaultRates,
            rateScheduleLines = listOf("Standard Calgary Parking Rates apply", "Mon-Sat: $estRate", "Sun/Holidays: Free"),
            cheapestPrice = cheapest,
            gisMetadata = gisMeta,
            source = "MyParking Local Cache (Offline Fallback)",
            isFallback = true
        )
    }

    /**
     * Built-in ranked fallback zones list for offline and test preview.
     */
    fun getFallbackNearbyZonesSorted(): List<ZoneWithPrice> {
        val list = listOf(
            ZoneWithPrice(
                zoneNumber = "1747",
                nameOrTitle = "Loading Zone (5 Av SW)",
                address = "5 Av SW, Fr 3 St SW To 4 St SW",
                zoneType = "Loading zone",
                stallType = "Parallel",
                maxTimeMinutes = 20,
                enforceableTime = "0900-1800 MON-SAT",
                cheapestPrice = CheapestPriceInfo("FREE", 0.0, isFree = true, rateDescription = "Free 20m Loading Zone"),
                zoneCapacity = 3,
                brzName = "Downtown",
                blockSide = "S",
                distanceMeters = 80
            ),
            ZoneWithPrice(
                zoneNumber = "1737",
                nameOrTitle = "Zone 1737 (4 St SW)",
                address = "4 St SW, Fr 5 Av SW To 6 Av SW",
                zoneType = "Parking Zone",
                stallType = "Parallel",
                maxTimeMinutes = 120,
                enforceableTime = "0900-1800 MON-SAT",
                cheapestPrice = CheapestPriceInfo("$1.50 / hr", 1.50, isFree = false, rateDescription = "Off-Peak / Weekend Rate"),
                zoneCapacity = 14,
                brzName = "Downtown",
                blockSide = "W",
                distanceMeters = 150
            ),
            ZoneWithPrice(
                zoneNumber = "9058",
                nameOrTitle = "Lot 58 (West Downtown)",
                address = "935 4 Av SW",
                zoneType = "Surface Lot",
                stallType = "Angle",
                maxTimeMinutes = 120,
                enforceableTime = "24/7 Mon-Sun",
                cheapestPrice = CheapestPriceInfo("$3.00 / hr", 3.00, isFree = false, rateDescription = "Flat Lot Rate"),
                zoneCapacity = 85,
                brzName = "Downtown West",
                isLot = true,
                distanceMeters = 230
            ),
            ZoneWithPrice(
                zoneNumber = "1550",
                nameOrTitle = "Zone 1550 (5 Av SW)",
                address = "5 Av SW, Fr 3 St SW To 4 St SW",
                zoneType = "Parking Zone",
                stallType = "Parallel",
                maxTimeMinutes = 120,
                enforceableTime = "0830-1530 MON-FRI",
                cheapestPrice = CheapestPriceInfo("$3.75 / hr", 3.75, isFree = false, rateDescription = "Standard Street Meter"),
                zoneCapacity = 16,
                brzName = "Downtown",
                blockSide = "N",
                distanceMeters = 90
            ),
            ZoneWithPrice(
                zoneNumber = "1008",
                nameOrTitle = "Zone 1008 (Eau Claire)",
                address = "Eau Claire Av SW, Fr 4 St SW To 5 St SW",
                zoneType = "Parking Zone",
                stallType = "Parallel",
                maxTimeMinutes = 180,
                enforceableTime = "0910-1750 MON-SAT",
                cheapestPrice = CheapestPriceInfo("$4.00 / hr", 4.00, isFree = false, rateDescription = "Premium Waterfront Zone"),
                zoneCapacity = 11,
                brzName = "Eau Claire",
                blockSide = "N",
                distanceMeters = 340
            )
        )
        return list.sortedWith(
            compareBy<ZoneWithPrice> { it.cheapestPrice.numericRatePerHour }
                .thenBy { it.distanceMeters ?: 999 }
        )
    }

    private data class Sept<A, B, C, D, E, F, G>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F,
        val seventh: G
    )
}
