package com.parktimedetector.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM detection_logs ORDER BY timestampMillis DESC LIMIT 300")
    fun getLogsFlow(): Flow<List<LogEntry>>

    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Query("DELETE FROM detection_logs")
    suspend fun clearAll()

    @Query("DELETE FROM detection_logs WHERE id NOT IN (SELECT id FROM detection_logs ORDER BY timestampMillis DESC LIMIT 300)")
    suspend fun pruneOldLogs()
}
