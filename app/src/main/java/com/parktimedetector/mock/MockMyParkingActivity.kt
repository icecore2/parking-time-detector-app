package com.parktimedetector.mock

import android.os.Bundle
import android.widget.Toast
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parktimedetector.network.ZoneWithPrice
import com.parktimedetector.network.CostDuration
import com.parktimedetector.network.MyParkingApiClient
import com.parktimedetector.network.ParkingMapPin
import com.parktimedetector.network.ParkingZoneDetails
import com.parktimedetector.ui.theme.DarkNavy
import com.parktimedetector.ui.theme.EmeraldGreen
import com.parktimedetector.ui.theme.PrimaryBlue
import com.parktimedetector.ui.theme.SurfaceDark
import com.parktimedetector.ui.theme.TextPrimary
import com.parktimedetector.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class MockMyParkingScreen {
    MAP_SEARCH,
    START_SESSION,
    ACTIVE_SESSION
}

class MockMyParkingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MockMyParkingApp(
                onClose = { finish() },
                onSessionStarted = { zone ->
                    Toast.makeText(this, "Mock MyParking: Session started for $zone", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun MockMyParkingApp(
    onClose: () -> Unit,
    onSessionStarted: (String) -> Unit
) {
    var screenState by remember { mutableStateOf(MockMyParkingScreen.MAP_SEARCH) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLot by remember { mutableStateOf("Lot 58 - 935 - 4 Av SW") }
    var selectedZoneNumber by remember { mutableStateOf("9058") }
    var selectedDurationMinutes by remember { mutableStateOf(120) }
    var selectedRateText by remember { mutableStateOf("Rate: $3.00 / hr") }

    // Live fetched zone details
    var activeZoneDetails by remember { mutableStateOf<ParkingZoneDetails?>(null) }
    var isLoadingDetails by remember { mutableStateOf(false) }

    // Nearby zones sorted by cheapest price
    var nearbyCheapestZones by remember { mutableStateOf<List<ZoneWithPrice>>(emptyList()) }
    var showCheapestList by remember { mutableStateOf(false) }
    var isLoadingCheapest by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Map pins representing Calgary Parking Authority locations
    val mapPins = remember {
        listOf(
            ParkingMapPin("9058", "Lot 58", "Downtown West", 51.0492, -114.0834, isLot = true),
            ParkingMapPin("1008", "Zone 1008", "Eau Claire Ave", 51.0520, -114.0720),
            ParkingMapPin("1205", "Zone 1205", "Kensington", 51.0532, -114.0865),
            ParkingMapPin("4022", "Zone 4022", "Downtown 8th Ave", 51.0486, -114.0708),
            ParkingMapPin("9025", "Lot 25", "City Hall P1", 51.0458, -114.0570, isLot = true)
        )
    }

    // Function to trigger live network fetch when a pin is pressed
    fun onPinPressed(pin: ParkingMapPin) {
        selectedZoneNumber = pin.zoneNumber
        selectedLot = "${pin.title} - ${pin.subtitle}"
        isLoadingDetails = true

        scope.launch {
            val details = MyParkingApiClient.getCombinedZoneDetails(
                zoneNumber = pin.zoneNumber,
                fallbackTitle = pin.title,
                fallbackAddress = "${pin.title} - ${pin.subtitle}"
            )
            activeZoneDetails = details
            selectedLot = details.address
            selectedRateText = details.hourlyRateEstimate ?: "Rate: $3.00 / hr"
            selectedDurationMinutes = details.maxTimeMinutes ?: 120
            isLoadingDetails = false
        }
    }

    // Auto-fetch default pin on initial launch
    LaunchedEffect(Unit) {
        onPinPressed(mapPins.first())
    }

    val sampleLots = listOf(
        "Lot 58 - 935 - 4 Av SW" to "9058",
        "Zone 4022 - Downtown 8th Ave" to "4022",
        "Zone 1205 - Kensington" to "1205",
        "Lot 25 - City Hall P1" to "9025"
    )

    val filteredLots = if (searchQuery.isBlank()) {
        sampleLots
    } else {
        sampleLots.filter { (name, zone) ->
            name.contains(searchQuery, ignoreCase = true) || zone.contains(searchQuery)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF131B2A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalParking,
                        contentDescription = "MyParking CPA Icon",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MyParking (Calgary Parking)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close Mock App", tint = Color.LightGray)
                }
            }

            when (screenState) {
                MockMyParkingScreen.MAP_SEARCH -> {
                    // Interactive Map Pin Bar & Status Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🗺️ Interactive Map (Press Pin to Fetch)",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                if (isLoadingDetails) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = EmeraldGreen
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Scrollable row of interactive Map Pins
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                mapPins.forEach { pin ->
                                    val isSelected = selectedZoneNumber == pin.zoneNumber
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) EmeraldGreen else Color(0xFF2C394B),
                                        modifier = Modifier
                                            .semantics {
                                                contentDescription = "Map Pin ${pin.zoneNumber} ${pin.title}"
                                            }
                                            .clickable { onPinPressed(pin) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Place,
                                                contentDescription = null,
                                                tint = if (isSelected) DarkNavy else PrimaryBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${pin.title} (${pin.zoneNumber})",
                                                color = if (isSelected) DarkNavy else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Dynamic Live Parking Details Card for the clicked pin
                            activeZoneDetails?.let { details ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF131B2A), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "📍 ${details.nameOrTitle}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = if (details.isFallback) "🟠 Offline Cache" else "🟢 CPA Live API",
                                                color = if (details.isFallback) Color(0xFFFFA726) else EmeraldGreen,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Text(
                                            text = details.address,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Rate: ${details.hourlyRateEstimate ?: "N/A"}",
                                                color = EmeraldGreen,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "Max Stay: ${details.maxTimeMinutes ?: 120}m",
                                                color = Color.LightGray,
                                                fontSize = 12.sp
                                            )
                                        }

                                        // Cost and Duration options from live VPM response
                                        if (details.costDurations.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Select Duration:",
                                                color = TextSecondary,
                                                fontSize = 10.sp
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState())
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                details.costDurations.take(5).forEach { opt ->
                                                    val isOptSelected = selectedDurationMinutes == opt.durationMinutes
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (isOptSelected) PrimaryBlue else Color(0xFF1E293B),
                                                        modifier = Modifier.clickable {
                                                            selectedDurationMinutes = opt.durationMinutes
                                                            selectedRateText = String.format(Locale.US, "$%.2f for %d mins", opt.cost, opt.durationMinutes)
                                                        }
                                                    ) {
                                                        Text(
                                                            text = "${opt.durationMinutes}m • $${String.format(Locale.US, "%.2f", opt.cost)}",
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isOptSelected) FontWeight.Bold else FontWeight.Normal,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                selectedLot = details.address
                                                screenState = MockMyParkingScreen.START_SESSION
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                        ) {
                                            Text(
                                                text = "Select Pin & Park Here",
                                                color = DarkNavy,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Button to toggle Cheapest Zones list
                    Button(
                        onClick = {
                            showCheapestList = !showCheapestList
                            if (showCheapestList && nearbyCheapestZones.isEmpty()) {
                                scope.launch {
                                    isLoadingCheapest = true
                                    nearbyCheapestZones = MyParkingApiClient.fetchNearbyZonesWithPrices(51.0486, -114.0708)
                                    isLoadingCheapest = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (showCheapestList) EmeraldGreen else Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (showCheapestList) "Hide Cheapest Zones Nearby ✕" else "🏷️ Show Cheapest Zones Nearby",
                                color = if (showCheapestList) DarkNavy else EmeraldGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (isLoadingCheapest) {
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = if (showCheapestList) DarkNavy else EmeraldGreen
                                )
                            }
                        }
                    }

                    // Collapsible list of Cheapest Zones
                    if (showCheapestList) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF162032)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Nearby Parking Zones (Cheapest First)",
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(nearbyCheapestZones) { zone ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = SurfaceDark,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedZoneNumber = zone.zoneNumber
                                                    selectedLot = zone.address
                                                    selectedDurationMinutes = zone.maxTimeMinutes ?: 120
                                                    selectedRateText = zone.cheapestPrice.displayPrice
                                                    onPinPressed(
                                                        ParkingMapPin(
                                                            zoneNumber = zone.zoneNumber,
                                                            title = "Zone ${zone.zoneNumber}",
                                                            subtitle = zone.address,
                                                            latitude = 51.0486,
                                                            longitude = -114.0708
                                                        )
                                                    )
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "Zone ${zone.zoneNumber}",
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp
                                                        )
                                                        if (zone.cheapestPrice.isFree) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = EmeraldGreen
                                                            ) {
                                                                Text(
                                                                    text = "FREE",
                                                                    color = DarkNavy,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 9.sp,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = zone.address,
                                                        color = TextSecondary,
                                                        fontSize = 10.sp,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = "${zone.stallType ?: "Parallel"} • Max ${zone.maxTimeMinutes ?: 120}m",
                                                        color = TextPrimary.copy(alpha = 0.6f),
                                                        fontSize = 9.sp
                                                    )
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (zone.cheapestPrice.isFree) EmeraldGreen else PrimaryBlue
                                                ) {
                                                    Text(
                                                        text = zone.cheapestPrice.displayPrice,
                                                        color = DarkNavy,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Real EditText with accessibility label for Quick-Renew search
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Search zone or address"
                            },
                        label = { Text("Search zone number or lot (e.g. 9058)") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search Icon")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = {
                                    scope.launch {
                                        isLoadingDetails = true
                                        val details = MyParkingApiClient.getCombinedZoneDetails(searchQuery.trim())
                                        activeZoneDetails = details
                                        selectedLot = details.address
                                        selectedZoneNumber = details.zoneNumber
                                        isLoadingDetails = false
                                    }
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Fetch Zone Data", tint = PrimaryBlue)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Search Suggestions / Recent Lots",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filteredLots) { (lotName, zoneNum) ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedZoneNumber = zoneNum
                                        selectedLot = "$lotName (Zone $zoneNum)"
                                        onPinPressed(
                                            ParkingMapPin(
                                                zoneNumber = zoneNum,
                                                title = lotName,
                                                subtitle = "Zone $zoneNum",
                                                latitude = 51.0486,
                                                longitude = -114.0708
                                            )
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = PrimaryBlue)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = lotName,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Zone $zoneNum • Tap to fetch live CPA rates",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                MockMyParkingScreen.START_SESSION -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Selected Location",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = selectedLot,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = selectedRateText,
                                color = EmeraldGreen,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Duration: ${selectedDurationMinutes / 60}h ${selectedDurationMinutes % 60}m (Expires in $selectedDurationMinutes mins)",
                                color = Color.White
                            )

                            activeZoneDetails?.enforceableTime?.let { hours ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Enforcement Hours: $hours",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Start Button targeted by QuickRenewManager
                            Button(
                                onClick = {
                                    onSessionStarted(selectedLot)
                                    screenState = MockMyParkingScreen.ACTIVE_SESSION
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .semantics {
                                        contentDescription = "START PARKING SESSION"
                                    },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "START PARKING SESSION",
                                    color = DarkNavy,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }

                MockMyParkingScreen.ACTIVE_SESSION -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🅿️ ACTIVE PARKING SESSION", fontWeight = FontWeight.Bold, color = EmeraldGreen)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(selectedLot, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Time Remaining: 01:59:45", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)

                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { screenState = MockMyParkingScreen.MAP_SEARCH },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Text("Back to Map")
                            }
                        }
                    }
                }
            }
        }
    }
}
