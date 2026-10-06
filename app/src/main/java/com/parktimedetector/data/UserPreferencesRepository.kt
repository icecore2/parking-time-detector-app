package com.parktimedetector.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

enum class LockScreenOverlayPolicy(val title: String, val description: String) {
    FOLLOW_SYSTEM(
        "Follow System Settings (Recommended)",
        "Respects Android lock screen privacy and keyguard state; shows overlay once device is unlocked."
    ),
    HIDE_WHEN_LOCKED(
        "Never Show When Locked",
        "Strictly withholds floating dialogs whenever the device is locked."
    ),
    REDACT_WHEN_LOCKED(
        "Mask Sensitive Details",
        "Allows overlay when locked, but masks location and cost details until unlocked."
    )
}

enum class OverlayDialogPosition(val title: String, val description: String) {
    TOP(
        "Top Floating Card (Recommended)",
        "Floats near the top of the screen just below the status bar."
    ),
    CENTER(
        "Center Dialog",
        "Centered prominently in the middle of the screen."
    )
}

enum class AppThemeMode(val title: String) {
    SYSTEM("Follow System"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_WALKING_BUFFER_MINUTES = intPreferencesKey("walking_buffer_minutes")
        val KEY_FAVORITE_ZONES = stringPreferencesKey("favorite_zones_json")
        val KEY_ADVANCE_WARNING_MINUTES = intPreferencesKey("advance_warning_minutes")
        val KEY_SOUND_ALERTS = booleanPreferencesKey("sound_alerts")
        val KEY_VIBRATION_ALERTS = booleanPreferencesKey("vibration_alerts")
        val KEY_AUTO_LAUNCH_PARKEDIN = booleanPreferencesKey("auto_launch_parkedin")
        val KEY_MONITORED_PACKAGES = stringSetPreferencesKey("monitored_packages")
        val KEY_LOG_ALL_NOTIFICATIONS = booleanPreferencesKey("log_all_notifications")
        val KEY_REQUIRE_APPROVAL = booleanPreferencesKey("require_approval_before_start")
        val KEY_SHOW_CONFIRMATION_DIALOGS = booleanPreferencesKey("show_confirmation_dialogs")
        val KEY_OVERLAY_DIALOG_POSITION = stringPreferencesKey("overlay_dialog_position")
        val KEY_LOCKSCREEN_OVERLAY_POLICY = stringPreferencesKey("lockscreen_overlay_policy")
        val KEY_DISMISS_OVERLAY_ON_SCREEN_OFF = booleanPreferencesKey("dismiss_overlay_on_screen_off")
        val KEY_ALWAYS_DETECT = booleanPreferencesKey("always_detect")
        val KEY_ENABLE_QUICK_RENEW_AUTOMATION = booleanPreferencesKey("enable_quick_renew_automation")
        val KEY_PAUSE_BEFORE_PAYMENT = booleanPreferencesKey("pause_before_payment")
        val KEY_ALARM_SOUND_TYPE = stringPreferencesKey("alarm_sound_type")
        val KEY_CUSTOM_ALARM_URI = stringPreferencesKey("custom_alarm_uri")
        val KEY_CUSTOM_ALARM_TITLE = stringPreferencesKey("custom_alarm_title")
        val KEY_VOLUME_ESCALATION_ENABLED = booleanPreferencesKey("volume_escalation_enabled")
        val KEY_ESCALATION_DURATION_SECONDS = intPreferencesKey("escalation_duration_seconds")
        val KEY_CRITICAL_WARNING_MINUTES = intPreferencesKey("critical_warning_minutes")
        val KEY_PERSISTENT_VIBRATION_ENABLED = booleanPreferencesKey("persistent_vibration_enabled")
        val KEY_VIBRATION_PATTERN_TYPE = stringPreferencesKey("vibration_pattern_type")
        val KEY_HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val KEY_SYNC_CALENDAR_ENABLED = booleanPreferencesKey("sync_calendar_enabled")
        val KEY_SELECTED_CALENDAR_ID = longPreferencesKey("selected_calendar_id")
        val KEY_SELECTED_CALENDAR_NAME = stringPreferencesKey("selected_calendar_name")

        const val DEFAULT_ADVANCE_MINUTES = 15
        const val DEFAULT_CRITICAL_MINUTES = 2
        const val DEFAULT_ESCALATION_SECONDS = 20
        const val DEFAULT_WALKING_BUFFER_MINUTES = 5
        const val DEFAULT_PARKEDIN_PACKAGE = "com.preciseparklink.parkedin"
        const val DEFAULT_MYPARKING_PACKAGE = "com.cpa.accountManagement"

        val DEFAULT_MONITORED_PACKAGES = setOf(DEFAULT_PARKEDIN_PACKAGE, DEFAULT_MYPARKING_PACKAGE)
    }

