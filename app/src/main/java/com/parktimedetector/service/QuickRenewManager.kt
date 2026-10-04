package com.parktimedetector.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.parktimedetector.data.AppLogger
import com.parktimedetector.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class QuickRenewState {
    IDLE,
    ARMED_AWAITING_APP,
    PARKEDIN_CLICKING_EXTEND,
    MYPARKING_FINDING_SEARCH,
    MYPARKING_ENTERING_ZONE,
    MYPARKING_SELECTING_RESULT,
    MYPARKING_AWAITING_START_SCREEN,
    PAUSED_FOR_USER_CONFIRMATION,
    COMPLETED,
    TIMEOUT
}

object QuickRenewManager {

    private const val TAG = "QUICK_RENEW"
    private const val WATCHDOG_TIMEOUT_MS = 15000L

    private val _state = MutableStateFlow(QuickRenewState.IDLE)
    val state = _state.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    var targetPackage: String? = null
        private set
    var zoneDigits: String = ""
        private set
    var zoneOrLotText: String? = null
        private set

    private var watchdogJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var lastActionTime = 0L

    /**
     * Extracts pure zone/lot numbers from zoneOrLot string or address.
     * e.g. "Zone 4022" -> "4022"
     * "Lot 58 - 935 - 4 Av SW" -> "9058" or "58"
     * "9058" -> "9058"
     */
    fun extractZoneDigits(zoneOrLot: String?, locationAddress: String? = null): String {
        if (!zoneOrLot.isNullOrBlank()) {
            // First check if string is pure digits or starts with digits (e.g. "9058")
            val pureDigitsMatch = Regex("""^\b(\d{2,6})\b""").find(zoneOrLot.trim())
            if (pureDigitsMatch != null) {
                return pureDigitsMatch.groupValues[1]
            }

            // Check for "Zone 4022" or "Lot 58"
            val zoneNumMatch = Regex("""(?i)\b(?:zone|lot|stall)\s*#?\s*(\d+)""").find(zoneOrLot)
            if (zoneNumMatch != null) {
                val num = zoneNumMatch.groupValues[1]
                // Special Calgary Parking Authority case: Lot 58 is often Zone 9058
                return if (num.length <= 2 && zoneOrLot.contains("Lot", ignoreCase = true)) {
                    "90$num"
                } else {
                    num
                }
            }

            // Any 3+ digit number in the string
            val anyDigits = Regex("""\b(\d{3,6})\b""").find(zoneOrLot)
            if (anyDigits != null) {
                return anyDigits.groupValues[1]
            }
        }

        if (!locationAddress.isNullOrBlank()) {
            val addrMatch = Regex("""(?i)\b(?:lot|zone)\s*#?\s*(\d+)""").find(locationAddress)
            if (addrMatch != null) {
                val num = addrMatch.groupValues[1]
                return if (num.length <= 2) "90$num" else num
            }
            val firstDigits = Regex("""\b(\d{3,6})\b""").find(locationAddress)
            if (firstDigits != null) {
                return firstDigits.groupValues[1]
            }
        }

        return zoneOrLot?.filter { it.isDigit() }?.takeIf { it.isNotBlank() } ?: "4022"
    }

