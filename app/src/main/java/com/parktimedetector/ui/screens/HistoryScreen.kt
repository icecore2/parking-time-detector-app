package com.parktimedetector.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parktimedetector.data.ParkingSession
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a grouped parking session representing a complete parking lifecycle.
 * Groups matching start & end session events that belong to the same parking instance.
 */
data class GroupedParkingSession(
    val groupKey: String,
    val primarySession: ParkingSession,
    val relatedSessions: List<ParkingSession> = emptyList()
) {
    val allSessions: List<ParkingSession> get() = listOf(primarySession) + relatedSessions

    val startSession: ParkingSession = allSessions.minByOrNull { it.startTimeMillis } ?: primarySession

    val stopSession: ParkingSession? = allSessions.firstOrNull { it.actualStopTimeMillis != null }
        ?: if (relatedSessions.isNotEmpty()) allSessions.maxByOrNull { it.startTimeMillis } else null

    val isStopped: Boolean = primarySession.actualStopTimeMillis != null || stopSession?.actualStopTimeMillis != null
    val isActive: Boolean = primarySession.isActive || relatedSessions.any { it.isActive }

    val zoneOrLot: String? = primarySession.zoneOrLot ?: relatedSessions.firstNotNullOfOrNull { it.zoneOrLot }

    val locationAddress: String? = primarySession.locationAddress
        ?: relatedSessions.firstNotNullOfOrNull { it.locationAddress }

    val displayLocation: String = primarySession.displayLocation.takeIf { it != "Parking Session" }
        ?: relatedSessions.firstNotNullOfOrNull { it.displayLocation.takeIf { loc -> loc != "Parking Session" } }
        ?: "Parking Session"

    val purchasedDuration: String? = primarySession.purchasedDurationText
        ?: relatedSessions.firstNotNullOfOrNull { it.purchasedDurationText }

    val initialCost: String? = primarySession.initialCostText
        ?: relatedSessions.firstNotNullOfOrNull { it.initialCostText }

    val costOrRefund: String? = primarySession.costOrRefundText
        ?: stopSession?.costOrRefundText
        ?: relatedSessions.firstNotNullOfOrNull { it.costOrRefundText }

    val stopReason: String? = primarySession.stopReason
        ?: stopSession?.stopReason
        ?: relatedSessions.firstNotNullOfOrNull { it.stopReason }

    val notes: String? = primarySession.notesText
        ?: relatedSessions.firstNotNullOfOrNull { it.notesText }

    val effectiveStartTime: Long = startSession.startTimeMillis
    val effectiveScheduledEndTime: Long = primarySession.endTimeMillis.coerceAtLeast(startSession.endTimeMillis)
    val effectiveStopTime: Long? = primarySession.actualStopTimeMillis ?: stopSession?.actualStopTimeMillis
}

fun groupParkingSessions(sessions: List<ParkingSession>): List<GroupedParkingSession> {
    val result = mutableListOf<GroupedParkingSession>()
    val visited = mutableSetOf<Long>()

    for (session in sessions) {
        if (session.id in visited) continue
        visited.add(session.id)

        // Find sessions from the same app with matching zone/lot or matching timeframe (< 8 hours apart)
        val related = sessions.filter { other ->
            other.id !in visited &&
            (
                (!session.zoneOrLot.isNullOrBlank() &&
                 session.zoneOrLot.equals(other.zoneOrLot, ignoreCase = true) &&
                 kotlin.math.abs(session.startTimeMillis - other.startTimeMillis) < 8 * 3600 * 1000L)
                ||
                (session.packageName.isNotBlank() &&
                 session.packageName == other.packageName &&
                 kotlin.math.abs(session.startTimeMillis - other.startTimeMillis) < 3 * 3600 * 1000L &&
                 ((session.actualStopTimeMillis == null && other.actualStopTimeMillis != null) ||
                  (session.actualStopTimeMillis != null && other.actualStopTimeMillis == null)))
            )
        }

        related.forEach { visited.add(it.id) }
        result.add(GroupedParkingSession(
            groupKey = "${session.zoneOrLot ?: "session"}_${session.id}",
            primarySession = session,
            relatedSessions = related
        ))
    }
    return result
}

