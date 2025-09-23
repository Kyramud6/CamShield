// Fast Approximate + Gradual Refinement GoogleMapScreen.kt (No Cache)
package com.camshield.app.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.os.Handler
import android.os.Looper

// Data classes (same as before)
data class DirectionsResponse(val routes: List<Route>, val status: String)
data class Route(val legs: List<Leg>, val overview_polyline: OverviewPolyline)
data class Leg(val steps: List<Step>, val distance: Distance, val duration: Duration)
data class Step(val html_instructions: String, val distance: Distance, val duration: Duration,
                val start_location: LocationData, val end_location: LocationData, val polyline: OverviewPolyline, val maneuver: String?)
data class Distance(val text: String, val value: Int)
data class Duration(val text: String, val value: Int)
data class LocationData(val lat: Double, val lng: Double)
data class OverviewPolyline(val points: String)
data class NavigationState(val isNavigating: Boolean = false, val currentStep: Step? = null,
                           val stepIndex: Int = 0, val totalSteps: Int = 0, val distanceToDestination: String? = null)

interface DirectionsService {
    @GET("maps/api/directions/json")
    suspend fun getDirections(@Query("origin") origin: String, @Query("destination") destination: String,
                              @Query("key") apiKey: String, @Query("mode") mode: String = "walking"): Response<DirectionsResponse>
}

