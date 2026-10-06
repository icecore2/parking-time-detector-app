package com.parktimedetector.mock

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parktimedetector.ui.theme.DarkNavy
import com.parktimedetector.ui.theme.EmeraldGreen
import com.parktimedetector.ui.theme.PrimaryBlue
import com.parktimedetector.ui.theme.SurfaceDark
import com.parktimedetector.ui.theme.TextPrimary
import com.parktimedetector.ui.theme.TextSecondary

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
                    // Map Placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🗺️ Map View Placeholder\n(Pin 9058 Active in Area)",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

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
                                        selectedLot = "$lotName (Zone $zoneNum)"
                                        screenState = MockMyParkingScreen.START_SESSION
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
                                            text = "Zone $zoneNum • Hourly Rate $3.00",
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
                            Text(text = "Rate: $3.00 / hr", color = EmeraldGreen, fontWeight = FontWeight.Medium)
                            Text(text = "Duration: 2 hrs (Expires in 120 mins)", color = Color.White)

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
