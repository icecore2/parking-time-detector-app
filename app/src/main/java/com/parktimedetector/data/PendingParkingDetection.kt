package com.parktimedetector.data

data class PendingParkingDetection(
    val packageName: String,
    val appName: String,
    val zoneOrLot: String?,
    val endTimeMillis: Long,
    val source: String,
    val reason: String,
    val rawData: String? = null,
    val detectedAtMillis: Long = System.currentTimeMillis(),
    val locationAddress: String? = null,
    val purchasedDurationText: String? = null,
    val initialCostText: String? = null,
    val remainingTimeText: String? = null,
    val notesText: String? = null,
    val startTimeMillis: Long = detectedAtMillis
) {
    val displayLocation: String
        get() = when {
            !zoneOrLot.isNullOrBlank() && !locationAddress.isNullOrBlank() -> "$zoneOrLot • $locationAddress"
            !zoneOrLot.isNullOrBlank() -> zoneOrLot
            !locationAddress.isNullOrBlank() -> locationAddress
            else -> "Parking Session"
        }

    val durationMinutes: Long
        get() = ((endTimeMillis - startTimeMillis) / (60 * 1000L)).coerceAtLeast(1L)
}
