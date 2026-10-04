package com.parktimedetector.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AppLogger {

    private const val DEFAULT_TAG = "ParkingDetectorLog"
    private val scope = CoroutineScope(Dispatchers.IO)

    fun info(context: Context, tag: String, message: String, packageName: String? = null, rawData: String? = null) {
        log(context, "INFO", tag, message, packageName, rawData)
    }

    fun success(context: Context, tag: String, message: String, packageName: String? = null, rawData: String? = null) {
        log(context, "SUCCESS", tag, message, packageName, rawData)
    }

    fun warn(context: Context, tag: String, message: String, packageName: String? = null, rawData: String? = null) {
        log(context, "WARN", tag, message, packageName, rawData)
    }

    fun error(context: Context, tag: String, message: String, packageName: String? = null, rawData: String? = null) {
        log(context, "ERROR", tag, message, packageName, rawData)
    }

    fun log(
        context: Context,
        level: String,
        tag: String,
        message: String,
        packageName: String? = null,
        rawData: String? = null
    ) {
        when (level) {
            "ERROR" -> Log.e(DEFAULT_TAG, "[$tag] $message (pkg: $packageName)")
            "WARN" -> Log.w(DEFAULT_TAG, "[$tag] $message (pkg: $packageName)")
            else -> Log.d(DEFAULT_TAG, "[$tag] $message (pkg: $packageName)")
        }

        scope.launch {
            try {
                val db = ParkingDatabase.getDatabase(context)
                db.logDao().insert(
                    LogEntry(
                        level = level,
                        tag = tag,
                        packageName = packageName,
                        message = message,
                        rawData = rawData
                    )
                )
                db.logDao().pruneOldLogs()
            } catch (e: Exception) {
                Log.e(DEFAULT_TAG, "Failed to persist log", e)
            }
        }
    }
}
