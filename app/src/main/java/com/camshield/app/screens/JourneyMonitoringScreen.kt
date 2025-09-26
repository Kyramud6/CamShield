// Complete Updated JourneyMonitoringScreen - WITH COLLAPSIBLE HEADER
package com.camshield.app.screens

import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

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

    // NEW: Collapsible state
    var isExpanded by remember { mutableStateOf(true) }

    // Existing states
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var currentAddress by remember { mutableStateOf("Getting location...") }
    var lastUpdated by remember { mutableStateOf<Date?>(null) }
    var totalDistance by remember { mutableStateOf(0f) }
    var isActive by remember { mutableStateOf(true) }
    var journeyStatus by remember { mutableStateOf("Active") }
    var sosData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var journeyPath by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var startLocation by remember { mutableStateOf<LatLng?>(null) }
    var startAddress by remember { mutableStateOf("Unknown") }
    var speed by remember { mutableStateOf(0f) }
    var locationHistory by remember { mutableStateOf<List<LocationUpdate>>(emptyList()) }
    var currentStreet by remember { mutableStateOf("Unknown Street") }
    var userPhoneNumber by remember { mutableStateOf<String?>(null) }

    // Destination states
    var destination by remember { mutableStateOf<LatLng?>(null) }
    var destinationName by remember { mutableStateOf<String?>(null) }
    var destinationAddress by remember { mutableStateOf<String?>(null) }
    var routeDistance by remember { mutableStateOf<Float?>(null) }
    var estimatedTime by remember { mutableStateOf<String?>(null) }

    var firestoreListener by remember { mutableStateOf<ListenerRegistration?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            userLocation ?: LatLng(3.1390, 101.6869), 15f
        )
    }

    // Function to fetch user phone number from Users collection
    fun fetchUserPhoneNumber() {
        val userEmail = sosData?.get("userEmail") as? String
        if (userEmail != null) {
            Log.d("JourneyMonitoring", "Fetching phone number for email: $userEmail")
            firestore.collection("Users")
                .whereEqualTo("email", userEmail)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val userDoc = documents.documents[0]
                        userPhoneNumber = userDoc.getString("phone") ?: userDoc.getString("phoneNumber")
                        Log.d("JourneyMonitoring", "User phone number fetched: $userPhoneNumber")
                    } else {
                        Log.w("JourneyMonitoring", "No user found with email: $userEmail")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("JourneyMonitoring", "Error fetching user phone", e)
                }
        }
    }

    // Better geocoding with error handling
    fun reverseGeocode(location: LatLng, isDestination: Boolean = false) {
        scope.launch {
            try {
                Log.d("JourneyMonitoring", "Geocoding ${if (isDestination) "destination" else "location"}: ${location.latitude}, ${location.longitude}")

                val address = withContext(Dispatchers.IO) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val fullAddress = when {
                            addr.getAddressLine(0) != null -> addr.getAddressLine(0)
                            addr.thoroughfare != null -> "${addr.thoroughfare}, ${addr.locality ?: ""}"
                            addr.subLocality != null -> "${addr.subLocality}, ${addr.locality ?: ""}"
                            addr.locality != null -> addr.locality
                            else -> "Near ${location.latitude.toString().take(6)}, ${location.longitude.toString().take(6)}"
                        }

                        val street = addr.thoroughfare ?: addr.subLocality ?: "Unknown Street"
                        Pair(fullAddress, street)
                    } else {
                        val coords = "${location.latitude.toString().take(6)}, ${location.longitude.toString().take(6)}"
                        Pair(coords, "Unknown Street")
                    }
                }

                if (isDestination) {
                    destinationAddress = address.first
                } else {
                    currentAddress = address.first
                    currentStreet = address.second
                }

                Log.d("JourneyMonitoring", "${if (isDestination) "Destination" else "Current"} address set: ${address.first}")

            } catch (e: Exception) {
                Log.e("JourneyMonitoring", "Geocoding error", e)
                val fallbackAddress = "Location: ${location.latitude.toString().take(6)}, ${location.longitude.toString().take(6)}"

                if (isDestination) {
                    destinationAddress = fallbackAddress
                } else {
                    currentAddress = fallbackAddress
                    currentStreet = "Unknown Street"
                }
            }
        }
    }

    // Calculate distance between two points
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    // Calculate speed between two points
    fun calculateSpeed(oldLocation: LatLng?, newLocation: LatLng, timeDiff: Long): Float {
        if (oldLocation == null || timeDiff == 0L) return 0f

        val distance = FloatArray(1)
        android.location.Location.distanceBetween(
            oldLocation.latitude, oldLocation.longitude,
            newLocation.latitude, newLocation.longitude,
            distance
        )

        return if (timeDiff > 0) {
            (distance[0] / 1000f) / (timeDiff / 3600000f)
        } else 0f
    }

    // Update camera to fit both user and destination
    fun fitCameraToShowBoth() {
        if (userLocation != null && destination != null) {
            scope.launch {
                val bounds = LatLngBounds.builder()
                    .include(userLocation!!)
                    .include(destination!!)
                    .build()

                try {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(bounds, 150),
                        1500
                    )
                } catch (e: Exception) {
                    Log.e("JourneyMonitoring", "Error fitting camera", e)
                }
            }
        }
    }

    // Fetch phone number when sosData changes
    LaunchedEffect(sosData) {
        if (sosData != null && userPhoneNumber == null) {
            fetchUserPhoneNumber()
        }
    }

    // Listen to SOS document for location updates
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
                    Log.d("JourneyMonitoring", "SOS update received")
                    Log.d("JourneyMonitoring", "SOS data keys: ${data?.keys}")

                    if (data != null) {
                        sosData = data

                        // Get user's current location
                        var lat = data["currentLatitude"] as? Double
                        var lng = data["currentLongitude"] as? Double
                        var isLiveUpdate = false

                        if (lat != null && lng != null) {
                            Log.d("JourneyMonitoring", "Found live location: $lat, $lng")
                            isLiveUpdate = true
                        } else {
                            lat = data["latitude"] as? Double
                            lng = data["longitude"] as? Double
                            if (lat != null && lng != null) {
                                Log.d("JourneyMonitoring", "Using initial location: $lat, $lng")
                            }
                        }

                        if (lat != null && lng != null) {
                            val newLocation = LatLng(lat, lng)
                            val oldLocation = userLocation

                            // Calculate speed only for live updates
                            if (oldLocation != null && lastUpdated != null && isLiveUpdate) {
                                val timeDiff = Date().time - lastUpdated!!.time
                                val newSpeed = calculateSpeed(oldLocation, newLocation, timeDiff)
                                speed = if (newSpeed.isFinite() && newSpeed < 200) newSpeed else 0f
                                Log.d("JourneyMonitoring", "Speed calculated: ${speed} km/h")
                            } else if (!isLiveUpdate) {
                                speed = 0f
                            }

                            // Update location and get address
                            if (userLocation != newLocation) {
                                userLocation = newLocation
                                reverseGeocode(newLocation, false)

                                // Add to journey path only for live updates
                                if (isLiveUpdate) {
                                    journeyPath = journeyPath + newLocation

                                    locationHistory = locationHistory + LocationUpdate(
                                        location = newLocation,
                                        timestamp = Date(),
                                        address = currentAddress
                                    )

                                    if (locationHistory.size > 50) {
                                        locationHistory = locationHistory.takeLast(50)
                                    }
                                }
                            }

                            // Set start location if not set
                            if (startLocation == null) {
                                val startLat = data["latitude"] as? Double
                                val startLng = data["longitude"] as? Double
                                if (startLat != null && startLng != null) {
                                    startLocation = LatLng(startLat, startLng)
                                    Log.d("JourneyMonitoring", "Start location set: $startLat, $startLng")

                                    // Get start address
                                    scope.launch {
                                        try {
                                            val address = withContext(Dispatchers.IO) {
                                                val geocoder = Geocoder(context, Locale.getDefault())
                                                val addresses = geocoder.getFromLocation(startLat, startLng, 1)
                                                addresses?.firstOrNull()?.getAddressLine(0) ?: "Start Location"
                                            }
                                            startAddress = address
                                            Log.d("JourneyMonitoring", "Start address: $startAddress")
                                        } catch (e: Exception) {
                                            startAddress = "Start Location"
                                        }
                                    }
                                }
                            }
                        }

                        // Extract destination info
                        val destLat = data["destinationLatitude"] as? Double
                        val destLng = data["destinationLongitude"] as? Double
                        val destName = data["destinationName"] as? String

                        if (destLat != null && destLng != null && !destName.isNullOrEmpty()) {
                            val newDestination = LatLng(destLat, destLng)

                            // Only update if destination changed
                            if (destination != newDestination) {
                                destination = newDestination
                                destinationName = destName

                                // Get destination address
                                reverseGeocode(newDestination, true)

                                // Calculate route distance and time
                                userLocation?.let { userLoc ->
                                    val distance = calculateDistance(
                                        userLoc.latitude, userLoc.longitude,
                                        destLat, destLng
                                    )
                                    routeDistance = (distance * 1000).toFloat() // Convert to meters

                                    // Estimate walking time (assuming 5 km/h)
                                    val walkingTimeMinutes = (distance / 5.0 * 60).toInt()
                                    estimatedTime = if (walkingTimeMinutes < 60) {
                                        "${walkingTimeMinutes} min"
                                    } else {
                                        val hours = walkingTimeMinutes / 60
                                        val mins = walkingTimeMinutes % 60
                                        "${hours}h ${mins}m"
                                    }
                                }

                                Log.d("JourneyMonitoring", "Destination set: $destName at ($destLat, $destLng)")

                                // Fit camera to show both locations
                                fitCameraToShowBoth()
                            }
                        } else {
                            // Clear destination if not found in data
                            if (destination != null) {
                                destination = null
                                destinationName = null
                                destinationAddress = null
                                routeDistance = null
                                estimatedTime = null
                                Log.d("JourneyMonitoring", "Destination cleared")
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
                        if (destination != null) {
                            Log.d("JourneyMonitoring", "Destination: $destinationName")
                        }
                    }
                } else {
                    Log.w("JourneyMonitoring", "No SOS document found for ID: $sosRequestId")
                    currentAddress = "SOS document not found"
                }
            }
    }

    // Update camera when location changes (only if no destination)
    LaunchedEffect(userLocation) {
        if (destination == null) {
            userLocation?.let { location ->
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(location, 16f),
                    1000
                )
            }
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
        // Enhanced Header with collapsible functionality
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
                // Always visible header section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Monitoring Journey",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (destinationName != null)
                                "$userName is going to $destinationName"
                            else
                                "Tracking $userName's location",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    if (isActive) Color.Green else Color(0xFFFF9800),
                                    CircleShape
                                )
                        )

                        // Toggle button
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Show Less" else "Show More",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Collapsible content
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Current Location Details Card
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
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Current Location",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentAddress,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (currentStreet != "Unknown Street") {
                                    Text(
                                        text = "on $currentStreet",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Destination Card
                        if (destination != null && destinationName != null) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF1976D2).copy(alpha = 0.2f)
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
                                            imageVector = Icons.Default.Flag,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Destination",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = destinationName!!,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (destinationAddress != null && destinationAddress != destinationName) {
                                        Text(
                                            text = destinationAddress!!,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 12.sp
                                        )
                                    }

                                    // Route info
                                    if (routeDistance != null && estimatedTime != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "${String.format("%.1f", routeDistance!! / 1000)} km",
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "~$estimatedTime",
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status cards - always visible but more compact
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isExpanded) 6.dp else 4.dp)
                ) {
                    StatusCard(
                        title = "Status",
                        value = journeyStatus,
                        modifier = Modifier.weight(1f),
                        isCompact = !isExpanded
                    )
                    StatusCard(
                        title = if (destination != null) "Traveled" else "Distance",
                        value = "${String.format("%.2f", totalDistance / 1000)} km",
                        modifier = Modifier.weight(1f),
                        isCompact = !isExpanded
                    )
                    StatusCard(
                        title = "Speed",
                        value = if (speed > 0) "${String.format("%.1f", speed)} km/h" else "0 km/h",
                        modifier = Modifier.weight(1f),
                        isCompact = !isExpanded
                    )
                    if (destination != null && routeDistance != null) {
                        StatusCard(
                            title = "Remaining",
                            value = "${String.format("%.1f", routeDistance!! / 1000)} km",
                            modifier = Modifier.weight(1f),
                            isCompact = !isExpanded
                        )
                    }
                }
            }
        }

        // Map with enhanced markers
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
                        snippet = startAddress,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                    )
                }

                // User's current location
                userLocation?.let { location ->
                    Marker(
                        state = MarkerState(position = location),
                        title = "$userName's Current Location",
                        snippet = "$currentAddress • ${formatLastUpdate(lastUpdated)}",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
                    )

                    // Circle around current location
                    Circle(
                        center = location,
                        radius = 50.0,
                        fillColor = Color.Green.copy(alpha = 0.3f),
                        strokeColor = Color.Green,
                        strokeWidth = 3f
                    )
                }

                // Destination marker
                destination?.let { dest ->
                    Marker(
                        state = MarkerState(position = dest),
                        title = "Destination: ${destinationName ?: "Unknown"}",
                        snippet = destinationAddress ?: destinationName,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }

                // Journey path (actual traveled path)
                if (journeyPath.size > 1) {
                    Polyline(
                        points = journeyPath,
                        color = Color.Blue,
                        width = 8f
                    )
                }

                // Route line to destination (direct line)
                if (userLocation != null && destination != null) {
                    Polyline(
                        points = listOf(userLocation!!, destination!!),
                        color = Color(0xFF1976D2),
                        width = 6f,
                        pattern = listOf(
                            com.google.android.gms.maps.model.Dash(20f),
                            com.google.android.gms.maps.model.Gap(10f)
                        )
                    )
                }
            }

            // Emergency alert button
            if (userLocation != null && isActive) {
                FloatingActionButton(
                    onClick = {
                        val callIntent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:999")
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

            // Quick expand/collapse button on map
            if (!isExpanded) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .clickable { isExpanded = true },
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show Details",
                            fontSize = 12.sp,
                            color = Color(0xFF059669),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(16.dp)
                        )
                    }
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
                    OutlinedButton(
                        onClick = {
                            if (!userPhoneNumber.isNullOrEmpty()) {
                                val callIntent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:$userPhoneNumber")
                                }
                                context.startActivity(callIntent)
                            } else {
                                fetchUserPhoneNumber()
                                Log.w("JourneyMonitoring", "Phone number not available")
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

                    OutlinedButton(
                        onClick = {
                            if (!userPhoneNumber.isNullOrEmpty()) {
                                val messageBody = if (destinationName != null) {
                                    "Hi $userName, I'm monitoring your journey to $destinationName from $currentAddress. Stay safe!"
                                } else {
                                    "Hi $userName, I'm monitoring your journey from $currentAddress. Stay safe!"
                                }

                                val smsIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("sms:$userPhoneNumber")
                                    putExtra("sms_body", messageBody)
                                }
                                context.startActivity(smsIntent)
                            } else {
                                fetchUserPhoneNumber()
                                Log.w("JourneyMonitoring", "Phone number not available for SMS")
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
            }
        }
    }
}

// Supporting components
data class LocationUpdate(
    val location: LatLng,
    val timestamp: Date,
    val address: String
)

@Composable
private fun StatusCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
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
                .padding(if (isCompact) 6.dp else 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = if (isCompact) 9.sp else 10.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(if (isCompact) 1.dp else 2.dp))
            Text(
                text = value,
                fontSize = if (isCompact) 10.sp else 11.sp,
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