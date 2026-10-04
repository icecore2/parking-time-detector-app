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

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = ParkingDatabase.getDatabase(context)
                    val activeSession = db.parkingDao().getActiveSession()
                    if (activeSession != null && activeSession.isActive && !activeSession.isExpired()) {
                        ParkingAlarmScheduler.scheduleAlarms(context, activeSession)
                        NotificationHelper.showActiveCountdownNotification(context, activeSession)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
