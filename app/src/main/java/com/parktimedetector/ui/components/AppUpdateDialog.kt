package com.parktimedetector.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parktimedetector.BuildConfig
import com.parktimedetector.ui.theme.AccentCyan
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
import com.parktimedetector.update.AppUpdateEngine
import com.parktimedetector.update.UpdateState
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppUpdateDialog(
    viewModel: ParkingViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsState()
    var hasInstallPermission by remember {
        mutableStateOf(AppUpdateEngine.canRequestPackageInstalls(context))
    }

    LaunchedEffect(Unit) {
        viewModel.checkOrClearIfInstalled(context)
        while (true) {
            hasInstallPermission = AppUpdateEngine.canRequestPackageInstalls(context)
            delay(2000)
        }
    }

    val activeInfo = when (val state = updateState) {
        is UpdateState.Available -> state.updateInfo
        is UpdateState.Downloading -> state.updateInfo
        is UpdateState.ReadyToInstall -> state.updateInfo
        else -> viewModel.latestCachedUpdateInfo
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "App Updates",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Version Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Current Version", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    if (activeInfo != null) {
                        Surface(
                            color = PrimaryBlue.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Target Version", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    activeInfo.versionName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                            }
                        }
                    }
                }

                // Dynamic Status & Action Area
                when (val state = updateState) {
                    is UpdateState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceDark)
                                .padding(14.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PrimaryBlue,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Checking for updates from ${state.source}...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    is UpdateState.Available -> {
                        Surface(
                            color = EmeraldGreen.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "New Version: ${state.updateInfo.versionName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = EmeraldGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                    state.updateInfo.fileSizeBytes?.let { bytes ->
                                        val mb = bytes / (1024.0 * 1024.0)
                                        Text(
                                            String.format(Locale.US, "%.1f MB", mb),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }

                                if (state.updateInfo.changelog.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Release Notes:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = SurfaceDark,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            state.updateInfo.changelog,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { viewModel.downloadUpdate(context, state.updateInfo) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download Update", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    is UpdateState.Downloading -> {
                        Surface(
                            color = PrimaryBlue.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Downloading ${state.updateInfo.versionName}...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (state.progressPercent >= 0) "${state.progressPercent}%" else "...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PrimaryBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (state.progressPercent >= 0) {
                                    LinearProgressIndicator(
                                        progress = { state.progressPercent / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = PrimaryBlue,
                                        trackColor = SurfaceDark
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = PrimaryBlue,
                                        trackColor = SurfaceDark
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { viewModel.cancelUpdateDownload(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = RoseRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cancel Download", color = Color.White)
                                }
                            }
                        }
                    }

                    is UpdateState.ReadyToInstall -> {
                        Surface(
                            color = EmeraldGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Ready to Install: ${state.updateInfo.versionName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = EmeraldGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Package has been downloaded and verified. Ready to install.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )

                                if (!hasInstallPermission) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "⚠️ Android requires permission to install APKs from outside Google Play.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AmberWarning
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { AppUpdateEngine.openInstallPermissionSettings(context) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Grant Install Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.installUpdate(context, state.apkFile) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Install Now", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    is UpdateState.Error -> {
                        Surface(
                            color = AmberWarning.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    else -> {
                        // Idle or UpToDate
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "You are running the latest version (v${BuildConfig.VERSION_NAME}).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }

                // Action & Simulator Row
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.checkForUpdates(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check for Updates", color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.simulatePushUpdate(
                                context = context,
                                version = "1.1.0",
                                versionCode = 2,
                                changelog = "Simulated Push Update:\n- Integrated push update engine\n- Tested push broadcast handler\n- UI update dialog"
                            )
                            Toast.makeText(context, "Simulated push update dispatched!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate Push Update", color = AccentCyan)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        },
        containerColor = CardBackground,
        shape = RoundedCornerShape(20.dp)
    )
}
