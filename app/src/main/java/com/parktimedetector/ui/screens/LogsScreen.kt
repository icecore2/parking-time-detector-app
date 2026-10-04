package com.parktimedetector.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parktimedetector.data.LogEntry
import com.parktimedetector.service.ParkingAccessibilityService
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
fun LogsScreen(
    viewModel: ParkingViewModel,
    logs: List<LogEntry>,
    isNotificationServiceConnected: Boolean,
    isAccessibilityConnected: Boolean,
    logAllNotifications: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasNotifPermission by remember { mutableStateOf(ParkingNotificationListenerService.isPermissionGranted(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(ParkingAccessibilityService.isAccessibilityServiceEnabled(context)) }
    var selectedFilter by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        while (true) {
            hasNotifPermission = ParkingNotificationListenerService.isPermissionGranted(context)
            hasAccessibilityPermission = ParkingAccessibilityService.isAccessibilityServiceEnabled(context)
            delay(2000)
        }
    }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "Flow" -> logs.filter {
                it.tag in listOf("PRESSING", "APP_OPENING", "DIALOG_WINDOW", "SUPPRESSED_START", "SUPPRESSED_STOP", "BUTTON_CLICKED", "STOP_BUTTON_CLICKED", "SCREEN_DETECTED", "SCREEN_STOP_DETECTED") ||
                    it.message.contains("[FLOW:")
            }
            "Screen" -> logs.filter { it.tag.contains("SCREEN") }
            "Clicks" -> logs.filter { it.tag == "BUTTON_CLICKED" || it.tag == "PRESSING" || it.tag == "STOP_BUTTON_CLICKED" }
            "Parking" -> logs.filter { it.tag.contains("NOTIF_RECEIVED") || it.tag.contains("SESSION_STARTED") || it.tag.contains("PARSE") || it.tag.contains("SCREEN") || it.tag.contains("APPROVED") || it.tag.contains("DIALOG") }
            "Success" -> logs.filter { it.level == "SUCCESS" }
            "Errors" -> logs.filter { it.level == "ERROR" || it.level == "WARN" }
            "Other Apps" -> logs.filter { it.tag == "NOTIF_OTHER" }
            else -> logs
        }
    }

    fun copyLogsToClipboard() {
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val text = buildString {
            append("=== PARKING TIME DETECTOR LOGS ===\n")
            append("Notification Listener Permission: ").append(if (hasNotifPermission) "GRANTED" else "DENIED").append("\n")
            append("Notification Service Connected: ").append(if (isNotificationServiceConnected) "YES" else "NO").append("\n")
            append("Accessibility Permission: ").append(if (hasAccessibilityPermission) "GRANTED" else "DENIED").append("\n")
            append("Accessibility Service Connected: ").append(if (isAccessibilityConnected) "YES" else "NO").append("\n")
            append("Total logs: ").append(logs.size).append("\n\n")
            logs.forEach { log ->
                append("[${timeFormat.format(Date(log.timestampMillis))}] [${log.level}] [${log.tag}]\n")
                if (log.packageName != null) append("Package: ${log.packageName}\n")
                append("Message: ${log.message}\n")
                if (!log.rawData.isNullOrBlank()) {
                    append("Raw Data:\n${log.rawData}\n")
                }
                append("----------------------------------------\n")
            }
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Detection Logs", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied ${logs.size} logs to clipboard!", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Detection Logs",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${filteredLogs.size} logs visible (${logs.size} total)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = {
                    viewModel.shareLogs(
                        context = context,
                        hasNotifPermission = hasNotifPermission,
                        isNotifConnected = isNotificationServiceConnected,
                        hasAccessibilityPermission = hasAccessibilityPermission,
                        isAccessibilityConnected = isAccessibilityConnected,
                        logAllNotifications = logAllNotifications
                    )
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share Logs (Zip)", tint = PrimaryBlue)
                }
                IconButton(onClick = { copyLogsToClipboard() }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs", tint = PrimaryBlue)
                }
                IconButton(onClick = { viewModel.clearLogs() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = RoseRed)
                }
            }
        }

        // Listener & Accessibility Detection Services Status Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 1. Notification Listener Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (hasNotifPermission && isNotificationServiceConnected) EmeraldGreen else AmberWarning)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Notification Listener: " + if (hasNotifPermission) {
                                    if (isNotificationServiceConnected) "Active" else "Connecting"
                                } else "Disabled",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasNotifPermission && isNotificationServiceConnected) EmeraldGreen else AmberWarning
                            )
                        }
                        Text(
                            text = if (!hasNotifPermission) "Grant notification access"
                            else "Monitoring ParkedIn notification events",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    if (!hasNotifPermission) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Enable", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.reconnectService(context) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reconnect", color = PrimaryBlue, fontSize = 12.sp)
                        }
                    }
                }

                // 2. Accessibility Screen Detector Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (hasAccessibilityPermission) EmeraldGreen else AmberWarning)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Screen Inspection: " + if (hasAccessibilityPermission) {
                                    if (isAccessibilityConnected) "Active & Connected" else "Enabled"
                                } else "Disabled",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasAccessibilityPermission) EmeraldGreen else AmberWarning
                            )
                        }
                        Text(
                            text = if (!hasAccessibilityPermission) "Required for MyParking screen capture"
                            else "Ready to capture in-app timers (MyParking & ParkedIn)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    if (!hasAccessibilityPermission) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Enable", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Log all notifications switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Log All Notifications", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("Records non-parking apps for diagnosis", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(
                        checked = logAllNotifications,
                        onCheckedChange = { viewModel.toggleLogAllNotifications(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PrimaryBlue,
                            checkedTrackColor = PrimaryBlue.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }

        // Filter chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filters = listOf("All", "Flow", "Screen", "Clicks", "Parking", "Success", "Errors", "Other Apps")
            filters.forEach { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    color = if (isSelected) PrimaryBlue else CardBackground,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedFilter = filter }
                ) {
                    Text(
                        text = filter,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = if (isSelected) Color.Black else TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Logs List
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Logs Recorded Yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Start a parking session in ParkedIn or MyParking to see real-time detection events and raw notification text here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { viewModel.addTestLog() },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add Test Log Entry", color = PrimaryBlue)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    LogItemCard(log = log)
                }
            }
        }
    }
}

