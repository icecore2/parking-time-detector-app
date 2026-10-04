package com.parktimedetector.service

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.parktimedetector.data.LockScreenOverlayPolicy

object OverlayPrivacyManager {

    fun isKeyguardLocked(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return km.isKeyguardLocked
    }

    fun isDeviceLocked(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            km.isDeviceLocked
        } else {
            km.isKeyguardLocked
        }
    }

    fun isLockScreenNotificationsAllowed(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(
                context.contentResolver,
                "lock_screen_show_notifications",
                1
            ) != 0
        } catch (_: Exception) {
            true
        }
    }

    fun isPrivateContentAllowedOnLockScreen(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(
                context.contentResolver,
                "lock_screen_allow_private_notifications",
                1
            ) != 0
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Determines whether a floating overlay is permitted to be shown right now based on
     * keyguard status, system notification privacy settings, and the selected user policy.
     */
    fun canShowOverlay(
        context: Context,
        policy: LockScreenOverlayPolicy = LockScreenOverlayPolicy.FOLLOW_SYSTEM
    ): Boolean {
        val locked = isKeyguardLocked(context)
        val notificationsAllowed = isLockScreenNotificationsAllowed(context)
        return evaluateCanShow(locked, notificationsAllowed, policy)
    }

    fun evaluateCanShow(
        isLocked: Boolean,
        isNotificationsAllowed: Boolean,
        policy: LockScreenOverlayPolicy
    ): Boolean {
        if (!isLocked) return true // Always allowed when device is unlocked

        return when (policy) {
            LockScreenOverlayPolicy.HIDE_WHEN_LOCKED -> false
            LockScreenOverlayPolicy.REDACT_WHEN_LOCKED -> isNotificationsAllowed
            LockScreenOverlayPolicy.FOLLOW_SYSTEM -> {
                // Best practice: assistive overlays should not pop up over keyguard.
                // Notifications handle locked display cleanly; overlay appears once unlocked.
                false
            }
        }
    }

    /**
     * Determines whether sensitive details (location, stall, cost) should be redacted.
     */
    fun shouldRedactDetails(
        context: Context,
        policy: LockScreenOverlayPolicy = LockScreenOverlayPolicy.FOLLOW_SYSTEM
    ): Boolean {
        val locked = isKeyguardLocked(context)
        val privateAllowed = isPrivateContentAllowedOnLockScreen(context)
        return evaluateShouldRedact(locked, privateAllowed, policy)
    }

    fun evaluateShouldRedact(
        isLocked: Boolean,
        isPrivateContentAllowed: Boolean,
        policy: LockScreenOverlayPolicy
    ): Boolean {
        if (!isLocked) return false

        return when (policy) {
            LockScreenOverlayPolicy.HIDE_WHEN_LOCKED -> true
            LockScreenOverlayPolicy.REDACT_WHEN_LOCKED -> true
            LockScreenOverlayPolicy.FOLLOW_SYSTEM -> {
                !isPrivateContentAllowed
            }
        }
    }

    fun getRedactedLocation(): String {
        return "Parking Active (Unlock to View Location)"
    }

    fun getRedactedStopLocation(): String {
        return "Parking Stopped (Unlock to View Details)"
    }

    fun getRedactedCost(): String {
        return "••••"
    }
}
