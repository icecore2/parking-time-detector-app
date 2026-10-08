package com.parktimedetector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.parktimedetector.debug.DebugBridgeProvider
import com.parktimedetector.ui.screens.HistoryScreen
import com.parktimedetector.ui.screens.HomeScreen
import com.parktimedetector.ui.screens.SettingsScreen
import com.parktimedetector.ui.theme.DarkNavy
import com.parktimedetector.ui.theme.ParkingTimeDetectorTheme
import com.parktimedetector.ui.theme.PrimaryBlue
import com.parktimedetector.ui.theme.SurfaceDark
import com.parktimedetector.ui.theme.TextMuted
import com.parktimedetector.ui.theme.TextPrimary
import com.parktimedetector.ui.viewmodel.ParkingViewModel
import com.parktimedetector.update.AppUpdateEngine

enum class ScreenTab(val title: String) {
    HOME("Timer"),
    HISTORY("History"),
    LOGS("Logs"),
    SETTINGS("Settings")
}

class MainActivity : ComponentActivity() {

    private val viewModel: ParkingViewModel by viewModels()

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Check for updates silently in the background if update engine is enabled
        if (AppUpdateEngine.IS_ENABLED) {
            viewModel.checkForUpdates(this, silent = true)
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            ParkingTimeDetectorTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.parktimedetector.audio.AlarmSoundManager.isPlaying.value) {
            com.parktimedetector.audio.AlarmSoundManager.stop(this)
        }
        if (AppUpdateEngine.IS_ENABLED) {
            viewModel.checkOrClearIfInstalled(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (com.parktimedetector.audio.AlarmSoundManager.isPlaying.value) {
            com.parktimedetector.audio.AlarmSoundManager.stop(this)
        }
    }
}

@Composable
fun MainAppContent(viewModel: ParkingViewModel) {
    var currentTab by remember { mutableStateOf(ScreenTab.HOME) }
    var showSimulatorSheet by remember { mutableStateOf(false) }

    val activeSession by viewModel.activeSession.collectAsState()
    val sessionHistory by viewModel.sessionHistory.collectAsState()
    val advanceWarningMinutes by viewModel.advanceWarningMinutes.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isServiceConnected by viewModel.isServiceConnected.collectAsState()
    val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsState()
    val logAllNotifications by viewModel.logAllNotifications.collectAsState()
    val showConfirmationDialogs by viewModel.showConfirmationDialogs.collectAsState()
    val pendingDetection by viewModel.pendingDetection.collectAsState()
    val pendingStopDetection by viewModel.pendingStopDetection.collectAsState()
    val recentlyStoppedSession by viewModel.recentlyStoppedSession.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity

    // When opened from notification or when pending detection exists in app,
    // ensure user is on the HOME tab and dismiss floating overlay so there's no visual clutter.
    androidx.compose.runtime.LaunchedEffect(activity?.intent, pendingDetection, pendingStopDetection) {
        if (activity?.intent?.getStringExtra("navigate_tab") == "SETTINGS") {
            currentTab = ScreenTab.SETTINGS
            activity.intent.removeExtra("navigate_tab")
        } else if (activity?.intent?.getBooleanExtra("EXTRA_SHOW_APPROVAL_DIALOG", false) == true ||
            activity?.intent?.getBooleanExtra("EXTRA_SHOW_STOP_DIALOG", false) == true
        ) {
            currentTab = ScreenTab.HOME
            com.parktimedetector.service.DetectionApprovalManager.overlayCallback?.hideOverlay()
            activity.intent.removeExtra("EXTRA_SHOW_APPROVAL_DIALOG")
            activity.intent.removeExtra("EXTRA_SHOW_STOP_DIALOG")
        }
    }

    if (showSimulatorSheet && DebugBridgeProvider.bridge.hasSimulator) {
        DebugBridgeProvider.bridge.SimulatorSheet(
            viewModel = viewModel,
            onDismiss = { showSimulatorSheet = false }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ParkingTimeDetector",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Simulator Quick Launch Button (Debug Only)
                    if (DebugBridgeProvider.bridge.hasSimulator) {
                        DebugBridgeProvider.bridge.SimulatorTopBarButton(
                            onOpenSimulator = { showSimulatorSheet = true }
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                val navBarItemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NavigationBarItem(
                    selected = currentTab == ScreenTab.HOME,
                    onClick = { currentTab = ScreenTab.HOME },
                    icon = { Icon(Icons.Default.Timer, contentDescription = "Timer") },
                    label = { Text("Timer") },
                    colors = navBarItemColors
                )
                NavigationBarItem(
                    selected = currentTab == ScreenTab.HISTORY,
                    onClick = { currentTab = ScreenTab.HISTORY },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                    colors = navBarItemColors
                )
                if (DebugBridgeProvider.bridge.showLogsTab) {
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.LOGS,
                        onClick = { currentTab = ScreenTab.LOGS },
                        icon = { Icon(Icons.Default.BugReport, contentDescription = "Logs") },
                        label = { Text("Logs") },
                        colors = navBarItemColors
                    )
                }
                NavigationBarItem(
                    selected = currentTab == ScreenTab.SETTINGS,
                    onClick = { currentTab = ScreenTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = navBarItemColors
                )
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            ScreenTab.HOME -> {
                if (recentlyStoppedSession != null) {
                    com.parktimedetector.ui.screens.SessionStopScreen(
                        session = recentlyStoppedSession!!,
                        onDone = { viewModel.clearRecentlyStoppedSession() },
                        onViewHistory = {
                            viewModel.clearRecentlyStoppedSession()
                            currentTab = ScreenTab.HISTORY
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                } else {
                    HomeScreen(
                        viewModel = viewModel,
                        activeSession = activeSession,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
            ScreenTab.HISTORY -> HistoryScreen(
                viewModel = viewModel,
                sessions = sessionHistory,
                modifier = Modifier.padding(innerPadding)
            )
            ScreenTab.LOGS -> {
                if (DebugBridgeProvider.bridge.showLogsTab) {
                    DebugBridgeProvider.bridge.RenderLogsScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                } else {
                    HomeScreen(
                        viewModel = viewModel,
                        activeSession = activeSession,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
            ScreenTab.SETTINGS -> SettingsScreen(
                viewModel = viewModel,
                advanceWarningMinutes = advanceWarningMinutes,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
