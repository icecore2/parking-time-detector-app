package com.parktimedetector.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parktimedetector.notification.NotificationHelper
import com.parktimedetector.service.SessionNotificationParser
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorBottomSheet(
    viewModel: ParkingViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkNavy,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(PrimaryBlue.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Simulator Playground",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Local Testing & Event Injection Harness",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            SimulatorPlaygroundContent(
                viewModel = viewModel,
                enableScroll = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulatorPlaygroundContent(
    viewModel: ParkingViewModel,
    modifier: Modifier = Modifier,
    enableScroll: Boolean = false
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Interactive custom session state
    var selectedApp by remember { mutableStateOf("ParkedIn") }
    var customZone by remember { mutableStateOf("Zone 4022") }
    var durationMinutes by remember { mutableFloatStateOf(45f) }

    // Custom regex parser tester state
    var liveParseInput by remember { mutableStateOf("Parking active in Zone 4022. Expires at 4:30 PM") }

    val liveParsedResult by remember(liveParseInput) {
        derivedStateOf {
            if (liveParseInput.isBlank()) null
            else SessionNotificationParser.parse("Test Notification", liveParseInput, null, System.currentTimeMillis())
        }
    }

    val contentModifier = if (enableScroll) {
        modifier.verticalScroll(scrollState)
    } else {
        modifier
    }

    Column(
        modifier = contentModifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. One-Click Scenario Presets
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Quick Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "Instantly trigger realistic start/stop/screen scenarios without manual typing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                // Quick 1-Min Expiry
                OutlinedButton(
                    onClick = {
                        val futureTime = System.currentTimeMillis() + 65_000L
                        val timeStr = SimpleDateFormat("h:mm:ss a", Locale.US).format(Date(futureTime))
                        val success = viewModel.simulateParkedInNotification(
                            title = "ParkedIn Session Started",
                            text = "Parking active in Zone 999. Expires at $timeStr"
                        )
                        if (success) {
                            Toast.makeText(context, "1-Min quick session started! Countdown & alarms active.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("⚡ 1-Minute Expiry Test (Watch timer count down & ring)", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                }

                // Quick 2-Min Critical Zone Expiry
                OutlinedButton(
                    onClick = {
                        val futureTime = System.currentTimeMillis() + 120_000L
                        val timeStr = SimpleDateFormat("h:mm:ss a", Locale.US).format(Date(futureTime))
                        val success = viewModel.simulateParkedInNotification(
                            title = "ParkedIn Session Started",
                            text = "Parking active in Zone 4022. Expires at $timeStr"
                        )
                        if (success) {
                            Toast.makeText(context, "2-Min Critical Zone session started! Check Pulsing Red gauge.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("🚨 2-Minute Critical Zone Test (Pulsing Red Gauge & Alarm)", color = RoseRed, fontWeight = FontWeight.Bold)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ParkedIn 30m
                    OutlinedButton(
                        onClick = {
                            val futureTime = System.currentTimeMillis() + (30 * 60 * 1000L)
                            val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(futureTime))
                            viewModel.simulateParkedInNotification("ParkedIn", "Zone 4022 active. Expires at $timeStr")
                            Toast.makeText(context, "Simulated 30m ParkedIn session created!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🅿️ ParkedIn (30m)", color = TextPrimary, fontSize = 12.sp)
                    }

                    // MyParking 45m
                    OutlinedButton(
                        onClick = {
                            val futureTime = System.currentTimeMillis() + (45 * 60 * 1000L)
                            val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(futureTime))
                            viewModel.simulateMyParkingNotification("MyParking", "Zone 1205 until $timeStr")
                            Toast.makeText(context, "Simulated 45m MyParking session created!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🅿️ MyParking (45m)", color = AccentCyan, fontSize = 12.sp)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Screen Split Nodes (MyParking)
                    OutlinedButton(
                        onClick = {
                            val futureTime = System.currentTimeMillis() + (45 * 60 * 1000L)
                            val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(futureTime))
                            val nodes = listOf("Calgary Parking Authority", "Active Session", "Zone", "1205", "Valid Until", timeStr, "Vehicle: AB-1234")
                            viewModel.simulateScreenDetection(nodes, NotificationHelper.MYPARKING_PACKAGE)
                            Toast.makeText(context, "Simulated MyParking screen capture!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("📱 MyParking Screen", color = EmeraldGreen, fontSize = 12.sp)
                    }

                    // Screen Countdown (ParkedIn)
                    OutlinedButton(
                        onClick = {
                            val nodes = listOf("Precise ParkLink", "ParkedIn Session", "Zone 4022", "Time Remaining: 01:15:00")
                            viewModel.simulateScreenDetection(nodes, NotificationHelper.PARKEDIN_PACKAGE)
                            Toast.makeText(context, "Simulated ParkedIn countdown capture!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("📱 ParkedIn Countdown", color = EmeraldGreen, fontSize = 12.sp)
                    }
                }

                // Exact START/END SESSION screen from user screenshot
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val nodes = listOf(
                                "START/END SESSION",
                                "END PARKING SESSION",
                                "Remaining: 19 mins : 57 secs",
                                "End Time: Monday, September 28 - 01:16 p.m.",
                                "50 Av SW , Fr 6 St SW To ELBOW Dr SW",
                                "Zone # 5586 20 mins. ($.00)",
                                "Note: Please remember to end your parking session so that unused time can be credited to your account. This doesn't apply to flat-rate parking zones.",
                                "LOCAL DEALS",
                                "FIND MY CAR"
                            )
                            viewModel.simulateScreenDetection(nodes, NotificationHelper.MYPARKING_PACKAGE)
                            Toast.makeText(context, "Simulated MyParking START/END active dashboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🎯 MyParking START/END", color = AccentCyan, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val stopNodes = listOf(
                                "START/END SESSION",
                                "END PARKING SESSION",
                                "Parking session ended successfully",
                                "Zone # 5586",
                                "50 Av SW , Fr 6 St SW To ELBOW Dr SW",
                                "Refund / Cost: $0.00",
                                "DONE"
                            )
                            viewModel.simulateStopScreenDetection(stopNodes, NotificationHelper.MYPARKING_PACKAGE)
                            Toast.makeText(context, "Simulated MyParking deactivation confirmation!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🛑 Deactivate Zone 5586", color = RoseRed, fontSize = 12.sp)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ParkedIn Stop
                    OutlinedButton(
                        onClick = {
                            val nodes = listOf("Precise ParkLink", "Parking Stopped", "Zone 4022", "Ended: 4:30 PM", "Duration: 1 hr 15 mins", "Session Summary")
                            viewModel.simulateStopScreenDetection(nodes, NotificationHelper.PARKEDIN_PACKAGE)
                            Toast.makeText(context, "Simulated ParkedIn stop receipt!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🛑 Stop: ParkedIn", color = AmberWarning, fontSize = 12.sp)
                    }

                    // MyParking Stop
                    OutlinedButton(
                        onClick = {
                            val nodes = listOf("Calgary Parking Authority", "Session Ended", "Lot 58 - 935 - 4 Av SW", "Stopped at: 5:15 PM", "Total Time: 45 mins", "Total Cost: $2.50", "Receipt #9058", "DONE")
                            viewModel.simulateStopScreenDetection(nodes, NotificationHelper.MYPARKING_PACKAGE)
                            Toast.makeText(context, "Simulated MyParking stop receipt!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🛑 Stop: MyParking", color = RoseRed, fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. Interactive Custom Session Builder
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Custom Session Builder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                // App Picker
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ParkedIn", "MyParking").forEach { app ->
                        val isSelected = selectedApp == app
                        Surface(
                            color = if (isSelected) PrimaryBlue else SurfaceDark,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedApp = app }
                        ) {
                            Text(
                                text = app,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = if (isSelected) Color.Black else TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Zone text input
                OutlinedTextField(
                    value = customZone,
                    onValueChange = { customZone = it },
                    label = { Text("Zone or Lot ID") },
                    placeholder = { Text("e.g. Zone 4022 or Lot 58") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Duration Slider & Chips
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Duration:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(
                            text = "${durationMinutes.roundToInt()} minutes",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }

                    Slider(
                        value = durationMinutes,
                        onValueChange = { durationMinutes = it },
                        valueRange = 1f..180f,
                        steps = 179,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryBlue,
                            activeTrackColor = PrimaryBlue
                        )
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 15, 30, 45, 60, 90, 120).forEach { mins ->
                            Surface(
                                color = if (durationMinutes.roundToInt() == mins) PrimaryBlue.copy(alpha = 0.3f) else SurfaceDark,
                                shape = RoundedCornerShape(8.dp),
                                border = if (durationMinutes.roundToInt() == mins) androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue) else null,
                                modifier = Modifier.clickable { durationMinutes = mins.toFloat() }
                            ) {
                                Text(
                                    text = "$mins m",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (durationMinutes.roundToInt() == mins) PrimaryBlue else TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val mins = durationMinutes.roundToInt()
                        val futureTime = System.currentTimeMillis() + (mins * 60 * 1000L)
                        val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(futureTime))
                        val text = "Parking active in $customZone. Valid until $timeStr"
                        if (selectedApp == "ParkedIn") {
                            viewModel.simulateParkedInNotification("ParkedIn", text)
                        } else {
                            viewModel.simulateMyParkingNotification("MyParking", text)
                        }
                        Toast.makeText(context, "Launched $mins min $selectedApp session ($customZone)!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Launch Custom Session", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Live Regex Parser Diagnostic Tester
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Live Parser Diagnostic Tester",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "Type or paste any notification or screen dump to inspect regex extraction in real time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = liveParseInput,
                    onValueChange = { liveParseInput = it },
                    label = { Text("Raw notification or screen text") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Real-time extraction preview badge
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val parsed = liveParsedResult
                        if (parsed != null) {
                            val timeStr = SimpleDateFormat("h:mm a (MMM d)", Locale.US).format(Date(parsed.endTimeMillis))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PARSE SUCCESSFUL", color = EmeraldGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                            Text("• End Time: $timeStr", color = TextPrimary, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Text("• Zone: ${parsed.zoneOrLot ?: "None specified"}", color = TextPrimary, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Text("• Rule: ${parsed.detectedReason}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                text = "⚠️ No valid parking expiry time or duration recognized in current text.",
                                color = AmberWarning,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val parsed = liveParsedResult
                        if (parsed != null) {
                            val success = viewModel.simulateParkedInNotification("ParkedIn", liveParseInput)
                            if (success) {
                                Toast.makeText(context, "Dispatched parsed session!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Cannot dispatch: Text has no valid time.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = liveParsedResult != null,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Dispatch Parsed Session", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4. Instant Alert & Overlay Triggers
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Instant Alarm & Overlay Triggers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "Test system notifications, vibrations, and floating lockscreen dialogs without waiting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.triggerAdvanceAlarmNow()
                            Toast.makeText(context, "Dispatched advance warning alert!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🔔 15m Advance", color = AmberWarning, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.triggerCriticalAlarmNow()
                            Toast.makeText(context, "Dispatched critical warning alert!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🚨 Critical (<2m)", color = RoseRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.triggerExpiryAlarmNow()
                            Toast.makeText(context, "Dispatched expiry alarm alert!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("⏰ Expiry", color = TextPrimary, fontSize = 12.sp)
                    }
                }

                val isAlarmPlaying by viewModel.isAlarmPlaying.collectAsState()
                if (isAlarmPlaying) {
                    Button(
                        onClick = {
                            viewModel.stopAlarmNow()
                            Toast.makeText(context, "Stopped alarm audio and vibration", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.StopCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("⏹ Stop Ringing Alarm", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        viewModel.triggerOverlayTest()
                        Toast.makeText(context, "Overlay test triggered! Check screen / lock screen.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("🪟 Test Lock Screen Overlay Dialog", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:" + context.packageName)
                            ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                            context.startActivity(intent)
                            Toast.makeText(context, "Please grant 'Draw over other apps' permission", Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.showFloatingZonesOverlay(context)
                            Toast.makeText(context, "Floating Cheapest Zones overlay launched!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("🏷️ Show Floating Cheapest Zones List", color = DarkNavy, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4b. Test Quick-Renew Screen Automation
        val quickRenewState by viewModel.quickRenewState.collectAsState()
        val quickRenewMsg by viewModel.quickRenewStatusMessage.collectAsState()

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Quick-Renew Screen Automation",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tests the automated state machine that locates zones and taps Extend in MyParking and ParkedIn.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current State:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            text = quickRenewState.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (quickRenewState == com.parktimedetector.service.QuickRenewState.IDLE) TextMuted else PrimaryBlue
                        )
                    }
                }

                val message = quickRenewMsg
                if (!message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldGreen
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.launchMockApp(context, isMyParking = true)
                            Toast.makeText(context, "Launched Mock MyParking + Armed Quick-Renew", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Mock MyParking", color = DarkNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.launchMockApp(context, isMyParking = false)
                            Toast.makeText(context, "Launched Mock ParkedIn + Armed Quick-Renew", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Mock ParkedIn", color = DarkNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            com.parktimedetector.service.QuickRenewManager.arm(
                                context = context,
                                packageName = NotificationHelper.MYPARKING_PACKAGE,
                                zoneOrLot = "Lot 58 - 935 - 4 Av SW",
                                appType = com.parktimedetector.service.RenewAppType.MYPARKING
                            )
                            Toast.makeText(context, "Armed MyParking Quick-Renew (Zone 9058)", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Arm Real MyParking", color = PrimaryBlue, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            com.parktimedetector.service.QuickRenewManager.arm(
                                context = context,
                                packageName = NotificationHelper.PARKEDIN_PACKAGE,
                                zoneOrLot = "Zone 4022",
                                appType = com.parktimedetector.service.RenewAppType.PARKEDIN
                            )
                            Toast.makeText(context, "Armed ParkedIn Quick-Renew (Zone 4022)", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Arm Real ParkedIn", color = AccentCyan, fontSize = 11.sp)
                    }
                }

                if (quickRenewState != com.parktimedetector.service.QuickRenewState.IDLE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.disarmQuickRenew()
                            Toast.makeText(context, "Disarmed Quick-Renew", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Disarm / Reset State Machine", color = RoseRed, fontSize = 12.sp)
                    }
                }
            }
        }

        // 5. Reset Simulator State
        Button(
            onClick = {
                viewModel.resetSimulator()
                Toast.makeText(context, "Simulator reset: All active sessions, alarms, and approvals cleared.", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = RoseRed, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset Simulator & Clear All Sessions", color = RoseRed, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
