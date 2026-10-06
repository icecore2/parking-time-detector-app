package com.parktimedetector.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parking_sessions")
data class ParkingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val source: String, // e.g. "ParkedIn App" or "Manual Timer"
    val zoneOrLot: String?, // e.g. "Zone 4022", "Lot B"
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val advanceWarningMinutes: Int = 10,
    val isActive: Boolean = true,
    val isNotifiedAdvance: Boolean = false,
    val isNotifiedCritical: Boolean = false,
    val isNotifiedExpiry: Boolean = false,
    val rawNotificationText: String? = null,
    val actualStopTimeMillis: Long? = null,
    val costOrRefundText: String? = null,
    val locationAddress: String? = null, // e.g. "50 Av SW , Fr 6 St SW To ELBOW Dr SW"
    val purchasedDurationText: String? = null, // e.g. "20 mins."
    val initialCostText: String? = null, // e.g. "$.00"
    val remainingTimeText: String? = null, // e.g. "19 mins : 57 secs"
    val notesText: String? = null,
    val stopReason: String? = null
) {
    val displayLocation: String
        get() = when {
            !zoneOrLot.isNullOrBlank() && !locationAddress.isNullOrBlank() -> "$zoneOrLot • $locationAddress"
            !zoneOrLot.isNullOrBlank() -> zoneOrLot
            !locationAddress.isNullOrBlank() -> locationAddress
            else -> "Parking Session"
        }

    val totalDurationMillis: Long
        get() = (endTimeMillis - startTimeMillis).coerceAtLeast(0L)

    val actualDurationMillis: Long
        get() {
            val stop = actualStopTimeMillis ?: (if (!isActive) System.currentTimeMillis() else endTimeMillis)
            return (stop - startTimeMillis).coerceAtLeast(0L)
        }

    fun remainingMillis(now: Long = System.currentTimeMillis()): Long {
        return (endTimeMillis - now).coerceAtLeast(0L)
    }

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return now >= endTimeMillis
    }

    fun isInAdvanceWarningZone(now: Long = System.currentTimeMillis()): Boolean {
        val warningThreshold = endTimeMillis - (advanceWarningMinutes * 60 * 1000L)
        return now >= warningThreshold && !isExpired(now)
    }

    fun isInCriticalZone(now: Long = System.currentTimeMillis(), criticalMinutes: Int = 2): Boolean {
        val criticalThreshold = endTimeMillis - (criticalMinutes * 60 * 1000L)
        return now >= criticalThreshold && !isExpired(now)
    }
}
