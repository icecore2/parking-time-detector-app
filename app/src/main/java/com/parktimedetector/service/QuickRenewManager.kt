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

enum class RenewAppType {
    PARKEDIN,
    MYPARKING
}

object QuickRenewManager {

    private const val TAG = "QUICK_RENEW"
    private const val WATCHDOG_TIMEOUT_MS = 30000L

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
    var resolvedAppType: RenewAppType = RenewAppType.MYPARKING
        private set

    private var watchdogJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var lastActionTime = 0L

    internal fun resetDebounceForTesting() {
        lastActionTime = 0L
    }

    fun resolveAppType(packageName: String): RenewAppType {
        return if (packageName == NotificationHelper.MYPARKING_PACKAGE ||
            packageName.contains("cpa", ignoreCase = true) ||
            packageName.contains("myparking", ignoreCase = true)
        ) {
            RenewAppType.MYPARKING
        } else {
            RenewAppType.PARKEDIN
        }
    }

    fun isTargetMock(): Boolean {
        val target = targetPackage ?: return false
        return target.contains("mock", ignoreCase = true) || target == "mock.parking"
    }

    /**
     * Extracts pure zone/lot numbers from zoneOrLot string or address.
     * e.g. "Zone 4022" -> "4022"
     * "Lot 58 - 935 - 4 Av SW" -> "9058"
     * "9058" -> "9058"
     */
    fun extractZoneDigits(zoneOrLot: String?, locationAddress: String? = null): String {
        if (!zoneOrLot.isNullOrBlank()) {
            val trimmed = zoneOrLot.trim()
            val pureDigitsMatch = Regex("""^\b(\d{2,6})\b""").find(trimmed)
            if (pureDigitsMatch != null) {
                return pureDigitsMatch.groupValues[1]
            }

            val zoneNumMatch = Regex("""(?i)\b(?:zone|lot|stall)\s*#?\s*(\d+)""").find(trimmed)
            if (zoneNumMatch != null) {
                val num = zoneNumMatch.groupValues[1]
                return if (num.length <= 2 && trimmed.contains("Lot", ignoreCase = true)) {
                    "90$num"
                } else {
                    num
                }
            }

            val anyDigits = Regex("""\b(\d{3,6})\b""").find(trimmed)
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
        context: Context? = null,
        packageName: String,
        zoneOrLot: String?,
        locationAddress: String? = null,
        appType: RenewAppType? = null
    ) {
        targetPackage = packageName
        zoneOrLotText = zoneOrLot
        zoneDigits = extractZoneDigits(zoneOrLot, locationAddress)
        resolvedAppType = appType ?: resolveAppType(packageName)

        // Reset debounce timer to 0 so the first screen event triggers immediately
        lastActionTime = 0L

        _state.value = QuickRenewState.ARMED_AWAITING_APP
        _statusMessage.value = "Quick-Renew armed for $packageName (Zone: $zoneDigits)"

        if (context != null) {
            AppLogger.info(
                context,
                TAG,
                "Armed Quick-Renew for $packageName [$resolvedAppType] with Zone $zoneDigits ($zoneOrLot)",
                packageName
            )
        }

        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(WATCHDOG_TIMEOUT_MS)
            if (isArmed()) {
                if (context != null) {
                    AppLogger.info(context, TAG, "Quick-Renew watchdog timeout expired. Disarming.", targetPackage)
                }
                completeWithState(QuickRenewState.TIMEOUT, "Quick-Renew timed out")
            }
        }
    }

    fun isArmed(): Boolean {
        return _state.value != QuickRenewState.IDLE &&
            _state.value != QuickRenewState.PAUSED_FOR_USER_CONFIRMATION &&
            _state.value != QuickRenewState.COMPLETED &&
            _state.value != QuickRenewState.TIMEOUT
    }

    fun completeWithState(newState: QuickRenewState, message: String) {
        watchdogJob?.cancel()
        watchdogJob = null
        targetPackage = null
        _state.value = newState
        _statusMessage.value = message
    }

    fun disarm() {
        watchdogJob?.cancel()
        watchdogJob = null
        targetPackage = null
        _state.value = QuickRenewState.IDLE
        _statusMessage.value = null
    }

    fun matchesTargetPackage(eventPackage: String): Boolean {
        val target = targetPackage ?: return false
        if (eventPackage.equals(target, ignoreCase = true)) return true
        if (isTargetMock() || target.contains("mock", ignoreCase = true)) {
            return eventPackage.contains("mock", ignoreCase = true) || eventPackage == "com.parktimedetector"
        }
        // Match Calgary Parking Authority / MyParking variations
        if ((target.contains("cpa", ignoreCase = true) || target.contains("myparking", ignoreCase = true)) &&
            (eventPackage.contains("cpa", ignoreCase = true) || eventPackage.contains("myparking", ignoreCase = true))
        ) {
            return true
        }
        // Match ParkedIn variations
        if (target.contains("parkedin", ignoreCase = true) && eventPackage.contains("parkedin", ignoreCase = true)) {
            return true
        }
        return false
    }

