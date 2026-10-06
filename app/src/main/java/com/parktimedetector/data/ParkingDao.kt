package com.parktimedetector.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkingDao {
    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 ORDER BY endTimeMillis DESC LIMIT 1")
    fun getActiveSessionFlow(): Flow<ParkingSession?>

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 ORDER BY endTimeMillis DESC LIMIT 1")
    suspend fun getActiveSession(): ParkingSession?

    @Query("SELECT * FROM parking_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): ParkingSession?

    @Query("SELECT * FROM parking_sessions ORDER BY startTimeMillis DESC")
    fun getAllSessionsFlow(): Flow<List<ParkingSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ParkingSession): Long

    @Update
    suspend fun update(session: ParkingSession)

    @Query("UPDATE parking_sessions SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAllSessions()

    @Query("UPDATE parking_sessions SET isActive = 0 WHERE id = :id")
    suspend fun deactivateSession(id: Long)

    @Query("UPDATE parking_sessions SET isActive = 0, actualStopTimeMillis = :stopTime, costOrRefundText = :costOrRefund WHERE id = :id")
    suspend fun endSessionWithDetails(id: Long, stopTime: Long, costOrRefund: String?)

    @Query("UPDATE parking_sessions SET isActive = 0, actualStopTimeMillis = :stopTime, costOrRefundText = COALESCE(:costOrRefund, costOrRefundText), stopReason = :stopReason, locationAddress = COALESCE(:locationAddress, locationAddress) WHERE id = :id")
    suspend fun endSessionWithFullDetails(id: Long, stopTime: Long, costOrRefund: String?, stopReason: String?, locationAddress: String? = null)

    @Query("UPDATE parking_sessions SET spotDetails = :spotDetails WHERE id = :id")
    suspend fun updateSpotDetails(id: Long, spotDetails: String?)

    @Query("UPDATE parking_sessions SET parkedLatitude = :lat, parkedLongitude = :lng WHERE id = :id")
    suspend fun updateParkedCoordinates(id: Long, lat: Double?, lng: Double?)

    @Query("UPDATE parking_sessions SET calendarEventId = :eventId WHERE id = :id")
    suspend fun updateCalendarEventId(id: Long, eventId: Long?)

    @Query("SELECT * FROM parking_sessions WHERE parkedLatitude IS NOT NULL AND parkedLongitude IS NOT NULL ORDER BY startTimeMillis DESC LIMIT 50")
    suspend fun getSessionsWithCoordinates(): List<ParkingSession>

    @Query("SELECT * FROM parking_sessions WHERE (packageName = :packageName OR :packageName IS NULL) AND (:zoneOrLot IS NULL OR zoneOrLot = :zoneOrLot) AND startTimeMillis >= :minStartTime ORDER BY startTimeMillis DESC LIMIT 1")
    suspend fun findMatchingRecentSession(packageName: String, zoneOrLot: String?, minStartTime: Long): ParkingSession?

    @Query("SELECT * FROM parking_sessions ORDER BY startTimeMillis DESC LIMIT 1")
    suspend fun getLatestSession(): ParkingSession?

    @Query("SELECT * FROM parking_sessions ORDER BY startTimeMillis DESC LIMIT 1")
    fun getLatestSessionFlow(): Flow<ParkingSession?>

    @Delete
    suspend fun delete(session: ParkingSession)

    @Query("DELETE FROM parking_sessions")
    suspend fun clearAll()
}
