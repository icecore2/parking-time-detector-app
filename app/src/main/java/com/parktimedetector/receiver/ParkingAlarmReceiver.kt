package com.parktimedetector.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parktimedetector.data.ParkingDatabase
import com.parktimedetector.notification.NotificationHelper
import com.parktimedetector.service.ParkingAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ParkingAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ADVANCE_WARNING = "com.parktimedetector.ACTION_ADVANCE_WARNING"
        const val ACTION_WALK_BUFFER_WARNING = "com.parktimedetector.ACTION_WALK_BUFFER_WARNING"
        const val ACTION_CRITICAL_WARNING = "com.parktimedetector.ACTION_CRITICAL_WARNING"
        const val ACTION_EXPIRED = "com.parktimedetector.ACTION_EXPIRED"
        const val ACTION_CANCEL_SESSION = "com.parktimedetector.ACTION_CANCEL_SESSION"
        const val ACTION_STOP_ALARM = "com.parktimedetector.ACTION_STOP_ALARM"
        const val ACTION_EXTEND_ALARM = "com.parktimedetector.ACTION_EXTEND_ALARM"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_EXTEND_MINUTES = "extra_extend_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == ACTION_STOP_ALARM) {
            com.parktimedetector.audio.AlarmSoundManager.stop(context)
            return
        }

        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        if (sessionId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = ParkingDatabase.getDatabase(context)
                val dao = db.parkingDao()
                val session = dao.getSessionById(sessionId)

                when (action) {
                    ACTION_ADVANCE_WARNING -> {
                        if (session != null && session.isActive) {
                            NotificationHelper.showAdvanceWarningNotification(context, session)
                            dao.update(session.copy(isNotifiedAdvance = true))
                        }
                    }
                    ACTION_WALK_BUFFER_WARNING -> {
                        if (session != null && session.isActive) {
                            NotificationHelper.showWalkBufferNotification(context, session)
                            dao.update(session.copy(isNotifiedWalkBuffer = true))
                        }
                    }
                    ACTION_CRITICAL_WARNING -> {
                        if (session != null && session.isActive) {
                            NotificationHelper.showCriticalWarningNotification(context, session)
                            com.parktimedetector.audio.AlarmSoundManager.startAlarm(context)
                            dao.update(session.copy(isNotifiedCritical = true))
                        }
                    }
                    ACTION_EXPIRED -> {
                        if (session != null) {
                            NotificationHelper.showExpiredNotification(context, session)
                            com.parktimedetector.audio.AlarmSoundManager.startAlarm(context)
                            dao.update(session.copy(isActive = false, isNotifiedExpiry = true))
                            if (session.calendarEventId != null) {
                                com.parktimedetector.calendar.CalendarSyncManager.truncateEventToStop(context, session.calendarEventId, session.endTimeMillis)
                            }
                            com.parktimedetector.widget.ParkingWidgetManager.updateAll(context)
                        }
                    }
                    ACTION_EXTEND_ALARM -> {
                        com.parktimedetector.audio.AlarmSoundManager.stop(context)
                        if (session != null && session.isActive) {
                            val extraMinutes = intent.getIntExtra(EXTRA_EXTEND_MINUTES, 15)
                            val newEndTime = session.endTimeMillis + (extraMinutes * 60 * 1000L)
                            val updated = session.copy(
                                endTimeMillis = newEndTime,
                                isNotifiedAdvance = false,
                                isNotifiedWalkBuffer = false,
                                isNotifiedCritical = false,
                                isNotifiedExpiry = false
                            )
                            dao.update(updated)
                            ParkingAlarmScheduler.scheduleAlarms(context, updated)
                            NotificationHelper.showActiveCountdownNotification(context, updated)
                            NotificationHelper.cancelCriticalWarningNotification(context)
                            NotificationHelper.cancelAdvanceWarningNotification(context)
                            NotificationHelper.cancelWalkBufferNotification(context)

                            if (session.calendarEventId != null) {
                                com.parktimedetector.calendar.CalendarSyncManager.updateEventEndTime(context, session.calendarEventId, newEndTime)
                            }
                            com.parktimedetector.widget.ParkingWidgetManager.updateAll(context)
                        }
                    }
                    ACTION_CANCEL_SESSION -> {
                        com.parktimedetector.audio.AlarmSoundManager.stop(context)
                        dao.deactivateSession(sessionId)
                        ParkingAlarmScheduler.cancelAlarms(context, sessionId)
                        NotificationHelper.cancelStatusNotification(context)
                        NotificationHelper.cancelCriticalWarningNotification(context)
                        NotificationHelper.cancelAdvanceWarningNotification(context)
                        NotificationHelper.cancelWalkBufferNotification(context)

                        if (session != null && session.calendarEventId != null) {
                            com.parktimedetector.calendar.CalendarSyncManager.truncateEventToStop(context, session.calendarEventId, System.currentTimeMillis())
                        }
                        com.parktimedetector.widget.ParkingWidgetManager.updateAll(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