    /**
     * Production entry point called by ParkingAccessibilityService.
     */
    fun handleAccessibilityEvent(
        service: AccessibilityService,
        rootNode: AccessibilityNodeInfo?,
        eventPackage: String,
        pauseBeforePayment: Boolean
    ): Boolean {
        if (!isArmed() || rootNode == null) return false
        return handleNode(
            rootNode = RealAccessibilityNode(rootNode),
            eventPackage = eventPackage,
            pauseBeforePayment = pauseBeforePayment,
            toastSender = { msg -> showToast(service, msg) },
            logSender = { tag, msg -> AppLogger.info(service, tag, msg, eventPackage) }
        )
    }

    /**
     * Core state machine handling an abstract AccessibilityNode.
     * Can be tested directly with MockAccessibilityNode without Android framework dependencies.
     */
    fun handleNode(
        rootNode: AccessibilityNode,
        eventPackage: String,
        pauseBeforePayment: Boolean,
        toastSender: (String) -> Unit = {},
        logSender: (String, String) -> Unit = { _, _ -> }
    ): Boolean {
        if (!isArmed()) return false
        if (!matchesTargetPackage(eventPackage)) return false

        val now = System.currentTimeMillis()
        if (now - lastActionTime < 400 && lastActionTime != 0L) {
            // Debounce rapid window changes between consecutive actions
            return false
        }

        return when (resolvedAppType) {
            RenewAppType.MYPARKING -> {
                handleMyParkingFlow(rootNode, pauseBeforePayment, toastSender, logSender)
            }
            RenewAppType.PARKEDIN -> {
                handleParkedInFlow(rootNode, pauseBeforePayment, toastSender, logSender)
            }
        }
    }

