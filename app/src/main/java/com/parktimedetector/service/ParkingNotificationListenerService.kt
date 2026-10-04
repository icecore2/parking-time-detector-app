package com.parktimedetector.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import com.parktimedetector.data.AppLogger
import com.parktimedetector.data.ParkingDatabase
import com.parktimedetector.data.ParkingSession
import com.parktimedetector.data.UserPreferencesRepository
import com.parktimedetector.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParkingNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "ParkingNotifListener"

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected = _isServiceConnected.asStateFlow()

        fun isPermissionGranted(context: Context): Boolean {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false

            if (!TextUtils.isEmpty(flat)) {
                val names = flat.split(":")
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
            return false
        }

        fun requestRebindService(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    requestRebind(ComponentName(context, ParkingNotificationListenerService::class.java))
                    AppLogger.info(context, "SYSTEM", "Requested NotificationListenerService rebind")
                } catch (e: Exception) {
                    AppLogger.error(context, "SYSTEM", "Failed to request rebind: ${e.message}")
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isServiceConnected.value = true
        Log.i(TAG, "NotificationListenerService connected")
        AppLogger.success(applicationContext, "SERVICE_STATUS", "Notification Listener Service connected and listening")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isServiceConnected.value = false
        Log.w(TAG, "NotificationListenerService disconnected")
        AppLogger.warn(applicationContext, "SERVICE_STATUS", "Notification Listener Service disconnected by system")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkgName = sbn.packageName ?: return

        // Ignore notifications posted by our own app to avoid feedback loop
        if (pkgName == packageName) return

        serviceScope.launch {
            try {
                val prefsRepo = UserPreferencesRepository(applicationContext)
                val monitored = prefsRepo.monitoredPackages.first()
                val logAll = prefsRepo.logAllNotifications.first()

                val notification = sbn.notification ?: return@launch
                val extras = notification.extras ?: return@launch

                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                val ticker = notification.tickerText?.toString()
                val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")

                val rawExtrasSummary = buildString {
                    append("Package: ").append(pkgName).append("\n")
                    append("Title: ").append(title ?: "<null>").append("\n")
                    append("Text: ").append(text ?: "<null>").append("\n")
                    if (bigText != null) append("BigText: ").append(bigText).append("\n")
                    if (subText != null) append("SubText: ").append(subText).append("\n")
                    if (ticker != null) append("Ticker: ").append(ticker).append("\n")
                    if (lines != null) append("Lines: ").append(lines).append("\n")
                    extras.keySet()?.forEach { k ->
                        if (k !in listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT)) {
                            append("extra[$k]: ").append(extras.get(k)).append("\n")
                        }
                    }
                }

                val combinedText = listOfNotNull(title, text, bigText, subText, ticker, lines).joinToString(" ")
                val isMonitoredPackage = monitored.contains(pkgName) ||
                    pkgName.contains("parkedin", ignoreCase = true) ||
                    pkgName.contains("accountManagement", ignoreCase = true) ||
                    pkgName.contains("cpa", ignoreCase = true)
                val containsParkingKeywords = combinedText.contains("parkedin", ignoreCase = true) ||
                    combinedText.contains("myparking", ignoreCase = true) ||
                    combinedText.contains("calgary parking", ignoreCase = true) ||
                    combinedText.contains("parking session", ignoreCase = true) ||
                    combinedText.contains("parking active", ignoreCase = true) ||
                    combinedText.contains("parking valid", ignoreCase = true)

                // If not parking and logging all is enabled, record as other app notification
                if (!isMonitoredPackage && !containsParkingKeywords) {
                    if (logAll) {
                        AppLogger.info(
                            context = applicationContext,
                            tag = "NOTIF_OTHER",
                            message = "${title ?: pkgName}: ${text ?: "<no text>"}",
                            packageName = pkgName,
                            rawData = rawExtrasSummary
                        )
                    }
                    return@launch
                }

                val alwaysDetect = prefsRepo.alwaysDetect.first()
                if (!alwaysDetect && !ManualDetectionManager.isDetectionWindowActive()) {
                    AppLogger.info(
                        context = applicationContext,
                        tag = "DETECTION_SUPPRESSED",
                        message = "Parking notification received from $pkgName, but 'Always Detect' is off and manual detection window is inactive.",
                        packageName = pkgName
                    )
                    return@launch
                }

                val detectedSource = when {
                    pkgName == NotificationHelper.MYPARKING_PACKAGE ||
                    pkgName.contains("cpa", ignoreCase = true) ||
                    combinedText.contains("myparking", ignoreCase = true) -> "MyParking App"
                    else -> "ParkedIn App"
                }

                AppLogger.info(
                    context = applicationContext,
                    tag = "NOTIF_RECEIVED",
                    message = "Parking notification detected from $pkgName! Checking content...",
                    packageName = pkgName,
                    rawData = rawExtrasSummary
                )

                val postTime = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()

                // Check for session stop / ended notification
                val isStopNotif = SessionNotificationParser.isStopScreen(listOf(combinedText))
                if (isStopNotif) {
                    val stopResult = SessionNotificationParser.parseStopNotification(
                        title = title,
                        text = bigText ?: text ?: lines ?: ticker,
                        subText = subText,
                        postTimeMillis = postTime
                    )
                    if (stopResult != null) {
                        val appName = NotificationHelper.getAppNameForPackage(pkgName)
                        val pendingStop = com.parktimedetector.data.PendingParkingStopDetection(
                            packageName = pkgName,
                            appName = appName,
                            zoneOrLot = stopResult.zoneOrLot,
                            stopTimeMillis = stopResult.stopTimeMillis,
                            durationParkedText = stopResult.durationParkedText,
                            costOrRefundText = stopResult.costOrRefundText,
                            source = detectedSource,
                            reason = stopResult.detectedReason,
                            rawData = rawExtrasSummary,
                            detectedAtMillis = postTime
                        )
                        DetectionApprovalManager.requestStopApproval(applicationContext, pendingStop)
                        return@launch
                    }
                }

                val parsedResult = SessionNotificationParser.parse(
                    title = title,
                    text = bigText ?: text ?: lines ?: ticker,
                    subText = subText,
                    postTimeMillis = postTime
                )

                if (parsedResult == null) {
                    AppLogger.error(
                        context = applicationContext,
                        tag = "PARSE_FAILED",
                        message = "Matched parking notification from $pkgName, but could not extract expiration time or duration from text: '$combinedText'",
                        packageName = pkgName,
                        rawData = rawExtrasSummary
                    )
                    return@launch
                }

                val timeFormat = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
                val formattedEnd = timeFormat.format(Date(parsedResult.endTimeMillis))

                if (parsedResult.endTimeMillis <= System.currentTimeMillis()) {
                    AppLogger.warn(
                        context = applicationContext,
                        tag = "PARSE_EXPIRED",
                        message = "Extracted end time $formattedEnd is already in the past.",
                        packageName = pkgName,
                        rawData = rawExtrasSummary
                    )
                    return@launch
                }

                val advanceMinutes = prefsRepo.advanceWarningMinutes.first()
                val db = ParkingDatabase.getDatabase(applicationContext)
                val dao = db.parkingDao()

                // Check if current active session is already identical to avoid duplicates
                val currentActive = dao.getActiveSession()
                if (currentActive != null &&
                    currentActive.isActive &&
                    Math.abs(currentActive.endTimeMillis - parsedResult.endTimeMillis) < 60_000L
                ) {
                    AppLogger.info(
                        context = applicationContext,
                        tag = "DUPLICATE_IGNORED",
                        message = "Active session already exists for end time $formattedEnd.",
                        packageName = pkgName
                    )
                    return@launch
                }

                val appName = NotificationHelper.getAppNameForPackage(pkgName)
                val pending = com.parktimedetector.data.PendingParkingDetection(
                    packageName = pkgName,
                    appName = appName,
                    zoneOrLot = parsedResult.zoneOrLot,
                    endTimeMillis = parsedResult.endTimeMillis,
                    source = detectedSource,
                    reason = parsedResult.detectedReason,
                    rawData = rawExtrasSummary,
                    detectedAtMillis = postTime,
                    startTimeMillis = parsedResult.startTimeMillis ?: postTime
                )
                DetectionApprovalManager.requestApprovalOrStart(applicationContext, pending)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling notification", e)
                AppLogger.error(applicationContext, "EXCEPTION", "Error in listener: ${e.message}", packageName = pkgName)
            }
        }
    }
}
