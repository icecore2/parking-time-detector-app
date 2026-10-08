package com.parktimedetector.debug

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parktimedetector.data.ParkingSession
import com.parktimedetector.mock.MockMyParkingActivity
import com.parktimedetector.mock.MockParkedInActivity
import com.parktimedetector.service.QuickRenewManager
import com.parktimedetector.service.RenewAppType
import com.parktimedetector.ui.screens.LogsScreen
import com.parktimedetector.ui.screens.SimulatorBottomSheet
import com.parktimedetector.ui.screens.SimulatorPlaygroundContent
import com.parktimedetector.ui.theme.CardBackground
import com.parktimedetector.ui.theme.PrimaryBlue
import com.parktimedetector.ui.theme.TextPrimary
import com.parktimedetector.ui.theme.TextSecondary
import com.parktimedetector.ui.viewmodel.ParkingViewModel

class DebugBridgeImpl : DebugBridge {
    override val isDebug: Boolean = true
    override val hasSimulator: Boolean = true
    override val showLogsTab: Boolean = true

    @Composable
    override fun SimulatorTopBarButton(onOpenSimulator: () -> Unit) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onOpenSimulator() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = "Open Simulator",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Simulator",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }

    @Composable
    override fun SimulatorSheet(viewModel: ParkingViewModel, onDismiss: () -> Unit) {
        SimulatorBottomSheet(
            viewModel = viewModel,
            onDismiss = onDismiss
        )
    }

    @Composable
    override fun SettingsSimulatorCard(viewModel: ParkingViewModel) {
        var isExpanded by rememberSaveable { mutableStateOf(false) }
        val chevronRotation by animateFloatAsState(
            targetValue = if (isExpanded) 180f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "chevron_rotation"
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(PrimaryBlue.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Test & Detection Simulator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            color = PrimaryBlue.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Debug Playground",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = TextSecondary,
                            modifier = Modifier.rotate(chevronRotation)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = TextSecondary.copy(alpha = 0.15f)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = "Verify notification detection, live screen text inspection, countdowns, and alarms right now without needing an active parking session.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.size(16.dp))
                            SimulatorPlaygroundContent(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxWidth(),
                                enableScroll = false
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun RenderLogsScreen(viewModel: ParkingViewModel, modifier: Modifier) {
        val logs by viewModel.logs.collectAsState()
        val isServiceConnected by viewModel.isServiceConnected.collectAsState()
        val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsState()
        val logAllNotifications by viewModel.logAllNotifications.collectAsState()

        LogsScreen(
            viewModel = viewModel,
            logs = logs,
            isNotificationServiceConnected = isServiceConnected,
            isAccessibilityConnected = isAccessibilityConnected,
            logAllNotifications = logAllNotifications,
            modifier = modifier
        )
    }

    override fun handleMissingParkingApp(
        context: Context,
        isMyParking: Boolean,
        session: ParkingSession?,
        targetPackage: String
    ) {
        val targetClass = if (isMyParking) {
            MockMyParkingActivity::class.java
        } else {
            MockParkedInActivity::class.java
        }

        QuickRenewManager.arm(
            context = context,
            packageName = context.packageName,
            zoneOrLot = session?.zoneOrLot ?: (if (isMyParking) "Lot 58 - 935 - 4 Av SW" else "Zone 4022"),
            locationAddress = session?.locationAddress,
            appType = if (isMyParking) RenewAppType.MYPARKING else RenewAppType.PARKEDIN
        )

        Toast.makeText(context, "Real app not installed. Launching debug mock sandbox...", Toast.LENGTH_SHORT).show()
        val intent = Intent(context, targetClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