@Composable
fun LogItemCard(log: LogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    val formattedTime = timeFormat.format(Date(log.timestampMillis))

    val (badgeColor, badgeText) = when {
        log.tag == "APP_OPENING" -> PrimaryBlue to "APP OPENING"
        log.tag == "PRESSING" -> AccentCyan to "PRESSING"
        log.tag == "DIALOG_WINDOW" -> Color(0xFFBB86FC) to "DIALOG WINDOW"
        log.tag == "SUPPRESSED_START" -> AmberWarning to "SUPPRESSED START"
        log.tag == "SUPPRESSED_STOP" -> AmberWarning to "SUPPRESSED STOP"
        log.tag == "STOP_BUTTON_CLICKED" -> AmberWarning to "STOP CLICKED"
        log.tag == "SCREEN_STOP_DETECTED" -> RoseRed to "STOP DETECTED"
        log.tag == "BUTTON_CLICKED" -> AccentCyan to "CLICKED"
        log.tag == "DETECTION_PENDING" -> AmberWarning to "PENDING APPROVAL"
        log.tag == "SESSION_APPROVED" -> EmeraldGreen to "APPROVED"
        log.tag == "SESSION_DISMISSED" -> RoseRed to "DISMISSED"
        log.tag == "SCREEN_DETECTED" -> EmeraldGreen to "SCREEN DETECTED"
        log.tag == "SCREEN_TEXT" -> AccentCyan to "SCREEN TEXT"
        log.tag == "SCREEN_PARSE_FAILED" -> AmberWarning to "SCREEN WARN"
        log.tag == "SCREEN_SERVICE" -> EmeraldGreen to "SCREEN SERVICE"
        log.level == "SUCCESS" -> EmeraldGreen to "SUCCESS"
        log.level == "ERROR" -> RoseRed to "ERROR"
        log.level == "WARN" -> AmberWarning to "WARN"
        else -> when (log.tag) {
            "NOTIF_RECEIVED" -> AccentCyan to "NOTIF"
            "NOTIF_OTHER" -> TextMuted to "OTHER"
            "SERVICE_STATUS" -> PrimaryBlue to "SYSTEM"
            else -> PrimaryBlue to log.tag
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!log.packageName.isNullOrBlank()) {
                        Text(
                            text = log.packageName.substringAfterLast("."),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )

            AnimatedVisibility(visible = expanded && !log.rawData.isNullOrBlank()) {
                val context = LocalContext.current
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkNavy)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Raw Notification Extras:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Raw Data", log.rawData ?: ""))
                                Toast.makeText(context, "Copied raw extras!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = log.rawData ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
