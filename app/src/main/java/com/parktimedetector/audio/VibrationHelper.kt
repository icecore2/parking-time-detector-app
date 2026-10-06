package com.parktimedetector.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibrationHelper {

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Starts persistent vibration loop with the given pattern.
     * Pattern repeats indefinitely until [stopVibration] is invoked.
     */
    fun startPersistentVibration(context: Context, patternType: VibrationPatternType) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Repeat from index 0
                val effect = VibrationEffect.createWaveform(patternType.pattern, 0)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(patternType.pattern, 0)
            }
        } catch (_: Exception) {
            // Ignore if vibration fails on device
        }
    }

    /**
     * Previews a vibration pattern once without repeating.
     */
    fun previewPatternOnce(context: Context, patternType: VibrationPatternType) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // -1 = don't repeat
                val effect = VibrationEffect.createWaveform(patternType.pattern, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(patternType.pattern, -1)
            }
        } catch (_: Exception) {
            // Ignore preview vibration failure
        }
    }

    /**
     * Stops any currently playing hardware vibration.
     */
    fun stopVibration(context: Context) {
        val vibrator = getVibrator(context) ?: return
        try {
            vibrator.cancel()
        } catch (_: Exception) {
            // Ignore cancel exception
        }
    }

    /**
     * Tactile haptic feedback when tapping timer quick-extend buttons (+15m, +30m, +1h).
     */
    fun performQuickExtendHaptic(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25)
            }
        } catch (_: Exception) {
            // Ignore haptic feedback failure
        }
    }
}
