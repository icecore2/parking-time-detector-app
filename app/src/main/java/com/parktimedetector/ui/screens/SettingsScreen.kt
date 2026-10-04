package com.parktimedetector.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parktimedetector.service.ParkingAlarmScheduler
import com.parktimedetector.service.ParkingNotificationListenerService
import com.parktimedetector.ui.theme.AmberWarning
import com.parktimedetector.ui.theme.CardBackground
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
fun SettingsScreen(
    viewModel: ParkingViewModel,
    advanceWarningMinutes: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var hasListenerPermission by remember { mutableStateOf(ParkingNotificationListenerService.isPermissionGranted(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(com.parktimedetector.service.ParkingAccessibilityService.isAccessibilityServiceEnabled(context)) }
    var hasExactAlarmPermission by remember { mutableStateOf(ParkingAlarmScheduler.canScheduleExactAlarms(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            hasListenerPermission = ParkingNotificationListenerService.isPermissionGranted(context)
            hasAccessibilityPermission = com.parktimedetector.service.ParkingAccessibilityService.isAccessibilityServiceEnabled(context)
            hasExactAlarmPermission = ParkingAlarmScheduler.canScheduleExactAlarms(context)
            delay(2000)
        }
    }

    val warningOptions = listOf(5, 10, 15, 20, 30)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings & Diagnostics",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        // 1. Advance Reminder Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Advance Renewal Alert",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Notify me this much time before the parking session ends, so I have enough time to renew in ParkedIn.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    warningOptions.forEach { minutes ->
                        val isSelected = advanceWarningMinutes == minutes
                        Surface(
                            color = if (isSelected) PrimaryBlue else SurfaceDark,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.updateAdvanceWarning(minutes) }
                        ) {
                            Text(
                                text = "$minutes mins${if (minutes == 15) " (Default)" else ""}",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = if (isSelected) Color.Black else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Detection Approval & Notification System Card
        val alwaysDetect by viewModel.alwaysDetect.collectAsState()
        val requireApproval by viewModel.requireApproval.collectAsState()
        val showConfirmationDialogs by viewModel.showConfirmationDialogs.collectAsState()
        val overlayDialogPosition by viewModel.overlayDialogPosition.collectAsState()
        val notificationManager = remember { context.getSystemService(android.app.NotificationManager::class.java) }
        var areNotificationsEnabled by remember { mutableStateOf(notificationManager?.areNotificationsEnabled() ?: true) }

        LaunchedEffect(Unit) {
            while (true) {
                areNotificationsEnabled = notificationManager?.areNotificationsEnabled() ?: true
                delay(2000)
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Detection Approval & Alerts",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Controls how detected parking sessions and stops are confirmed before timers and history are updated.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 0. Always Detect Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Always Detect",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (alwaysDetect) EmeraldGreen.copy(alpha = 0.2f) else AmberWarning.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (alwaysDetect) "CONTINUOUS" else "ON-DEMAND (5m)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (alwaysDetect) EmeraldGreen else AmberWarning,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = if (alwaysDetect) {
                                "Continuously monitor and detect parking sessions in the background."
                            } else {
                                "Automatic background detection paused. Tap 'Start Detection' on the main screen or launch a supported app to activate a 5-minute detection window."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    androidx.compose.material3.Switch(
                        checked = alwaysDetect,
                        onCheckedChange = { viewModel.toggleAlwaysDetect(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = EmeraldGreen,
                            checkedTrackColor = EmeraldGreen.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Require Approval Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Require User Confirmation",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Ask for your confirmation instead of automatically starting a timer when parking is detected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    androidx.compose.material3.Switch(
                        checked = requireApproval,
                        onCheckedChange = { viewModel.toggleRequireApproval(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = EmeraldGreen,
                            checkedTrackColor = EmeraldGreen.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Built-in Notifications info badge (Default / Recommended)
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = EmeraldGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "DEFAULT METHOD",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Built-in System Notifications",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Detections send native heads-up notifications with action buttons ('Start Timer', 'Accept Stop', 'Dismiss'). Tap the notification action to confirm without interrupting your current screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Confirmation Dialog Windows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Confirmation Dialog Window",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = PrimaryBlue.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "Floating Overlay",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryBlue,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Show a floating confirmation dialog directly over parking apps. When enabled, notifications stay quiet in the shade to prevent on-screen popup clutter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    androidx.compose.material3.Switch(
                        checked = showConfirmationDialogs,
                        onCheckedChange = { viewModel.toggleShowConfirmationDialogs(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = PrimaryBlue,
                            checkedTrackColor = PrimaryBlue.copy(alpha = 0.4f)
                        )
                    )
                }

                if (showConfirmationDialogs) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = SurfaceDark.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Dialog Placement",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            com.parktimedetector.data.OverlayDialogPosition.values().forEach { pos ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setOverlayDialogPosition(pos) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = overlayDialogPosition == pos,
                                        onClick = { viewModel.setOverlayDialogPosition(pos) },
                                        colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                            selectedColor = PrimaryBlue
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = pos.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (overlayDialogPosition == pos) FontWeight.Bold else FontWeight.Normal,
                                            color = if (overlayDialogPosition == pos) TextPrimary else TextSecondary
                                        )
                                        Text(
                                            text = pos.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Notification Troubleshooting Section
                Surface(
                    color = if (!areNotificationsEnabled) RoseRed.copy(alpha = 0.12f) else SurfaceDark.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    border = if (!areNotificationsEnabled) androidx.compose.foundation.BorderStroke(1.dp, RoseRed) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Having trouble with notifications?",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (!areNotificationsEnabled) RoseRed else TextPrimary
                            )
                            Surface(
                                color = if (areNotificationsEnabled) EmeraldGreen.copy(alpha = 0.2f) else RoseRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (areNotificationsEnabled) "Notifications Allowed" else "Notifications Blocked",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (areNotificationsEnabled) EmeraldGreen else RoseRed,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (!areNotificationsEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "System notifications are disabled for ParkingTimeDetector. Notifications cannot appear until enabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RoseRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Open System Notification Settings", color = Color.White, fontSize = 12.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Some device brands (Samsung, Xiaomi, Huawei) silence heads-up banners or aggressively put apps to sleep. Send a test notification below to check if heads-up notifications show on your phone. If not, enable 'Confirmation Dialog Windows' above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.sendTestApprovalNotification()
                                    Toast.makeText(context, "Test approval notification sent! Check your notification bar.", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Send Test Approval Notification", color = PrimaryBlue, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Automated Screen Actions (Quick-Renew Assist) Card
        val enableQuickRenewAutomation by viewModel.enableQuickRenewAutomation.collectAsState()
        val pauseBeforePayment by viewModel.pauseBeforePayment.collectAsState()

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Automated Screen Actions (Quick-Renew)",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Uses Accessibility Service to automate finding your zone and preparing renewal in supported parking apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle 1: Enable Quick-Renew Automation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Quick-Renew Assist",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "In MyParking: types zone number & selects lot. In ParkedIn: clicks 'Extend' automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    androidx.compose.material3.Switch(
                        checked = enableQuickRenewAutomation,
                        onCheckedChange = { viewModel.toggleEnableQuickRenewAutomation(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = PrimaryBlue,
                            checkedTrackColor = PrimaryBlue.copy(alpha = 0.4f)
                        )
                    )
                }

                if (enableQuickRenewAutomation) {
                    Spacer(modifier = Modifier.height(14.dp))

                    // Toggle 2: Pause at Final Start / Pay Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Pause Before Payment",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = EmeraldGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "RECOMMENDED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldGreen,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Pauses on the final START / Pay screen so you can verify rates, vehicle, and duration before payment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        androidx.compose.material3.Switch(
                            checked = pauseBeforePayment,
                            onCheckedChange = { viewModel.togglePauseBeforePayment(it) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = EmeraldGreen,
                                checkedTrackColor = EmeraldGreen.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
        }

        // Lock Screen Privacy & Overlay Policy Card
        val lockScreenPolicy by viewModel.lockScreenOverlayPolicy.collectAsState()
        val dismissOnScreenOff by viewModel.dismissOverlayOnScreenOff.collectAsState()
        val isLocked = remember { mutableStateOf(com.parktimedetector.service.OverlayPrivacyManager.isKeyguardLocked(context)) }
        val privateAllowed = remember { mutableStateOf(com.parktimedetector.service.OverlayPrivacyManager.isPrivateContentAllowedOnLockScreen(context)) }

        LaunchedEffect(Unit) {
            while (true) {
                isLocked.value = com.parktimedetector.service.OverlayPrivacyManager.isKeyguardLocked(context)
                privateAllowed.value = com.parktimedetector.service.OverlayPrivacyManager.isPrivateContentAllowedOnLockScreen(context)
                delay(2000)
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Overlay Privacy & Lock Screen",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Control floating dialog visibility on locked screens to protect sensitive parking location, duration, and cost details.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Diagnostic badges for system privacy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (isLocked.value) AmberWarning.copy(alpha = 0.2f) else EmeraldGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Keyguard", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                if (isLocked.value) "Locked" else "Unlocked",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isLocked.value) AmberWarning else EmeraldGreen
                            )
                        }
                    }
                    Surface(
                        color = if (privateAllowed.value) EmeraldGreen.copy(alpha = 0.2f) else RoseRed.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Private Content", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                if (privateAllowed.value) "Allowed" else "Masked",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (privateAllowed.value) EmeraldGreen else RoseRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Lock Screen Overlay Behavior",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                com.parktimedetector.data.LockScreenOverlayPolicy.values().forEach { policy ->
                    val isSelected = lockScreenPolicy == policy
                    Surface(
                        color = if (isSelected) PrimaryBlue.copy(alpha = 0.15f) else SurfaceDark,
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlue) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.setLockScreenOverlayPolicy(policy) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setLockScreenOverlayPolicy(policy) },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = PrimaryBlue
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = policy.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) PrimaryBlue else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = policy.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dismiss on Screen Off toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dismiss Overlay When Screen Turns Off",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Hides active overlay dialog immediately on display power-off; resumes upon unlock.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    androidx.compose.material3.Switch(
                        checked = dismissOnScreenOff,
                        onCheckedChange = { viewModel.setDismissOverlayOnScreenOff(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = PrimaryBlue,
                            checkedTrackColor = PrimaryBlue.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }

        // 2. Notification Action Explanation & Supported Apps Test
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Supported Parking Apps",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pressing any parking notification directly opens the corresponding app (ParkedIn or MyParking) so you can renew your session without searching for the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.openParkedInApp(context) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Launch ParkedIn", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.openMyParkingApp(context) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = com.parktimedetector.ui.theme.AccentCyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Launch MyParking", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Permissions Section
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Required Permissions",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }

                // Notification Access
                PermissionRow(
                    title = "Notification Access",
                    description = "Required to detect ParkedIn parking session start notifications",
                    isGranted = hasListenerPermission,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )

                // Screen Inspection (Accessibility Service)
                PermissionRow(
                    title = "Screen Inspection (Accessibility)",
                    description = "Required for apps like MyParking that do not post notifications. Reads on-screen session timers.",
                    isGranted = hasAccessibilityPermission,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )

                // Exact Alarms
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PermissionRow(
                        title = "Exact Alarms",
                        description = "Required to trigger renewal alerts precisely on time during Doze sleep",
                        isGranted = hasExactAlarmPermission,
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        // 4. In-App Simulator Tool
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Test & Detection Simulator",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Verify notification detection, live screen text inspection, countdowns, and alarms right now without needing an active parking session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                SimulatorPlaygroundContent(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth(),
                    enableScroll = false
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) EmeraldGreen else AmberWarning,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (isGranted) {
            Surface(
                color = EmeraldGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Granted",
                    color = EmeraldGreen,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Fix", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
