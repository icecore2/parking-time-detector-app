package com.parktimedetector.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.parktimedetector.data.ParkingSession
import com.parktimedetector.receiver.ParkingAlarmReceiver

object ParkingAlarmScheduler {

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            return alarmManager?.canScheduleExactAlarms() ?: false
        }
        return true
    }

    fun scheduleAlarms(context: Context, session: ParkingSession) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()

        // 1. Advance Warning Alarm
        val advanceTimeMillis = session.endTimeMillis - (session.advanceWarningMinutes * 60 * 1000L)
        if (advanceTimeMillis > now && !session.isNotifiedAdvance) {
            val advanceIntent = Intent(context, ParkingAlarmReceiver::class.java).apply {
                action = ParkingAlarmReceiver.ACTION_ADVANCE_WARNING
                putExtra(ParkingAlarmReceiver.EXTRA_SESSION_ID, session.id)
            }
            val advancePI = PendingIntent.getBroadcast(
                context,
                (session.id * 10 + 1).toInt(),
                advanceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            setExactAlarm(alarmManager, advanceTimeMillis, advancePI)
        }

        // 2. Expiry Alarm
        if (session.endTimeMillis > now && !session.isNotifiedExpiry) {
            val expireIntent = Intent(context, ParkingAlarmReceiver::class.java).apply {
                action = ParkingAlarmReceiver.ACTION_EXPIRED
                putExtra(ParkingAlarmReceiver.EXTRA_SESSION_ID, session.id)
            }
            val expirePI = PendingIntent.getBroadcast(
                context,
                (session.id * 10 + 2).toInt(),
                expireIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            setExactAlarm(alarmManager, session.endTimeMillis, expirePI)
        }
    }

    private fun setExactAlarm(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            // Fallback if exact alarm permission was revoked
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancelAlarms(context: Context, sessionId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

        val advanceIntent = Intent(context, ParkingAlarmReceiver::class.java).apply {
            action = ParkingAlarmReceiver.ACTION_ADVANCE_WARNING
        }
        val advancePI = PendingIntent.getBroadcast(
            context,
            (sessionId * 10 + 1).toInt(),
            advanceIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (advancePI != null) {
            alarmManager.cancel(advancePI)
            advancePI.cancel()
        }

        val expireIntent = Intent(context, ParkingAlarmReceiver::class.java).apply {
            action = ParkingAlarmReceiver.ACTION_EXPIRED
        }
        val expirePI = PendingIntent.getBroadcast(
            context,
            (sessionId * 10 + 2).toInt(),
            expireIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (expirePI != null) {
            alarmManager.cancel(expirePI)
            expirePI.cancel()
        }
    }
}
