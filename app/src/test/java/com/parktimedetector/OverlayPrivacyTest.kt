package com.parktimedetector

import com.parktimedetector.data.LockScreenOverlayPolicy
import com.parktimedetector.service.OverlayPrivacyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPrivacyTest {

    @Test
    fun testWhenDeviceUnlocked_overlayAlwaysPermitted() {
        for (policy in LockScreenOverlayPolicy.values()) {
            val canShow = OverlayPrivacyManager.evaluateCanShow(
                isLocked = false,
                isNotificationsAllowed = false,
                policy = policy
            )
            assertTrue("Overlay should always be permitted when device is unlocked under policy $policy", canShow)
        }
    }

    @Test
    fun testWhenDeviceUnlocked_noRedactionNeeded() {
        for (policy in LockScreenOverlayPolicy.values()) {
            val shouldRedact = OverlayPrivacyManager.evaluateShouldRedact(
                isLocked = false,
                isPrivateContentAllowed = false,
                policy = policy
            )
            assertFalse("No redaction should be applied when device is unlocked under policy $policy", shouldRedact)
        }
    }

    @Test
    fun testWhenDeviceLocked_followSystemPolicy_suppressesOverlay() {
        // Following Android accessibility overlay best practices, floating windows
        // are suppressed over secure keyguard to avoid tapjacking and privacy leaks.
        val canShow = OverlayPrivacyManager.evaluateCanShow(
            isLocked = true,
            isNotificationsAllowed = true,
            policy = LockScreenOverlayPolicy.FOLLOW_SYSTEM
        )
        assertFalse("FOLLOW_SYSTEM should suppress floating overlay when keyguard is locked", canShow)
    }

    @Test
    fun testWhenDeviceLocked_hideWhenLockedPolicy_suppressesOverlay() {
        val canShow = OverlayPrivacyManager.evaluateCanShow(
            isLocked = true,
            isNotificationsAllowed = true,
            policy = LockScreenOverlayPolicy.HIDE_WHEN_LOCKED
        )
        assertFalse("HIDE_WHEN_LOCKED should strictly suppress floating overlay on locked device", canShow)
    }

    @Test
    fun testWhenDeviceLocked_redactWhenLockedPolicy_respectsNotificationSettings() {
        val canShowWhenNotificationsAllowed = OverlayPrivacyManager.evaluateCanShow(
            isLocked = true,
            isNotificationsAllowed = true,
            policy = LockScreenOverlayPolicy.REDACT_WHEN_LOCKED
        )
        assertTrue(
            "REDACT_WHEN_LOCKED should permit overlay if lock screen notifications are allowed",
            canShowWhenNotificationsAllowed
        )

        val canShowWhenNotificationsBlocked = OverlayPrivacyManager.evaluateCanShow(
            isLocked = true,
            isNotificationsAllowed = false,
            policy = LockScreenOverlayPolicy.REDACT_WHEN_LOCKED
        )
        assertFalse(
            "REDACT_WHEN_LOCKED should suppress overlay if lock screen notifications are blocked by system settings",
            canShowWhenNotificationsBlocked
        )
    }

    @Test
    fun testWhenDeviceLocked_redactionEvaluation() {
        // HIDE_WHEN_LOCKED always marks redaction required if locked
        assertTrue(
            OverlayPrivacyManager.evaluateShouldRedact(
                isLocked = true,
                isPrivateContentAllowed = true,
                policy = LockScreenOverlayPolicy.HIDE_WHEN_LOCKED
            )
        )

        // REDACT_WHEN_LOCKED always masks sensitive details when locked
        assertTrue(
            OverlayPrivacyManager.evaluateShouldRedact(
                isLocked = true,
                isPrivateContentAllowed = true,
                policy = LockScreenOverlayPolicy.REDACT_WHEN_LOCKED
            )
        )

        // FOLLOW_SYSTEM evaluates based on whether private content is allowed on lock screen
        val shouldRedactWhenPrivateDisallowed = OverlayPrivacyManager.evaluateShouldRedact(
            isLocked = true,
            isPrivateContentAllowed = false,
            policy = LockScreenOverlayPolicy.FOLLOW_SYSTEM
        )
        assertTrue(shouldRedactWhenPrivateDisallowed)

        val shouldRedactWhenPrivateAllowed = OverlayPrivacyManager.evaluateShouldRedact(
            isLocked = true,
            isPrivateContentAllowed = true,
            policy = LockScreenOverlayPolicy.FOLLOW_SYSTEM
        )
        assertFalse(shouldRedactWhenPrivateAllowed)
    }

    @Test
    fun testRedactedStringPlaceholders() {
        val startLoc = OverlayPrivacyManager.getRedactedLocation()
        assertTrue("Redacted location must mask specific stall/lot", startLoc.contains("Unlock"))

        val stopLoc = OverlayPrivacyManager.getRedactedStopLocation()
        assertTrue("Redacted stop location must mask specific stall/lot", stopLoc.contains("Unlock"))

        val cost = OverlayPrivacyManager.getRedactedCost()
        assertEquals("••••", cost)
    }

    @Test
    fun testPolicyEnumMetadata() {
        assertEquals(3, LockScreenOverlayPolicy.values().size)
        for (policy in LockScreenOverlayPolicy.values()) {
            assertTrue(policy.title.isNotBlank())
            assertTrue(policy.description.isNotBlank())
        }
    }

    @Test
    fun testConfirmationDialogsPreferenceKey() {
        // Confirmation dialog windows should have a defined preference key
        assertEquals("show_confirmation_dialogs", com.parktimedetector.data.UserPreferencesRepository.KEY_SHOW_CONFIRMATION_DIALOGS.name)
    }

    @Test
    fun testOverlayDialogPositionEnumMetadata() {
        val positions = com.parktimedetector.data.OverlayDialogPosition.values()
        assertEquals(2, positions.size)
        for (pos in positions) {
            assertTrue(pos.title.isNotBlank())
            assertTrue(pos.description.isNotBlank())
        }
        assertEquals("overlay_dialog_position", com.parktimedetector.data.UserPreferencesRepository.KEY_OVERLAY_DIALOG_POSITION.name)
    }
}
