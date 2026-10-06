package com.parktimedetector.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parktimedetector.ui.theme.AmberWarning
import com.parktimedetector.ui.theme.EmeraldGreen
import com.parktimedetector.ui.theme.RoseRed
import com.parktimedetector.ui.theme.SurfaceDark
import com.parktimedetector.ui.theme.TextPrimary
import com.parktimedetector.ui.theme.TextSecondary

enum class ParkingUrgencyState {
    SAFE,       // Healthy duration (> advance warning)
    WARNING,    // Approaching expiry (advance warning zone)
    CRITICAL,   // Immediate action needed (<= critical zone, e.g. 2m)
    EXPIRED     // Time elapsed
}

@Composable
fun ParkingCircularGauge(
    progress: Float,
    timeString: String,
    subtitleText: String,
    urgencyState: ParkingUrgencyState,
    modifier: Modifier = Modifier,
    size: Dp = 196.dp,
    strokeWidth: Dp = 12.dp
) {
    // 1. Target color selection
    val targetColor = when (urgencyState) {
        ParkingUrgencyState.SAFE -> EmeraldGreen
        ParkingUrgencyState.WARNING -> AmberWarning
        ParkingUrgencyState.CRITICAL -> RoseRed
        ParkingUrgencyState.EXPIRED -> RoseRed
    }

    // 2. Smooth color transition (Green -> Amber -> Red)
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 600),
        label = "gaugeColorTransition"
    )

    // 3. Smooth progress interpolation
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "gaugeProgressTransition"
    )

    // 4. Pulsing Red Animation for Critical & Expired zones
    val isPulsing = urgencyState == ParkingUrgencyState.CRITICAL || urgencyState == ParkingUrgencyState.EXPIRED
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")

    val pulseAlpha by if (isPulsing) {
        infiniteTransition.animateFloat(
            initialValue = 0.20f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val pulseScale by if (isPulsing) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1.0f) }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(if (isPulsing) pulseScale else 1.0f)
    ) {
        // Custom Canvas Gauge
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = this.size.minDimension
            val strokePx = strokeWidth.toPx()
            val arcSize = canvasSize - strokePx
            val arcOffset = Offset(strokePx / 2f, strokePx / 2f)
            val centerOffset = Offset(size.toPx() / 2f, size.toPx() / 2f)

            // Ambient background pulse aura when in critical/expired zone
            if (isPulsing) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            RoseRed.copy(alpha = pulseAlpha * 0.40f),
                            RoseRed.copy(alpha = pulseAlpha * 0.10f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = canvasSize / 2f
                    ),
                    radius = canvasSize / 2f,
                    center = centerOffset
                )
            }

            // Background Track Arc
            drawArc(
                color = SurfaceDark,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcOffset,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Dynamic Active Progress Arc with rounded caps
            val sweep = 360f * animatedProgress
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.85f),
                            animatedColor,
                            animatedColor
                        ),
                        center = centerOffset
                    ),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcOffset,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        // Center Countdown & Urgency Text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeString,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp),
                fontWeight = FontWeight.ExtraBold,
                color = if (isPulsing) animatedColor else TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPulsing) {
                    Surface(
                        modifier = Modifier.size(7.dp),
                        shape = CircleShape,
                        color = RoseRed.copy(alpha = pulseAlpha)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPulsing) RoseRed else TextSecondary,
                    fontWeight = if (isPulsing) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