    /**
     * Arms the Quick-Renew automation state machine with target app and zone info.
     */
    fun arm(
        context: Context,
        packageName: String,
        zoneOrLot: String?,
        locationAddress: String? = null
    ) {
        targetPackage = packageName
        zoneOrLotText = zoneOrLot
        zoneDigits = extractZoneDigits(zoneOrLot, locationAddress)
        lastActionTime = System.currentTimeMillis()

        _state.value = QuickRenewState.ARMED_AWAITING_APP
        _statusMessage.value = "Quick-Renew armed for $packageName (Zone: $zoneDigits)"

        AppLogger.info(
            context,
            TAG,
            "Armed Quick-Renew for $packageName with Zone $zoneDigits ($zoneOrLot)",
            packageName
        )

        // Cancel previous watchdog if active
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(WATCHDOG_TIMEOUT_MS)
            if (isArmed()) {
                AppLogger.info(context, TAG, "Quick-Renew watchdog timeout expired. Disarming.", targetPackage)
                _state.value = QuickRenewState.TIMEOUT
                _statusMessage.value = "Quick-Renew timed out"
                disarm()
            }
        }
    }

    fun isArmed(): Boolean {
        return _state.value != QuickRenewState.IDLE &&
            _state.value != QuickRenewState.COMPLETED &&
            _state.value != QuickRenewState.TIMEOUT
    }

    fun disarm() {
        watchdogJob?.cancel()
        watchdogJob = null
        targetPackage = null
        _state.value = QuickRenewState.IDLE
        _statusMessage.value = null
    }

    /**
     * Called by ParkingAccessibilityService when accessibility events occur.
     */
    fun handleAccessibilityEvent(
        service: AccessibilityService,
        rootNode: AccessibilityNodeInfo?,
        eventPackage: String,
        pauseBeforePayment: Boolean
    ): Boolean {
        if (!isArmed() || rootNode == null) return false

        val target = targetPackage ?: return false
        if (!eventPackage.equals(target, ignoreCase = true)) return false

        val now = System.currentTimeMillis()
        if (now - lastActionTime < 400) {
            // Debounce actions to give UI time to animate/render
            return false
        }

        return when (target) {
            NotificationHelper.MYPARKING_PACKAGE -> {
                handleMyParkingFlow(service, rootNode, pauseBeforePayment)
            }
            NotificationHelper.PARKEDIN_PACKAGE -> {
                handleParkedInFlow(service, rootNode, pauseBeforePayment)
            }
            else -> {
                // Generic fallback: Try finding Extend button first
                handleParkedInFlow(service, rootNode, pauseBeforePayment)
            }
        }
    }

    private fun handleParkedInFlow(
        service: AccessibilityService,
        rootNode: AccessibilityNodeInfo,
        pauseBeforePayment: Boolean
    ): Boolean {
        // Search for Extend / Renew button
        val extendRegex = Regex("""(?i)^(?:extend|extend session|renew|renew session|add time)$""")
        val extendNode = findNodeMatching(rootNode) { node ->
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            extendRegex.containsMatchIn(text) || extendRegex.containsMatchIn(desc)
        }

        if (extendNode != null) {
            val clickable = findClickableTarget(extendNode)
            if (clickable != null) {
                lastActionTime = System.currentTimeMillis()
                _state.value = QuickRenewState.PARKEDIN_CLICKING_EXTEND
                AppLogger.info(service, TAG, "Clicked 'Extend' button in ParkedIn")

                val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    showToast(service, "⚡ Quick-Renew: Opened extension in ParkedIn")
                    _state.value = QuickRenewState.PAUSED_FOR_USER_CONFIRMATION
                    _statusMessage.value = "Review extension in ParkedIn"
                    disarm()
                    return true
                }
            }
        }
        return false
    }

    private fun handleMyParkingFlow(
        service: AccessibilityService,
        rootNode: AccessibilityNodeInfo,
        pauseBeforePayment: Boolean
    ): Boolean {
        // State Machine progression for MyParking
        when (_state.value) {
            QuickRenewState.ARMED_AWAITING_APP,
            QuickRenewState.MYPARKING_FINDING_SEARCH -> {
                // Step 1: Find Search Bar
                val searchNode = findSearchBarNode(rootNode)
                if (searchNode != null) {
                    lastActionTime = System.currentTimeMillis()
                    _state.value = QuickRenewState.MYPARKING_ENTERING_ZONE
                    AppLogger.info(service, TAG, "Found MyParking search bar. Injecting zone: $zoneDigits")

                    // Focus & set text
                    searchNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            zoneDigits
                        )
                    }
                    val setTextOk = searchNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    if (setTextOk) {
                        _state.value = QuickRenewState.MYPARKING_SELECTING_RESULT
                        showToast(service, "⚡ Quick-Renew: Entering Zone $zoneDigits...")
                        return true
                    }
                }
            }

            QuickRenewState.MYPARKING_ENTERING_ZONE,
            QuickRenewState.MYPARKING_SELECTING_RESULT -> {
                // Step 2: Look for search suggestions / lot results matching zone digits
                val resultNode = findNodeMatching(rootNode) { node ->
                    val text = node.text?.toString() ?: ""
                    val desc = node.contentDescription?.toString() ?: ""
                    (text.contains(zoneDigits) || desc.contains(zoneDigits) ||
                        (!zoneOrLotText.isNullOrBlank() && (text.contains(zoneOrLotText!!) || desc.contains(zoneOrLotText!!)))) &&
                        !isSearchBarNode(node)
                }

                if (resultNode != null) {
                    val clickable = findClickableTarget(resultNode)
                    if (clickable != null) {
                        lastActionTime = System.currentTimeMillis()
                        AppLogger.info(service, TAG, "Selecting MyParking search result for Zone $zoneDigits")
                        val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clicked) {
                            _state.value = QuickRenewState.MYPARKING_AWAITING_START_SCREEN
                            showToast(service, "⚡ Quick-Renew: Selected Zone $zoneDigits")
                            return true
                        }
                    }
                }
            }

            QuickRenewState.MYPARKING_AWAITING_START_SCREEN -> {
                // Step 3: Wait for "START" / "START PARKING SESSION" screen
                val startButtonNode = findNodeMatching(rootNode) { node ->
                    val text = (node.text?.toString() ?: "").trim()
                    val desc = (node.contentDescription?.toString() ?: "").trim()
                    text.equals("START", ignoreCase = true) ||
                        text.equals("START PARKING SESSION", ignoreCase = true) ||
                        desc.equals("START", ignoreCase = true) ||
                        desc.equals("START PARKING SESSION", ignoreCase = true)
                }

                if (startButtonNode != null) {
                    lastActionTime = System.currentTimeMillis()
                    AppLogger.info(service, TAG, "Reached MyParking Start screen for Zone $zoneDigits")

                    if (pauseBeforePayment) {
                        // User requested pause for confirmation before starting/paying
                        showToast(service, "⚡ Quick-Renew: Zone $zoneDigits ready! Tap START to confirm.")
                        _state.value = QuickRenewState.PAUSED_FOR_USER_CONFIRMATION
                        _statusMessage.value = "Zone $zoneDigits ready. Tap START to confirm."
                        disarm()
                        return true
                    } else {
                        // Full auto mode: Click START directly
                        val clickable = findClickableTarget(startButtonNode)
                        if (clickable != null) {
                            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            showToast(service, "⚡ Quick-Renew: Parking session started!")
                            _state.value = QuickRenewState.COMPLETED
                            disarm()
                            return true
                        }
                    }
                }
            }

            else -> {
                // Other states
            }
        }

        return false
    }

    private fun findSearchBarNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeMatching(rootNode) { isSearchBarNode(it) }
    }

    private fun isSearchBarNode(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: ""
        val text = (node.text?.toString() ?: "").lowercase()
        val desc = (node.contentDescription?.toString() ?: "").lowercase()
        val viewId = (node.viewIdResourceName ?: "").lowercase()

        val isEdit = className.contains("EditText", ignoreCase = true) || node.isEditable
        val matchesSearchKeyword = text.contains("search") || text.contains("zone") ||
            text.contains("lot") || desc.contains("search") || desc.contains("zone") ||
            viewId.contains("search") || viewId.contains("zone")

        return isEdit || (node.isClickable && matchesSearchKeyword)
    }

    fun findNodeMatching(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeMatching(child, predicate)
            if (found != null) return found
        }
        return null
    }

    fun findClickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return node // Fallback to original node
    }

    private fun showToast(context: Context, message: String) {
        try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
            }
        } catch (_: Throwable) {
            // Ignored in headless/unit test environments
        }
    }
}
