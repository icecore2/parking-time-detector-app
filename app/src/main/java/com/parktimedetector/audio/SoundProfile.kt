package com.parktimedetector.audio

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

enum class SoundProfileType(val displayName: String, val description: String) {
    SYSTEM_ALARM(
        "System Default Alarm",
        "Plays your device's default alarm sound (Recommended)"
    ),
    SYSTEM_NOTIFICATION(
        "System Notification",
        "Plays your device's default notification tone"
    ),
    SYSTEM_RINGTONE(
        "System Ringtone",
        "Plays your phone's incoming call ringtone"
    ),
    CUSTOM_TONE(
        "Custom Sound",
        "Select any alarm tone or sound file from your device"
    );

    fun resolveUri(context: Context, customUriString: String? = null): Uri {
        return when (this) {
            SYSTEM_ALARM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            SYSTEM_NOTIFICATION -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            SYSTEM_RINGTONE -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            CUSTOM_TONE -> {
                if (!customUriString.isNullOrBlank()) {
                    try {
                        Uri.parse(customUriString)
                    } catch (_: Exception) {
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    }
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                }
            }
        }
    }
}

enum class VibrationPatternType(val displayName: String, val description: String, val pattern: LongArray) {
    URGENT_PULSE(
        "Urgent Double Pulse (Recommended)",
        "Distinct double-tap pulse followed by a long buzz",
        longArrayOf(0, 250, 150, 250, 150, 600, 500)
    ),
    HEARTBEAT(
        "Heartbeat Pulse",
        "Rhythmic double thump pattern",
        longArrayOf(0, 150, 150, 350, 700)
    ),
    CONTINUOUS_SOS(
        "Continuous SOS",
        "Three short, three long, three short Morse pulse",
        longArrayOf(0, 150, 100, 150, 100, 150, 300, 400, 100, 400, 100, 400, 300, 150, 100, 150, 100, 150, 800)
    ),
    STANDARD_BUZZ(
        "Standard Repetitive Buzz",
        "Continuous 500ms on / 500ms off vibration",
        longArrayOf(0, 500, 500)
    );

    companion object {
        fun fromNameOrDefault(name: String?): VibrationPatternType {
            return try {
                if (name != null) valueOf(name) else URGENT_PULSE
            } catch (_: Exception) {
                URGENT_PULSE
            }
        }
    }
}