@Composable
fun GoogleMapScreen(
    context: Context,
    destinationLatLng: LatLng?,
    currentLocationState: MutableState<String?>,
    locationPermissionGranted: Boolean,
    onNavigationUpdate: (NavigationState) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    // Location states
    var hasLocationPermission by remember { mutableStateOf(locationPermissionGranted) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var isLocationLoading by remember { mutableStateOf(true) }
    var locationAccuracy by remember { mutableStateOf<Float?>(null) }
    var locationSource by remember { mutableStateOf<String?>(null) }
    var isRefiningLocation by remember { mutableStateOf(false) }

    // Navigation states
    var routeSteps by remember { mutableStateOf<List<Step>>(emptyList()) }
    var currentStepIndex by remember { mutableStateOf(0) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isNavigating by remember { mutableStateOf(false) }

    // Update permission state
    LaunchedEffect(locationPermissionGranted) {
        hasLocationPermission = locationPermissionGranted
        if (locationPermissionGranted) {
            currentLocationState.value = "Finding location..."
        }
    }

    // FRESH LOCATION ONLY - NO CACHE
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            getFreshLocationWithGradualRefinement(fusedLocationClient) { location, accuracy, source, isRefining ->
                userLocation = location
                locationAccuracy = accuracy
                locationSource = source
                isLocationLoading = false
                isRefiningLocation = isRefining

                val accuracyText = if (accuracy <= 50f) "High accuracy"
                else if (accuracy <= 100f) "Good accuracy"
                else "Approximate location"

                currentLocationState.value = if (isRefining) {
                    "Improving accuracy... ($accuracyText)"
                } else {
                    "Location found! ($accuracyText)"
                }
            }
        } else {
            currentLocationState.value = "Location permission required"
            isLocationLoading = false
        }
    }

    // Continuous location updates for real-time tracking
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val newLocation = LatLng(location.latitude, location.longitude)
                    userLocation = newLocation
                    locationAccuracy = location.accuracy
                    locationSource = location.provider?.uppercase() ?: "GPS"

                    // Update navigation if active
                    if (isNavigating && routeSteps.isNotEmpty()) {
                        updateNavigationProgress(newLocation, routeSteps, currentStepIndex) { newIndex ->
                            currentStepIndex = newIndex
                            onNavigationUpdate(NavigationState(
                                isNavigating = true,
                                currentStep = routeSteps.getOrNull(currentStepIndex),
                                stepIndex = currentStepIndex,
                                totalSteps = routeSteps.size,
                                distanceToDestination = calculateDistance(newLocation, destinationLatLng)
                            ))
                        }
                    }
                }
            }
        }
    }

    // Start continuous updates after getting initial location
    LaunchedEffect(userLocation, hasLocationPermission) {
        if (hasLocationPermission && userLocation != null) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(3f)
                .build()

            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            } catch (e: SecurityException) {
                // Handle silently
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    val directionsService = remember {
        Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DirectionsService::class.java)
    }

    val campusBoundary = listOf(
        LatLng(2.816326, 101.756493),
        LatLng(2.816128, 101.762675),
        LatLng(2.808067, 101.764248),
        LatLng(2.807677, 101.756583)
    )

    // Camera position - immediately show location when available
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            userLocation ?: LatLng(2.814, 101.758),
            if (userLocation != null) 17f else 15f
        )
    }

    // Smooth camera updates
    LaunchedEffect(userLocation) {
        userLocation?.let { location ->
            scope.launch {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(location, 17f),
                    durationMs = 800
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission && userLocation != null,
                mapType = MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = true,
                zoomControlsEnabled = true,
                compassEnabled = true,
                rotationGesturesEnabled = true,
                scrollGesturesEnabled = true,
                tiltGesturesEnabled = true,
                zoomGesturesEnabled = true
            )
        ) {
            // Campus polygon
            Polygon(
                points = campusBoundary,
                strokeColor = Color.Red,
                fillColor = Color(0x44FF0000),
                strokeWidth = 3f
            )

            // User location marker with accuracy circle
            userLocation?.let { location ->
                // Accuracy circle
                locationAccuracy?.let { accuracy ->
                    Circle(
                        center = location,
                        radius = accuracy.toDouble(),
                        strokeColor = Color.Blue.copy(alpha = 0.3f),
                        fillColor = Color.Blue.copy(alpha = 0.1f),
                        strokeWidth = 2f
                    )
                }

                // Location marker
                Marker(
                    state = MarkerState(position = location),
                    title = "Your Location",
                    snippet = "${locationAccuracy?.toInt()}m accuracy • ${locationSource ?: "GPS"}"
                )
            }

            // Destination marker
            destinationLatLng?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Destination"
                )
            }

            // Route visualization
            if (routePoints.size > 1) {
                Polyline(
                    points = routePoints,
                    color = if (isNavigating) Color(0xFF1976D2) else Color.Gray,
                    width = if (isNavigating) 12f else 8f
                )
            }

            // Navigation step markers
            if (isNavigating && routeSteps.isNotEmpty()) {
                routeSteps.forEachIndexed { index, step ->
                    val position = LatLng(step.start_location.lat, step.start_location.lng)
                    Marker(
                        state = MarkerState(position = position),
                        title = "Step ${index + 1}",
                        snippet = cleanHtmlInstructions(step.html_instructions)
                    )
                }
            }
        }

        // Loading indicator - only shows initially
        if (isLocationLoading && hasLocationPermission) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Finding your location...")
                }
            }
        }

        // Permission warning
        if (!hasLocationPermission) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Text(
                    text = "Location permission required",
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFFC62828)
                )
            }
        }

        // Location accuracy indicator with refinement status
        locationAccuracy?.let { accuracy ->
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        accuracy <= 10f -> Color(0xFF4CAF50).copy(alpha = 0.9f) // Excellent
                        accuracy <= 30f -> Color(0xFF8BC34A).copy(alpha = 0.9f) // Good
                        accuracy <= 100f -> Color(0xFFFF9800).copy(alpha = 0.9f) // Fair
                        else -> Color(0xFFF44336).copy(alpha = 0.9f) // Poor
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRefiningLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = "±${accuracy.toInt()}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                    Text(
                        text = locationSource ?: "GPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    if (isRefiningLocation) {
                        Text(
                            text = "Refining...",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Navigation controls
        if (destinationLatLng != null && userLocation != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isNavigating) {
                    Button(
                        onClick = {
                            scope.launch {
                                getDirectionsAndStartNavigation(
                                    directionsService,
                                    userLocation!!,
                                    destinationLatLng,
                                    "YOUR_API_KEY_HERE",
                                    onSuccess = { steps, points ->
                                        routeSteps = steps
                                        routePoints = points
                                        isNavigating = true
                                        currentStepIndex = 0
                                        onNavigationUpdate(NavigationState(
                                            isNavigating = true,
                                            currentStep = steps.firstOrNull(),
                                            stepIndex = 0,
                                            totalSteps = steps.size,
                                            distanceToDestination = calculateDistance(userLocation!!, destinationLatLng)
                                        ))
                                    },
                                    onError = { error ->
                                        Toast.makeText(context, "Navigation error: $error", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    ) {
                        Text("Start Navigation")
                    }
                } else {
                    Button(
                        onClick = {
                            isNavigating = false
                            routeSteps = emptyList()
                            currentStepIndex = 0
                            onNavigationUpdate(NavigationState(isNavigating = false))
                        }
                    ) {
                        Text("Stop Navigation")
                    }
                }
            }
        }
    }

    // Update boundary status
    LaunchedEffect(userLocation) {
        userLocation?.let { loc ->
            val boundary = if (isInsidePolygon(loc, campusBoundary)) "Inside campus" else "Outside campus"
            val accuracy = locationAccuracy?.let {
                if (it <= 50f) "accurate" else if (it <= 100f) "approximate" else "rough"
            } ?: "location"
            currentLocationState.value = "$boundary ($accuracy)"
        }
    }
}

// FRESH LOCATION ONLY - LOW TO HIGH ACCURACY APPROACH
@SuppressLint("MissingPermission")
fun getFreshLocationWithGradualRefinement(
    fusedLocationClient: FusedLocationProviderClient,
    onLocationUpdate: (LatLng, Float, String, Boolean) -> Unit
) {
    var bestAccuracy = Float.MAX_VALUE
    var hasFoundInitialLocation = false

    // STEP 1: Start with low accuracy, fast network-based location
    val networkLocationRequest = LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, 2000L)
        .setMaxUpdateDelayMillis(5000L)
        .setMaxUpdates(2)
        .setWaitForAccurateLocation(false)
        .build()

    val networkCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                if (!hasFoundInitialLocation) {
                    hasFoundInitialLocation = true
                    bestAccuracy = location.accuracy
                    onLocationUpdate(
                        LatLng(location.latitude, location.longitude),
                        location.accuracy,
                        "NETWORK",
                        true // Still refining
                    )
                }
            }
        }
    }

    // Start with low accuracy first
    fusedLocationClient.requestLocationUpdates(networkLocationRequest, networkCallback, null)

    // STEP 2: After getting initial location, upgrade to balanced accuracy
    Handler(Looper.getMainLooper()).postDelayed({
        val balancedLocationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 1000L)
            .setMaxUpdateDelayMillis(5000L)
            .setMaxUpdates(3)
            .setWaitForAccurateLocation(false)
            .build()

        val balancedCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    if (location.accuracy < bestAccuracy) {
                        bestAccuracy = location.accuracy
                        onLocationUpdate(
                            LatLng(location.latitude, location.longitude),
                            location.accuracy,
                            "GPS",
                            location.accuracy > 30f // Still refining if accuracy > 30m
                        )
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(balancedLocationRequest, balancedCallback, null)

        // STEP 3: Finally, upgrade to high accuracy if needed
        Handler(Looper.getMainLooper()).postDelayed({
            if (bestAccuracy > 50f) { // Only use high accuracy if we still don't have good accuracy
                val gpsLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                    .setMaxUpdateDelayMillis(8000L)
                    .setMaxUpdates(4)
                    .setWaitForAccurateLocation(false)
                    .build()

                val gpsCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        locationResult.lastLocation?.let { location ->
                            if (location.accuracy < bestAccuracy) {
                                bestAccuracy = location.accuracy
                                onLocationUpdate(
                                    LatLng(location.latitude, location.longitude),
                                    location.accuracy,
                                    "GPS",
                                    location.accuracy > 20f // Still refining if accuracy > 20m
                                )
                            }
                        }
                    }
                }

                fusedLocationClient.requestLocationUpdates(gpsLocationRequest, gpsCallback, null)

                // Clean up high accuracy after 8 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    fusedLocationClient.removeLocationUpdates(gpsCallback)
                }, 8000)
            }

            // Clean up balanced accuracy
            fusedLocationClient.removeLocationUpdates(balancedCallback)
        }, 5000) // Wait 5 seconds before trying high accuracy

        // Clean up network callback
        fusedLocationClient.removeLocationUpdates(networkCallback)
    }, 3000) // Wait 3 seconds before upgrading from low power
}

