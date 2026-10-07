package com.parktimedetector.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.parktimedetector.data.AppLogger
import com.parktimedetector.data.ParkingDatabase
import com.parktimedetector.data.PendingParkingDetection
import com.parktimedetector.data.PendingParkingStopDetection
import com.parktimedetector.notification.NotificationHelper
import com.parktimedetector.service.DetectionApprovalManager
import com.parktimedetector.service.ParkingAlarmScheduler
import com.parktimedetector.service.SessionNotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BroadcastReceiver for local development, emulator automation, and CI testing.
 * Allows firing mock parking sessions, screen text captures, stop receipts, and alarms
 * via ADB commands:
 *
 * Example:
 * adb shell am broadcast -a com.parktimedetector.action.SIMULATE --es type start --es app parkedin --ei minutes 45
 */
class SimulationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SIMULATE = "com.parktimedetector.action.SIMULATE"
        private const val TAG = "SimulationReceiver"
        private val receiverScope = CoroutineScope(Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || (intent.action != ACTION_SIMULATE && intent.action != "com.parktimedetector.SIMULATE")) {
            return
        }

        val type = (intent.getStringExtra("type") ?: intent.getStringExtra("action_type") ?: "start").lowercase(Locale.ROOT)
        val appRaw = intent.getStringExtra("app") ?: intent.getStringExtra("package") ?: "parkedin"
        val resolvedPkg = when {
            appRaw.contains("myparking", ignoreCase = true) || appRaw.contains("cpa", ignoreCase = true) ->
                NotificationHelper.MYPARKING_PACKAGE
            else -> NotificationHelper.PARKEDIN_PACKAGE
        }
        val appName = NotificationHelper.getAppNameForPackage(resolvedPkg)

        Log.i(TAG, "Received local simulation broadcast: type=$type, app=$appName")

        receiverScope.launch {
            try {
                when (type) {
                    "start", "session_start" -> handleSimulateStart(context, intent, resolvedPkg, appName)
                    "stop", "session_stop" -> handleSimulateStop(context, intent, resolvedPkg, appName)
                    "screen", "screen_text" -> handleSimulateScreen(context, intent, resolvedPkg, appName)
                    "stop_screen", "stopscreen" -> handleSimulateStopScreen(context, intent, resolvedPkg, appName)
                    "overlay", "overlay_test" -> handleOverlayTest(context, resolvedPkg, appName)
                    "advance_alarm", "test_advance" -> handleTriggerAdvanceAlarm(context)
                    "expiry_alarm", "test_expiry" -> handleTriggerExpiryAlarm(context)
                    "update", "push_update" -> handleSimulatePushUpdate(context, intent)
                    "clear" -> handleClearAll(context)
                    else -> {
                        Log.w(TAG, "Unknown simulation action type: $type")
                        AppLogger.warn(context, "SIMULATOR", "Unknown simulation type: $type")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing simulation broadcast", e)
                AppLogger.error(context, "SIMULATOR", "Simulation error: ${e.message}")
            }
        }
    }

    private fun handleSimulateStart(context: Context, intent: Intent, pkgName: String, appName: String) {
        val now = System.currentTimeMillis()
        val minutes = intent.getIntExtra("minutes", 0)
        val seconds = intent.getIntExtra("seconds", 0)
        val durationMillis = when {
            seconds > 0 -> seconds * 1000L
            minutes > 0 -> minutes * 60 * 1000L
            else -> 45 * 60 * 1000L // Default: 45 minutes
        }

        val zone = intent.getStringExtra("zone") ?: if (pkgName == NotificationHelper.PARKEDIN_PACKAGE) "Zone 4022" else "Zone 1205"
        val customText = intent.getStringExtra("text")
        val endTime = now + durationMillis

        val (finalEndTime, finalZone, reason, rawText) = if (!customText.isNullOrBlank()) {
            val parsed = SessionNotificationParser.parse(
                title = "$appName Parking",
                text = customText,
                subText = null,
                postTimeMillis = now
            )
            if (parsed != null) {
                listOf(parsed.endTimeMillis, parsed.zoneOrLot ?: zone, parsed.detectedReason, customText)
            } else {
                listOf(endTime, zone, "Simulated from text: $customText", customText)
            }
        } else {
            val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(endTime))
            val text = "Parking active in $zone. Valid until $timeStr"
            listOf(endTime, zone, "CLI Simulation ($appName, ${durationMillis / 60000}m)", text)
        }

        val pending = PendingParkingDetection(
            packageName = pkgName,
            appName = appName,
            zoneOrLot = finalZone as? String,
            endTimeMillis = finalEndTime as Long,
            source = "$appName (ADB Simulated)",
            reason = reason as String,
            rawData = rawText as String
        )

        AppLogger.info(context, "SIMULATOR", "Injected start simulation for $appName: ${pending.zoneOrLot}, ends at ${Date(pending.endTimeMillis)}")
        DetectionApprovalManager.requestApprovalOrStart(context, pending)
    }

    private fun handleSimulateStop(context: Context, intent: Intent, pkgName: String, appName: String) {
        val now = System.currentTimeMillis()
        val zone = intent.getStringExtra("zone") ?: intent.getStringExtra("lot") ?: "Zone 4022"
        val duration = intent.getStringExtra("duration") ?: "45 mins"
        val cost = intent.getStringExtra("cost") ?: "$2.50"
        val customText = intent.getStringExtra("text") ?: "Parking session in $zone ended. Duration: $duration. Total: $cost"

        val stopDetection = PendingParkingStopDetection(
            packageName = pkgName,
            appName = appName,
            zoneOrLot = zone,
            stopTimeMillis = now,
            durationParkedText = duration,
            costOrRefundText = cost,
            source = "$appName (ADB Stop Simulated)",
            reason = "CLI Simulated stop receipt",
            rawData = customText
        )

        AppLogger.info(context, "SIMULATOR", "Injected stop simulation for $appName: $zone, duration=$duration, cost=$cost")
        DetectionApprovalManager.requestStopApproval(context, stopDetection)
    }

    private fun handleSimulateScreen(context: Context, intent: Intent, pkgName: String, appName: String) {
        val now = System.currentTimeMillis()
        val nodesRaw = intent.getStringExtra("nodes")
        val nodes = if (!nodesRaw.isNullOrBlank()) {
            nodesRaw.split(";", "\n", ",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(now + 30 * 60 * 1000L))
            listOf(
                "Calgary Parking Authority",
                "Active Session",
                "Zone",
                "1205",
                "Valid Until",
                timeStr,
                "Vehicle: AB-1234"
            )
        }

        val parsed = SessionNotificationParser.parseScreenText(nodes, now)
        val endTime = parsed?.endTimeMillis ?: (now + 30 * 60 * 1000L)
        val zone = parsed?.zoneOrLot ?: "Zone 1205"
        val reason = parsed?.detectedReason ?: "CLI Screen Nodes Inspection"

        val pending = PendingParkingDetection(
            packageName = pkgName,
            appName = appName,
            zoneOrLot = zone,
            endTimeMillis = endTime,
            source = "$appName (Screen Simulated)",
            reason = reason,
            rawData = nodes.joinToString("\n")
        )

        AppLogger.info(context, "SIMULATOR", "Injected screen detection simulation for $appName: $zone")
        DetectionApprovalManager.requestApprovalOrStart(context, pending)
    }

    private fun handleSimulateStopScreen(context: Context, intent: Intent, pkgName: String, appName: String) {
        val now = System.currentTimeMillis()
        val nodesRaw = intent.getStringExtra("nodes")
        val nodes = if (!nodesRaw.isNullOrBlank()) {
            nodesRaw.split(";", "\n", ",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            listOf(
                "Calgary Parking Authority",
                "Session Ended",
                "Lot 58 - 935 - 4 Av SW",
                "Stopped at: 5:15 PM",
                "Total Time: 45 mins",
                "Total Cost: $2.50",
                "Receipt #9058",
                "DONE"
            )
        }

        val parsed = SessionNotificationParser.parseStopScreenText(nodes, now)
        val stopDetection = PendingParkingStopDetection(
            packageName = pkgName,
            appName = appName,
            zoneOrLot = parsed?.zoneOrLot ?: "Lot 58",
            stopTimeMillis = parsed?.stopTimeMillis ?: now,
            durationParkedText = parsed?.durationParkedText ?: "45 mins",
            costOrRefundText = parsed?.costOrRefundText ?: "$2.50",
            source = "$appName (Screen Stop Simulated)",
            reason = parsed?.detectedReason ?: "CLI Screen Stop Nodes Inspection",
            rawData = nodes.joinToString("\n")
        )

        AppLogger.info(context, "SIMULATOR", "Injected screen stop detection simulation for $appName")
        DetectionApprovalManager.requestStopApproval(context, stopDetection)
    }

    private fun handleOverlayTest(context: Context, pkgName: String, appName: String) {
        val dummy = PendingParkingDetection(
            packageName = pkgName,
            appName = appName,
            zoneOrLot = "Zone 4022",
            endTimeMillis = System.currentTimeMillis() + 45 * 60 * 1000L,
            source = "Overlay Quick Test",
            reason = "CLI lock screen overlay test trigger",
            rawData = "Simulated Lock Screen Overlay"
        )
        AppLogger.info(context, "SIMULATOR", "Triggering overlay test for $appName")
        DetectionApprovalManager.requestApprovalOrStart(context, dummy)
    }

    private suspend fun handleTriggerAdvanceAlarm(context: Context) {
        val db = ParkingDatabase.getDatabase(context)
        val session = db.parkingDao().getActiveSession()
        if (session != null) {
            NotificationHelper.showAdvanceWarningNotification(context, session)
            AppLogger.info(context, "SIMULATOR", "Triggered advance warning alert for session #${session.id}")
        } else {
            AppLogger.warn(context, "SIMULATOR", "No active session found to trigger advance warning")
        }
    }

    private suspend fun handleTriggerExpiryAlarm(context: Context) {
        val db = ParkingDatabase.getDatabase(context)
        val session = db.parkingDao().getActiveSession()
        if (session != null) {
            NotificationHelper.showExpiredNotification(context, session)
            AppLogger.info(context, "SIMULATOR", "Triggered expiry alert for session #${session.id}")
        } else {
            AppLogger.warn(context, "SIMULATOR", "No active session found to trigger expiry alert")
        }
    }

    private suspend fun handleClearAll(context: Context) {
        val db = ParkingDatabase.getDatabase(context)
        val current = db.parkingDao().getActiveSession()
        if (current != null) {
            ParkingAlarmScheduler.cancelAlarms(context, current.id)
            NotificationHelper.cancelStatusNotification(context)
        }
        db.parkingDao().clearAll()
        DetectionApprovalManager.clearRecentlyStoppedSession()
        NotificationHelper.cancelDetectionApprovalNotification(context)
        NotificationHelper.cancelStopApprovalNotification(context)
        AppLogger.info(context, "SIMULATOR", "Cleared all sessions and reset simulator state")
    }

    private fun handleSimulatePushUpdate(context: Context, intent: Intent) {
        val version = intent.getStringExtra("version") ?: "1.1.0"
        val versionCode = if (intent.hasExtra("versionCode")) intent.getIntExtra("versionCode", 2) else 2
        val downloadUrl = intent.getStringExtra("downloadUrl")
            ?: "https://github.com/icecore2/parking-time-detector-app/releases/download/v1.1.0/ParkingTimeDetector-v1.1.0.apk"
        val changelog = intent.getStringExtra("changelog") ?: "Simulated push update for local testing"
        val autoDownload = intent.getBooleanExtra("autoDownload", false)

        val info = com.parktimedetector.update.AppUpdateInfo(
            versionName = version,
            versionCode = versionCode,
            title = "Parking Time Detector $version",
            changelog = changelog,
            downloadUrl = downloadUrl,
            fileName = "ParkingTimeDetector-$version.apk"
        )
        com.parktimedetector.update.AppUpdateEngine.processPushUpdate(context, info, autoDownload)
    }
}
