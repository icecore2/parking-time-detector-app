package com.parktimedetector.service

import android.content.Context
import com.parktimedetector.data.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ManualDetectionManager {
    private const val TAG = "ManualDetectionMgr"
    const val DETECTION_WINDOW_SECONDS = 300 // 5 minutes

    private val scope = CoroutineScope(Dispatchers.Default)
    private var countdownJob: Job? = null

    private val _isDetectionActive = MutableStateFlow(false)
    val isDetectionActive = _isDetectionActive.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds = _remainingSeconds.asStateFlow()

    fun isDetectionWindowActive(): Boolean = _isDetectionActive.value

    fun startOrResetDetectionWindow(context: Context?, sourceReason: String = "Manual Button") {
        countdownJob?.cancel()
        _isDetectionActive.value = true
        _remainingSeconds.value = DETECTION_WINDOW_SECONDS

        context?.let { ctx ->
            AppLogger.info(
                context = ctx,
                tag = "DETECTION_WINDOW",
                message = "Detection window activated for 5 minutes (Source: $sourceReason). Screen & notification listeners active."
            )
        }

        countdownJob = scope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1000L)
                val current = _remainingSeconds.value - 1
                _remainingSeconds.value = current
            }
            _isDetectionActive.value = false
            context?.let { ctx ->
                AppLogger.info(
                    context = ctx,
                    tag = "DETECTION_TIMEOUT",
                    message = "Detection window timed out after 5 minutes. Background detection paused."
                )
            }
        }
    }

    fun stopDetectionWindow(context: Context? = null) {
        countdownJob?.cancel()
        _isDetectionActive.value = false
        _remainingSeconds.value = 0
        context?.let { ctx ->
            AppLogger.info(
                context = ctx,
                tag = "DETECTION_WINDOW",
                message = "Detection window stopped manually."
            )
        }
    }
}
