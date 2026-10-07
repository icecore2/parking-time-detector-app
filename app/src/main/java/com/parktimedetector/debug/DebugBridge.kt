package com.parktimedetector.debug

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parktimedetector.data.ParkingSession
import com.parktimedetector.ui.viewmodel.ParkingViewModel

interface DebugBridge {
    val isDebug: Boolean
    val hasSimulator: Boolean
    val showLogsTab: Boolean

    @Composable
    fun SimulatorTopBarButton(onOpenSimulator: () -> Unit)

    @Composable
    fun SimulatorSheet(viewModel: ParkingViewModel, onDismiss: () -> Unit)

    @Composable
    fun SettingsSimulatorCard(viewModel: ParkingViewModel)

    @Composable
    fun RenderLogsScreen(
        viewModel: ParkingViewModel,
        modifier: Modifier
    )

    fun handleMissingParkingApp(
        context: Context,
        isMyParking: Boolean,
        session: ParkingSession?,
        targetPackage: String
    )
}

object DebugBridgeProvider {
    val bridge: DebugBridge by lazy {
        try {
            val clazz = Class.forName("com.parktimedetector.debug.DebugBridgeImpl")
            clazz.getDeclaredConstructor().newInstance() as DebugBridge
        } catch (_: Throwable) {
            ReleaseDebugBridge()
        }
    }
}

class ReleaseDebugBridge : DebugBridge {
    override val isDebug: Boolean = false
    override val hasSimulator: Boolean = false
    override val showLogsTab: Boolean = false

    @Composable
    override fun SimulatorTopBarButton(onOpenSimulator: () -> Unit) {
        // No-op in release builds
    }

    @Composable
    override fun SimulatorSheet(viewModel: ParkingViewModel, onDismiss: () -> Unit) {
        // No-op in release builds
    }

    @Composable
    override fun SettingsSimulatorCard(viewModel: ParkingViewModel) {
        // No-op in release builds
    }

    @Composable
    override fun RenderLogsScreen(viewModel: ParkingViewModel, modifier: Modifier) {
        // No-op in release builds
    }

    override fun handleMissingParkingApp(
        context: Context,
        isMyParking: Boolean,
        session: ParkingSession?,
        targetPackage: String
    ) {
        try {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$targetPackage")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$targetPackage")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            try {
                context.startActivity(webIntent)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Target parking app ($targetPackage) is not installed.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
