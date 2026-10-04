package com.parktimedetector.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.parktimedetector.MainActivity
import com.parktimedetector.R
import com.parktimedetector.data.ParkingSession
import com.parktimedetector.receiver.ParkingAlarmReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_URGENT_ID = "parking_urgent_alerts"
    const val CHANNEL_STATUS_ID = "parking_status_timer"
    const val CHANNEL_APPROVAL_QUIET_ID = "parking_approval_quiet"

    const val NOTIFICATION_ID_STATUS = 1001
    const val NOTIFICATION_ID_ADVANCE = 1002
    const val NOTIFICATION_ID_EXPIRED = 1003

    const val PARKEDIN_PACKAGE = "com.preciseparklink.parkedin"
    const val MYPARKING_PACKAGE = "com.cpa.accountManagement"

    fun getAppNameForPackage(packageName: String): String {
        return when {
            packageName == MYPARKING_PACKAGE ||
            packageName.contains("cpa", ignoreCase = true) ||
            packageName.contains("myparking", ignoreCase = true) -> "MyParking"
            else -> "ParkedIn"
        }
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

            // 1. High priority channel for Expiry and Advance Warnings
            val urgentChannel = NotificationChannel(
                CHANNEL_URGENT_ID,
                context.getString(R.string.channel_urgent_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_urgent_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            // 2. Low/Ongoing channel for Live Countdown Timer
            val statusChannel = NotificationChannel(
                CHANNEL_STATUS_ID,
                context.getString(R.string.channel_status_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_status_desc)
                enableVibration(false)
                setShowBadge(false)
            }

            // 3. Low priority channel when floating dialog is active on screen
            val quietApprovalChannel = NotificationChannel(
                CHANNEL_APPROVAL_QUIET_ID,
                "Parking Detection (Quiet Shade)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps pending detections in notification drawer when on-screen dialog is active without popping up heads-up banner"
                enableVibration(false)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(urgentChannel)
            notificationManager.createNotificationChannel(statusChannel)
            notificationManager.createNotificationChannel(quietApprovalChannel)
        }
    }

    /**
     * Creates a PendingIntent that opens the specific parking app (ParkedIn or MyParking) directly when pressed.
     * If the app is not installed on the device, opens the Play Store page or falls back to our app.
     */
    fun createTargetAppPendingIntent(context: Context, targetPackage: String, requestCode: Int): PendingIntent {
        val pm = context.packageManager
        val resolvedPkg = when {
            targetPackage == MYPARKING_PACKAGE -> MYPARKING_PACKAGE
            targetPackage == PARKEDIN_PACKAGE -> PARKEDIN_PACKAGE
            targetPackage.contains("cpa", ignoreCase = true) || targetPackage.contains("myparking", ignoreCase = true) -> MYPARKING_PACKAGE
            // Fallback for manual timer: open whichever parking app is installed
            pm.getLaunchIntentForPackage(MYPARKING_PACKAGE) != null && pm.getLaunchIntentForPackage(PARKEDIN_PACKAGE) == null -> MYPARKING_PACKAGE
            else -> PARKEDIN_PACKAGE
        }

        val launchIntent = pm.getLaunchIntentForPackage(resolvedPkg)

        val intent = if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } else {
            // Fallback: Try Play Store or MainActivity
            try {
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$resolvedPkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (_: Exception) {
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    fun createParkedInPendingIntent(context: Context, requestCode: Int): PendingIntent {
        return createTargetAppPendingIntent(context, PARKEDIN_PACKAGE, requestCode)
    }

    /**
     * PendingIntent to open our own app
     */
    fun createAppPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    /**
     * PendingIntent to end or dismiss the active session
     */
    private fun createEndSessionPendingIntent(context: Context, sessionId: Long): PendingIntent {
        val intent = Intent(context, ParkingAlarmReceiver::class.java).apply {
            action = ParkingAlarmReceiver.ACTION_CANCEL_SESSION
            putExtra(ParkingAlarmReceiver.EXTRA_SESSION_ID, sessionId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, (sessionId + 900).toInt(), intent, flags)
    }

    /**
     * Shows live ongoing countdown notification.
     * Pressing this notification opens the relevant parking app (ParkedIn or MyParking).
     */
    fun showActiveCountdownNotification(context: Context, session: ParkingSession) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        val openAppPI = createTargetAppPendingIntent(context, session.packageName, 101)
        val endSessionPI = createEndSessionPendingIntent(context, session.id)

        val appName = getAppNameForPackage(session.packageName)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedEndTime = timeFormat.format(Date(session.endTimeMillis))
        val zoneInfo = session.zoneOrLot?.let { " ($it)" } ?: ""

        val publicStatusNotification = NotificationCompat.Builder(context, CHANNEL_STATUS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Active Parking")
            .setContentText("Parking timer in progress • Unlock device for details")
            .setContentIntent(openAppPI)
            .build()

        val builder = NotificationCompat.Builder(context, CHANNEL_STATUS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Active Parking$zoneInfo")
            .setContentText("Expires at $formattedEndTime • Tap to open $appName")
            .setWhen(session.endTimeMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicStatusNotification)
            .setContentIntent(openAppPI)
            .addAction(0, "Open $appName", openAppPI)
            .addAction(0, "End Parking", endSessionPI)

        notificationManager.notify(NOTIFICATION_ID_STATUS, builder.build())
    }

    /**
     * Shows high-priority Advance Warning notification (e.g. 15 mins before expiry).
     * Pressing this notification opens the parking app to renew.
     */
    fun showAdvanceWarningNotification(context: Context, session: ParkingSession) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        val openAppPI = createTargetAppPendingIntent(context, session.packageName, 102)

        val appName = getAppNameForPackage(session.packageName)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedEndTime = timeFormat.format(Date(session.endTimeMillis))
        val minutesLeft = ((session.endTimeMillis - System.currentTimeMillis()) / (60 * 1000L)).coerceAtLeast(1L)
        val zoneText = session.zoneOrLot?.let { " at $it" } ?: ""

        val builder = NotificationCompat.Builder(context, CHANNEL_URGENT_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ Parking Expiring Soon$zoneText!")
            .setContentText("Expires in ~$minutesLeft min ($formattedEndTime). Tap to open $appName and renew!")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Your parking session$zoneText will expire at $formattedEndTime (~$minutesLeft minutes remaining). Tap anywhere or 'Renew Now' to open $appName before time runs out!"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppPI)
            .addAction(0, "Renew in $appName", openAppPI)

        notificationManager.notify(NOTIFICATION_ID_ADVANCE, builder.build())
    }

    /**
     * Shows high-priority Expired notification.
     * Pressing opens the parking app.
     */
    fun showExpiredNotification(context: Context, session: ParkingSession) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        val openAppPI = createTargetAppPendingIntent(context, session.packageName, 103)

        val appName = getAppNameForPackage(session.packageName)
        val zoneText = session.zoneOrLot?.let { " at $it" } ?: ""

        val builder = NotificationCompat.Builder(context, CHANNEL_URGENT_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 Parking Expired$zoneText!")
            .setContentText("Your parking session has ended. Tap to open $appName immediately!")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Your parking session$zoneText has reached its end time. Tap here or 'Open $appName' to extend or pay to avoid a parking ticket!"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppPI)
            .addAction(0, "Open $appName", openAppPI)

        notificationManager.notify(NOTIFICATION_ID_EXPIRED, builder.build())
        cancelStatusNotification(context)
    }

    const val NOTIFICATION_ID_APPROVAL = 1004

    /**
     * Shows notification asking the user to approve starting the timer.
     * When isQuietMode is true (on-screen dialog active), notification is quietly added to tray without heads-up collision.
     */
    fun showDetectionApprovalNotification(
        context: Context,
        detection: com.parktimedetector.data.PendingParkingDetection,
        isQuietMode: Boolean = false
    ) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

        // Approve Action
        val approveIntent = Intent(context, com.parktimedetector.receiver.PendingDetectionReceiver::class.java).apply {
            action = com.parktimedetector.receiver.PendingDetectionReceiver.ACTION_APPROVE_DETECTION
        }
        val approvePI = PendingIntent.getBroadcast(
            context,
            201,
            approveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss Action
        val dismissIntent = Intent(context, com.parktimedetector.receiver.PendingDetectionReceiver::class.java).apply {
            action = com.parktimedetector.receiver.PendingDetectionReceiver.ACTION_DISMISS_DETECTION
        }
        val dismissPI = PendingIntent.getBroadcast(
            context,
            202,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Open App Intent
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_SHOW_APPROVAL_DIALOG", true)
        }
        val openAppPI = PendingIntent.getActivity(
            context,
            203,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedEndTime = timeFormat.format(Date(detection.endTimeMillis))
        val zoneText = detection.zoneOrLot?.let { "$it • " } ?: ""

        val channelId = if (isQuietMode) CHANNEL_APPROVAL_QUIET_ID else CHANNEL_URGENT_ID
        val priority = if (isQuietMode) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH

        val publicApprovalNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🅿️ Parking Detected")
            .setContentText("Unlock device to review and approve parking timer")
            .setContentIntent(openAppPI)
            .build()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🅿️ ${detection.appName} Parking Detected")
            .setContentText("${zoneText}Expires at $formattedEndTime (${detection.durationMinutes}m). Start timer?")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Detected parking session in ${detection.appName}!\n${zoneText}Valid until $formattedEndTime (~${detection.durationMinutes} minutes duration).\n\nTap 'Start Timer' to track countdown & receive renewal alerts."
                )
            )
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicApprovalNotification)
            .setAutoCancel(true)
            .setContentIntent(openAppPI)
            .addAction(0, "Start Timer", approvePI)
            .addAction(0, "Dismiss", dismissPI)

        notificationManager.notify(NOTIFICATION_ID_APPROVAL, builder.build())
    }

    const val NOTIFICATION_ID_STOP_APPROVAL = 1005

    /**
     * Shows notification asking the user to accept detected session stop.
     * When isQuietMode is true (on-screen dialog active), notification is quietly added to tray without heads-up collision.
     */
    fun showStopApprovalNotification(
        context: Context,
        detection: com.parktimedetector.data.PendingParkingStopDetection,
        isQuietMode: Boolean = false
    ) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

        // Approve Stop Action
        val approveIntent = Intent(context, com.parktimedetector.receiver.PendingDetectionReceiver::class.java).apply {
            action = com.parktimedetector.receiver.PendingDetectionReceiver.ACTION_APPROVE_STOP_DETECTION
        }
        val approvePI = PendingIntent.getBroadcast(
            context,
            204,
            approveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss Stop Action
        val dismissIntent = Intent(context, com.parktimedetector.receiver.PendingDetectionReceiver::class.java).apply {
            action = com.parktimedetector.receiver.PendingDetectionReceiver.ACTION_DISMISS_STOP_DETECTION
        }
        val dismissPI = PendingIntent.getBroadcast(
            context,
            205,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Open App Intent
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_SHOW_STOP_DIALOG", true)
        }
        val openAppPI = PendingIntent.getActivity(
            context,
            206,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedStopTime = timeFormat.format(Date(detection.stopTimeMillis))
        val zoneText = detection.zoneOrLot?.let { "$it • " } ?: ""
        val durationInfo = detection.durationParkedText?.let { " ($it)" } ?: ""
        val costInfo = detection.costOrRefundText?.let { " • $it" } ?: ""

        val channelId = if (isQuietMode) CHANNEL_APPROVAL_QUIET_ID else CHANNEL_URGENT_ID
        val priority = if (isQuietMode) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH

        val publicStopNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🛑 Parking Stopped")
            .setContentText("Unlock device to review and accept session stop")
            .setContentIntent(openAppPI)
            .build()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🛑 ${detection.appName} Parking Stopped")
            .setContentText("${zoneText}Stopped at $formattedStopTime$durationInfo$costInfo")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Detected that parking ended in ${detection.appName}!\n${zoneText}Stopped at $formattedStopTime$durationInfo$costInfo.\n\nTap 'Accept Stop' to end active timer and view summary."
                )
            )
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicStopNotification)
            .setAutoCancel(true)
            .setContentIntent(openAppPI)
            .addAction(0, "Accept Stop", approvePI)
            .addAction(0, "Dismiss", dismissPI)

        notificationManager.notify(NOTIFICATION_ID_STOP_APPROVAL, builder.build())
    }

    fun cancelStopApprovalNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        notificationManager.cancel(NOTIFICATION_ID_STOP_APPROVAL)
    }

    fun cancelDetectionApprovalNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        notificationManager.cancel(NOTIFICATION_ID_APPROVAL)
    }

    fun cancelStatusNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        notificationManager.cancel(NOTIFICATION_ID_STATUS)
    }

    fun cancelAll(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        notificationManager.cancel(NOTIFICATION_ID_STATUS)
        notificationManager.cancel(NOTIFICATION_ID_ADVANCE)
        notificationManager.cancel(NOTIFICATION_ID_EXPIRED)
        notificationManager.cancel(NOTIFICATION_ID_APPROVAL)
        notificationManager.cancel(NOTIFICATION_ID_STOP_APPROVAL)
    }
}