@Composable
fun HistoryScreen(
    viewModel: ParkingViewModel,
    sessions: List<ParkingSession>,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterSource by remember { mutableStateOf("ALL") }

    val analytics = remember(sessions) {
        com.parktimedetector.analytics.ParkingCsvExporter.calculateAnalytics(sessions)
    }

    // Filter sessions based on search text and source chips
    val filteredSessions = remember(sessions, searchQuery, selectedFilterSource) {
        sessions.filter { s ->
            val matchesSource = when (selectedFilterSource) {
                "PARKEDIN" -> s.packageName == com.parktimedetector.notification.NotificationHelper.PARKEDIN_PACKAGE || s.source.contains("ParkedIn", ignoreCase = true)
                "MYPARKING" -> s.packageName == com.parktimedetector.notification.NotificationHelper.MYPARKING_PACKAGE || s.source.contains("MyParking", ignoreCase = true)
                "MANUAL" -> s.packageName.contains("manual", ignoreCase = true) || s.source.contains("Timer", ignoreCase = true)
                else -> true
            }

            val query = searchQuery.trim().lowercase()
            val matchesQuery = query.isEmpty() ||
                (s.zoneOrLot?.lowercase()?.contains(query) == true) ||
                (s.locationAddress?.lowercase()?.contains(query) == true) ||
                (s.spotDetails?.lowercase()?.contains(query) == true) ||
                (s.notesText?.lowercase()?.contains(query) == true) ||
                (s.source.lowercase().contains(query))

            matchesSource && matchesQuery
        }
    }

    val groupedSessions = remember(filteredSessions) { groupParkingSessions(filteredSessions) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All History?") },
            text = { Text("This will permanently remove all past parking records. Active session timers will also be stopped.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RoseRed)
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Header with Export CSV & Clear Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Parking History",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${groupedSessions.size} grouped sessions (${sessions.size} total records)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sessions.isNotEmpty()) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { viewModel.exportParkingExpensesCsv(context) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export CSV", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = RoseRed
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Analytics Summary Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "📊 Expense & Usage Analytics",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("This Month Spend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = String.format(Locale.US, "$%.2f", analytics.thisMonthCost),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                        Text(
                            text = "${analytics.thisMonthSessions} sessions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Parked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val totalHrs = analytics.totalDurationMinutes / 60
                        val totalMins = analytics.totalDurationMinutes % 60
                        Text(
                            text = if (totalHrs > 0) "${totalHrs}h ${totalMins}m" else "${totalMins}m",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${analytics.totalSessions} sessions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("All-Time Spend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = String.format(Locale.US, "$%.2f", analytics.totalCost),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tracked",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar & Filter Chips Row
        androidx.compose.material3.OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by zone, address, spot, notes...") },
            leadingIcon = {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips: All, ParkedIn, MyParking, Manual
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val chips = listOf("ALL" to "All", "PARKEDIN" to "ParkedIn", "MYPARKING" to "MyParking", "MANUAL" to "Manual Timer")
            chips.forEach { (key, label) ->
                val isSelected = selectedFilterSource == key
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedFilterSource = key }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (groupedSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty() || selectedFilterSource != "ALL") "No Matching Records" else "No Parking History Yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty() || selectedFilterSource != "ALL")
                            "Try clearing your search query or switching source filters."
                        else
                            "Sessions detected from MyParking or ParkedIn will automatically appear here with full start and stop details.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groupedSessions, key = { it.groupKey }) { group ->
                    GroupedHistoryItemCard(
                        group = group,
                        onAddToFavorites = { zoneName ->
                            viewModel.addFavoriteZone(
                                name = zoneName,
                                durationMinutes = 60,
                                notes = group.locationAddress
                            )
                            android.widget.Toast.makeText(context, "Added $zoneName to favorites!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GroupedHistoryItemCard(
    group: GroupedParkingSession,
    onAddToFavorites: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Collapsible state - collapsed as default
    var isExpanded by remember { mutableStateOf(false) }
    var expandedRecords by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    val startDate = dateFormat.format(Date(group.effectiveStartTime))
    val startTime = timeFormat.format(Date(group.effectiveStartTime))
    val scheduledEndTime = if (group.effectiveScheduledEndTime > 0) timeFormat.format(Date(group.effectiveScheduledEndTime)) else "N/A"
    val stopTime = group.effectiveStopTime?.let { timeFormat.format(Date(it)) }

    val durationMinutes = if (group.effectiveStopTime != null) {
        ((group.effectiveStopTime - group.effectiveStartTime) / (1000 * 60)).coerceAtLeast(1L)
    } else {
        (group.primarySession.actualDurationMillis / (1000 * 60)).coerceAtLeast(1L)
    }

    val actualDurationText = if (durationMinutes >= 60) {
        val h = durationMinutes / 60
        val m = durationMinutes % 60
        if (m == 0L) "${h} hr" else "${h} hr ${m} min"
    } else {
        "${durationMinutes} min"
    }

    val statusColor = when {
        group.isActive -> EmeraldGreen
        group.isStopped -> RoseRed
        group.primarySession.isExpired() -> TextMuted
        else -> PrimaryBlue
    }

    val statusText = when {
        group.isActive -> "Active"
        group.isStopped -> "Deactivated / Stopped"
        group.primarySession.isExpired() -> "Expired"
        else -> "Completed"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            // Header Row: Icon, Zone/Title, App Source, Status Pill, Favorite action, and Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocalParking,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = group.zoneOrLot ?: "Parking Session",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = group.primarySession.source,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (group.relatedSessions.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Link,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "Merged (${group.allSessions.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (!group.zoneOrLot.isNullOrBlank()) {
                        IconButton(
                            onClick = { onAddToFavorites(group.zoneOrLot) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.StarBorder,
                                contentDescription = "Add to Favorites",
                                tint = AmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Compact Summary Row (Always visible)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$startDate • $startTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (group.isStopped) "Parked: $actualDurationText" else actualDurationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    val displayCost = group.costOrRefund ?: group.initialCost
                    if (!displayCost.isNullOrBlank()) {
                        Text(
                            text = " • $displayCost",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Compact Location Preview (Shown only when collapsed, single-line with ellipsis)
            if (!group.locationAddress.isNullOrBlank() && !isExpanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = group.locationAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Expandable Detailed Body
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    // Full Location Address Row (expanded)
                    if (!group.locationAddress.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = group.locationAddress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Start & End Lifecycle Sections
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Start Section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Start Session Details",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Started", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$startDate, $startTime", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Scheduled Expiry", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(scheduledEndTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (!group.purchasedDuration.isNullOrBlank()) {
                                    Column {
                                        Text("Purchased Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(group.purchasedDuration, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                if (!group.initialCost.isNullOrBlank()) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Initial Cost / Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(group.initialCost, style = MaterialTheme.typography.bodySmall, color = EmeraldGreen, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            // Deactivation / End Section (if stopped)
                            if (group.isStopped || stopTime != null) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Stop,
                                        contentDescription = null,
                                        tint = RoseRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Deactivation / End Details",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = RoseRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Stopped At", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(stopTime ?: "Manual Deactivation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Actual Parked Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(actualDurationText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (!group.stopReason.isNullOrBlank()) {
                                        Column {
                                            Text("Reason", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(group.stopReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                    if (!group.costOrRefund.isNullOrBlank()) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Cost / Refund", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(group.costOrRefund, style = MaterialTheme.typography.bodySmall, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Notes Box (if available)
                    if (!group.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = group.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Expandable raw records (if merged from multiple records)
                    if (group.relatedSessions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedRecords = !expandedRecords }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (expandedRecords) "Hide Individual Records" else "View Individual Records (${group.allSessions.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                if (expandedRecords) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        AnimatedVisibility(visible = expandedRecords) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                group.allSessions.forEachIndexed { index, item ->
                                    val itemTime = timeFormat.format(Date(item.startTimeMillis))
                                    val itemStop = item.actualStopTimeMillis?.let { timeFormat.format(Date(it)) } ?: "None"
                                    Text(
                                        text = "• #${item.id} [${item.source}] Start: $itemTime | Stop: $itemStop | Active: ${item.isActive}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

