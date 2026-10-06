package com.parktimedetector.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.Button
import com.parktimedetector.MainActivity
import com.parktimedetector.data.ParkingDatabase
import com.parktimedetector.data.ParkingSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParkingGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = ParkingDatabase.getDatabase(context)
        val session = db.parkingDao().getActiveSession()

        provideContent {
            GlanceTheme {
                if (session != null && session.isActive && !session.isExpired()) {
                    ActiveSessionWidgetContent(session)
                } else {
                    InactiveWidgetContent()
                }
            }
        }
    }

    @Composable
    private fun ActiveSessionWidgetContent(session: ParkingSession) {
        val total = (session.endTimeMillis - session.startTimeMillis).coerceAtLeast(1000L)
        val elapsed = (System.currentTimeMillis() - session.startTimeMillis).coerceIn(0L, total)
        val progressFloat = (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)

        val minutesRemaining = (session.remainingMillis() / (60 * 1000L)).coerceAtLeast(0L)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedEndTime = timeFormat.format(Date(session.endTimeMillis))

        val isCritical = session.isInCriticalZone()
        val isWarning = session.isInAdvanceWarningZone()
        val statusText = when {
            isCritical -> "CRITICAL"
            isWarning -> "EXPIRING SOON"
            else -> "ACTIVE"
        }
        val statusColor = when {
            isCritical -> Color(0xFFEF4444)
            isWarning -> Color(0xFFF59E0B)
            else -> Color(0xFF10B981)
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .cornerRadius(16.dp)
                .padding(12.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start
            ) {
                // Header
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🅿️ Parking Time",
                        style = TextStyle(
                            color = androidx.glance.unit.ColorProvider(Color(0xFF94A3B8)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = statusText,
                        style = TextStyle(
                            color = androidx.glance.unit.ColorProvider(statusColor),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Zone Title & Time Left
                Text(
                    text = session.zoneOrLot ?: "Parking Session",
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color.White),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )

                Text(
                    text = "Expires at $formattedEndTime • ${minutesRemaining}m left",
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFFCBD5E1)),
                        fontSize = 13.sp
                    )
                )

                Spacer(modifier = GlanceModifier.height(6.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = progressFloat,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                    color = androidx.glance.unit.ColorProvider(statusColor),
                    backgroundColor = androidx.glance.unit.ColorProvider(Color(0xFF334155))
                )

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Action Buttons Row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        text = "+15m Extend",
                        onClick = actionRunCallback<GlanceExtendCallback>(),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Button(
                        text = "Open App",
                        onClick = actionStartActivity<MainActivity>(),
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            }
        }
    }

    @Composable
    private fun InactiveWidgetContent() {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .cornerRadius(16.dp)
                .padding(12.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🅿️ Parking Time Detector",
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFF94A3B8)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                Text(
                    text = "No Active Parking",
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color.White),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Text(
                    text = "Tap below to track or open app",
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFF64748B)),
                        fontSize = 12.sp
                    )
                )

                Spacer(modifier = GlanceModifier.height(10.dp))

                Button(
                    text = "Open App",
                    onClick = actionStartActivity<MainActivity>()
                )
            }
        }
    }
}
