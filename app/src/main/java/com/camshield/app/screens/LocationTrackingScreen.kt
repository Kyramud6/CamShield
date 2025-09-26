// LocationTrackingScreen.kt - User A shares their location
package com.camshield.app.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationTrackingScreen(
    sosRequestId: String,
    userName: String,
    responderName: String? = null,
    onEndTracking: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }

    var isTracking by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var trackingStartTime by remember { mutableStateOf<Date?>(null) }
    var totalDistance by remember { mutableStateOf(0f) }
    var locationPath by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var monitoringUser by remember { mutableStateOf(responderName ?: "Someone") }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var locationCallback: LocationCallback? by remember { mutableStateOf(null) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            startLocationTracking(
                fusedLocationClient = fusedLocationClient,
                firestore = firestore,
                sosRequestId = sosRequestId,
                onLocationUpdate = { location, path, distance ->
                    currentLocation = location
                    locationPath = path
                    totalDistance = distance
                },
                onCallbackSet = { callback ->
                    locationCallback = callback
                }
            )
            isTracking = true
            trackingStartTime = Date()
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            currentLocation ?: LatLng(0.0, 0.0), 15f
        )
    }

    // Update camera when location changes
    LaunchedEffect(currentLocation) {
        currentLocation?.let { location ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(location, 17f),
                1000
            )
        }
    }

    // Get monitoring user info if not provided
    LaunchedEffect(Unit) {
        if (responderName == null) {
            try {
                val sosDoc = firestore.collection("SOS").document(sosRequestId).get().await()
                val responder = sosDoc.getString("responderName")
                if (responder != null) {
                    monitoringUser = responder
                }
            } catch (e: Exception) {
                Log.e("LocationTracking", "Error getting responder name", e)
            }
        }
    }

    // Start tracking automatically
    LaunchedEffect(Unit) {
        if (hasLocationPermissions(context)) {
            startLocationTracking(
                fusedLocationClient = fusedLocationClient,
                firestore = firestore,
                sosRequestId = sosRequestId,
                onLocationUpdate = { location, path, distance ->
                    currentLocation = location
                    locationPath = path
                    totalDistance = distance
                },
                onCallbackSet = { callback ->
                    locationCallback = callback
                }
            )
            isTracking = true
            trackingStartTime = Date()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Cleanup on disposal
    DisposableEffect(Unit) {
        onDispose {
            locationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E3A8A),
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
                            text = "Sharing Location",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$monitoringUser is monitoring your journey",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }

                    if (isTracking) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color.Green, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Monitoring status card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.RemoveRedEye,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Being Monitored by",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = monitoringUser,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Your location is being shared in real-time",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusCard(
                        title = "Duration",
                        value = formatDuration(trackingStartTime),
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        title = "Distance",
                        value = "${String.format("%.2f", totalDistance / 1000)} km",
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
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(zoomControlsEnabled = false)
            ) {
                // Current location marker
                currentLocation?.let { location ->
                    Marker(
                        state = MarkerState(position = location),
                        title = "Your Location"
                    )
                }

                // Path polyline
                if (locationPath.size > 1) {
                    Polyline(
                        points = locationPath,
                        color = Color.Blue,
                        width = 8f
                    )
                }
            }

            // Monitoring indicator
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.9f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Location Sharing Active",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
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
                Button(
                    onClick = {
                        scope.launch {
                            // Stop tracking
                            locationCallback?.let { callback ->
                                fusedLocationClient.removeLocationUpdates(callback)
                            }

                            // Update SOS status
                            try {
                                firestore.collection("SOS")
                                    .document(sosRequestId)
                                    .set(
                                        mapOf(
                                            "status" to "Completed",
                                            "endedAt" to FieldValue.serverTimestamp(),
                                            "totalDistance" to totalDistance,
                                            "isActive" to false
                                        ),
                                        SetOptions.merge()
                                    )
                                    .await()

                                Log.d("LocationTracking", "Journey ended successfully")
                            } catch (e: Exception) {
                                Log.e("LocationTracking", "Error ending journey", e)
                            }

                            onEndTracking()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "End Journey",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                color = Color(0xFF1E3A8A),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun startLocationTracking(
    fusedLocationClient: FusedLocationProviderClient,
    firestore: FirebaseFirestore,
    sosRequestId: String,
    onLocationUpdate: (LatLng, List<LatLng>, Float) -> Unit,
    onCallbackSet: (LocationCallback) -> Unit
) {
    val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        5000L // Update every 5 seconds
    ).apply {
        setMinUpdateIntervalMillis(2000L)
        setMinUpdateDistanceMeters(10f)
    }.build()

    val locationPath = mutableListOf<LatLng>()
    var totalDistance = 0f
    var lastLocation: Location? = null

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)

            locationResult.lastLocation?.let { location ->
                val currentLatLng = LatLng(location.latitude, location.longitude)
                locationPath.add(currentLatLng)

                // Calculate distance
                lastLocation?.let { last ->
                    val distance = last.distanceTo(location)
                    totalDistance += distance
                }
                lastLocation = location

                // Update UI
                onLocationUpdate(currentLatLng, locationPath.toList(), totalDistance)

                // Update Firebase
                updateLocationInFirebase(
                    firestore = firestore,
                    sosRequestId = sosRequestId,
                    location = currentLatLng,
                    totalDistance = totalDistance
                )
            }
        }
    }

    fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback,
        null
    )

    onCallbackSet(locationCallback)
    Log.d("LocationTracking", "Location tracking started for SOS: $sosRequestId")
}

private fun updateLocationInFirebase(
    firestore: FirebaseFirestore,
    sosRequestId: String,
    location: LatLng,
    totalDistance: Float
) {
    // Must be MUTABLE because we conditionally add fields later
    val locationData = mutableMapOf<String, Any>(
        "currentLatitude" to location.latitude,
        "currentLongitude" to location.longitude,
        "lastUpdated" to FieldValue.serverTimestamp(),
        "totalDistance" to totalDistance,
        "isActive" to true
    )

    firestore.collection("SOS")
        .document(sosRequestId)
        .get()
        .addOnSuccessListener { doc ->
            // If the doc exists but has no start lat/lng yet, write them once
            val missingStart =
                (doc != null && doc.exists() &&
                        !doc.contains("latitude") && !doc.contains("longitude"))

            if (missingStart) {
                locationData["latitude"] = location.latitude
                locationData["longitude"] = location.longitude
            }

            // Use set(..., merge) so the doc is created if it doesn't exist
            firestore.collection("SOS")
                .document(sosRequestId)
                .set(locationData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("LocationTracking", "Location updated in Firebase")
                }
                .addOnFailureListener { e ->
                    Log.e("LocationTracking", "Error updating location", e)
                }
        }
        .addOnFailureListener { e ->
            // If the read fails, still try to write with merge so tracking continues
            firestore.collection("SOS")
                .document(sosRequestId)
                .set(locationData, SetOptions.merge())
                .addOnFailureListener { w -> Log.e("LocationTracking", "Read+Write failed", w) }

            Log.e("LocationTracking", "Error reading doc before update", e)
        }
}

private fun hasLocationPermissions(context: Context): Boolean {
    return ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
}

private fun formatDuration(startTime: Date?): String {
    return if (startTime != null) {
        val duration = (Date().time - startTime.time) / 1000
        val minutes = duration / 60
        val seconds = duration % 60
        "${minutes}m ${seconds}s"
    } else {
        "0m 0s"
    }
}