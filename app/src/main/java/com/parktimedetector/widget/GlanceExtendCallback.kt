package com.parktimedetector.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.parktimedetector.audio.AlarmSoundManager
import com.parktimedetector.calendar.CalendarSyncManager
import com.parktimedetector.data.AppLogger
import com.parktimedetector.data.ParkingDatabase
import com.parktimedetector.notification.NotificationHelper
import com.parktimedetector.service.ParkingAlarmScheduler

class GlanceExtendCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val db = ParkingDatabase.getDatabase(context)
        val dao = db.parkingDao()
        val session = dao.getActiveSession()

        if (session != null && session.isActive) {
            AlarmSoundManager.stop(context)
            val newEndTime = session.endTimeMillis + (15 * 60 * 1000L)
            val updated = session.copy(
                endTimeMillis = newEndTime,
                isNotifiedAdvance = false,
                isNotifiedWalkBuffer = false,
                isNotifiedCritical = false,
                isNotifiedExpiry = false
            )
            dao.update(updated)

            // Reschedule system alarms
            ParkingAlarmScheduler.scheduleAlarms(context, updated)

            // Update status bar notification
            NotificationHelper.showActiveCountdownNotification(context, updated)
            NotificationHelper.cancelCriticalWarningNotification(context)
            NotificationHelper.cancelAdvanceWarningNotification(context)
            NotificationHelper.cancelWalkBufferNotification(context)

            // Update calendar event if synced
            if (session.calendarEventId != null) {
                CalendarSyncManager.updateEventEndTime(context, session.calendarEventId, newEndTime)
            }

            AppLogger.info(
                context = context,
                tag = "WIDGET_EXTEND",
                message = "Extended session #${session.id} by +15m directly from Home Screen Widget"
            )

            // Refresh all widget instances
            ParkingGlanceWidget().updateAll(context)
        }
    }
}
