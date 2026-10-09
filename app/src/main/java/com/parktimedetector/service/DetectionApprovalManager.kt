package com.parktimedetector.service

import android.content.Context
import android.util.Log
import com.parktimedetector.data.AppLogger
import com.parktimedetector.data.ParkingDatabase
import com.parktimedetector.data.ParkingSession
import com.parktimedetector.data.PendingParkingDetection
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

object DetectionApprovalManager {

    private const val TAG = "DetectionApprovalMgr"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _pendingDetection = MutableStateFlow<PendingParkingDetection?>(null)
    val pendingDetection = _pendingDetection.asStateFlow()

    private val _pendingStopDetection = MutableStateFlow<com.parktimedetector.data.PendingParkingStopDetection?>(null)
    val pendingStopDetection = _pendingStopDetection.asStateFlow()

    private val _recentlyStoppedSession = MutableStateFlow<ParkingSession?>(null)
    val recentlyStoppedSession = _recentlyStoppedSession.asStateFlow()

    fun clearRecentlyStoppedSession() {
        _recentlyStoppedSession.value = null
    }

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground = _isAppInForeground.asStateFlow()

    fun setAppInForeground(inForeground: Boolean) {
        if (_isAppInForeground.value != inForeground) {
            _isAppInForeground.value = inForeground
            overlayCallback?.onAppForegroundStateChanged(inForeground)
        }
    }

    interface OverlayCallback {
        fun showOverlay(detection: PendingParkingDetection)
        fun showStopOverlay(detection: com.parktimedetector.data.PendingParkingStopDetection)
        fun hideOverlay()
        fun isBubbleCollapsed(): Boolean = false
        fun onAppForegroundStateChanged(inForeground: Boolean) {}
    }

    var overlayCallback: OverlayCallback? = null

    fun isBubbleCollapsed(): Boolean = overlayCallback?.isBubbleCollapsed() == true

    fun autoStartSession(context: Context, detection: PendingParkingDetection) {
        scope.launch {
            _pendingDetection.value = null
            overlayCallback?.hideOverlay()
            NotificationHelper.cancelDetectionApprovalNotification(context)
            startApprovedSession(context, detection)
        }
    }

    fun recordDirectDeactivation(
        context: Context,
        stopDetection: com.parktimedetector.data.PendingParkingStopDetection
    ) {
        _pendingStopDetection.value = stopDetection
        approveCurrentStopDetection(context)
    }


