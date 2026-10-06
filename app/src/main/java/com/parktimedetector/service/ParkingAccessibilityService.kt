package com.parktimedetector.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import com.parktimedetector.R
import com.parktimedetector.data.AppLogger
import com.parktimedetector.data.PendingParkingDetection
import com.parktimedetector.data.UserPreferencesRepository
import com.parktimedetector.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParkingAccessibilityService : AccessibilityService(), DetectionApprovalManager.OverlayCallback {

    companion object {
        private const val TAG = "ParkingAccessibility"

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected = _isServiceConnected.asStateFlow()

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            val myComponent = ComponentName(context, ParkingAccessibilityService::class.java).flattenToString()

            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(myComponent, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        private val STOP_WORD_REGEX = Regex("""(?i)\b(?:stop|ends?|ended|ending|leave|finish|cancel|complete|deactivat(?:e|ed|ion)|exit)\b""")

        fun isStopActionClick(text: String): Boolean {
            val lower = text.lowercase().trim()
            if (lower.contains("extend") || lower.contains("renew")) {
                return false
            }
            // "START / END SESSION" or "START/END" is a navigation header/tab, not a pure stop button
            if (lower.contains("start") && lower.contains("end")) {
                return false
            }
            return STOP_WORD_REGEX.containsMatchIn(lower)
        }

        private val PARK_START_REGEX = Regex("""(?i)\b(?:park(?:\s*(?:now|here|vehicle))?|pay\s*&\s*park)\b""")

        fun isStartOrActionClick(text: String): Boolean {
            val lower = text.lowercase().trim()
            // If it matches a stop action, it must not be treated as a start action
            if (isStopActionClick(text)) {
                return false
            }
            if (lower.contains("start") && lower.contains("end")) {
                return false
            }
            return lower.contains("start") ||
                lower.contains("pay") ||
                lower.contains("purchase") ||
                lower.contains("confirm") ||
                PARK_START_REGEX.containsMatchIn(lower) ||
                lower.contains("activate") ||
                lower.contains("agree") ||
                lower.contains("renew") ||
                lower.contains("extend") ||
                lower.contains("continue") ||
                lower.contains("checkout")
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanJob: Job? = null
    private var lastExtractedHash: Int = 0
    private var lastDetectedEndTime: Long = 0L
    private var overlayView: View? = null
    private var isBubbleMode = false
    private var isStopOverlay = false
    private var currentOverlayParams: WindowManager.LayoutParams? = null
    private var lastDetectedZone: String? = null
    private var lastDetectedDuration: String? = null
    private var lastDetectedCost: String? = null
    private var cachedQuickRenewEnabled = true
    private var cachedPauseBeforePayment = true

    // Flow tracking state variables
    private var isStartConfirmationDialogActive = false
    private var lastStartConfirmationDialogTime = 0L
    private var lastStartConfirmationDialogText: String? = null
    private var userConfirmedStartInDialog = false
    private var userInitiatedStopAttempt = false
    private var lastForegroundActivity: String? = null

    override fun isBubbleCollapsed(): Boolean = isBubbleMode && overlayView != null


    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                android.content.Intent.ACTION_SCREEN_OFF -> {
                    serviceScope.launch {
                        val prefsRepo = UserPreferencesRepository(applicationContext)
                        val dismissOnScreenOff = prefsRepo.dismissOverlayOnScreenOff.first()
                        if (dismissOnScreenOff) {
                            hideOverlay()
                        }
                    }
                }
                android.content.Intent.ACTION_USER_PRESENT -> {
                    // Device unlocked! If there is an active pending detection or stop detection,
                    // resume and present the floating overlay now that device is safely unlocked,
                    // provided confirmation dialog windows are enabled.
                    serviceScope.launch {
                        val prefsRepo = UserPreferencesRepository(applicationContext)
                        if (!prefsRepo.showConfirmationDialogs.first()) return@launch

                        val pendingStart = DetectionApprovalManager.pendingDetection.value
                        val pendingStop = DetectionApprovalManager.pendingStopDetection.value
                        if (pendingStart != null) {
                            showOverlay(pendingStart)
                        } else if (pendingStop != null) {
                            showStopOverlay(pendingStop)
                        }
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceConnected.value = true
        DetectionApprovalManager.overlayCallback = this
        Log.i(TAG, "ParkingAccessibilityService connected and ready")
        AppLogger.success(
            applicationContext,
            "SCREEN_SERVICE",
            "Screen Detection Accessibility Service connected and active"
        )

        // Cache Quick-Renew settings in memory for zero-latency Main-thread access
        serviceScope.launch {
            val prefs = UserPreferencesRepository(applicationContext)
            prefs.enableQuickRenewAutomation.collect { cachedQuickRenewEnabled = it }
        }
        serviceScope.launch {
            val prefs = UserPreferencesRepository(applicationContext)
            prefs.pauseBeforePayment.collect { cachedPauseBeforePayment = it }
        }

        try {
            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_SCREEN_OFF)
                addAction(android.content.Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenStateReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering screen state receiver", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkgName = event.packageName?.toString() ?: return

        val isMockActivityEvent = (pkgName == packageName) && (
            (QuickRenewManager.isArmed() && QuickRenewManager.matchesTargetPackage(pkgName)) ||
            event.className?.contains("Mock", ignoreCase = true) == true
        )

        // Ignore our own app unless testing a mock activity or QuickRenew is armed for mock
        if (pkgName == packageName && !isMockActivityEvent) return

        // Immediately handle Quick-Renew synchronously on Main thread while rootNode is fresh
        if (QuickRenewManager.isArmed() && QuickRenewManager.matchesTargetPackage(pkgName) && cachedQuickRenewEnabled) {
            val root = try { rootInActiveWindow ?: event.source } catch (_: Exception) { null }
            if (root != null) {
                QuickRenewManager.handleAccessibilityEvent(
                    service = this@ParkingAccessibilityService,
                    rootNode = root,
                    eventPackage = pkgName,
                    pauseBeforePayment = cachedPauseBeforePayment
                )
            }
        }

        serviceScope.launch {
            try {
                val prefsRepo = UserPreferencesRepository(applicationContext)
                val monitored = prefsRepo.monitoredPackages.first()

                val isTargetParkingApp = monitored.contains(pkgName) ||
                    pkgName == NotificationHelper.MYPARKING_PACKAGE ||
                    pkgName == NotificationHelper.PARKEDIN_PACKAGE ||
                    pkgName.contains("cpa", ignoreCase = true) ||
                    pkgName.contains("myparking", ignoreCase = true) ||
                    pkgName.contains("parkedin", ignoreCase = true) ||
                    isMockActivityEvent

                if (!isTargetParkingApp) return@launch

                val alwaysDetect = prefsRepo.alwaysDetect.first()
                if (!alwaysDetect && !ManualDetectionManager.isDetectionWindowActive()) {
                    return@launch
                }

                val appName = NotificationHelper.getAppNameForPackage(pkgName)
                val now = System.currentTimeMillis()

                when (event.eventType) {
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                        val clickedText = (event.text?.joinToString(" ") ?: "")
                            .ifBlank { event.contentDescription?.toString() ?: "" }
                            .ifBlank { event.source?.text?.toString() ?: "" }
                            .trim()
                        val resId = event.source?.viewIdResourceName ?: "no_id"
                        val className = (event.className?.toString() ?: "View").substringAfterLast('.')

                        AppLogger.info(
                            context = applicationContext,
                            tag = "PRESSING",
                            message = "[FLOW: PRESSING] Clicked '${clickedText.ifBlank { "(unlabeled)" }}' [ID: $resId, Class: $className] in $appName",
                            packageName = pkgName
                        )

                        if (clickedText.isNotBlank()) {
                            if (isStopActionClick(clickedText)) {
                                userInitiatedStopAttempt = true
                                AppLogger.info(
                                    context = applicationContext,
                                    tag = "STOP_BUTTON_CLICKED",
                                    message = "User clicked stop action '$clickedText' in $appName. Requesting deactivation confirmation...",
                                    packageName = pkgName
                                )

                                try {
                                    val rootNode = rootInActiveWindow
                                    val currentTexts = mutableListOf<String>()
                                    extractTextsFromNode(rootNode, currentTexts)
                                    val currentParsed = SessionNotificationParser.parseScreenText(currentTexts, now)
                                    val activeDbSession = com.parktimedetector.data.ParkingDatabase.getDatabase(applicationContext).parkingDao().getActiveSession()
                                    val zone = currentParsed?.zoneOrLot ?: activeDbSession?.zoneOrLot
                                    val location = currentParsed?.locationAddress ?: activeDbSession?.locationAddress

                                    val pendingStop = com.parktimedetector.data.PendingParkingStopDetection(
                                        packageName = pkgName,
                                        appName = appName,
                                        zoneOrLot = zone,
                                        stopTimeMillis = now,
                                        durationParkedText = null,
                                        costOrRefundText = currentParsed?.costText ?: activeDbSession?.costOrRefundText,
                                        source = "$appName App (Stop Clicked)",
                                        reason = "User clicked '$clickedText'",
                                        rawData = currentTexts.joinToString("\n"),
                                        locationAddress = location,
                                        stopReason = "User clicked $clickedText in $appName"
                                    )
                                    DetectionApprovalManager.requestStopApproval(applicationContext, pendingStop)
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Error processing stop click for $pkgName", t)
                                }

                                scheduleProactiveScans(pkgName)
                                return@launch
                            } else if (isStartOrActionClick(clickedText)) {
                                userConfirmedStartInDialog = true
                                lastStartConfirmationDialogTime = now
                                AppLogger.info(
                                    context = applicationContext,
                                    tag = "BUTTON_CLICKED",
                                    message = "User clicked start action '$clickedText' in $appName. Scheduling proactive scans for parking receipt...",
                                    packageName = pkgName
                                )

                                // Proactively schedule multiple scans as the screen loads
                                scheduleProactiveScans(pkgName)
                                return@launch
                            }
                        }
                    }

                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                        val className = event.className?.toString() ?: ""
                        val isDialogOrModal = className.contains("Dialog", ignoreCase = true) ||
                            className.contains("Popup", ignoreCase = true) ||
                            className.contains("BottomSheet", ignoreCase = true) ||
                            event.isFullScreen == false

                        if (isDialogOrModal) {
                            val dialogTexts = mutableListOf<String>()
                            val dialogButtons = mutableListOf<String>()
                            extractTextsAndButtons(event.source ?: rootInActiveWindow, dialogTexts, dialogButtons)

                            val dialogSummary = dialogTexts.take(3).joinToString(" • ")
                            val buttonsSummary = if (dialogButtons.isNotEmpty()) {
                                " | Buttons: [${dialogButtons.distinct().take(4).joinToString(", ")}]"
                            } else ""

                            AppLogger.info(
                                context = applicationContext,
                                tag = "DIALOG_WINDOW",
                                message = "[FLOW: DIALOG_WINDOW] Dialog window appeared in $appName: '${dialogSummary.ifBlank { className.substringAfterLast('.') }}'$buttonsSummary",
                                packageName = pkgName,
                                rawData = dialogTexts.joinToString("\n")
                            )

                            // Check if this dialog looks like a parking start confirmation dialog
                            val combined = dialogTexts.joinToString(" ").lowercase()
                            if (combined.contains("confirm") || combined.contains("start") ||
                                combined.contains("pay") || combined.contains("purchase") ||
                                combined.contains("rate") || combined.contains("zone") ||
                                combined.contains("duration") || combined.contains("parking") ||
                                combined.contains("vehicle") || combined.contains("license") ||
                                dialogButtons.any { isStartOrActionClick(it) }
                            ) {
                                isStartConfirmationDialogActive = true
                                lastStartConfirmationDialogTime = now
                                lastStartConfirmationDialogText = dialogSummary
                            }
                        } else {
                            val simpleClass = className.substringAfterLast('.')
                            if (simpleClass != lastForegroundActivity) {
                                lastForegroundActivity = simpleClass
                                AppLogger.info(
                                    context = applicationContext,
                                    tag = "APP_OPENING",
                                    message = "[FLOW: APP_OPENING] Opened $appName screen ($simpleClass)",
                                    packageName = pkgName
                                )
                            }
                        }

                        // Debounce rapid window changes
                        scheduleSingleScan(pkgName)
                    }

                    else -> {
                        scheduleSingleScan(pkgName)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onAccessibilityEvent", e)
            }
        }
    }

    private fun scheduleSingleScan(pkgName: String, delayMs: Long = 600L) {
        scanJob?.cancel()
        scanJob = serviceScope.launch {
            try {
                delay(delayMs)
                safeScanScreenContent(pkgName)
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Expected when debouncing/canceling older scan job
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error in scheduleSingleScan for $pkgName", t)
            }
        }
    }

    private fun scheduleProactiveScans(pkgName: String) {
        scanJob?.cancel()
        scanJob = serviceScope.launch {
            try {
                delay(600)
                safeScanScreenContent(pkgName)
                delay(1200)
                safeScanScreenContent(pkgName)
                delay(2000)
                safeScanScreenContent(pkgName)
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Expected when debouncing/canceling older scan job
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error in scheduleProactiveScans for $pkgName", t)
            }
        }
    }

    private suspend fun safeScanScreenContent(pkgName: String) {
        try {
            scanScreenContent(pkgName)
        } catch (t: Throwable) {
            Log.e(TAG, "Error executing scanScreenContent for $pkgName", t)
            AppLogger.error(
                context = applicationContext,
                tag = "SCAN_ERROR",
                message = "Error scanning screen in $pkgName: ${t.message}",
                packageName = pkgName
            )
        }
    }

    private suspend fun scanScreenContent(pkgName: String) {
        val rootNode = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        extractTextsFromNode(rootNode, texts)

        if (texts.isEmpty()) return

        val textHash = texts.hashCode()
        if (textHash == lastExtractedHash) {
            // Screen content unchanged since last scan
            return
        }
        lastExtractedHash = textHash

        val fullDump = texts.joinToString("\n")
        val appName = NotificationHelper.getAppNameForPackage(pkgName)
        val isMyParkingApp = pkgName == NotificationHelper.MYPARKING_PACKAGE || pkgName.contains("myparking", ignoreCase = true)

        val combinedLower = texts.joinToString(" ").lowercase()
        val looksLikeParkingScreen = combinedLower.contains("zone") ||
            combinedLower.contains("lot") ||
            combinedLower.contains("stall") ||
            combinedLower.contains("rate") ||
            combinedLower.contains("parking") ||
            combinedLower.contains("session") ||
            combinedLower.contains("expires") ||
            combinedLower.contains("until") ||
            combinedLower.contains("vehicle") ||
            combinedLower.contains("plate")

        if (!looksLikeParkingScreen) return

        AppLogger.info(
            context = applicationContext,
            tag = "SCREEN_TEXT",
            message = "Scanned on-screen UI in $appName (${texts.size} text elements)",
            packageName = pkgName,
            rawData = fullDump
        )

        val now = System.currentTimeMillis()

        // 1. Check if this is a Session Stop screen
        if (SessionNotificationParser.isStopScreen(texts)) {
            val stopResult = SessionNotificationParser.parseStopScreenText(texts, now)
            if (stopResult != null) {
                AppLogger.success(
                    context = applicationContext,
                    tag = "SCREEN_STOP_DETECTED",
                    message = "SUCCESS: Parking session stop detected on-screen from $appName! (${stopResult.detectedReason})${stopResult.zoneOrLot?.let { " • $it" } ?: ""}",
                    packageName = pkgName,
                    rawData = fullDump
                )

                userInitiatedStopAttempt = false
                val activeDbSession = try {
                    com.parktimedetector.data.ParkingDatabase.getDatabase(applicationContext).parkingDao().getActiveSession()
                } catch (e: Exception) {
                    null
                }
                val zone = stopResult.zoneOrLot ?: activeDbSession?.zoneOrLot
                val location = stopResult.locationAddress ?: activeDbSession?.locationAddress
                val cost = stopResult.costOrRefundText ?: activeDbSession?.costOrRefundText

                val pendingStop = com.parktimedetector.data.PendingParkingStopDetection(
                    packageName = pkgName,
                    appName = appName,
                    zoneOrLot = zone,
                    stopTimeMillis = stopResult.stopTimeMillis,
                    durationParkedText = stopResult.durationParkedText,
                    costOrRefundText = cost,
                    source = "$appName App (Screen Detected Stop)",
                    reason = stopResult.detectedReason,
                    rawData = fullDump,
                    locationAddress = location,
                    stopReason = stopResult.stopReason ?: "Detected on-screen stop in $appName"
                )
                DetectionApprovalManager.requestStopApproval(applicationContext, pendingStop)
                return
            }
        } else if (SessionNotificationParser.containsStopActionButton(texts)) {
            AppLogger.info(
                context = applicationContext,
                tag = "SUPPRESSED_STOP",
                message = "[FLOW: SUPPRESSED_STOP] Stop detection suppressed in $appName: Screen contains stop button but is an active session dashboard (not a stopped/receipt screen).",
                packageName = pkgName,
                rawData = fullDump
            )
        }

        // 2. Parse active parking session start
        val parsedResult = SessionNotificationParser.parseScreenText(texts, now)

        if (parsedResult != null) {
            if (parsedResult.endTimeMillis <= now) {
                AppLogger.warn(
                    context = applicationContext,
                    tag = "SCREEN_EXPIRED",
                    message = "Detected on-screen time is already in the past.",
                    packageName = pkgName,
                    rawData = fullDump
                )
                return
            }

            // Check if start session requires confirmation dialog interaction (e.g. MyParking app)
            if (isMyParkingApp) {
                val isExplicitActiveDashboard = SessionNotificationParser.isStartEndActiveSessionScreen(texts)
                val isDetectionOrBubbleActive = DetectionApprovalManager.pendingDetection.value != null || isBubbleMode || overlayView != null
                val hasRecentDialog = userConfirmedStartInDialog ||
                    (isStartConfirmationDialogActive && (now - lastStartConfirmationDialogTime < 30_000L)) ||
                    (now - lastStartConfirmationDialogTime < 15_000L) ||
                    isExplicitActiveDashboard ||
                    isDetectionOrBubbleActive

                if (!hasRecentDialog) {
                    AppLogger.info(
                        context = applicationContext,
                        tag = "SUPPRESSED_START",
                        message = "[FLOW: SUPPRESSED_START] Start session suppressed in $appName: Screen displays parking session expiry, but no start confirmation dialog was interacted with.",
                        packageName = pkgName,
                        rawData = fullDump
                    )
                    return
                }
            }

            // Reset dialog confirmation flag only if not tracking an active overlay or bubble
            if (!isBubbleMode && overlayView == null) {
                userConfirmedStartInDialog = false
                isStartConfirmationDialogActive = false
            }

            // Deduplication: Avoid re-triggering only if the exact same detection (same end time, same zone, same duration, same cost)
            val isExactSameDetection = Math.abs(parsedResult.endTimeMillis - lastDetectedEndTime) < 60_000L &&
                parsedResult.zoneOrLot == lastDetectedZone &&
                parsedResult.purchasedDurationText == lastDetectedDuration &&
                parsedResult.costText == lastDetectedCost

            if (isExactSameDetection) {
                return
            }
            lastDetectedEndTime = parsedResult.endTimeMillis
            lastDetectedZone = parsedResult.zoneOrLot
            lastDetectedDuration = parsedResult.purchasedDurationText
            lastDetectedCost = parsedResult.costText

            val timeFormat = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
            val formattedEnd = timeFormat.format(Date(parsedResult.endTimeMillis))

            AppLogger.success(
                context = applicationContext,
                tag = "SCREEN_DETECTED",
                message = "SUCCESS: Parking session detected on-screen from $appName! Valid until $formattedEnd (${parsedResult.detectedReason})${parsedResult.zoneOrLot?.let { " • $it" } ?: ""}",
                packageName = pkgName,
                rawData = fullDump
            )

            // Submit for user confirmation via popup dialog or direct start based on preference
            val pending = PendingParkingDetection(
                packageName = pkgName,
                appName = appName,
                zoneOrLot = parsedResult.zoneOrLot,
                endTimeMillis = parsedResult.endTimeMillis,
                source = "$appName App (Screen Detected)",
                reason = parsedResult.detectedReason,
                rawData = fullDump,
                locationAddress = parsedResult.locationAddress,
                purchasedDurationText = parsedResult.purchasedDurationText,
                initialCostText = parsedResult.costText,
                remainingTimeText = parsedResult.remainingTimeText,
                notesText = parsedResult.notesText,
                startTimeMillis = parsedResult.startTimeMillis ?: now
            )
            DetectionApprovalManager.requestApprovalOrStart(applicationContext, pending)
        } else {
            // Screen looked like a parking screen but couldn't parse time
            AppLogger.warn(
                context = applicationContext,
                tag = "SCREEN_PARSE_FAILED",
                message = "Opened parking screen in $appName, but could not detect end time or duration. Visible text: ${texts.take(6).joinToString(" • ")}",
                packageName = pkgName,
                rawData = fullDump
            )
        }
    }

    private fun extractTextsAndButtons(
        node: AccessibilityNodeInfo?,
        texts: MutableList<String>,
        buttons: MutableList<String>
    ) {
        if (node == null) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val className = node.className?.toString() ?: ""
        val isButton = className.contains("Button", ignoreCase = true) || node.isClickable

        val label = if (!text.isNullOrBlank()) text else (if (!desc.isNullOrBlank()) desc else null)
        if (!label.isNullOrBlank()) {
            texts.add(label)
            if (isButton) {
                buttons.add(label)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractTextsAndButtons(child, texts, buttons)
            }
        }
    }

    private fun extractTextsFromNode(node: AccessibilityNodeInfo?, result: MutableList<String>) {
        if (node == null) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            result.add(text)
        }

        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrBlank() && desc != text) {
            result.add(desc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractTextsFromNode(child, result)
            }
        }
    }

    private fun makeDraggable(
        rootView: View,
        touchTarget: View,
        params: WindowManager.LayoutParams,
        onClick: (() -> Unit)? = null
    ) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        touchTarget.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val displayMetrics = resources.displayMetrics
                        val maxX = (displayMetrics.widthPixels - params.width).coerceAtLeast(0)
                        val maxY = (displayMetrics.heightPixels - dpToPx(80)).coerceAtLeast(0)
                        params.x = (initialX + dx).coerceIn(0, maxX)
                        params.y = (initialY + dy).coerceIn(dpToPx(24), maxY)
                        try {
                            wm.updateViewLayout(rootView, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        if (onClick != null) {
                            onClick()
                        } else {
                            v.performClick()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateDetectionDataInViews(
        view: View,
        detection: PendingParkingDetection,
        shouldRedact: Boolean
    ) {
        val tvBubbleLot = view.findViewById<TextView>(R.id.tv_overlay_bubble_lot)
        val tvBubbleTime = view.findViewById<TextView>(R.id.tv_overlay_bubble_time)

        val tvBadge = view.findViewById<TextView>(R.id.tv_overlay_badge_text)
        val tvPrimaryText = view.findViewById<TextView>(R.id.tv_overlay_primary_text)
        val tvSecondaryText = view.findViewById<TextView>(R.id.tv_overlay_secondary_text)
        val tvSuggestionText = view.findViewById<TextView>(R.id.tv_overlay_suggestion_text)

        val tvDetailLocation = view.findViewById<TextView>(R.id.tv_overlay_detail_location)
        val tvDetailExpiry = view.findViewById<TextView>(R.id.tv_overlay_detail_expiry)
        val tvDetailDuration = view.findViewById<TextView>(R.id.tv_overlay_detail_duration)
        val tvDetailSource = view.findViewById<TextView>(R.id.tv_overlay_detail_source)

        val locationText = if (shouldRedact) OverlayPrivacyManager.getRedactedLocation() else (detection.zoneOrLot ?: "Parking Session Active")
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val startMillis = detection.startTimeMillis
        val formattedStart = timeFormat.format(Date(startMillis))
        val formattedEnd = timeFormat.format(Date(detection.endTimeMillis))
        val durationStr = if (detection.durationMinutes >= 60) {
            val hrs = detection.durationMinutes / 60
            val mins = detection.durationMinutes % 60
            if (mins > 0) "${hrs}h ${mins}m" else "${hrs}h"
        } else {
            "${detection.durationMinutes}m"
        }

        // 1. Update data visible inside the bubble
        tvBubbleLot?.text = if (shouldRedact) "Parking" else (detection.zoneOrLot?.substringBefore(" -") ?: "Parking")
        tvBubbleTime?.text = formattedEnd

        // 2. Update data inside the dialog card
        tvBadge?.text = "${detection.appName.uppercase()} DETECTED"
        tvPrimaryText?.text = locationText
        tvSecondaryText?.text = "Ends $formattedEnd • ~$durationStr duration"

        val zoneText = if (!detection.zoneOrLot.isNullOrBlank() && !shouldRedact) " (${detection.zoneOrLot})" else ""
        tvSuggestionText?.text = "Session detected: $formattedStart – $formattedEnd (~$durationStr)$zoneText. Start timer now to track remaining time and receive renewal alerts?"

        tvDetailLocation?.text = if (shouldRedact) OverlayPrivacyManager.getRedactedLocation() else "Location: ${detection.zoneOrLot ?: "Not specified"}"
        tvDetailExpiry?.text = "Expires: $formattedEnd"
        tvDetailDuration?.text = "Duration: ~${detection.durationMinutes} mins ($durationStr)"
        tvDetailSource?.text = "Source: ${detection.source}"
    }

    private fun updateStopDetectionDataInViews(
        view: View,
        detection: com.parktimedetector.data.PendingParkingStopDetection,
        shouldRedact: Boolean
    ) {
        val tvBubbleLot = view.findViewById<TextView>(R.id.tv_overlay_stop_bubble_lot)
        val tvBubbleTime = view.findViewById<TextView>(R.id.tv_overlay_stop_bubble_time)

        val tvBadge = view.findViewById<TextView>(R.id.tv_overlay_stop_badge_text)
        val tvPrimaryText = view.findViewById<TextView>(R.id.tv_overlay_stop_primary_text)
        val tvSecondaryText = view.findViewById<TextView>(R.id.tv_overlay_stop_secondary_text)
        val tvSuggestionText = view.findViewById<TextView>(R.id.tv_overlay_stop_suggestion_text)

        val tvDetailLocation = view.findViewById<TextView>(R.id.tv_overlay_stop_detail_location)
        val tvDetailTime = view.findViewById<TextView>(R.id.tv_overlay_stop_detail_time)
        val tvDetailDuration = view.findViewById<TextView>(R.id.tv_overlay_stop_detail_duration)
        val tvDetailCost = view.findViewById<TextView>(R.id.tv_overlay_stop_detail_cost)
        val tvDetailSource = view.findViewById<TextView>(R.id.tv_overlay_stop_detail_source)

        val locationText = if (shouldRedact) OverlayPrivacyManager.getRedactedStopLocation() else (detection.zoneOrLot ?: "Parking Stopped")
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedStop = timeFormat.format(Date(detection.stopTimeMillis))
        val durationStr = detection.durationParkedText?.let { " • $it" } ?: ""
        val costStr = if (shouldRedact) " • " + OverlayPrivacyManager.getRedactedCost() else (detection.costOrRefundText?.let { " • $it" } ?: "")

        // 1. Update data visible inside the stop bubble
        tvBubbleLot?.text = if (shouldRedact) "Stopped" else (detection.zoneOrLot?.substringBefore(" -") ?: "Stopped")
        tvBubbleTime?.text = formattedStop

        // 2. Update data inside the dialog card
        tvBadge?.text = "${detection.appName.uppercase()} STOPPED"
        tvPrimaryText?.text = locationText
        tvSecondaryText?.text = "Stopped at $formattedStop$durationStr$costStr"

        val stopDurationDetail = detection.durationParkedText?.let { " ($it parked)" } ?: ""
        val stopCostDetail = if (shouldRedact) "" else (detection.costOrRefundText?.let { " • $it" } ?: "")
        tvSuggestionText?.text = "Parking stop detected at $formattedStop$stopDurationDetail$stopCostDetail. End active timer and record session summary?"

        tvDetailLocation?.text = if (shouldRedact) OverlayPrivacyManager.getRedactedStopLocation() else "Location: ${detection.zoneOrLot ?: "Not specified"}"
        tvDetailTime?.text = "Stopped At: $formattedStop"
        tvDetailDuration?.text = "Time Parked: ${detection.durationParkedText ?: "Recorded at stop"}"
        tvDetailCost?.text = if (shouldRedact) "Total Cost / Refund: ${OverlayPrivacyManager.getRedactedCost()}" else "Total Cost / Refund: ${detection.costOrRefundText ?: "N/A"}"
        tvDetailSource?.text = "Source: ${detection.source}"
    }

    override fun showOverlay(detection: PendingParkingDetection) {
        serviceScope.launch {
            try {
                val prefsRepo = UserPreferencesRepository(applicationContext)
                val showDialogs = prefsRepo.showConfirmationDialogs.first()
                if (!showDialogs) {
                    Log.d(TAG, "Confirmation dialog windows disabled; relying on built-in notifications")
                    return@launch
                }
                val policy = prefsRepo.lockScreenOverlayPolicy.first()
                val dialogPosition = prefsRepo.overlayDialogPosition.first()
                if (!OverlayPrivacyManager.canShowOverlay(applicationContext, policy)) {
                    Log.i(TAG, "Floating overlay suppressed on lock screen per policy ($policy)")
                    return@launch
                }
                val shouldRedact = OverlayPrivacyManager.shouldRedactDetails(applicationContext, policy)

                mainHandler.post {
                    try {
                        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post

                        // If overlay is already active and bubble is collapsed, DO NOT RAISE DIALOG!
                        // Update the data about the parking selected inside the bubble until open.
                        if (overlayView != null && isBubbleMode && !isStopOverlay) {
                            updateDetectionDataInViews(overlayView!!, detection, shouldRedact)
                            AppLogger.info(
                                context = applicationContext,
                                tag = "BUBBLE_DATA_UPDATED",
                                message = "[FLOW: BUBBLE] Updated selected parking inside collapsed bubble: ${detection.zoneOrLot ?: "Active"}",
                                packageName = detection.packageName
                            )
                            return@post
                        }

                        // If overlay is already active in open dialog mode, smoothly update in-place
                        if (overlayView != null && !isBubbleMode && !isStopOverlay) {
                            updateDetectionDataInViews(overlayView!!, detection, shouldRedact)
                            AppLogger.info(
                                context = applicationContext,
                                tag = "DIALOG_DATA_UPDATED",
                                message = "[FLOW: DIALOG] Updated selected parking inside open dialog: ${detection.zoneOrLot ?: "Active"}",
                                packageName = detection.packageName
                            )
                            return@post
                        }

                        hideOverlayInternal()
                        isStopOverlay = false

                        val inflater = LayoutInflater.from(this@ParkingAccessibilityService)
                        val view = inflater.inflate(R.layout.dialog_parking_detection_overlay, null)

                        val layoutCard = view.findViewById<View>(R.id.layout_overlay_card)
                        val layoutBubble = view.findViewById<View>(R.id.layout_overlay_bubble)
                        val viewDragHandle = view.findViewById<View>(R.id.view_overlay_drag_handle)
                        val btnMinimize = view.findViewById<TextView>(R.id.btn_overlay_minimize)
                        val layoutHeader = view.findViewById<android.widget.LinearLayout>(R.id.layout_overlay_header)
                        val layoutDetails = view.findViewById<android.widget.LinearLayout>(R.id.layout_overlay_details)
                        val btnQuickStart = view.findViewById<Button>(R.id.btn_overlay_quick_start)
                        val btnExpand = view.findViewById<TextView>(R.id.btn_overlay_expand)
                        val btnClose = view.findViewById<TextView>(R.id.btn_overlay_close)
                        val btnStart = view.findViewById<Button>(R.id.btn_overlay_start)
                        val btnDismiss = view.findViewById<Button>(R.id.btn_overlay_dismiss)

                        updateDetectionDataInViews(view, detection, shouldRedact)

                        var isExpanded = false
                        val toggleExpand = {
                            isExpanded = !isExpanded
                            layoutDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE
                            btnExpand.text = if (isExpanded) "Details ▲" else "Details ▼"
                        }

                        btnExpand.setOnClickListener { toggleExpand() }

                        val approveAction = {
                            hideOverlayInternal()
                            DetectionApprovalManager.approveCurrentDetection(applicationContext)
                        }
                        btnQuickStart?.setOnClickListener { approveAction() }
                        btnStart?.setOnClickListener { approveAction() }

                        val dismissAction = {
                            hideOverlayInternal()
                            DetectionApprovalManager.dismissCurrentDetection(applicationContext)
                        }
                        btnClose?.setOnClickListener { dismissAction() }
                        btnDismiss?.setOnClickListener { dismissAction() }

                        val screenMetrics = resources.displayMetrics
                        val screenWidth = screenMetrics.widthPixels
                        val screenHeight = screenMetrics.heightPixels
                        val cardWidth = Math.min(screenWidth - dpToPx(32), dpToPx(380))

                        val params = WindowManager.LayoutParams(
                            cardWidth,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                            PixelFormat.TRANSLUCENT
                        ).apply {
                            gravity = Gravity.TOP or Gravity.START
                            x = (screenWidth - cardWidth) / 2
                            y = if (dialogPosition == com.parktimedetector.data.OverlayDialogPosition.CENTER) {
                                (screenHeight - dpToPx(320)) / 2
                            } else {
                                dpToPx(48)
                            }
                        }
                        currentOverlayParams = params

                        // Make dialog card draggable via drag handle and header
                        if (viewDragHandle != null) {
                            makeDraggable(view, viewDragHandle, params)
                        }
                        if (layoutHeader != null) {
                            makeDraggable(view, layoutHeader, params)
                        }

                        // Minimize to bubble mode
                        btnMinimize?.setOnClickListener {
                            isBubbleMode = true
                            layoutCard?.visibility = View.GONE
                            layoutBubble?.visibility = View.VISIBLE
                            params.width = dpToPx(76)
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT
                            params.x = (screenWidth - dpToPx(88)).coerceAtLeast(0)
                            currentOverlayParams = params
                            try {
                                wm.updateViewLayout(view, params)
                            } catch (_: Exception) {}
                            AppLogger.info(
                                context = applicationContext,
                                tag = "BUBBLE_COLLAPSED",
                                message = "[FLOW: BUBBLE] Detection dialog minimized to bubble"
                            )
                        }

                        // Expand from bubble mode
                        val expandFromBubble = {
                            isBubbleMode = false
                            layoutBubble?.visibility = View.GONE
                            layoutCard?.visibility = View.VISIBLE
                            params.width = cardWidth
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT
                            params.x = (screenWidth - cardWidth) / 2
                            currentOverlayParams = params
                            try {
                                wm.updateViewLayout(view, params)
                            } catch (_: Exception) {}
                            AppLogger.info(
                                context = applicationContext,
                                tag = "BUBBLE_EXPANDED",
                                message = "[FLOW: BUBBLE] Bubble expanded to detection dialog"
                            )
                        }

                        if (layoutBubble != null) {
                            makeDraggable(view, layoutBubble, params, onClick = expandFromBubble)
                        }

                        wm.addView(view, params)
                        overlayView = view
                    } catch (e: Exception) {
                        Log.e(TAG, "Error displaying accessibility overlay dialog", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating overlay privacy policy", e)
            }
        }
    }

    override fun showStopOverlay(detection: com.parktimedetector.data.PendingParkingStopDetection) {
        serviceScope.launch {
            try {
                val prefsRepo = UserPreferencesRepository(applicationContext)
                val showDialogs = prefsRepo.showConfirmationDialogs.first()
                if (!showDialogs) {
                    Log.d(TAG, "Confirmation dialog windows disabled; relying on built-in notifications")
                    return@launch
                }
                val policy = prefsRepo.lockScreenOverlayPolicy.first()
                val dialogPosition = prefsRepo.overlayDialogPosition.first()
                if (!OverlayPrivacyManager.canShowOverlay(applicationContext, policy)) {
                    Log.i(TAG, "Floating stop overlay suppressed on lock screen per policy ($policy)")
                    return@launch
                }
                val shouldRedact = OverlayPrivacyManager.shouldRedactDetails(applicationContext, policy)

                mainHandler.post {
                    try {
                        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post

                        // If overlay already exists and bubble is collapsed, DO NOT RAISE DIALOG!
                        if (overlayView != null && isBubbleMode && isStopOverlay) {
                            updateStopDetectionDataInViews(overlayView!!, detection, shouldRedact)
                            AppLogger.info(
                                context = applicationContext,
                                tag = "BUBBLE_STOP_UPDATED",
                                message = "[FLOW: BUBBLE] Updated stop detection inside collapsed bubble: ${detection.zoneOrLot ?: "Stopped"}",
                                packageName = detection.packageName
                            )
                            return@post
                        }

                        // If overlay is already active in open dialog mode, smoothly update in-place
                        if (overlayView != null && !isBubbleMode && isStopOverlay) {
                            updateStopDetectionDataInViews(overlayView!!, detection, shouldRedact)
                            AppLogger.info(
                                context = applicationContext,
                                tag = "DIALOG_STOP_UPDATED",
                                message = "[FLOW: DIALOG] Updated stop detection inside open dialog: ${detection.zoneOrLot ?: "Stopped"}",
                                packageName = detection.packageName
                            )
                            return@post
                        }

                        val previousX = currentOverlayParams?.x
                        val previousY = currentOverlayParams?.y
                        val wasInBubbleMode = isBubbleMode

                        hideOverlayInternal()
                        isStopOverlay = true
                        isBubbleMode = wasInBubbleMode

                        val inflater = LayoutInflater.from(this@ParkingAccessibilityService)
                        val view = inflater.inflate(R.layout.dialog_parking_stop_overlay, null)

                        val layoutCard = view.findViewById<View>(R.id.layout_overlay_stop_card)
                        val layoutBubble = view.findViewById<View>(R.id.layout_overlay_stop_bubble)
                        val viewDragHandle = view.findViewById<View>(R.id.view_overlay_stop_drag_handle)
                        val btnMinimize = view.findViewById<TextView>(R.id.btn_overlay_stop_minimize)
                        val layoutHeader = view.findViewById<android.widget.LinearLayout>(R.id.layout_overlay_stop_header)
                        val layoutDetails = view.findViewById<android.widget.LinearLayout>(R.id.layout_overlay_stop_details)
                        val btnQuickAccept = view.findViewById<Button>(R.id.btn_overlay_stop_quick_accept)
                        val btnExpand = view.findViewById<TextView>(R.id.btn_overlay_stop_expand)
                        val btnClose = view.findViewById<TextView>(R.id.btn_overlay_stop_close)
                        val btnAccept = view.findViewById<Button>(R.id.btn_overlay_stop_accept)
                        val btnDismiss = view.findViewById<Button>(R.id.btn_overlay_stop_dismiss)

                        updateStopDetectionDataInViews(view, detection, shouldRedact)

                        var isExpanded = false
                        val toggleExpand = {
                            isExpanded = !isExpanded
                            layoutDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE
                            btnExpand.text = if (isExpanded) "Details ▲" else "Details ▼"
                        }

                        btnExpand.setOnClickListener { toggleExpand() }

                        val acceptAction = {
                            hideOverlayInternal()
                            DetectionApprovalManager.approveCurrentStopDetection(applicationContext)
                        }
                        btnQuickAccept?.setOnClickListener { acceptAction() }
                        btnAccept?.setOnClickListener { acceptAction() }

                        val dismissAction = {
                            hideOverlayInternal()
                            DetectionApprovalManager.dismissCurrentStopDetection(applicationContext)
                        }
                        btnClose?.setOnClickListener { dismissAction() }
                        btnDismiss?.setOnClickListener { dismissAction() }

                        val screenMetrics = resources.displayMetrics
                        val screenWidth = screenMetrics.widthPixels
                        val screenHeight = screenMetrics.heightPixels
                        val cardWidth = Math.min(screenWidth - dpToPx(32), dpToPx(380))

                        val params = WindowManager.LayoutParams(
                            if (wasInBubbleMode) dpToPx(76) else cardWidth,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                            PixelFormat.TRANSLUCENT
                        ).apply {
                            gravity = Gravity.TOP or Gravity.START
                            x = previousX ?: if (wasInBubbleMode) (screenWidth - dpToPx(88)).coerceAtLeast(0) else (screenWidth - cardWidth) / 2
                            y = previousY ?: if (dialogPosition == com.parktimedetector.data.OverlayDialogPosition.CENTER) {
                                (screenHeight - dpToPx(320)) / 2
                            } else {
                                dpToPx(48)
                            }
                        }
                        currentOverlayParams = params

                        if (wasInBubbleMode) {
                            layoutCard?.visibility = View.GONE
                            layoutBubble?.visibility = View.VISIBLE
                        } else {
                            layoutCard?.visibility = View.VISIBLE
                            layoutBubble?.visibility = View.GONE
                        }

                        // Make stop dialog card draggable via drag handle and header
                        if (viewDragHandle != null) {
                            makeDraggable(view, viewDragHandle, params)
                        }
                        if (layoutHeader != null) {
                            makeDraggable(view, layoutHeader, params)
                        }

                        // Minimize to bubble mode
                        btnMinimize?.setOnClickListener {
                            isBubbleMode = true
                            layoutCard?.visibility = View.GONE
                            layoutBubble?.visibility = View.VISIBLE
                            params.width = dpToPx(76)
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT
                            params.x = (screenWidth - dpToPx(88)).coerceAtLeast(0)
                            currentOverlayParams = params
                            try {
                                wm.updateViewLayout(view, params)
                            } catch (_: Exception) {}
                        }

                        // Expand from bubble mode
                        val expandFromBubble = {
                            isBubbleMode = false
                            layoutBubble?.visibility = View.GONE
                            layoutCard?.visibility = View.VISIBLE
                            params.width = cardWidth
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT
                            params.x = (screenWidth - cardWidth) / 2
                            currentOverlayParams = params
                            try {
                                wm.updateViewLayout(view, params)
                            } catch (_: Exception) {}
                        }

                        if (layoutBubble != null) {
                            makeDraggable(view, layoutBubble, params, onClick = expandFromBubble)
                        }

                        wm.addView(view, params)
                        overlayView = view
                    } catch (e: Exception) {
                        Log.e(TAG, "Error displaying stop accessibility overlay dialog", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating stop overlay privacy policy", e)
            }
        }
    }

    override fun hideOverlay() {
        mainHandler.post {
            hideOverlayInternal()
        }
    }

    private fun hideOverlayInternal() {
        try {
            overlayView?.let { view ->
                val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.removeViewImmediate(view)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing accessibility overlay view", e)
        } finally {
            isBubbleMode = false
            isStopOverlay = false
            currentOverlayParams = null
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "ParkingAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceConnected.value = false
        DetectionApprovalManager.overlayCallback = null
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}
        hideOverlayInternal()
        Log.i(TAG, "ParkingAccessibilityService destroyed")
    }
}

