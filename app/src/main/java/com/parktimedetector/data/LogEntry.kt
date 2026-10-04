package com.parktimedetector.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detection_logs")
data class LogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMillis: Long = System.currentTimeMillis(),
    val level: String = "INFO", // "INFO", "SUCCESS", "WARN", "ERROR"
    val tag: String, // "NOTIF_RECEIVED", "PARSE_SUCCESS", "PARSE_FAILED", "SERVICE_STATUS", "SYSTEM"
    val packageName: String? = null,
    val message: String,
    val rawData: String? = null
)
