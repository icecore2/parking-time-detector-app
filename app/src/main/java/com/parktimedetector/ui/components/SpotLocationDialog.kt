package com.parktimedetector.ui.components

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parktimedetector.location.LocationHelper
import com.parktimedetector.ui.theme.EmeraldGreen
import com.parktimedetector.ui.theme.RoseRed

@Composable
fun SpotLocationDialog(
    initialSpotDetails: String?,
    parkedLatitude: Double?,
    parkedLongitude: Double?,
    onSaveSpot: (String) -> Unit,
    onSaveLocation: (Double, Double) -> Unit,
    onClearLocation: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var spotText by remember { mutableStateOf(initialSpotDetails ?: "") }
    var currentLat by remember { mutableStateOf(parkedLatitude) }
    var currentLng by remember { mutableStateOf(parkedLongitude) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            val loc = LocationHelper.getLastKnownLocation(context)
            if (loc != null) {
                currentLat = loc.latitude
                currentLng = loc.longitude
                onSaveLocation(loc.latitude, loc.longitude)
                Toast.makeText(context, "📍 Car location pinned!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Acquiring GPS fix... Please try again in a few seconds.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Location permission needed to pin parking spot.", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Where Did I Park?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Save your vehicle spot / parking level and GPS pin so you can easily walk back to your car later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = spotText,
                    onValueChange = { spotText = it },
                    label = { Text("Spot / Pillar / Level Note") },
                    placeholder = { Text("e.g. Level 2B, Spot #42 near elevator") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // GPS Pin Status Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (currentLat != null) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (currentLat != null) "GPS Location Pinned" else "No GPS Pin Saved",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentLat != null) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (currentLat != null) {
                                TextButton(onClick = {
                                    currentLat = null
                                    currentLng = null
                                    onClearLocation()
                                }) {
                                    Text("Clear", color = RoseRed, fontSize = 11.sp)
                                }
                            }
                        }

                        if (currentLat != null && currentLng != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format("Coordinates: %.5f, %.5f", currentLat, currentLng),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val intent = LocationHelper.createWalkingDirectionsIntent(currentLat!!, currentLng!!)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.surface)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🧭 Walking Directions to Car", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    if (LocationHelper.hasLocationPermission(context)) {
                                        val loc = LocationHelper.getLastKnownLocation(context)
                                        if (loc != null) {
                                            currentLat = loc.latitude
                                            currentLng = loc.longitude
                                            onSaveLocation(loc.latitude, loc.longitude)
                                            Toast.makeText(context, "📍 Car location pinned!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Acquiring GPS fix...", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("📍 Pin Current GPS Location")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveSpot(spotText)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
