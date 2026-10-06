package com.parktimedetector.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import com.parktimedetector.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object AlarmSoundManager {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var mediaPlayer: MediaPlayer? = null
    private var escalationJob: Job? = null
    private var autoTimeoutJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private const val DEFAULT_START_VOLUME = 0.15f
    private const val MAX_VOLUME = 1.0f
    private const val AUTO_STOP_TIMEOUT_MS = 45_000L // 45 seconds safety cutoff

    /**
     * Starts the escalating alarm playback and persistent vibration according to user preferences.
     */
    fun startAlarm(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val prefsRepo = UserPreferencesRepository(appContext)
                val soundEnabled = prefsRepo.soundAlerts.first()
                val vibrationEnabled = prefsRepo.vibrationAlerts.first()
                val soundProfileName = prefsRepo.alarmSoundType.first()
                val customSoundUri = prefsRepo.customAlarmUri.first()
                val escalationEnabled = prefsRepo.volumeEscalationEnabled.first()
                val escalationSeconds = prefsRepo.escalationDurationSeconds.first().coerceIn(5, 60)
                val patternName = prefsRepo.vibrationPatternType.first()

                val profile = try {
                    SoundProfileType.valueOf(soundProfileName)
                } catch (_: Exception) {
                    SoundProfileType.SYSTEM_ALARM
                }
                val vibrationPattern = VibrationPatternType.fromNameOrDefault(patternName)

                val soundUri = profile.resolveUri(appContext, customSoundUri)

                playInternal(
                    context = appContext,
                    soundUri = soundUri,
                    soundEnabled = soundEnabled,
                    vibrationEnabled = vibrationEnabled,
                    escalationEnabled = escalationEnabled,
                    escalationSeconds = escalationSeconds,
                    vibrationPattern = vibrationPattern
                )
            } catch (e: Exception) {
                com.parktimedetector.data.AppLogger.error(
                    appContext,
                    "ALARM",
                    "Failed to start alarm: ${e.message}"
                )
            }
        }
    }

    /**
     * Previews an alarm sound with volume escalation from the Settings screen.
     */
    fun previewAlarm(
        context: Context,
        profile: SoundProfileType,
        customUriString: String?,
        escalationEnabled: Boolean,
        escalationSeconds: Int,
        vibrationEnabled: Boolean,
        vibrationPattern: VibrationPatternType
    ) {
        val appContext = context.applicationContext
        scope.launch {
            val soundUri = profile.resolveUri(appContext, customUriString)
            playInternal(
                context = appContext,
                soundUri = soundUri,
                soundEnabled = true,
                vibrationEnabled = vibrationEnabled,
                escalationEnabled = escalationEnabled,
                escalationSeconds = escalationSeconds,
                vibrationPattern = vibrationPattern
            )
        }
    }

    @Synchronized
    private fun playInternal(
        context: Context,
        soundUri: Uri,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        escalationEnabled: Boolean,
        escalationSeconds: Int,
        vibrationPattern: VibrationPatternType
    ) {
        // Stop any currently running instance first
        stopInternal(context)

        // 1. Acquire partial wake lock to keep CPU alive during alert
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ParkingTimeDetector:AlarmSoundManager"
            )?.apply {
                acquire(AUTO_STOP_TIMEOUT_MS + 5_000L)
            }
        } catch (_: Exception) {
            // Ignore wakelock acquisition failure
        }

        // 2. Start persistent vibration if enabled
        if (vibrationEnabled) {
            VibrationHelper.startPersistentVibration(context, vibrationPattern)
        }

        // 3. Start audio playback if enabled
        if (soundEnabled) {
            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, soundUri)
                    isLooping = true
                    val initialVol = if (escalationEnabled) DEFAULT_START_VOLUME else MAX_VOLUME
                    setVolume(initialVol, initialVol)
                    prepare()
                    start()
                }
                mediaPlayer = player

                // 4. Volume escalation loop
                if (escalationEnabled) {
                    escalationJob = scope.launch {
                        val steps = (escalationSeconds * 2).coerceAtLeast(1) // 2 steps per second
                        val stepDelay = 500L
                        val volumeIncrement = (MAX_VOLUME - DEFAULT_START_VOLUME) / steps
                        var currentVol = DEFAULT_START_VOLUME

                        for (i in 1..steps) {
                            delay(stepDelay)
                            currentVol = (currentVol + volumeIncrement).coerceAtMost(MAX_VOLUME)
                            synchronized(AlarmSoundManager) {
                                try {
                                    mediaPlayer?.setVolume(currentVol, currentVol)
                                } catch (_: Exception) {
                                    return@launch
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                com.parktimedetector.data.AppLogger.error(
                    context,
                    "ALARM",
                    "MediaPlayer preparation failed: ${e.message}"
                )
            }
        }

        _isPlaying.value = true

        // 5. Auto-stop timeout safety
        autoTimeoutJob = scope.launch {
            delay(AUTO_STOP_TIMEOUT_MS)
            stop(context)
        }
    }

    /**
     * Immediately stops audio playback, releases wake lock, and halts vibration.
     */
    fun stop(context: Context? = null) {
        synchronized(this) {
            stopInternal(context)
        }
    }

    private fun stopInternal(context: Context?) {
        escalationJob?.cancel()
        escalationJob = null

        autoTimeoutJob?.cancel()
        autoTimeoutJob = null

        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (_: Exception) {
            // Ignore release exceptions
        } finally {
            mediaPlayer = null
        }

        if (context != null) {
            VibrationHelper.stopVibration(context)
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
            // Ignore wakelock release failure
        } finally {
            wakeLock = null
        }

        _isPlaying.value = false
    }
}