// Keep all existing helper functions
suspend fun getDirectionsAndStartNavigation(
    service: DirectionsService, origin: LatLng, destination: LatLng, apiKey: String,
    onSuccess: (List<Step>, List<LatLng>) -> Unit, onError: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val originStr = "${origin.latitude},${origin.longitude}"
            val destinationStr = "${destination.latitude},${destination.longitude}"
            val response = service.getDirections(originStr, destinationStr, apiKey)
            if (response.isSuccessful) {
                val directionsResponse = response.body()
                if (directionsResponse?.status == "OK" && directionsResponse.routes.isNotEmpty()) {
                    val route = directionsResponse.routes[0]
                    val steps = route.legs.flatMap { it.steps }
                    val polylinePoints = decodePolyline(route.overview_polyline.points)
                    withContext(Dispatchers.Main) { onSuccess(steps, polylinePoints) }
                } else {
                    withContext(Dispatchers.Main) { onError("No route found") }
                }
            } else {
                withContext(Dispatchers.Main) { onError("API request failed") }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
        }
    }
}

fun updateNavigationProgress(currentLocation: LatLng, steps: List<Step>, currentStepIndex: Int, onStepChanged: (Int) -> Unit) {
    if (currentStepIndex < steps.size) {
        val currentStep = steps[currentStepIndex]
        val stepEndLocation = LatLng(currentStep.end_location.lat, currentStep.end_location.lng)
        val distanceToStepEnd = calculateDistanceInMeters(currentLocation, stepEndLocation)
        if (distanceToStepEnd < 20 && currentStepIndex < steps.size - 1) {
            onStepChanged(currentStepIndex + 1)
        }
    }
}

