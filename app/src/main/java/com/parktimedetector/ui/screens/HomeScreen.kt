package com.parktimedetector.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parktimedetector.data.ParkingSession
import com.parktimedetector.service.ParkingAccessibilityService
import com.parktimedetector.service.ParkingAlarmScheduler
import com.parktimedetector.service.ParkingNotificationListenerService
import com.parktimedetector.ui.theme.AccentCyan
import com.parktimedetector.ui.theme.AmberWarning
import com.parktimedetector.ui.theme.CardBackground
import com.parktimedetector.ui.theme.DarkNavy
import com.parktimedetector.ui.theme.EmeraldGreen
import com.parktimedetector.ui.theme.PrimaryBlue
import com.parktimedetector.ui.theme.RoseRed
import com.parktimedetector.ui.theme.SurfaceDark
import com.parktimedetector.ui.theme.TextMuted
import com.parktimedetector.ui.theme.TextPrimary
import com.parktimedetector.ui.theme.TextSecondary
import com.parktimedetector.ui.viewmodel.ParkingViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: ParkingViewModel,
    activeSession: ParkingSession?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var hasListenerPermission by remember { mutableStateOf(ParkingNotificationListenerService.isPermissionGranted(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(ParkingAccessibilityService.isAccessibilityServiceEnabled(context)) }
    var hasExactAlarmPermission by remember { mutableStateOf(ParkingAlarmScheduler.canScheduleExactAlarms(context)) }

    // Periodically re-check permissions on resume
    LaunchedEffect(Unit) {
        while (true) {
            hasListenerPermission = ParkingNotificationListenerService.isPermissionGranted(context)
            hasAccessibilityPermission = ParkingAccessibilityService.isAccessibilityServiceEnabled(context)
            hasExactAlarmPermission = ParkingAlarmScheduler.canScheduleExactAlarms(context)
            delay(2000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Permission Diagnostic Banners if needed
        if (!hasListenerPermission) {
            PermissionBanner(
                title = "Notification Detection Disabled",
                description = "Enable Notification Access so the app can automatically detect ParkedIn parking sessions.",
                actionLabel = "Enable in Settings",
                onAction = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )
        }

        if (!hasAccessibilityPermission) {
            PermissionBanner(
                title = "Screen Auto-Detection Disabled",
                description = "MyParking does not send notifications. Enable Accessibility Service so the app can automatically detect your active parking session right from the screen.",
                actionLabel = "Enable Screen Detection",
                onAction = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )
        }

        if (!hasExactAlarmPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionBanner(
                title = "Exact Alarms Disabled",
                description = "Grant exact alarm access to ensure parking renewal warnings wake your phone on time.",
                actionLabel = "Grant Permission",
                onAction = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )
        }

        val pendingDetection by viewModel.pendingDetection.collectAsState()
        val pendingStopDetection by viewModel.pendingStopDetection.collectAsState()
        val alwaysDetect by viewModel.alwaysDetect.collectAsState()
        val isManualActive by viewModel.isManualDetectionActive.collectAsState()
        val manualRemaining by viewModel.manualDetectionRemainingSeconds.collectAsState()

        // Manual Detection Control Banner (when Always Detect is off, or when detection window is active)
        if (!alwaysDetect || isManualActive) {
            ManualDetectionControlCard(
                isDetectionActive = isManualActive,
                remainingSeconds = manualRemaining,
                onStartOrReset = { viewModel.startOrResetManualDetection() },
                onStop = { viewModel.stopManualDetection() }
            )
        }

        // 1. Pending Stop Detection Inline Banner
        if (pendingStopDetection != null) {
            val stop = pendingStopDetection!!
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val formattedStop = timeFormat.format(Date(stop.stopTimeMillis))
            val durationText = stop.durationParkedText?.let { " • $it" } ?: ""
            val costText = stop.costOrRefundText?.let { " • $it" } ?: ""

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RoseRed.copy(alpha = 0.15f)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, RoseRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🛑", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Session Stop Detected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RoseRed
                            )
                        }
                        Surface(
                            color = RoseRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stop.appName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = RoseRed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${stop.displayLocation} ended at $formattedStop$durationText$costText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.approvePendingStopDetection() },
                            colors = ButtonDefaults.buttonColors(containerColor = RoseRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Accept & End Timer", color = com.parktimedetector.ui.theme.DarkNavy, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.dismissPendingStopDetection() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Dismiss", color = TextSecondary)
                        }
                    }
                }
            }
        }

        // 2. Pending Detection Inline Banner
        if (pendingDetection != null) {
            val detection = pendingDetection!!
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val formattedEnd = timeFormat.format(Date(detection.endTimeMillis))
            val durationStr = if (detection.durationMinutes >= 60) {
                val hrs = detection.durationMinutes / 60
                val mins = detection.durationMinutes % 60
                if (mins > 0) "${hrs}h ${mins}m" else "${hrs}h"
            } else {
                "${detection.durationMinutes}m"
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.15f)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🅿️", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Parking Session Detected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }
                        Surface(
                            color = EmeraldGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = detection.appName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    val formattedStart = timeFormat.format(Date(detection.startTimeMillis))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Suggested: Start parking timer for ${detection.displayLocation}\nSession: $formattedStart – $formattedEnd (~$durationStr)${detection.initialCostText?.let { " • $it" } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.approvePendingDetection() },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start Timer", color = com.parktimedetector.ui.theme.DarkNavy, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.dismissPendingDetection() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Dismiss", color = RoseRed)
                        }
                    }
                }
            }
        }

        // Active Parking Session or Empty / Quick Start
        if (activeSession != null && activeSession.isActive) {
            ActiveSessionCard(
                session = activeSession,
                onOpenApp = { viewModel.openTargetApp(context, activeSession.packageName) },
                onQuickRenew = { viewModel.launchQuickRenew(context, activeSession) },
                onExtend = { extraMinutes -> viewModel.extendCurrentSession(extraMinutes) },
                onEndSession = { viewModel.endCurrentSession() }
            )
        } else {
            NoActiveSessionCard(
                onOpenParkedIn = { viewModel.openParkedInApp(context) },
                onOpenMyParking = { viewModel.openMyParkingApp(context) },
                alwaysDetect = alwaysDetect
            )
            ManualTimerSetupCard(
                onStartTimer = { duration, zone ->
                    viewModel.startManualSession(duration, zone)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PermissionBanner(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.15f)),
        modifier = Modifier.border(1.dp, AmberWarning.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarning)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AmberWarning,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(actionLabel, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveSessionCard(
    session: ParkingSession,
    onOpenApp: () -> Unit,
    onQuickRenew: () -> Unit = onOpenApp,
    onExtend: (Int) -> Unit,
    onEndSession: () -> Unit
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Live clock ticker every second
    LaunchedEffect(session.id) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    val remainingMillis = session.remainingMillis(currentTime)
    val isExpired = session.isExpired(currentTime)
    val inAdvanceWarning = session.isInAdvanceWarningZone(currentTime)

    val remainingSeconds = (remainingMillis / 1000) % 60
    val remainingMinutes = (remainingMillis / (1000 * 60)) % 60
    val remainingHours = remainingMillis / (1000 * 60 * 60)

    val timeString = if (isExpired) {
        "EXPIRED"
    } else {
        String.format(Locale.US, "%02d:%02d:%02d", remainingHours, remainingMinutes, remainingSeconds)
    }

    val totalDuration = session.totalDurationMillis.coerceAtLeast(1L)
    val progress = if (isExpired) 1f else (1f - (remainingMillis.toFloat() / totalDuration)).coerceIn(0f, 1f)

    val statusColor = when {
        isExpired -> RoseRed
        inAdvanceWarning -> AmberWarning
        else -> EmeraldGreen
    }

    val statusText = when {
        isExpired -> "PARKING EXPIRED"
        inAdvanceWarning -> "EXPIRING SOON"
        else -> "ACTIVE SESSION"
    }

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val endTimeString = timeFormat.format(Date(session.endTimeMillis))
    val startTimeString = timeFormat.format(Date(session.startTimeMillis))

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Tag
            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Big Timer Display
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(190.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 10.dp,
                    color = statusColor,
                    trackColor = SurfaceDark
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isExpired) RoseRed else TextPrimary
                    )
                    Text(
                        text = if (isExpired) "Renew now!" else "Time remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Session Metadata
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Expires at:", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(endTimeString, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Started at:", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(startTimeString, color = TextPrimary)
                }
                if (!session.zoneOrLot.isNullOrBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Location / Zone:", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Text(session.zoneOrLot, color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (!session.locationAddress.isNullOrBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Street Address:", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            session.locationAddress,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth(0.65f),
                            textAlign = TextAlign.End
                        )
                    }
                }
                if (!session.purchasedDurationText.isNullOrBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Duration:", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Text(session.purchasedDurationText, color = TextPrimary)
                    }
                }
                if (!session.initialCostText.isNullOrBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Cost / Rate:", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Text(session.initialCostText, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                    }
                }
                if (!session.notesText.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark.copy(alpha = 0.5f))
                            .padding(8.dp)
                    ) {
                        Text("Note", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(session.notesText, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Detection Source:", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(session.source, color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val appName = com.parktimedetector.notification.NotificationHelper.getAppNameForPackage(session.packageName)

            // Primary Highlighted Button: Quick-Renew with Screen Automation
            Button(
                onClick = onQuickRenew,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚡ Quick-Renew in $appName",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (appName == "MyParking") "Auto-searches zone & prepares session" else "Auto-opens extend screen",
                        color = DarkNavy.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Option: Open app directly without automation
            OutlinedButton(
                onClick = onOpenApp,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open $appName Manually", color = TextSecondary, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Extension Buttons
            Text(
                text = "Quick Extend Timer",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onExtend(15) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("+15m", color = TextPrimary)
                }
                OutlinedButton(
                    onClick = { onExtend(30) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("+30m", color = TextPrimary)
                }
                OutlinedButton(
                    onClick = { onExtend(60) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("+1h", color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            var showEndConfirmation by remember { mutableStateOf(false) }

            if (showEndConfirmation) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showEndConfirmation = false },
                    title = { Text("Deactivate Parking Session?") },
                    text = {
                        Text(
                            "Are you sure you want to deactivate and end this parking session for ${session.displayLocation}? " +
                            "The active timer will stop, renewal alerts will be cancelled, and your session summary will be recorded."
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.Button(
                            onClick = {
                                showEndConfirmation = false
                                onEndSession()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoseRed)
                        ) {
                            Text("Deactivate & End", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.OutlinedButton(onClick = { showEndConfirmation = false }) {
                            Text("Keep Active")
                        }
                    },
                    containerColor = SurfaceDark
                )
            }

            // End Parking Button
            OutlinedButton(
                onClick = { showEndConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = RoseRed)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Deactivate / End Session", color = RoseRed)
            }
        }
    }
}

@Composable
fun NoActiveSessionCard(
    onOpenParkedIn: () -> Unit,
    onOpenMyParking: () -> Unit,
    alwaysDetect: Boolean = true
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No Active Parking Session",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "When you start parking in ParkedIn or MyParking, the app will automatically detect it and set a timer with renewal alerts!",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenParkedIn,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ParkedIn", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onOpenMyParking,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("MyParking", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            }

            if (!alwaysDetect) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "💡 Pressing either app button activates a 5-minute detection window (resets if pressed again).",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualTimerSetupCard(
    onStartTimer: (durationMinutes: Int, zone: String?) -> Unit
) {
    var selectedMinutes by remember { mutableStateOf(60) }
    var zoneInput by remember { mutableStateOf("") }

    val presets = listOf(15, 30, 45, 60, 90, 120, 180)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = PrimaryBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Set In-App Parking Timer",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Paid at a parking meter or web QR code? Start a timer here to receive the same live notifications and ParkedIn renewal alerts.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Select Duration:", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { minutes ->
                    val isSelected = selectedMinutes == minutes
                    val label = if (minutes >= 60) {
                        val hours = minutes / 60
                        val mins = minutes % 60
                        if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
                    } else {
                        "${minutes}m"
                    }

                    Surface(
                        color = if (isSelected) PrimaryBlue else SurfaceDark,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedMinutes = minutes }
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = if (isSelected) Color.Black else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = zoneInput,
                onValueChange = { zoneInput = it },
                label = { Text("Zone / Lot (Optional, e.g. Zone 4022)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = TextMuted,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = { onStartTimer(selectedMinutes, zoneInput) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Parking Timer", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ManualDetectionControlCard(
    isDetectionActive: Boolean,
    remainingSeconds: Int,
    onStartOrReset: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d", mins, secs)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDetectionActive) EmeraldGreen.copy(alpha = 0.12f) else CardBackground
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isDetectionActive) EmeraldGreen else SurfaceDark
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isDetectionActive) "🟢" else "🔍", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDetectionActive) "Detection Window Active" else "Manual Detection Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDetectionActive) EmeraldGreen else TextPrimary
                    )
                }

                if (isDetectionActive) {
                    Surface(
                        color = EmeraldGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isDetectionActive) {
                    "Actively scanning screen and notifications for parking start/stop. Detection will timeout in $timeFormatted. Pressing 'Restart 5m' will start the time over."
                } else {
                    "'Always detect' is off in Settings. Tap 'Start Detection' to begin a 5-minute detection window, or tap a supported app button below."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartOrReset,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDetectionActive) EmeraldGreen else PrimaryBlue
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isDetectionActive) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isDetectionActive) DarkNavy else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDetectionActive) "Restart 5m" else "Start Detection",
                        color = if (isDetectionActive) DarkNavy else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isDetectionActive) {
                    OutlinedButton(
                        onClick = onStop,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Stop", color = RoseRed)
                    }
                }
            }
        }
    }
}