    private fun handleParkedInFlow(
        rootNode: AccessibilityNode,
        pauseBeforePayment: Boolean,
        toastSender: (String) -> Unit,
        logSender: (String, String) -> Unit
    ): Boolean {
        val extendRegex = Regex("""(?i)\b(?:extend(?:\s*(?:session|parking|time))?|renew(?:\s*(?:session|parking))?|add\s*time)\b""")
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
                logSender(TAG, "Clicked 'Extend' button in ParkedIn")

                val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    toastSender("⚡ Quick-Renew: Opened extension in ParkedIn")
                    completeWithState(QuickRenewState.PAUSED_FOR_USER_CONFIRMATION, "Review extension in ParkedIn")
                    return true
                }
            }
        }
        return false
    }

    private fun handleMyParkingFlow(
        rootNode: AccessibilityNode,
        pauseBeforePayment: Boolean,
        toastSender: (String) -> Unit,
        logSender: (String, String) -> Unit
    ): Boolean {
        // Step 0: Check if MyParking already displays an active session with an Extend/Renew button
        val extendRegex = Regex("""(?i)\b(?:extend(?:\s*(?:session|parking|time))?|renew(?:\s*(?:session|parking))?|add\s*time)\b""")
        val extendNode = findNodeMatching(rootNode) { node ->
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            extendRegex.containsMatchIn(text) || extendRegex.containsMatchIn(desc)
        }

        if (extendNode != null) {
            val clickable = findClickableTarget(extendNode)
            if (clickable != null) {
                lastActionTime = System.currentTimeMillis()
                logSender(TAG, "Clicked 'Extend' button in MyParking")
                val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    toastSender("⚡ Quick-Renew: Opened extension in MyParking")
                    completeWithState(QuickRenewState.PAUSED_FOR_USER_CONFIRMATION, "Review extension in MyParking")
                    return true
                }
            }
        }

        when (_state.value) {
            QuickRenewState.ARMED_AWAITING_APP,
            QuickRenewState.MYPARKING_FINDING_SEARCH -> {
                // Step 1A: Look for an editable EditText search field
                val editNode = findNodeMatching(rootNode) { isEditTextNode(it) }
                if (editNode != null) {
                    lastActionTime = System.currentTimeMillis()
                    _state.value = QuickRenewState.MYPARKING_ENTERING_ZONE
                    logSender(TAG, "Found MyParking search input. Injecting zone: $zoneDigits")

                    editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            zoneDigits
                        )
                    }
                    val setTextOk = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    if (setTextOk) {
                        _state.value = QuickRenewState.MYPARKING_SELECTING_RESULT
                        toastSender("⚡ Quick-Renew: Entering Zone $zoneDigits...")
                        return true
                    }
                } else {
                    // Step 1B: If no EditText yet, check for an unexpanded clickable search container/bar
                    val searchContainer = findNodeMatching(rootNode) { isClickableSearchContainer(it) }
                    if (searchContainer != null) {
                        val clickable = findClickableTarget(searchContainer)
                        if (clickable != null) {
                            lastActionTime = System.currentTimeMillis()
                            logSender(TAG, "Clicking search container to open search view")
                            val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            if (clicked) {
                                _state.value = QuickRenewState.MYPARKING_FINDING_SEARCH
                                return true
                            }
                        }
                    }
                }
            }

            QuickRenewState.MYPARKING_ENTERING_ZONE,
            QuickRenewState.MYPARKING_SELECTING_RESULT -> {
                // Step 2: Look for search suggestions / lot results matching zone digits or lot text
                val resultNode = findNodeMatching(rootNode) { node ->
                    val text = node.text?.toString() ?: ""
                    val desc = node.contentDescription?.toString() ?: ""
                    val matchesZone = (zoneDigits.isNotBlank() && (text.contains(zoneDigits) || desc.contains(zoneDigits))) ||
                        (!zoneOrLotText.isNullOrBlank() && (text.contains(zoneOrLotText!!, ignoreCase = true) || desc.contains(zoneOrLotText!!, ignoreCase = true)))
                    matchesZone && !isEditTextNode(node) && !isClearButtonNode(node)
                }

                if (resultNode != null) {
                    val clickable = findClickableTarget(resultNode)
                    if (clickable != null) {
                        lastActionTime = System.currentTimeMillis()
                        logSender(TAG, "Selecting MyParking search result for Zone $zoneDigits")
                        val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clicked) {
                            _state.value = QuickRenewState.MYPARKING_AWAITING_START_SCREEN
                            toastSender("⚡ Quick-Renew: Selected Zone $zoneDigits")
                            return true
                        }
                    }
                }
            }

            QuickRenewState.MYPARKING_AWAITING_START_SCREEN -> {
                // Step 3: Wait for "START" / "START PARKING SESSION" screen
                val startButtonRegex = Regex("""(?i)\b(?:start(?:\s*(?:parking\s*session|parking|session))?|pay\s*&\s*park|park\s*now|confirm\s*&\s*start)\b""")
                val startButtonNode = findNodeMatching(rootNode) { node ->
                    val text = (node.text?.toString() ?: "").trim()
                    val desc = (node.contentDescription?.toString() ?: "").trim()
                    (startButtonRegex.containsMatchIn(text) || startButtonRegex.containsMatchIn(desc)) &&
                        !text.contains("start/end", ignoreCase = true)
                }

                if (startButtonNode != null) {
                    lastActionTime = System.currentTimeMillis()
                    logSender(TAG, "Reached MyParking Start screen for Zone $zoneDigits")

                    if (pauseBeforePayment) {
                        toastSender("⚡ Quick-Renew: Zone $zoneDigits ready! Tap START to confirm.")
                        completeWithState(QuickRenewState.PAUSED_FOR_USER_CONFIRMATION, "Zone $zoneDigits ready. Tap START to confirm.")
                        return true
                    } else {
                        val clickable = findClickableTarget(startButtonNode)
                        if (clickable != null) {
                            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            toastSender("⚡ Quick-Renew: Parking session started!")
                            completeWithState(QuickRenewState.COMPLETED, "Parking session started for Zone $zoneDigits")
                            return true
                        }
                    }
                }
            }

            else -> {}
        }

        return false
    }

    private fun isEditTextNode(node: AccessibilityNode): Boolean {
        val className = node.className?.toString() ?: ""
        return className.contains("EditText", ignoreCase = true) || node.isEditable
    }

    private fun isClickableSearchContainer(node: AccessibilityNode): Boolean {
        if (!node.isClickable) return false
        val text = (node.text?.toString() ?: "").lowercase()
        val desc = (node.contentDescription?.toString() ?: "").lowercase()
        val viewId = (node.viewIdResourceName ?: "").lowercase()
        return text.contains("search") || text.contains("zone") || text.contains("lot") ||
            desc.contains("search") || desc.contains("zone") ||
            viewId.contains("search") || viewId.contains("zone")
    }

    private fun isClearButtonNode(node: AccessibilityNode): Boolean {
        val desc = (node.contentDescription?.toString() ?: "").lowercase()
        val viewId = (node.viewIdResourceName ?: "").lowercase()
        return desc.contains("clear") || desc.contains("delete") || viewId.contains("clear")
    }

    fun findNodeMatching(
        node: AccessibilityNode,
        predicate: (AccessibilityNode) -> Boolean
    ): AccessibilityNode? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeMatching(child, predicate)
            if (found != null) return found
        }
        return null
    }

    fun findClickableTarget(node: AccessibilityNode): AccessibilityNode? {
        var current: AccessibilityNode? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && child.isClickable) return child
        }
        return node
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
