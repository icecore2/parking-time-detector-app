package com.parktimedetector.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

class UserPreferencesRepository(private val context: Context) {

    companion object {
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

        const val DEFAULT_ADVANCE_MINUTES = 15
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
}