    val alwaysDetect: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ALWAYS_DETECT] ?: true
    }

    val requireApproval: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_REQUIRE_APPROVAL] ?: true
    }

    val showConfirmationDialogs: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SHOW_CONFIRMATION_DIALOGS] ?: true
    }

    val advanceWarningMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_ADVANCE_WARNING_MINUTES] ?: DEFAULT_ADVANCE_MINUTES
    }

    val soundAlerts: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SOUND_ALERTS] ?: true
    }

    val vibrationAlerts: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_VIBRATION_ALERTS] ?: true
    }

    val autoLaunchParkedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_LAUNCH_PARKEDIN] ?: true
    }

    val logAllNotifications: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_LOG_ALL_NOTIFICATIONS] ?: true
    }

    val monitoredPackages: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_MONITORED_PACKAGES] ?: DEFAULT_MONITORED_PACKAGES
    }

    val lockScreenOverlayPolicy: Flow<LockScreenOverlayPolicy> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_LOCKSCREEN_OVERLAY_POLICY]
        try {
            if (raw != null) LockScreenOverlayPolicy.valueOf(raw) else LockScreenOverlayPolicy.FOLLOW_SYSTEM
        } catch (_: Exception) {
            LockScreenOverlayPolicy.FOLLOW_SYSTEM
        }
    }

    val overlayDialogPosition: Flow<OverlayDialogPosition> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_OVERLAY_DIALOG_POSITION]
        try {
            if (raw != null) OverlayDialogPosition.valueOf(raw) else OverlayDialogPosition.TOP
        } catch (_: Exception) {
            OverlayDialogPosition.TOP
        }
    }

    val dismissOverlayOnScreenOff: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DISMISS_OVERLAY_ON_SCREEN_OFF] ?: true
    }

    val enableQuickRenewAutomation: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ENABLE_QUICK_RENEW_AUTOMATION] ?: true
    }

    val pauseBeforePayment: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_PAUSE_BEFORE_PAYMENT] ?: true
    }

    val alarmSoundType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_ALARM_SOUND_TYPE] ?: com.parktimedetector.audio.SoundProfileType.SYSTEM_ALARM.name
    }

    val customAlarmUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_CUSTOM_ALARM_URI]
    }

    val customAlarmTitle: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_CUSTOM_ALARM_TITLE]
    }

    val volumeEscalationEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_VOLUME_ESCALATION_ENABLED] ?: true
    }

    val escalationDurationSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_ESCALATION_DURATION_SECONDS] ?: DEFAULT_ESCALATION_SECONDS
    }

    val criticalWarningMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_CRITICAL_WARNING_MINUTES] ?: DEFAULT_CRITICAL_MINUTES
    }

    val persistentVibrationEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_PERSISTENT_VIBRATION_ENABLED] ?: true
    }

    val vibrationPatternType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_VIBRATION_PATTERN_TYPE] ?: com.parktimedetector.audio.VibrationPatternType.URGENT_PULSE.name
    }

    val hapticFeedbackEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_HAPTIC_FEEDBACK_ENABLED] ?: true
    }

    suspend fun setOverlayDialogPosition(position: OverlayDialogPosition) {
        context.dataStore.edit { preferences ->
            preferences[KEY_OVERLAY_DIALOG_POSITION] = position.name
        }
    }

    suspend fun setLockScreenOverlayPolicy(policy: LockScreenOverlayPolicy) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LOCKSCREEN_OVERLAY_POLICY] = policy.name
        }
    }

    suspend fun setDismissOverlayOnScreenOff(dismiss: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DISMISS_OVERLAY_ON_SCREEN_OFF] = dismiss
        }
    }

    suspend fun setAlwaysDetect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ALWAYS_DETECT] = enabled
        }
    }

    suspend fun setRequireApproval(required: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_REQUIRE_APPROVAL] = required
        }
    }

    suspend fun setShowConfirmationDialogs(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SHOW_CONFIRMATION_DIALOGS] = enabled
        }
    }

    suspend fun setLogAllNotifications(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LOG_ALL_NOTIFICATIONS] = enabled
        }
    }

    suspend fun setAdvanceWarningMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ADVANCE_WARNING_MINUTES] = minutes
        }
    }

    suspend fun setSoundAlerts(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SOUND_ALERTS] = enabled
        }
    }

    suspend fun setVibrationAlerts(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_VIBRATION_ALERTS] = enabled
        }
    }

    suspend fun setAutoLaunchParkedIn(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_LAUNCH_PARKEDIN] = enabled
        }
    }

    suspend fun addMonitoredPackage(pkg: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_MONITORED_PACKAGES] ?: DEFAULT_MONITORED_PACKAGES
            preferences[KEY_MONITORED_PACKAGES] = current + pkg
        }
    }

    suspend fun removeMonitoredPackage(pkg: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_MONITORED_PACKAGES] ?: DEFAULT_MONITORED_PACKAGES
            preferences[KEY_MONITORED_PACKAGES] = current - pkg
        }
    }

    suspend fun setEnableQuickRenewAutomation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ENABLE_QUICK_RENEW_AUTOMATION] = enabled
        }
    }

    suspend fun setPauseBeforePayment(paused: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PAUSE_BEFORE_PAYMENT] = paused
        }
    }

    suspend fun setAlarmSoundType(type: com.parktimedetector.audio.SoundProfileType) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ALARM_SOUND_TYPE] = type.name
        }
    }

    suspend fun setCustomAlarmTone(uriString: String?, title: String?) {
        context.dataStore.edit { preferences ->
            if (uriString != null) {
                preferences[KEY_CUSTOM_ALARM_URI] = uriString
            } else {
                preferences.remove(KEY_CUSTOM_ALARM_URI)
            }
            if (title != null) {
                preferences[KEY_CUSTOM_ALARM_TITLE] = title
            } else {
                preferences.remove(KEY_CUSTOM_ALARM_TITLE)
            }
        }
    }

    suspend fun setVolumeEscalationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_VOLUME_ESCALATION_ENABLED] = enabled
        }
    }

    suspend fun setEscalationDurationSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ESCALATION_DURATION_SECONDS] = seconds
        }
    }

    suspend fun setCriticalWarningMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CRITICAL_WARNING_MINUTES] = minutes
        }
    }

    suspend fun setPersistentVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PERSISTENT_VIBRATION_ENABLED] = enabled
        }
    }

    suspend fun setVibrationPatternType(type: com.parktimedetector.audio.VibrationPatternType) {
        context.dataStore.edit { preferences ->
            preferences[KEY_VIBRATION_PATTERN_TYPE] = type.name
        }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_THEME_MODE]
        try {
            if (raw != null) AppThemeMode.valueOf(raw) else AppThemeMode.SYSTEM
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }

    val walkingBufferMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_WALKING_BUFFER_MINUTES] ?: DEFAULT_WALKING_BUFFER_MINUTES
    }

    suspend fun setWalkingBufferMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_WALKING_BUFFER_MINUTES] = minutes
        }
    }

    val favoriteZones: Flow<List<FavoriteZone>> = context.dataStore.data.map { preferences ->
        FavoriteZone.listFromJsonString(preferences[KEY_FAVORITE_ZONES])
    }

    suspend fun setFavoriteZones(zones: List<FavoriteZone>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FAVORITE_ZONES] = FavoriteZone.listToJsonString(zones)
        }
    }

    suspend fun addFavoriteZone(zone: FavoriteZone) {
        context.dataStore.edit { preferences ->
            val current = FavoriteZone.listFromJsonString(preferences[KEY_FAVORITE_ZONES])
            val updated = (current.filterNot { it.id == zone.id || it.name.equals(zone.name, ignoreCase = true) } + zone)
            preferences[KEY_FAVORITE_ZONES] = FavoriteZone.listToJsonString(updated)
        }
    }

    suspend fun removeFavoriteZone(zoneId: String) {
        context.dataStore.edit { preferences ->
            val current = FavoriteZone.listFromJsonString(preferences[KEY_FAVORITE_ZONES])
            val updated = current.filterNot { it.id == zoneId }
            preferences[KEY_FAVORITE_ZONES] = FavoriteZone.listToJsonString(updated)
        }
    }

    val syncCalendarEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SYNC_CALENDAR_ENABLED] ?: false
    }

    val selectedCalendarId: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_CALENDAR_ID]
    }

    val selectedCalendarName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_CALENDAR_NAME]
    }

    suspend fun setSyncCalendarEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SYNC_CALENDAR_ENABLED] = enabled
        }
    }

    suspend fun setSelectedCalendar(calendarId: Long?, calendarName: String?) {
        context.dataStore.edit { preferences ->
            if (calendarId != null) {
                preferences[KEY_SELECTED_CALENDAR_ID] = calendarId
            } else {
                preferences.remove(KEY_SELECTED_CALENDAR_ID)
            }
            if (calendarName != null) {
                preferences[KEY_SELECTED_CALENDAR_NAME] = calendarName
            } else {
                preferences.remove(KEY_SELECTED_CALENDAR_NAME)
            }
        }
    }
}
