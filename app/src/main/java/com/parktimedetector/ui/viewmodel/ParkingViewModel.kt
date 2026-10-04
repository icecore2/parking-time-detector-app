package com.parktimedetector.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.parktimedetector.data.ParkingDatabase
import com.parktimedetector.data.ParkingSession
import com.parktimedetector.data.UserPreferencesRepository
import com.parktimedetector.notification.NotificationHelper
import com.parktimedetector.service.ParkingAlarmScheduler
import com.parktimedetector.service.SessionNotificationParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ParkingViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ParkingDatabase.getDatabase(application)
    private val dao = db.parkingDao()
    private val prefsRepo = UserPreferencesRepository(application)

    val activeSession: StateFlow<ParkingSession?> = dao.getActiveSessionFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sessionHistory: StateFlow<List<ParkingSession>> = dao.getAllSessionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val advanceWarningMinutes: StateFlow<Int> = prefsRepo.advanceWarningMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesRepository.DEFAULT_ADVANCE_MINUTES)

    val logs: StateFlow<List<com.parktimedetector.data.LogEntry>> = db.logDao().getLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isServiceConnected: StateFlow<Boolean> = com.parktimedetector.service.ParkingNotificationListenerService.isServiceConnected
    val isAccessibilityConnected: StateFlow<Boolean> = com.parktimedetector.service.ParkingAccessibilityService.isServiceConnected

    val pendingDetection: StateFlow<com.parktimedetector.data.PendingParkingDetection?> =
        com.parktimedetector.service.DetectionApprovalManager.pendingDetection

    val pendingStopDetection: StateFlow<com.parktimedetector.data.PendingParkingStopDetection?> =
        com.parktimedetector.service.DetectionApprovalManager.pendingStopDetection

    val recentlyStoppedSession: StateFlow<ParkingSession?> =
        com.parktimedetector.service.DetectionApprovalManager.recentlyStoppedSession

    val requireApproval: StateFlow<Boolean> = prefsRepo.requireApproval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showConfirmationDialogs: StateFlow<Boolean> = prefsRepo.showConfirmationDialogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val overlayDialogPosition: StateFlow<com.parktimedetector.data.OverlayDialogPosition> = prefsRepo.overlayDialogPosition
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.parktimedetector.data.OverlayDialogPosition.TOP)

    val lockScreenOverlayPolicy: StateFlow<com.parktimedetector.data.LockScreenOverlayPolicy> = prefsRepo.lockScreenOverlayPolicy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.parktimedetector.data.LockScreenOverlayPolicy.FOLLOW_SYSTEM)

    val dismissOverlayOnScreenOff: StateFlow<Boolean> = prefsRepo.dismissOverlayOnScreenOff
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val logAllNotifications: StateFlow<Boolean> = prefsRepo.logAllNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val alwaysDetect: StateFlow<Boolean> = prefsRepo.alwaysDetect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val enableQuickRenewAutomation: StateFlow<Boolean> = prefsRepo.enableQuickRenewAutomation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val pauseBeforePayment: StateFlow<Boolean> = prefsRepo.pauseBeforePayment
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val quickRenewState: StateFlow<com.parktimedetector.service.QuickRenewState> =
        com.parktimedetector.service.QuickRenewManager.state

    val quickRenewStatusMessage: StateFlow<String?> =
        com.parktimedetector.service.QuickRenewManager.statusMessage

    val isManualDetectionActive: StateFlow<Boolean> = com.parktimedetector.service.ManualDetectionManager.isDetectionActive
    val manualDetectionRemainingSeconds: StateFlow<Int> = com.parktimedetector.service.ManualDetectionManager.remainingSeconds

    fun toggleAlwaysDetect(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAlwaysDetect(enabled)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Always detect set to: $enabled")
        }
    }

    fun startOrResetManualDetection() {
        com.parktimedetector.service.ManualDetectionManager.startOrResetDetectionWindow(getApplication(), "Main Screen Manual Button")
    }

    fun stopManualDetection() {
        com.parktimedetector.service.ManualDetectionManager.stopDetectionWindow(getApplication())
    }

    fun toggleShowConfirmationDialogs(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setShowConfirmationDialogs(enabled)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Confirmation dialog windows set to: $enabled")
        }
    }

    fun sendTestApprovalNotification() {
        val dummy = com.parktimedetector.data.PendingParkingDetection(
            packageName = NotificationHelper.PARKEDIN_PACKAGE,
            appName = "ParkedIn",
            zoneOrLot = "Zone 4022",
            endTimeMillis = System.currentTimeMillis() + 45 * 60 * 1000L,
            source = "Notification Test",
            reason = "User tested notification delivery in Settings",
            rawData = "Test Notification Delivery"
        )
        NotificationHelper.showDetectionApprovalNotification(getApplication(), dummy)
        com.parktimedetector.data.AppLogger.info(getApplication(), "TEST", "Dispatched test detection approval notification")
    }

    fun triggerAdvanceAlarmNow() {
        viewModelScope.launch {
            val session = dao.getActiveSession()
            if (session != null) {
                NotificationHelper.showAdvanceWarningNotification(getApplication(), session)
                com.parktimedetector.data.AppLogger.info(getApplication(), "SIMULATOR", "Triggered advance warning alert for session #${session.id}")
            }
        }
    }

    fun triggerExpiryAlarmNow() {
        viewModelScope.launch {
            val session = dao.getActiveSession()
            if (session != null) {
                NotificationHelper.showExpiredNotification(getApplication(), session)
                com.parktimedetector.data.AppLogger.info(getApplication(), "SIMULATOR", "Triggered expiry alert for session #${session.id}")
            }
        }
    }

    fun triggerOverlayTest() {
        val dummy = com.parktimedetector.data.PendingParkingDetection(
            packageName = NotificationHelper.PARKEDIN_PACKAGE,
            appName = "ParkedIn",
            zoneOrLot = "Zone 4022",
            endTimeMillis = System.currentTimeMillis() + 45 * 60 * 1000L,
            source = "Overlay Quick Test",
            reason = "In-App Lock Screen Overlay Test",
            rawData = "Simulated Overlay Preview"
        )
        com.parktimedetector.service.DetectionApprovalManager.requestApprovalOrStart(getApplication(), dummy)
    }

    fun resetSimulator() {
        clearHistory()
        dismissPendingDetection()
        dismissPendingStopDetection()
        clearRecentlyStoppedSession()
        NotificationHelper.cancelDetectionApprovalNotification(getApplication())
        NotificationHelper.cancelStopApprovalNotification(getApplication())
    }

    fun setOverlayDialogPosition(position: com.parktimedetector.data.OverlayDialogPosition) {
        viewModelScope.launch {
            prefsRepo.setOverlayDialogPosition(position)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Overlay dialog position set to: ${position.name}")
        }
    }

    fun setLockScreenOverlayPolicy(policy: com.parktimedetector.data.LockScreenOverlayPolicy) {
        viewModelScope.launch {
            prefsRepo.setLockScreenOverlayPolicy(policy)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Lock screen overlay privacy policy set to: ${policy.name}")
        }
    }

    fun setDismissOverlayOnScreenOff(dismiss: Boolean) {
        viewModelScope.launch {
            prefsRepo.setDismissOverlayOnScreenOff(dismiss)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Dismiss overlay on screen off set to: $dismiss")
        }
    }

    fun approvePendingDetection() {
        com.parktimedetector.service.DetectionApprovalManager.approveCurrentDetection(getApplication())
    }

    fun dismissPendingDetection() {
        com.parktimedetector.service.DetectionApprovalManager.dismissCurrentDetection(getApplication())
    }

    fun approvePendingStopDetection() {
        com.parktimedetector.service.DetectionApprovalManager.approveCurrentStopDetection(getApplication())
    }

    fun dismissPendingStopDetection() {
        com.parktimedetector.service.DetectionApprovalManager.dismissCurrentStopDetection(getApplication())
    }

    fun clearRecentlyStoppedSession() {
        com.parktimedetector.service.DetectionApprovalManager.clearRecentlyStoppedSession()
    }

    fun toggleRequireApproval(required: Boolean) {
        viewModelScope.launch {
            prefsRepo.setRequireApproval(required)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Require approval before starting timer set to: $required")
        }
    }

    fun simulateScreenDetection(nodes: List<String>, packageName: String = NotificationHelper.MYPARKING_PACKAGE): Boolean {
        val now = System.currentTimeMillis()
        val parsed = SessionNotificationParser.parseScreenText(nodes, now) ?: return false

        val appName = NotificationHelper.getAppNameForPackage(packageName)
        val pending = com.parktimedetector.data.PendingParkingDetection(
            packageName = packageName,
            appName = appName,
            zoneOrLot = parsed.zoneOrLot,
            endTimeMillis = parsed.endTimeMillis,
            source = "$appName App (Screen Simulated)",
            reason = parsed.detectedReason,
            rawData = nodes.joinToString("\n"),
            locationAddress = parsed.locationAddress,
            purchasedDurationText = parsed.purchasedDurationText,
            initialCostText = parsed.costText,
            remainingTimeText = parsed.remainingTimeText,
            notesText = parsed.notesText,
            startTimeMillis = parsed.startTimeMillis ?: now
        )
        com.parktimedetector.service.DetectionApprovalManager.requestApprovalOrStart(getApplication(), pending)
        return true
    }

    fun toggleLogAllNotifications(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setLogAllNotifications(enabled)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Log all notifications set to: $enabled")
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            db.logDao().clearAll()
        }
    }

    fun shareLogs(
        context: Context,
        hasNotifPermission: Boolean,
        isNotifConnected: Boolean,
        hasAccessibilityPermission: Boolean,
        isAccessibilityConnected: Boolean,
        logAllNotifications: Boolean
    ) {
        viewModelScope.launch {
            val currentLogs = logs.value
            val status = com.parktimedetector.data.LogExporter.DiagnosticStatus(
                hasNotifPermission = hasNotifPermission,
                isNotifConnected = isNotifConnected,
                hasAccessibilityPermission = hasAccessibilityPermission,
                isAccessibilityConnected = isAccessibilityConnected,
                logAllNotifications = logAllNotifications
            )
            com.parktimedetector.data.LogExporter.shareLogs(context, currentLogs, status)
        }
    }

    fun reconnectService(context: Context) {
        com.parktimedetector.service.ParkingNotificationListenerService.requestRebindService(context)
    }

    fun addTestLog() {
        com.parktimedetector.data.AppLogger.info(
            getApplication(),
            "MANUAL_TEST",
            "Diagnostic test log triggered from Logs tab",
            "com.parktimedetector",
            "Raw sample data:\nTimestamp: ${System.currentTimeMillis()}\nStatus: OK"
        )
    }

    fun startManualSession(durationMinutes: Int, zoneOrLot: String?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val endTime = now + (durationMinutes * 60 * 1000L)
            val advance = advanceWarningMinutes.first()

            val current = dao.getActiveSession()
            if (current != null) {
                ParkingAlarmScheduler.cancelAlarms(getApplication(), current.id)
            }
            dao.deactivateAllSessions()

            val newSession = ParkingSession(
                packageName = "manual.timer",
                source = "In-App Timer",
                zoneOrLot = zoneOrLot?.takeIf { it.isNotBlank() },
                startTimeMillis = now,
                endTimeMillis = endTime,
                advanceWarningMinutes = advance,
                isActive = true
            )

            val id = dao.insert(newSession)
            val savedSession = newSession.copy(id = id)

            ParkingAlarmScheduler.scheduleAlarms(getApplication(), savedSession)
            NotificationHelper.showActiveCountdownNotification(getApplication(), savedSession)
        }
    }

    fun extendCurrentSession(extraMinutes: Int) {
        viewModelScope.launch {
            val current = dao.getActiveSession() ?: return@launch
            val newEndTime = current.endTimeMillis + (extraMinutes * 60 * 1000L)
            val updated = current.copy(
                endTimeMillis = newEndTime,
                isNotifiedAdvance = false, // reset advance warning if extended beyond it
                isNotifiedExpiry = false
            )
            dao.update(updated)

            ParkingAlarmScheduler.scheduleAlarms(getApplication(), updated)
            NotificationHelper.showActiveCountdownNotification(getApplication(), updated)
        }
    }

    fun endCurrentSession() {
        viewModelScope.launch {
            val current = dao.getActiveSession() ?: return@launch
            val now = System.currentTimeMillis()
            dao.endSessionWithFullDetails(
                id = current.id,
                stopTime = now,
                costOrRefund = current.costOrRefundText ?: current.initialCostText,
                stopReason = "Deactivated by user in app",
                locationAddress = current.locationAddress
            )
            ParkingAlarmScheduler.cancelAlarms(getApplication(), current.id)
            NotificationHelper.cancelStatusNotification(getApplication())

            val stopped = current.copy(
                isActive = false,
                actualStopTimeMillis = now,
                costOrRefundText = current.costOrRefundText ?: current.initialCostText,
                stopReason = "Deactivated by user in app"
            )
            // Save as recently stopped for the UI session stop screen
            com.parktimedetector.service.DetectionApprovalManager.requestStopApproval(
                getApplication(),
                com.parktimedetector.data.PendingParkingStopDetection(
                    packageName = current.packageName,
                    appName = NotificationHelper.getAppNameForPackage(current.packageName),
                    zoneOrLot = current.zoneOrLot,
                    stopTimeMillis = now,
                    durationParkedText = null,
                    costOrRefundText = current.costOrRefundText ?: current.initialCostText,
                    source = current.source,
                    reason = "User manually ended session in app",
                    locationAddress = current.locationAddress,
                    stopReason = "Deactivated by user in app"
                )
            )
            com.parktimedetector.service.DetectionApprovalManager.approveCurrentStopDetection(getApplication())
        }
    }

    fun simulateStopScreenDetection(nodes: List<String>, packageName: String = NotificationHelper.MYPARKING_PACKAGE): Boolean {
        val now = System.currentTimeMillis()
        val parsed = SessionNotificationParser.parseStopScreenText(nodes, now) ?: return false

        val appName = NotificationHelper.getAppNameForPackage(packageName)
        val pending = com.parktimedetector.data.PendingParkingStopDetection(
            packageName = packageName,
            appName = appName,
            zoneOrLot = parsed.zoneOrLot,
            stopTimeMillis = parsed.stopTimeMillis,
            durationParkedText = parsed.durationParkedText,
            costOrRefundText = parsed.costOrRefundText,
            source = "$appName App (Screen Stop Simulated)",
            reason = parsed.detectedReason,
            rawData = nodes.joinToString("\n"),
            locationAddress = parsed.locationAddress,
            stopReason = parsed.stopReason ?: "Stop screen detected"
        )
        com.parktimedetector.service.DetectionApprovalManager.requestStopApproval(getApplication(), pending)
        return true
    }

    fun simulateStopNotification(
        title: String,
        text: String,
        packageName: String = NotificationHelper.PARKEDIN_PACKAGE,
        subText: String? = null
    ): Boolean {
        val now = System.currentTimeMillis()
        val parsed = SessionNotificationParser.parseStopNotification(title, text, subText, now) ?: return false

        val appName = NotificationHelper.getAppNameForPackage(packageName)
        val pending = com.parktimedetector.data.PendingParkingStopDetection(
            packageName = packageName,
            appName = appName,
            zoneOrLot = parsed.zoneOrLot,
            stopTimeMillis = parsed.stopTimeMillis,
            durationParkedText = parsed.durationParkedText,
            costOrRefundText = parsed.costOrRefundText,
            source = "$appName App (Stop Notif Simulated)",
            reason = parsed.detectedReason,
            rawData = "$title - $text"
        )
        com.parktimedetector.service.DetectionApprovalManager.requestStopApproval(getApplication(), pending)
        return true
    }

    fun updateAdvanceWarning(minutes: Int) {
        viewModelScope.launch {
            prefsRepo.setAdvanceWarningMinutes(minutes)
            val current = dao.getActiveSession()
            if (current != null && current.isActive) {
                val updated = current.copy(advanceWarningMinutes = minutes, isNotifiedAdvance = false)
                dao.update(updated)
                ParkingAlarmScheduler.scheduleAlarms(getApplication(), updated)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val current = dao.getActiveSession()
            if (current != null) {
                ParkingAlarmScheduler.cancelAlarms(getApplication(), current.id)
                NotificationHelper.cancelStatusNotification(getApplication())
            }
            dao.clearAll()
        }
    }

    fun simulateParkingNotification(
        title: String,
        text: String,
        packageName: String = NotificationHelper.PARKEDIN_PACKAGE,
        subText: String? = null
    ): Boolean {
        val now = System.currentTimeMillis()
        val parsed = SessionNotificationParser.parse(title, text, subText, now) ?: return false

        val appName = NotificationHelper.getAppNameForPackage(packageName)
        val pending = com.parktimedetector.data.PendingParkingDetection(
            packageName = packageName,
            appName = appName,
            zoneOrLot = parsed.zoneOrLot,
            endTimeMillis = parsed.endTimeMillis,
            source = "$appName App (Simulated)",
            reason = parsed.detectedReason,
            rawData = "$title - $text"
        )
        com.parktimedetector.service.DetectionApprovalManager.requestApprovalOrStart(getApplication(), pending)
        return true
    }

    fun simulateParkedInNotification(title: String, text: String, subText: String? = null): Boolean {
        return simulateParkingNotification(title, text, NotificationHelper.PARKEDIN_PACKAGE, subText)
    }

    fun simulateMyParkingNotification(title: String, text: String, subText: String? = null): Boolean {
        return simulateParkingNotification(title, text, NotificationHelper.MYPARKING_PACKAGE, subText)
    }

    fun openTargetApp(context: Context, packageName: String) {
        val resolvedPkg = when {
            packageName == NotificationHelper.MYPARKING_PACKAGE ||
            packageName.contains("cpa", ignoreCase = true) ||
            packageName.contains("myparking", ignoreCase = true) -> NotificationHelper.MYPARKING_PACKAGE
            else -> NotificationHelper.PARKEDIN_PACKAGE
        }
        val appName = NotificationHelper.getAppNameForPackage(resolvedPkg)

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(resolvedPkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            Toast.makeText(
                context,
                "$appName app not installed. Opening Google Play Store...",
                Toast.LENGTH_SHORT
            ).show()
            try {
                val marketIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$resolvedPkg")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(marketIntent)
            } catch (_: Exception) {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$resolvedPkg")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(webIntent)
            }
        }
    }

    fun openParkedInApp(context: Context) {
        com.parktimedetector.service.ManualDetectionManager.startOrResetDetectionWindow(context, "ParkedIn App Button")
        openTargetApp(context, NotificationHelper.PARKEDIN_PACKAGE)
    }

    fun openMyParkingApp(context: Context) {
        com.parktimedetector.service.ManualDetectionManager.startOrResetDetectionWindow(context, "MyParking App Button")
        openTargetApp(context, NotificationHelper.MYPARKING_PACKAGE)
    }

    fun toggleEnableQuickRenewAutomation(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setEnableQuickRenewAutomation(enabled)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Quick-Renew automation set to: $enabled")
        }
    }

    fun togglePauseBeforePayment(paused: Boolean) {
        viewModelScope.launch {
            prefsRepo.setPauseBeforePayment(paused)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Pause before payment set to: $paused")
        }
    }

    fun launchQuickRenew(context: Context, session: ParkingSession) {
        val autoEnabled = enableQuickRenewAutomation.value
        val resolvedPkg = when {
            session.packageName == NotificationHelper.MYPARKING_PACKAGE ||
            session.packageName.contains("cpa", ignoreCase = true) ||
            session.packageName.contains("myparking", ignoreCase = true) -> NotificationHelper.MYPARKING_PACKAGE
            else -> NotificationHelper.PARKEDIN_PACKAGE
        }

        if (autoEnabled) {
            com.parktimedetector.service.QuickRenewManager.arm(
                context = context,
                packageName = resolvedPkg,
                zoneOrLot = session.zoneOrLot,
                locationAddress = session.locationAddress
            )
        }

        openTargetApp(context, resolvedPkg)
    }

    fun disarmQuickRenew() {
        com.parktimedetector.service.QuickRenewManager.disarm()
    }
}
