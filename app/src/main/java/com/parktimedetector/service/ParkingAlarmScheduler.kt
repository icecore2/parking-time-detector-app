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

    fun scheduleAlarms(context: Context, session: ParkingSession, criticalWarningMinutes: Int = 2) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()

        // 1. Advance Warning Alarm (e.g. 15 mins before expiry)
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

        // 2. Walking Buffer Alarm (e.g. 5-15 mins before expiry)
        if (session.walkingBufferMinutes > 0) {
            val walkTimeMillis = session.endTimeMillis - (session.walkingBufferMinutes * 60 * 1000L)
            if (walkTimeMillis > now && !session.isNotifiedWalkBuffer) {
                val walkIntent = Intent(context, ParkingAlarmReceiver::class.java).apply {
                    action = ParkingAlarmReceiver.ACTION_WALK_BUFFER_WARNING
                    putExtra(ParkingAlarmReceiver.EXTRA_SESSION_ID, session.id)
                }
                val walkPI = PendingIntent.getBroadcast(
                    context,
                    (session.id * 10 + 4).toInt(),
                    walkIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setExactAlarm(alarmManager, walkTimeMillis, walkPI)
            }
        }

        // 3. Critical Warning Alarm (e.g. 2 mins before expiry)
        val criticalTimeMillis = session.endTimeMillis - (criticalWarningMinutes * 60 * 1000L)
        if (criticalTimeMillis > now && !session.isNotifiedCritical) {
            val criticalIntent = Intent(context, ParkingAlarmReceiver::class.java).apply {
                action = ParkingAlarmReceiver.ACTION_CRITICAL_WARNING
                putExtra(ParkingAlarmReceiver.EXTRA_SESSION_ID, session.id)
            }
            val criticalPI = PendingIntent.getBroadcast(
                context,
                (session.id * 10 + 3).toInt(),
                criticalIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            setExactAlarm(alarmManager, criticalTimeMillis, criticalPI)
        }

        // 3. Expiry Alarm (at expiry time)
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

        val criticalIntent = Intent(context, ParkingAlarmReceiver::class.java).apply {
            action = ParkingAlarmReceiver.ACTION_CRITICAL_WARNING
        }
        val criticalPI = PendingIntent.getBroadcast(
            context,
            (sessionId * 10 + 3).toInt(),
            criticalIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (criticalPI != null) {
            alarmManager.cancel(criticalPI)
            criticalPI.cancel()
        }

        val walkIntent = Intent(context, ParkingAlarmReceiver::class.java).apply {
            action = ParkingAlarmReceiver.ACTION_WALK_BUFFER_WARNING
        }
        val walkPI = PendingIntent.getBroadcast(
            context,
            (sessionId * 10 + 4).toInt(),
            walkIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (walkPI != null) {
            alarmManager.cancel(walkPI)
            walkPI.cancel()
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

        // Halt any playing alarm sound or vibration immediately
        com.parktimedetector.audio.AlarmSoundManager.stop(context)
        com.parktimedetector.notification.NotificationHelper.cancelCriticalWarningNotification(context)
    }
}