    fun requestApprovalOrStart(context: Context, detection: PendingParkingDetection) {
        scope.launch {
            try {
                val prefsRepo = UserPreferencesRepository(context)
                val requireApproval = prefsRepo.requireApproval.first()

                if (!requireApproval) {
                    startApprovedSession(context, detection)
                    return@launch
                }

                _pendingDetection.value = detection

                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                val formattedEnd = timeFormat.format(Date(detection.endTimeMillis))
                val zoneText = detection.zoneOrLot?.let { " • $it" } ?: ""

                AppLogger.info(
                    context = context,
                    tag = "DETECTION_PENDING",
                    message = "Awaiting user approval for ${detection.appName} parking$zoneText valid until $formattedEnd (${detection.durationMinutes}m duration).",
                    packageName = detection.packageName,
                    rawData = detection.rawData
                )

                // 1. Check if on-screen dialogs are enabled
                val showDialogWindows = prefsRepo.showConfirmationDialogs.first()

                // 2. Show notification with action buttons (Quiet if dialog is active on screen to prevent collision)
                NotificationHelper.showDetectionApprovalNotification(context, detection, isQuietMode = showDialogWindows)

                // 3. Request floating overlay dialog ONLY if user enabled confirmation dialog windows
                if (showDialogWindows) {
                    overlayCallback?.showOverlay(detection)
                } else {
                    Log.d(TAG, "Confirmation dialog windows disabled; relying on built-in notifications")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in requestApprovalOrStart", e)
            }
        }
    }

    fun approveCurrentDetection(context: Context) {
        val detection = _pendingDetection.value ?: return
        _pendingDetection.value = null

        overlayCallback?.hideOverlay()
        NotificationHelper.cancelDetectionApprovalNotification(context)

        scope.launch {
            startApprovedSession(context, detection)

            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val formattedEnd = timeFormat.format(Date(detection.endTimeMillis))
            AppLogger.success(
                context = context,
                tag = "SESSION_APPROVED",
                message = "User accepted parking detection for ${detection.appName}! Timer started until $formattedEnd.",
                packageName = detection.packageName
            )
        }
    }

    fun dismissCurrentDetection(context: Context) {
        val detection = _pendingDetection.value
        _pendingDetection.value = null

        overlayCallback?.hideOverlay()
        NotificationHelper.cancelDetectionApprovalNotification(context)

        if (detection != null) {
            AppLogger.warn(
                context = context,
                tag = "SESSION_DISMISSED",
                message = "User dismissed parking detection for ${detection.appName}.",
                packageName = detection.packageName
            )
        }
    }

    private suspend fun startApprovedSession(context: Context, detection: PendingParkingDetection) {
        val prefsRepo = UserPreferencesRepository(context)
        val advanceMinutes = prefsRepo.advanceWarningMinutes.first()
        val db = ParkingDatabase.getDatabase(context)
        val dao = db.parkingDao()

        val currentActive = dao.getActiveSession()
        if (currentActive != null) {
            ParkingAlarmScheduler.cancelAlarms(context, currentActive.id)
        }
        dao.deactivateAllSessions()

        val newSession = ParkingSession(
            packageName = detection.packageName,
            source = detection.source,
            zoneOrLot = detection.zoneOrLot,
            startTimeMillis = detection.startTimeMillis,
            endTimeMillis = detection.endTimeMillis,
            advanceWarningMinutes = advanceMinutes,
            isActive = true,
            rawNotificationText = detection.rawData,
            locationAddress = detection.locationAddress,
            purchasedDurationText = detection.purchasedDurationText,
            initialCostText = detection.initialCostText,
            remainingTimeText = detection.remainingTimeText,
            notesText = detection.notesText
        )

        val newId = dao.insert(newSession)
        val savedSession = newSession.copy(id = newId)

        val eventId = com.parktimedetector.calendar.CalendarSyncManager.createParkingEvent(context, savedSession)
        val finalSession = if (eventId != null) {
            dao.updateCalendarEventId(newId, eventId)
            savedSession.copy(calendarEventId = eventId)
        } else {
            savedSession
        }

        ParkingAlarmScheduler.scheduleAlarms(context, finalSession)
        NotificationHelper.showActiveCountdownNotification(context, finalSession)
        com.parktimedetector.widget.ParkingWidgetManager.updateAll(context)
    }

    fun requestStopApproval(context: Context, stopDetection: com.parktimedetector.data.PendingParkingStopDetection) {
        scope.launch {
            try {
                _pendingStopDetection.value = stopDetection

                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                val formattedStop = timeFormat.format(Date(stopDetection.stopTimeMillis))
                val zoneText = stopDetection.zoneOrLot?.let { " • $it" } ?: ""
                val durationText = stopDetection.durationParkedText?.let { " ($it)" } ?: ""
                val costText = stopDetection.costOrRefundText?.let { " • $it" } ?: ""

                AppLogger.info(
                    context = context,
                    tag = "STOP_DETECTION_PENDING",
                    message = "Awaiting user approval to stop session for ${stopDetection.appName}$zoneText at $formattedStop$durationText$costText.",
                    packageName = stopDetection.packageName,
                    rawData = stopDetection.rawData
                )

                val prefsRepo = UserPreferencesRepository(context)
                val showDialogWindows = prefsRepo.showConfirmationDialogs.first()

                // 1. Show notification with action buttons (Quiet if dialog is active on screen to prevent collision)
                NotificationHelper.showStopApprovalNotification(context, stopDetection, isQuietMode = showDialogWindows)

                // 2. Request floating overlay dialog ONLY if user enabled confirmation dialog windows
                if (showDialogWindows) {
                    overlayCallback?.showStopOverlay(stopDetection)
                } else {
                    Log.d(TAG, "Confirmation dialog windows disabled; relying on built-in notifications")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in requestStopApproval", e)
            }
        }
    }

    fun approveCurrentStopDetection(context: Context) {
        val stopDetection = _pendingStopDetection.value ?: return
        _pendingStopDetection.value = null

        overlayCallback?.hideOverlay()
        NotificationHelper.cancelStopApprovalNotification(context)

        scope.launch {
            try {
                val db = ParkingDatabase.getDatabase(context)
                val dao = db.parkingDao()

                val activeSession = dao.getActiveSession() ?: dao.findMatchingRecentSession(
                    packageName = stopDetection.packageName,
                    zoneOrLot = stopDetection.zoneOrLot,
                    minStartTime = stopDetection.stopTimeMillis - (24 * 60 * 60 * 1000L)
                )

                if (activeSession != null) {
                    val location = stopDetection.locationAddress ?: activeSession.locationAddress
                    val cost = stopDetection.costOrRefundText ?: activeSession.costOrRefundText
                    val stopReason = stopDetection.stopReason ?: "User confirmed deactivation"

                    dao.endSessionWithFullDetails(
                        id = activeSession.id,
                        stopTime = stopDetection.stopTimeMillis,
                        costOrRefund = cost,
                        stopReason = stopReason,
                        locationAddress = location
                    )
                    ParkingAlarmScheduler.cancelAlarms(context, activeSession.id)
                    NotificationHelper.cancelStatusNotification(context)
                    NotificationHelper.cancelCriticalWarningNotification(context)
                    NotificationHelper.cancelAdvanceWarningNotification(context)
                    NotificationHelper.cancelWalkBufferNotification(context)
                    com.parktimedetector.audio.AlarmSoundManager.stop(context)

                    if (activeSession.calendarEventId != null) {
                        com.parktimedetector.calendar.CalendarSyncManager.truncateEventToStop(context, activeSession.calendarEventId, stopDetection.stopTimeMillis)
                    }

                    val stoppedSession = activeSession.copy(
                        isActive = false,
                        actualStopTimeMillis = stopDetection.stopTimeMillis,
                        costOrRefundText = cost,
                        locationAddress = location,
                        stopReason = stopReason
                    )
                    _recentlyStoppedSession.value = stoppedSession
                    com.parktimedetector.widget.ParkingWidgetManager.updateAll(context)
                } else {
                    // Even if no active session in DB, record a stopped session record
                    val newStopped = ParkingSession(
                        packageName = stopDetection.packageName,
                        source = stopDetection.source,
                        zoneOrLot = stopDetection.zoneOrLot,
                        startTimeMillis = stopDetection.stopTimeMillis - (30 * 60 * 1000L), // fallback start
                        endTimeMillis = stopDetection.stopTimeMillis,
                        advanceWarningMinutes = 15,
                        isActive = false,
                        actualStopTimeMillis = stopDetection.stopTimeMillis,
                        costOrRefundText = stopDetection.costOrRefundText,
                        locationAddress = stopDetection.locationAddress,
                        stopReason = stopDetection.stopReason ?: "Confirmed deactivation"
                    )
                    val id = dao.insert(newStopped)
                    _recentlyStoppedSession.value = newStopped.copy(id = id)
                    NotificationHelper.cancelStatusNotification(context)
                    NotificationHelper.cancelCriticalWarningNotification(context)
                    NotificationHelper.cancelAdvanceWarningNotification(context)
                    NotificationHelper.cancelWalkBufferNotification(context)
                    com.parktimedetector.audio.AlarmSoundManager.stop(context)
                }

                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                val formattedStop = timeFormat.format(Date(stopDetection.stopTimeMillis))
                AppLogger.success(
                    context = context,
                    tag = "SESSION_STOP_APPROVED",
                    message = "User accepted parking stop for ${stopDetection.appName}! Session ended at $formattedStop.",
                    packageName = stopDetection.packageName
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in approveCurrentStopDetection", e)
            }
        }
    }

    fun dismissCurrentStopDetection(context: Context) {
        val stopDetection = _pendingStopDetection.value
        _pendingStopDetection.value = null

        overlayCallback?.hideOverlay()
        NotificationHelper.cancelStopApprovalNotification(context)

        if (stopDetection != null) {
            AppLogger.warn(
                context = context,
                tag = "SESSION_STOP_DISMISSED",
                message = "User dismissed session stop for ${stopDetection.appName}.",
                packageName = stopDetection.packageName
            )
        }
    }
}
