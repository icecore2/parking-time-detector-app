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

    val autoShowFloatingRatesOnMap: StateFlow<Boolean> = prefsRepo.autoShowFloatingRatesOnMap
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

    val soundAlerts: StateFlow<Boolean> = prefsRepo.soundAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val vibrationAlerts: StateFlow<Boolean> = prefsRepo.vibrationAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val alarmSoundType: StateFlow<String> = prefsRepo.alarmSoundType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.parktimedetector.audio.SoundProfileType.SYSTEM_ALARM.name)

    val customAlarmUri: StateFlow<String?> = prefsRepo.customAlarmUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val customAlarmTitle: StateFlow<String?> = prefsRepo.customAlarmTitle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val volumeEscalationEnabled: StateFlow<Boolean> = prefsRepo.volumeEscalationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val escalationDurationSeconds: StateFlow<Int> = prefsRepo.escalationDurationSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesRepository.DEFAULT_ESCALATION_SECONDS)

    val criticalWarningMinutes: StateFlow<Int> = prefsRepo.criticalWarningMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesRepository.DEFAULT_CRITICAL_MINUTES)

    val persistentVibrationEnabled: StateFlow<Boolean> = prefsRepo.persistentVibrationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val vibrationPatternType: StateFlow<String> = prefsRepo.vibrationPatternType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.parktimedetector.audio.VibrationPatternType.URGENT_PULSE.name)

    val hapticFeedbackEnabled: StateFlow<Boolean> = prefsRepo.hapticFeedbackEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val themeMode: StateFlow<com.parktimedetector.data.AppThemeMode> = prefsRepo.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.parktimedetector.data.AppThemeMode.SYSTEM)

    val walkingBufferMinutes: StateFlow<Int> = prefsRepo.walkingBufferMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesRepository.DEFAULT_WALKING_BUFFER_MINUTES)

    val favoriteZones: StateFlow<List<com.parktimedetector.data.FavoriteZone>> = prefsRepo.favoriteZones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.parktimedetector.data.FavoriteZone.defaultFavorites())

    val isAlarmPlaying: StateFlow<Boolean> = com.parktimedetector.audio.AlarmSoundManager.isPlaying

    val syncCalendarEnabled: StateFlow<Boolean> = prefsRepo.syncCalendarEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val selectedCalendarId: StateFlow<Long?> = prefsRepo.selectedCalendarId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedCalendarName: StateFlow<String?> = prefsRepo.selectedCalendarName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _availableCalendars = kotlinx.coroutines.flow.MutableStateFlow<List<com.parktimedetector.calendar.CalendarInfo>>(emptyList())
    val availableCalendars: StateFlow<List<com.parktimedetector.calendar.CalendarInfo>> = _availableCalendars

    fun refreshAvailableCalendars() {
        viewModelScope.launch {
            _availableCalendars.value = com.parktimedetector.calendar.CalendarSyncManager.getAvailableCalendars(getApplication())
        }
    }

    fun toggleSyncCalendar(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setSyncCalendarEnabled(enabled)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Calendar sync enabled: $enabled")
            if (enabled) {
                refreshAvailableCalendars()
            }
        }
    }

    fun selectCalendar(calendarId: Long?, calendarName: String?) {
        viewModelScope.launch {
            prefsRepo.setSelectedCalendar(calendarId, calendarName)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Selected calendar: $calendarName (ID: $calendarId)")
        }
    }

    fun setThemeMode(mode: com.parktimedetector.data.AppThemeMode) {
        viewModelScope.launch {
            prefsRepo.setThemeMode(mode)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Theme mode set to: ${mode.name}")
        }
    }

    fun setWalkingBufferMinutes(minutes: Int) {
        viewModelScope.launch {
            prefsRepo.setWalkingBufferMinutes(minutes)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Walking buffer set to: ${minutes}m")
            val current = dao.getActiveSession()
            if (current != null && current.isActive) {
                val updated = current.copy(walkingBufferMinutes = minutes, isNotifiedWalkBuffer = false)
                dao.update(updated)
                ParkingAlarmScheduler.scheduleAlarms(getApplication(), updated)
            }
        }
    }

    fun addFavoriteZone(name: String, durationMinutes: Int = 60, notes: String? = null, lat: Double? = null, lng: Double? = null) {
        viewModelScope.launch {
            val newZone = com.parktimedetector.data.FavoriteZone(
                id = "fav_${System.currentTimeMillis()}",
                name = name,
                defaultDurationMinutes = durationMinutes,
                notes = notes,
                latitude = lat,
                longitude = lng
            )
            prefsRepo.addFavoriteZone(newZone)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Added favorite zone: $name")
        }
    }

    fun removeFavoriteZone(id: String) {
        viewModelScope.launch {
            prefsRepo.removeFavoriteZone(id)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Removed favorite zone ID: $id")
        }
    }

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

    fun toggleAutoShowFloatingRatesOnMap(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAutoShowFloatingRatesOnMap(enabled)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Auto-show floating rates on map set to: $enabled")
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
                com.parktimedetector.audio.AlarmSoundManager.startAlarm(getApplication())
                com.parktimedetector.data.AppLogger.info(getApplication(), "SIMULATOR", "Triggered expiry alert for session #${session.id}")
            }
        }
    }

    fun triggerCriticalAlarmNow() {
        viewModelScope.launch {
            val session = dao.getActiveSession()
            if (session != null) {
                NotificationHelper.showCriticalWarningNotification(getApplication(), session)
                com.parktimedetector.audio.AlarmSoundManager.startAlarm(getApplication())
                com.parktimedetector.data.AppLogger.info(getApplication(), "SIMULATOR", "Triggered critical warning alert for session #${session.id}")
            } else {
                // If no active session, synthesize a temporary session for alarm testing
                val dummy = ParkingSession(
                    id = 9999L,
                    packageName = NotificationHelper.PARKEDIN_PACKAGE,
                    source = "Simulator Test",
                    zoneOrLot = "Zone 4022",
                    startTimeMillis = System.currentTimeMillis() - (58 * 60 * 1000L),
                    endTimeMillis = System.currentTimeMillis() + (2 * 60 * 1000L),
                    isActive = true
                )
                NotificationHelper.showCriticalWarningNotification(getApplication(), dummy)
                com.parktimedetector.audio.AlarmSoundManager.startAlarm(getApplication())
                com.parktimedetector.data.AppLogger.info(getApplication(), "SIMULATOR", "Triggered critical warning alert with test session")
            }
        }
    }

    fun stopAlarmNow() {
        com.parktimedetector.audio.AlarmSoundManager.stop(getApplication())
    }

    fun previewAlarmSound(
        profile: com.parktimedetector.audio.SoundProfileType,
        customUriString: String?,
        escalationEnabled: Boolean,
        escalationSeconds: Int,
        vibrationEnabled: Boolean,
        vibrationPattern: com.parktimedetector.audio.VibrationPatternType
    ) {
        com.parktimedetector.audio.AlarmSoundManager.previewAlarm(
            context = getApplication(),
            profile = profile,
            customUriString = customUriString,
            escalationEnabled = escalationEnabled,
            escalationSeconds = escalationSeconds,
            vibrationEnabled = vibrationEnabled,
            vibrationPattern = vibrationPattern
        )
    }

    fun previewVibrationPattern(pattern: com.parktimedetector.audio.VibrationPatternType) {
        com.parktimedetector.audio.VibrationHelper.previewPatternOnce(getApplication(), pattern)
    }

    fun toggleSoundAlerts(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setSoundAlerts(enabled)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Sound alerts set to: $enabled")
        }
    }

    fun toggleVibrationAlerts(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setVibrationAlerts(enabled)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Vibration alerts set to: $enabled")
        }
    }

    fun setAlarmSoundType(type: com.parktimedetector.audio.SoundProfileType) {
        viewModelScope.launch {
            prefsRepo.setAlarmSoundType(type)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Alarm sound profile set to: ${type.name}")
        }
    }

    fun setCustomAlarmTone(uriString: String?, title: String?) {
        viewModelScope.launch {
            prefsRepo.setCustomAlarmTone(uriString, title)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Custom alarm tone set: $title ($uriString)")
        }
    }

    fun toggleVolumeEscalation(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setVolumeEscalationEnabled(enabled)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Volume escalation set to: $enabled")
        }
    }

    fun setEscalationDurationSeconds(seconds: Int) {
        viewModelScope.launch {
            prefsRepo.setEscalationDurationSeconds(seconds)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Escalation duration set to: ${seconds}s")
        }
    }

    fun setCriticalWarningMinutes(minutes: Int) {
        viewModelScope.launch {
            prefsRepo.setCriticalWarningMinutes(minutes)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Critical warning threshold set to: ${minutes}m")
            val current = dao.getActiveSession()
            if (current != null && current.isActive) {
                ParkingAlarmScheduler.scheduleAlarms(getApplication(), current, minutes)
            }
        }
    }

    fun togglePersistentVibration(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setPersistentVibrationEnabled(enabled)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Persistent vibration set to: $enabled")
        }
    }

    fun setVibrationPatternType(type: com.parktimedetector.audio.VibrationPatternType) {
        viewModelScope.launch {
            prefsRepo.setVibrationPatternType(type)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Vibration pattern set to: ${type.name}")
        }
    }

    fun toggleHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setHapticFeedbackEnabled(enabled)
            com.parktimedetector.data.AppLogger.info(getApplication(), "SETTINGS", "Haptic feedback set to: $enabled")
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

    fun startManualSession(
        durationMinutes: Int,
        zoneOrLot: String?,
        spotDetails: String? = null,
        parkedLat: Double? = null,
        parkedLng: Double? = null
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val endTime = now + (durationMinutes * 60 * 1000L)
            val advance = advanceWarningMinutes.first()
            val walkBuffer = walkingBufferMinutes.first()

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
                walkingBufferMinutes = walkBuffer,
                spotDetails = spotDetails?.takeIf { it.isNotBlank() },
                parkedLatitude = parkedLat,
                parkedLongitude = parkedLng,
                isActive = true
            )

            val id = dao.insert(newSession)
            val savedSession = newSession.copy(id = id)

            if (parkedLat != null && parkedLng != null && !zoneOrLot.isNullOrBlank()) {
                com.parktimedetector.location.ZoneDiscoveryManager.registerDiscoveredZone(
                    zoneOrLot = zoneOrLot,
                    address = null,
                    lat = parkedLat,
                    lng = parkedLng,
                    durationMinutes = durationMinutes
                )
            }

            // Sync with Google/Device Calendar if enabled
            val eventId = com.parktimedetector.calendar.CalendarSyncManager.createParkingEvent(getApplication(), savedSession)
            val finalSession = if (eventId != null) {
                dao.updateCalendarEventId(id, eventId)
                savedSession.copy(calendarEventId = eventId)
            } else {
                savedSession
            }

            ParkingAlarmScheduler.scheduleAlarms(getApplication(), finalSession)
            NotificationHelper.showActiveCountdownNotification(getApplication(), finalSession)
            com.parktimedetector.widget.ParkingWidgetManager.updateAll(getApplication())
        }
    }

    fun extendCurrentSession(extraMinutes: Int) {
        viewModelScope.launch {
            val current = dao.getActiveSession() ?: return@launch
            val newEndTime = current.endTimeMillis + (extraMinutes * 60 * 1000L)
            val updated = current.copy(
                endTimeMillis = newEndTime,
                isNotifiedAdvance = false,
                isNotifiedWalkBuffer = false,
                isNotifiedCritical = false,
                isNotifiedExpiry = false
            )
            dao.update(updated)

            ParkingAlarmScheduler.scheduleAlarms(getApplication(), updated)
            NotificationHelper.showActiveCountdownNotification(getApplication(), updated)
            NotificationHelper.cancelCriticalWarningNotification(getApplication())
            NotificationHelper.cancelAdvanceWarningNotification(getApplication())
            NotificationHelper.cancelWalkBufferNotification(getApplication())

            if (current.calendarEventId != null) {
                com.parktimedetector.calendar.CalendarSyncManager.updateEventEndTime(getApplication(), current.calendarEventId, newEndTime)
            }
            com.parktimedetector.widget.ParkingWidgetManager.updateAll(getApplication())
        }
    }

    fun extendCurrentSessionToTargetTime(targetEndTimeMillis: Long) {
        viewModelScope.launch {
            val current = dao.getActiveSession() ?: return@launch
            if (targetEndTimeMillis <= System.currentTimeMillis()) return@launch
            val updated = current.copy(
                endTimeMillis = targetEndTimeMillis,
                isNotifiedAdvance = false,
                isNotifiedWalkBuffer = false,
                isNotifiedCritical = false,
                isNotifiedExpiry = false
            )
            dao.update(updated)

            ParkingAlarmScheduler.scheduleAlarms(getApplication(), updated)
            NotificationHelper.showActiveCountdownNotification(getApplication(), updated)
            NotificationHelper.cancelCriticalWarningNotification(getApplication())
            NotificationHelper.cancelAdvanceWarningNotification(getApplication())
            NotificationHelper.cancelWalkBufferNotification(getApplication())

            if (current.calendarEventId != null) {
                com.parktimedetector.calendar.CalendarSyncManager.updateEventEndTime(getApplication(), current.calendarEventId, targetEndTimeMillis)
            }
            com.parktimedetector.widget.ParkingWidgetManager.updateAll(getApplication())
        }
    }

    fun updateActiveSessionSpotDetails(spot: String) {
        viewModelScope.launch {
            val current = dao.getActiveSession() ?: return@launch
            dao.updateSpotDetails(current.id, spot)
            val updated = current.copy(spotDetails = spot)
            NotificationHelper.showActiveCountdownNotification(getApplication(), updated)
        }
    }

    fun updateActiveSessionLocation(lat: Double, lng: Double) {
        viewModelScope.launch {
            val current = dao.getActiveSession() ?: return@launch
            dao.updateParkedCoordinates(current.id, lat, lng)
            if (!current.zoneOrLot.isNullOrBlank()) {
                com.parktimedetector.location.ZoneDiscoveryManager.registerDiscoveredZone(
                    zoneOrLot = current.zoneOrLot,
                    address = current.locationAddress,
                    lat = lat,
                    lng = lng
                )
            }
        }
    }

    fun autoDetectNearbyZone(context: Context, onResult: (com.parktimedetector.location.DiscoveredZoneResult?) -> Unit) {
        val location = com.parktimedetector.location.LocationHelper.getLastKnownLocation(context)
        if (location == null) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val pastSessions = dao.getSessionsWithCoordinates()
            val favorites = favoriteZones.value
            val result = com.parktimedetector.location.ZoneDiscoveryManager.findNearestZone(
                currentLat = location.latitude,
                currentLng = location.longitude,
                maxRadiusMeters = 350.0,
                pastSessions = pastSessions,
                favoriteZones = favorites
            )
            onResult(result)
        }
    }

    fun exportParkingExpensesCsv(context: Context) {
        viewModelScope.launch {
            val sessions = sessionHistory.value
            com.parktimedetector.analytics.ParkingCsvExporter.exportAndShareCsv(context, sessions)
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
                if (current.calendarEventId != null) {
                    com.parktimedetector.calendar.CalendarSyncManager.truncateEventToStop(getApplication(), current.calendarEventId, System.currentTimeMillis())
                }
            }
            dao.clearAll()
            com.parktimedetector.widget.ParkingWidgetManager.updateAll(getApplication())
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
        val isMyParking = resolvedPkg == NotificationHelper.MYPARKING_PACKAGE
        val pm = context.packageManager
        val isAppInstalled = pm.getLaunchIntentForPackage(resolvedPkg) != null

        if (isAppInstalled) {
            if (autoEnabled) {
                com.parktimedetector.service.QuickRenewManager.arm(
                    context = context,
                    packageName = resolvedPkg,
                    zoneOrLot = session.zoneOrLot,
                    locationAddress = session.locationAddress,
                    appType = if (isMyParking) com.parktimedetector.service.RenewAppType.MYPARKING else com.parktimedetector.service.RenewAppType.PARKEDIN
                )
            }
            openTargetApp(context, resolvedPkg)
        } else {
            // Target app is not installed: Launch simulated mock app to test & validate Quick-Renew!
            launchMockApp(context, isMyParking, session)
        }
    }

    fun launchMockApp(context: Context, isMyParking: Boolean, session: ParkingSession? = null) {
        val targetClass = if (isMyParking) {
            com.parktimedetector.mock.MockMyParkingActivity::class.java
        } else {
            com.parktimedetector.mock.MockParkedInActivity::class.java
        }

        if (enableQuickRenewAutomation.value) {
            com.parktimedetector.service.QuickRenewManager.arm(
                context = context,
                packageName = context.packageName,
                zoneOrLot = session?.zoneOrLot ?: (if (isMyParking) "Lot 58 - 935 - 4 Av SW" else "Zone 4022"),
                locationAddress = session?.locationAddress,
                appType = if (isMyParking) com.parktimedetector.service.RenewAppType.MYPARKING else com.parktimedetector.service.RenewAppType.PARKEDIN
            )
        }

        val intent = Intent(context, targetClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun disarmQuickRenew() {
        com.parktimedetector.service.QuickRenewManager.disarm()
    }

    fun showFloatingZonesOverlay(context: Context, lat: Double = 51.0486, lng: Double = -114.0708) {
        com.parktimedetector.service.FloatingZonesOverlayService.onZoneSelectedCallback = { zone ->
            // Launch simulated mock or direct session on user selection
            val maxMins = (zone.maxTimeMinutes ?: 120).coerceAtLeast(30)
            launchMockApp(
                context = context,
                isMyParking = true,
                session = ParkingSession(
                    packageName = NotificationHelper.MYPARKING_PACKAGE,
                    source = "Cheapest Zones Overlay",
                    zoneOrLot = "Zone ${zone.zoneNumber}",
                    locationAddress = zone.address,
                    startTimeMillis = System.currentTimeMillis(),
                    endTimeMillis = System.currentTimeMillis() + (maxMins * 60 * 1000L),
                    advanceWarningMinutes = 15
                )
            )
        }
        com.parktimedetector.service.FloatingZonesOverlayService.show(context, lat, lng)
    }

    fun hideFloatingZonesOverlay() {
        com.parktimedetector.service.FloatingZonesOverlayService.hide()
    }

    // App Update Engine Integration
    val updateState: StateFlow<com.parktimedetector.update.UpdateState> =
        com.parktimedetector.update.AppUpdateEngine.updateState

    fun checkForUpdates(context: Context, silent: Boolean = false) {
        com.parktimedetector.update.AppUpdateEngine.checkForUpdates(context, silent = silent)
    }

    fun downloadUpdate(context: Context, updateInfo: com.parktimedetector.update.AppUpdateInfo) {
        com.parktimedetector.update.AppUpdateEngine.downloadUpdate(context, updateInfo)
    }

    fun cancelUpdateDownload(context: Context) {
        com.parktimedetector.update.AppUpdateEngine.cancelDownload(context)
    }

    fun installUpdate(context: Context, apkFile: java.io.File) {
        com.parktimedetector.update.AppUpdateEngine.installUpdate(context, apkFile)
    }

    fun dismissUpdate(context: Context? = null) {
        com.parktimedetector.update.AppUpdateEngine.dismissUpdate(context)
    }

    fun simulatePushUpdate(
        context: Context,
        version: String = "1.1.0",
        versionCode: Int = 2,
        downloadUrl: String = "https://github.com/icecore2/parking-time-detector-app/releases/download/v1.1.0/ParkingTimeDetector-v1.1.0.apk",
        changelog: String = "Simulated push update:\n- Added update engine\n- Performance enhancements",
        autoDownload: Boolean = false
    ) {
        val info = com.parktimedetector.update.AppUpdateInfo(
            versionName = version,
            versionCode = versionCode,
            title = "Push Update Available ($version)",
            changelog = changelog,
            downloadUrl = downloadUrl,
            fileName = "ParkingTimeDetector-$version.apk"
        )
        com.parktimedetector.update.AppUpdateEngine.processPushUpdate(
            context = context,
            updateInfo = info,
            autoDownload = autoDownload
        )
    }
}
