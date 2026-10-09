package com.parktimedetector

import com.parktimedetector.audio.SoundProfileType
import com.parktimedetector.audio.VibrationPatternType
import com.parktimedetector.data.ParkingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSoundProfilesTest {

    @Test
    fun testSoundProfileTypeEnumValues() {
        val names = SoundProfileType.values().map { it.name }
        assertTrue(names.contains("SYSTEM_ALARM"))
        assertTrue(names.contains("SYSTEM_NOTIFICATION"))
        assertTrue(names.contains("SYSTEM_RINGTONE"))
        assertTrue(names.contains("CUSTOM_TONE"))
    }

    @Test
    fun testVibrationPatternTypeLookup() {
        assertEquals(
            VibrationPatternType.URGENT_PULSE,
            VibrationPatternType.fromNameOrDefault("URGENT_PULSE")
        )
        assertEquals(
            VibrationPatternType.HEARTBEAT,
            VibrationPatternType.fromNameOrDefault("HEARTBEAT")
        )
        assertEquals(
            VibrationPatternType.CONTINUOUS_SOS,
            VibrationPatternType.fromNameOrDefault("CONTINUOUS_SOS")
        )
        assertEquals(
            VibrationPatternType.STANDARD_BUZZ,
            VibrationPatternType.fromNameOrDefault("STANDARD_BUZZ")
        )
        // Fallback for invalid/null names
        assertEquals(
            VibrationPatternType.URGENT_PULSE,
            VibrationPatternType.fromNameOrDefault("INVALID_NAME")
        )
        assertEquals(
            VibrationPatternType.URGENT_PULSE,
            VibrationPatternType.fromNameOrDefault(null)
        )
    }

    @Test
    fun testVibrationPatternsHavePositiveTimings() {
        VibrationPatternType.values().forEach { patternType ->
            assertTrue("Pattern ${patternType.name} should not be empty", patternType.pattern.isNotEmpty())
            patternType.pattern.forEachIndexed { index, timing ->
                if (index > 0) {
                    assertTrue("Pattern timing should be positive", timing > 0)
                }
            }
        }
    }

    @Test
    fun testCriticalZoneThresholdLogic() {
        val now = 100_000L
        val advanceWarningMinutes = 15
        val criticalWarningMinutes = 2

        // Session with 20 minutes remaining -> Not in advance, Not in critical
        val safeSession = ParkingSession(
            packageName = "com.test",
            source = "Test",
            zoneOrLot = "Zone 1",
            startTimeMillis = now,
            endTimeMillis = now + (20 * 60 * 1000L),
            advanceWarningMinutes = advanceWarningMinutes
        )
        assertFalse(safeSession.isInAdvanceWarningZone(now))
        assertFalse(safeSession.isInCriticalZone(now, criticalWarningMinutes))
        assertFalse(safeSession.isExpired(now))

        // Session with 10 minutes remaining -> In advance warning, NOT in critical zone
        val warningSession = safeSession.copy(endTimeMillis = now + (10 * 60 * 1000L))
        assertTrue(warningSession.isInAdvanceWarningZone(now))
        assertFalse(warningSession.isInCriticalZone(now, criticalWarningMinutes))
        assertFalse(warningSession.isExpired(now))

        // Session with 2 minutes remaining -> In advance warning AND in critical zone
        val criticalSessionExact = safeSession.copy(endTimeMillis = now + (2 * 60 * 1000L))
        assertTrue(criticalSessionExact.isInAdvanceWarningZone(now))
        assertTrue(criticalSessionExact.isInCriticalZone(now, criticalWarningMinutes))
        assertFalse(criticalSessionExact.isExpired(now))

        // Session with 1 minute remaining -> In critical zone
        val criticalSession1m = safeSession.copy(endTimeMillis = now + (1 * 60 * 1000L))
        assertTrue(criticalSession1m.isInAdvanceWarningZone(now))
        assertTrue(criticalSession1m.isInCriticalZone(now, criticalWarningMinutes))
        assertFalse(criticalSession1m.isExpired(now))

        // Session expired (0m or negative) -> Expired, isInCriticalZone returns false because already expired
        val expiredSession = safeSession.copy(endTimeMillis = now - 1000L)
        assertTrue(expiredSession.isExpired(now))
        assertFalse(expiredSession.isInCriticalZone(now, criticalWarningMinutes))
        assertFalse(expiredSession.isInAdvanceWarningZone(now))
    }

    @Test
    fun testDefaultSessionNotifiedFlags() {
        val session = ParkingSession(
            packageName = "com.test",
            source = "Test",
            zoneOrLot = "Zone 1",
            startTimeMillis = 1000L,
            endTimeMillis = 5000L
        )
        assertFalse(session.isNotifiedAdvance)
        assertFalse(session.isNotifiedCritical)
        assertFalse(session.isNotifiedExpiry)
    }

    @Test
    fun testAlarmSoundManagerStopResetsPlaying() {
        com.parktimedetector.audio.AlarmSoundManager.stop(null)
        assertFalse(com.parktimedetector.audio.AlarmSoundManager.isPlaying.value)
    }
}
