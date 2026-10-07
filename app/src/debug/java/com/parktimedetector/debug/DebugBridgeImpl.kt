package com.parktimedetector.debug

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                Spacer(modifier = Modifier.size(6.dp))
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
