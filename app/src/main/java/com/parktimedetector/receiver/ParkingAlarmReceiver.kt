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
        const val ACTION_EXPIRED = "com.parktimedetector.ACTION_EXPIRED"
        const val ACTION_CANCEL_SESSION = "com.parktimedetector.ACTION_CANCEL_SESSION"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
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
                    ACTION_EXPIRED -> {
                        if (session != null) {
                            NotificationHelper.showExpiredNotification(context, session)
                            dao.update(session.copy(isActive = false, isNotifiedExpiry = true))
                        }
                    }
                    ACTION_CANCEL_SESSION -> {
                        dao.deactivateSession(sessionId)
                        ParkingAlarmScheduler.cancelAlarms(context, sessionId)
                        NotificationHelper.cancelStatusNotification(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
