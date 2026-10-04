package com.parktimedetector.data

data class PendingParkingStopDetection(
    val packageName: String,
    val appName: String,
    val zoneOrLot: String?,
    val stopTimeMillis: Long,
    val durationParkedText: String?,
    val costOrRefundText: String?,
    val source: String,
    val reason: String,
    val rawData: String? = null,
    val detectedAtMillis: Long = System.currentTimeMillis(),
    val locationAddress: String? = null,
    val stopReason: String? = null
) {
    val displayLocation: String
        get() = when {
            !zoneOrLot.isNullOrBlank() && !locationAddress.isNullOrBlank() -> "$zoneOrLot • $locationAddress"
            !zoneOrLot.isNullOrBlank() -> zoneOrLot
            !locationAddress.isNullOrBlank() -> locationAddress
            else -> "Parking Session"
        }
}
