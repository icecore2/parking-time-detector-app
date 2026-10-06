package com.parktimedetector.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.parktimedetector.data.AppLogger
import com.parktimedetector.data.ParkingSession
import com.parktimedetector.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.util.TimeZone

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isPrimary: Boolean
)

object CalendarSyncManager {

    private const val TAG = "CalendarSyncManager"

    fun hasPermissions(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    fun getAvailableCalendars(context: Context): List<CalendarInfo> {
        if (!hasPermissions(context)) return emptyList()

        val calendars = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY
        )

        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndex(CalendarContract.Calendars._ID)
                val nameCol = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountCol = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val primaryCol = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = if (nameCol != -1) it.getString(nameCol) ?: "Calendar" else "Calendar"
                    val account = if (accountCol != -1) it.getString(accountCol) ?: "" else ""
                    val isPrimary = if (primaryCol != -1) it.getInt(primaryCol) == 1 else false

                    calendars.add(
                        CalendarInfo(
                            id = id,
                            displayName = name,
                            accountName = account,
                            isPrimary = isPrimary
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying available calendars", e)
        }

        return calendars
    }

    suspend fun createParkingEvent(context: Context, session: ParkingSession): Long? {
        if (!hasPermissions(context)) {
            Log.d(TAG, "Skipping calendar event creation: Missing calendar permissions")
            return null
        }

        val prefs = UserPreferencesRepository(context)
        val syncEnabled = prefs.syncCalendarEnabled.first()
        if (!syncEnabled) {
            Log.d(TAG, "Skipping calendar event creation: Calendar sync is disabled")
            return null
        }

        val availableCalendars = getAvailableCalendars(context)
        if (availableCalendars.isEmpty()) {
            Log.w(TAG, "No writable calendars found on device")
            return null
        }

        val targetCalId = prefs.selectedCalendarId.first()
        val chosenCalendar = availableCalendars.find { it.id == targetCalId }
            ?: availableCalendars.find { it.isPrimary }
            ?: availableCalendars.first()

        try {
            val title = if (!session.zoneOrLot.isNullOrBlank()) {
                "🅿️ Parking: ${session.zoneOrLot}"
            } else {
                "🅿️ Parking Session"
            }

            val descBuilder = StringBuilder()
            descBuilder.appendLine("Parking session tracked by Parking Time Detector.")
            descBuilder.appendLine("Source: ${session.source}")
            if (!session.zoneOrLot.isNullOrBlank()) {
                descBuilder.appendLine("Zone/Lot: ${session.zoneOrLot}")
            }
            if (!session.spotDetails.isNullOrBlank()) {
                descBuilder.appendLine("Spot: ${session.spotDetails}")
            }
            if (!session.locationAddress.isNullOrBlank()) {
                descBuilder.appendLine("Location: ${session.locationAddress}")
            }
            if (!session.notesText.isNullOrBlank()) {
                descBuilder.appendLine("Notes: ${session.notesText}")
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, chosenCalendar.id)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, descBuilder.toString().trim())
                put(CalendarContract.Events.EVENT_LOCATION, session.displayLocation)
                put(CalendarContract.Events.DTSTART, session.startTimeMillis)
                put(CalendarContract.Events.DTEND, session.endTimeMillis)
                put(CalendarContract.Events.ALL_DAY, 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                // AVAILABILITY_BUSY prevents overlapping meetings in Google Calendar
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                val eventId = ContentUris.parseId(uri)
                AppLogger.info(
                    context = context,
                    tag = "CALENDAR_SYNC",
                    message = "Created calendar event #$eventId in '${chosenCalendar.displayName}' (Busy slot: ${session.displayLocation})"
                )
                return eventId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting parking calendar event", e)
            AppLogger.error(context, "CALENDAR_SYNC", "Failed to create calendar event: ${e.message}")
        }

        return null
    }

    fun updateEventEndTime(context: Context, eventId: Long, newEndTimeMillis: Long) {
        if (!hasPermissions(context)) return

        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTEND, newEndTimeMillis)
            }
            val count = context.contentResolver.update(uri, values, null, null)
            if (count > 0) {
                AppLogger.info(
                    context = context,
                    tag = "CALENDAR_SYNC",
                    message = "Updated calendar event #$eventId end time to $newEndTimeMillis"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating event #$eventId end time", e)
        }
    }

    fun truncateEventToStop(context: Context, eventId: Long, stopTimeMillis: Long) {
        if (!hasPermissions(context)) return

        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTEND, stopTimeMillis)
            }
            val count = context.contentResolver.update(uri, values, null, null)
            if (count > 0) {
                AppLogger.info(
                    context = context,
                    tag = "CALENDAR_SYNC",
                    message = "Truncated calendar event #$eventId end time to stop time $stopTimeMillis (freed upcoming schedule slots)"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error truncating event #$eventId on session stop", e)
        }
    }

    fun deleteEvent(context: Context, eventId: Long) {
        if (!hasPermissions(context)) return

        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val count = context.contentResolver.delete(uri, null, null)
            if (count > 0) {
                AppLogger.info(
                    context = context,
                    tag = "CALENDAR_SYNC",
                    message = "Deleted calendar event #$eventId"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting event #$eventId", e)
        }
    }
}
