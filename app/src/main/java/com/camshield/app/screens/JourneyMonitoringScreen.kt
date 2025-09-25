// JourneyMonitoringScreen.kt - User B monitors User A's location
package com.camshield.app.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyMonitoringScreen(
    sosRequestId: String,
    userName: String, // Person being tracked
    onStopMonitoring: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var lastUpdated by remember { mutableStateOf<Date?>(null) }
    var totalDistance by remember { mutableStateOf(0f) }
    var isActive by remember { mutableStateOf(true) }
    var journeyStatus by remember { mutableStateOf("Active") }
    var sosData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var journeyPath by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var startLocation by remember { mutableStateOf<LatLng?>(null) }

    var firestoreListener by remember { mutableStateOf<ListenerRegistration?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            userLocation ?: LatLng(3.1390, 101.6869), 15f // Default to KL
        )
    }

    // Start listening to location updates
    LaunchedEffect(sosRequestId) {
        Log.d("JourneyMonitoring", "Starting to monitor SOS request: $sosRequestId")

        firestoreListener = firestore.collection("SOS")
            .document(sosRequestId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("JourneyMonitoring", "Error listening to SOS updates", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.data
                    Log.d("JourneyMonitoring", "SOS update received: $data")

                    if (data != null) {
                        sosData = data

                        // Update location if available
                        val lat = data["currentLatitude"] as? Double
                        val lng = data["currentLongitude"] as? Double

                        if (lat != null && lng != null) {
                            val newLocation = LatLng(lat, lng)

                            // Add to journey path if it's a new location
                            if (userLocation != newLocation) {
                                journeyPath = journeyPath + newLocation
                            }

                            userLocation = newLocation
                            Log.d("JourneyMonitoring", "Location updated: $lat, $lng")

                            // Set start location if not set
                            if (startLocation == null) {
                                val startLat = data["latitude"] as? Double
                                val startLng = data["longitude"] as? Double
                                if (startLat != null && startLng != null) {
                                    startLocation = LatLng(startLat, startLng)
                                }
                            }
                        }

                        // Update other data
                        totalDistance = (data["totalDistance"] as? Number)?.toFloat() ?: 0f
                        isActive = data["isActive"] as? Boolean ?: true
                        journeyStatus = data["status"] as? String ?: "Active"

                        // Parse timestamp
                        val timestamp = data["lastUpdated"] as? com.google.firebase.Timestamp
                        lastUpdated = timestamp?.toDate()

                        Log.d("JourneyMonitoring", "Status: $journeyStatus, Active: $isActive, Distance: ${totalDistance/1000}km")
                    }
                }
            }
    }

    // Update camera when location changes
    LaunchedEffect(userLocation) {
        userLocation?.let { location ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(location, 16f),
                1000
            )
        }
    }

    // Cleanup listener
    DisposableEffect(Unit) {
        onDispose {
            firestoreListener?.remove()
            Log.d("JourneyMonitoring", "Monitoring stopped")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF059669),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Monitoring Journey",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tracking $userName's location",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (isActive) Color.Green else Color(0xFFFF9800),
                                CircleShape
                            )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusCard(
                        title = "Status",
                        value = journeyStatus,
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        title = "Distance",
                        value = "${String.format("%.2f", totalDistance / 1000)} km",
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        title = "Last Update",
                        value = formatLastUpdate(lastUpdated),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Map
        Box(modifier = Modifier.weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(),
                uiSettings = MapUiSettings(zoomControlsEnabled = false)
            ) {
                // Starting point marker
                startLocation?.let { start ->
                    Marker(
                        state = MarkerState(position = start),
                        title = "$userName started here",
                        snippet = "Journey starting point"
                    )
                }

                // User's current location
                userLocation?.let { location ->
                    Marker(
                        state = MarkerState(position = location),
                        title = "$userName's Current Location",
                        snippet = "Last updated: ${formatLastUpdate(lastUpdated)}"
                    )

                    // Circle around current location
                    Circle(
                        center = location,
                        radius = 30.0,
                        fillColor = Color.Green.copy(alpha = 0.3f),
                        strokeColor = Color.Green,
                        strokeWidth = 3f
                    )
                }

                // Journey path
                if (journeyPath.size > 1) {
                    Polyline(
                        points = journeyPath,
                        color = Color.Blue,
                        width = 6f,
                        pattern = null
                    )
                }
            }

            // Emergency alert button if user seems in danger
            if (userLocation != null && isActive) {
                FloatingActionButton(
                    onClick = {
                        // Call emergency services or alert others
                        val callIntent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:999") // Emergency number
                        }
                        context.startActivity(callIntent)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = Color(0xFFDC2626)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Emergency Alert",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom controls
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Call button
                    OutlinedButton(
                        onClick = {
                            // Get phone number from SOS data and make call
                            val phoneNumber = sosData?.get("userEmail") as? String ?: ""
                            if (phoneNumber.isNotEmpty()) {
                                val callIntent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:$phoneNumber")
                                }
                                context.startActivity(callIntent)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call")
                    }

                    // Message button
                    OutlinedButton(
                        onClick = {
                            // Open SMS
                            val phoneNumber = sosData?.get("userEmail") as? String ?: ""
                            if (phoneNumber.isNotEmpty()) {
                                val smsIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("sms:$phoneNumber")
                                    putExtra("sms_body", "Hi $userName, I'm monitoring your journey. Stay safe!")
                                }
                                context.startActivity(smsIntent)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = "Message",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Message")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stop monitoring button
                Button(
                    onClick = {
                        scope.launch {
                            // Update monitoring status
                            try {
                                firestore.collection("SOS")
                                    .document(sosRequestId)
                                    .update("monitoringActive", false)

                                Log.d("JourneyMonitoring", "Monitoring stopped by user")
                            } catch (e: Exception) {
                                Log.e("JourneyMonitoring", "Error stopping monitoring", e)
                            }

                            onStopMonitoring()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stop Monitoring",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isActive) {
                        "You are remotely monitoring $userName's journey. Contact them if something seems wrong."
                    } else {
                        "$userName's journey has ended safely."
                    },
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                color = Color(0xFF059669),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatLastUpdate(date: Date?): String {
    return if (date != null) {
        val now = Date()
        val diffInSeconds = (now.time - date.time) / 1000

        when {
            diffInSeconds < 60 -> "Just now"
            diffInSeconds < 3600 -> "${diffInSeconds / 60}m ago"
            else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }
    } else {
        "Unknown"
    }
}