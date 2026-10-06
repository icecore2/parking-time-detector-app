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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parktimedetector.audio.VibrationHelper
import com.parktimedetector.ui.theme.AmberWarning
import com.parktimedetector.ui.theme.EmeraldGreen
import com.parktimedetector.ui.theme.RoseRed
import kotlin.math.cos
import kotlin.math.sin

enum class ParkingUrgencyState {
    SAFE,       // Healthy duration (> advance warning)
    WARNING,    // Approaching expiry (advance warning zone)
    CRITICAL,   // Immediate action needed (<= critical zone, e.g. 2m)
    EXPIRED     // Time elapsed
}

@Composable
fun ParkingCircularGauge(
    progress: Float,
    hours: Long,
    minutes: Long,
    seconds: Long,
    subtitleText: String,
    urgencyState: ParkingUrgencyState,
    walkingBufferFraction: Float = 0f,
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    size: Dp = 230.dp,
    strokeWidth: Dp = 12.dp
) {
    val context = LocalContext.current
    val isExpired = urgencyState == ParkingUrgencyState.EXPIRED

    // 1. Target color selection
    val targetColor = when (urgencyState) {
        ParkingUrgencyState.SAFE -> EmeraldGreen
        ParkingUrgencyState.WARNING -> AmberWarning
        ParkingUrgencyState.CRITICAL -> RoseRed
        ParkingUrgencyState.EXPIRED -> RoseRed
    }

    // 2. Smooth color transition
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

    // 4. Pulsing Warning & Expiry Halo Animations
    val isCriticalOrExpired = urgencyState == ParkingUrgencyState.CRITICAL || isExpired
    val isWarning = urgencyState == ParkingUrgencyState.WARNING
    val infiniteTransition = rememberInfiniteTransition(label = "haloTransition")

    val pulseAlpha by if (isCriticalOrExpired) {
        infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.90f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlphaCritical"
        )
    } else if (isWarning) {
        infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlphaWarning"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val pulseScale by if (isCriticalOrExpired) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.035f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(1.0f) }
    }

    // 5. Tactile Haptic Feedback triggers
    var previousUrgency by remember { mutableStateOf(urgencyState) }
    LaunchedEffect(urgencyState) {
        if (hapticsEnabled && previousUrgency != urgencyState) {
            VibrationHelper.performWarningTransitionHaptic(context)
            previousUrgency = urgencyState
        }
    }

    LaunchedEffect(seconds) {
        val totalSecsLeft = hours * 3600 + minutes * 60 + seconds
        if (hapticsEnabled && totalSecsLeft in 1..60) {
            VibrationHelper.performTickHaptic(context)
        }
    }

    val surfaceContainer = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(if (isCriticalOrExpired) pulseScale else 1.0f)
    ) {
        // Multi-ring glowing dial canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = this.size.minDimension
            val centerOffset = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val strokePx = strokeWidth.toPx()
            val radius = (canvasSize - strokePx) / 2f

            // Layer 1: Ambient background pulse aura
            if (isCriticalOrExpired || isWarning) {
                val auraColor = if (isCriticalOrExpired) RoseRed else AmberWarning
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            auraColor.copy(alpha = pulseAlpha * 0.45f),
                            auraColor.copy(alpha = pulseAlpha * 0.15f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = canvasSize / 2f
                    ),
                    radius = canvasSize / 2f,
                    center = centerOffset
                )
            }

            // Layer 2: Outer Calibrated Tick Ring (48 radial tick marks)
            val tickCount = 48
            val tickRadiusOuter = radius + (strokePx * 0.7f)
            val tickRadiusInner = radius + (strokePx * 0.45f)
            for (i in 0 until tickCount) {
                val angleDeg = (i.toFloat() / tickCount) * 360f - 90f
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val x1 = centerOffset.x + (tickRadiusInner * cos(angleRad)).toFloat()
                val y1 = centerOffset.y + (tickRadiusInner * sin(angleRad)).toFloat()
                val x2 = centerOffset.x + (tickRadiusOuter * cos(angleRad)).toFloat()
                val y2 = centerOffset.y + (tickRadiusOuter * sin(angleRad)).toFloat()

                val tickProgress = i.toFloat() / tickCount
                val isHighlighted = tickProgress <= animatedProgress && !isExpired
                val tickColor = if (isHighlighted) {
                    animatedColor.copy(alpha = 0.85f)
                } else {
                    outlineColor.copy(alpha = 0.25f)
                }
                drawLine(
                    color = tickColor,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = if (i % 4 == 0) 2.5f else 1.5f,
                    cap = StrokeCap.Round
                )
            }

            // Layer 3: Inner Guide Track Ring
            drawCircle(
                color = outlineColor.copy(alpha = 0.18f),
                radius = radius - strokePx * 0.8f,
                center = centerOffset,
                style = Stroke(width = 1.5f)
            )

            // Layer 4: Background Main Track Arc
            val arcOffset = Offset(strokePx / 2f, strokePx / 2f)
            val arcSize = canvasSize - strokePx
            drawArc(
                color = surfaceContainer,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcOffset,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Layer 5: Walking Buffer Marker (if set)
            if (walkingBufferFraction in 0.01f..0.99f) {
                val walkAngle = (360f * (1f - walkingBufferFraction)) - 90f
                val walkRad = Math.toRadians(walkAngle.toDouble())
                val markerX = centerOffset.x + (radius * cos(walkRad)).toFloat()
                val markerY = centerOffset.y + (radius * sin(walkRad)).toFloat()
                drawCircle(
                    color = AmberWarning,
                    radius = strokePx * 0.45f,
                    center = Offset(markerX, markerY)
                )
            }

            // Layer 6: Dynamic Active Progress Arc with Neon Glow
            val sweep = 360f * animatedProgress
            if (sweep > 0f) {
                // Diffuse Glowing Under-Stroke
                drawArc(
                    color = animatedColor.copy(alpha = 0.35f),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcOffset,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokePx * 1.6f, cap = StrokeCap.Round)
                )
                // Crisp Foreground Arc
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

        // Center Content: Segmented Countdown Boxes or Expired Banner
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            if (isExpired) {
                Surface(
                    color = RoseRed.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, RoseRed)
                ) {
                    Text(
                        text = "EXPIRED",
                        color = RoseRed,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            } else {
                // Segmented Digital Clock Boxes [ HR ] : [ MIN ] : [ SEC ]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SegmentBox(value = String.format("%02d", hours), label = "HRS", color = onSurfaceColor)
                    Text(":", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = animatedColor)
                    SegmentBox(value = String.format("%02d", minutes), label = "MIN", color = onSurfaceColor)
                    Text(":", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = animatedColor)
                    SegmentBox(value = String.format("%02d", seconds), label = "SEC", color = onSurfaceColor)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle Tag with status indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCriticalOrExpired || isWarning) {
                    Surface(
                        modifier = Modifier.size(7.dp),
                        shape = CircleShape,
                        color = animatedColor.copy(alpha = pulseAlpha)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCriticalOrExpired) RoseRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isCriticalOrExpired) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SegmentBox(
    value: String,
    label: String,
    color: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = color
            )
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