fun calculateDistance(origin: LatLng?, destination: LatLng?): String? {
    if (origin == null || destination == null) return null
    val distance = calculateDistanceInMeters(origin, destination)
    return if (distance < 1000) "${distance.toInt()}m" else String.format("%.1fkm", distance / 1000)
}

fun calculateDistanceInMeters(origin: LatLng, destination: LatLng): Double {
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(destination.latitude - origin.latitude)
    val dLng = Math.toRadians(destination.longitude - origin.longitude)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(origin.latitude)) *
            Math.cos(Math.toRadians(destination.latitude)) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return earthRadius * c
}

fun decodePolyline(encoded: String): List<LatLng> {
    val polylinePoints = mutableListOf<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0
    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat
        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng
        polylinePoints.add(LatLng(lat / 1E5, lng / 1E5))
    }
    return polylinePoints
}

fun isInsidePolygon(point: LatLng, polygon: List<LatLng>): Boolean {
    var intersectCount = 0
    for (i in polygon.indices) {
        val j = (i + 1) % polygon.size
        val xi = polygon[i].latitude
        val yi = polygon[i].longitude
        val xj = polygon[j].latitude
        val yj = polygon[j].longitude
        if (((yi > point.longitude) != (yj > point.longitude)) &&
            (point.latitude < (xj - xi) * (point.longitude - yi) / (yj - yi) + xi)) {
            intersectCount++
        }
    }
    return intersectCount % 2 == 1
}

fun cleanHtmlInstructions(htmlText: String): String {
    return htmlText.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim()
}