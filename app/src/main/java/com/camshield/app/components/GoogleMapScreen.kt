// Updated GoogleMapScreen.kt - Add callback for route info
package com.camshield.app.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.widget.Toast
import android.util.Log
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

// Data classes for Directions API
data class DirectionsResponse(val routes: List<Route>, val status: String)
data class Route(val legs: List<Leg>, val overview_polyline: OverviewPolyline)
data class Leg(val steps: List<Step>, val distance: Distance, val duration: Duration)
data class Step(
    val html_instructions: String,
    val distance: Distance,
    val duration: Duration,
    val start_location: LocationData,
    val end_location: LocationData,
    val polyline: OverviewPolyline,
    val maneuver: String?
)
data class Distance(val text: String, val value: Int)
data class Duration(val text: String, val value: Int)
data class LocationData(val lat: Double, val lng: Double)
data class OverviewPolyline(val points: String)
data class NavigationState(
    val isNavigating: Boolean = false,
    val currentStep: Step? = null,
    val stepIndex: Int = 0,
    val totalSteps: Int = 0,
    val distanceToDestination: String? = null
)

// Retrofit interface for Directions API
interface DirectionsService {
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String,
        @Query("mode") mode: String = "walking"
    ): Response<DirectionsResponse>
}

@Composable
fun GoogleMapScreen(
    context: Context,
    destinationLatLng: LatLng?,
    currentLocationState: MutableState<String?>,
    locationPermissionGranted: Boolean,
    onNavigationUpdate: (NavigationState) -> Unit = {},
    onLocationUpdate: (LatLng?) -> Unit = {},
    modifier: Modifier = Modifier,
    onRouteLoadingStateChanged: (Boolean) -> Unit = {},
    // NEW: Callback for route info
    onRouteInfoUpdate: (RouteInfo?) -> Unit = {},
    shouldStartNavigation: Boolean = false,
    onNavigationStarted: () -> Unit = {}
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
    var locationAge by remember { mutableStateOf<Long?>(null) }

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

    val directionsService = remember {
        Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DirectionsService::class.java)
    }

    // NEW: Load route when destination is set (but don't start navigation)
    LaunchedEffect(destinationLatLng, userLocation) {
        if (destinationLatLng != null && userLocation != null && !isNavigating && !shouldStartNavigation) {
            Log.d("Route", "Loading route preview from $userLocation to $destinationLatLng")
            onRouteLoadingStateChanged(true)

            getDirectionsAndLoadRoute(
                service = directionsService,
                origin = userLocation!!,
                destination = destinationLatLng,
                apiKey = getApiKey(),
                onSuccess = { steps, polylinePoints, routeInfo ->
                    Log.d("Route", "Route preview loaded with ${steps.size} steps")
                    routeSteps = steps
                    routePoints = polylinePoints
                    // Update route info in the panel
                    onRouteInfoUpdate(routeInfo)
                    onRouteLoadingStateChanged(false)
                    currentLocationState.value = "Route loaded - ready to navigate"
                },
                onError = { error ->
                    Log.e("Route", "Route loading failed: $error")
                    onRouteLoadingStateChanged(false)
                    onRouteInfoUpdate(null)
                    currentLocationState.value = "Route loading failed: $error"
                }
            )
        }
    }

    // Navigation trigger - START navigation when button pressed
    LaunchedEffect(shouldStartNavigation, destinationLatLng, userLocation) {
        if (shouldStartNavigation && destinationLatLng != null && userLocation != null && !isNavigating) {
            Log.d("Navigation", "Starting navigation from $userLocation to $destinationLatLng")

            // If we already have route steps, start navigation immediately
            if (routeSteps.isNotEmpty()) {
                Log.d("Navigation", "Using existing route steps")
                isNavigating = true
                currentStepIndex = 0

                onNavigationUpdate(NavigationState(
                    isNavigating = true,
                    currentStep = routeSteps.firstOrNull(),
                    stepIndex = 0,
                    totalSteps = routeSteps.size,
                    distanceToDestination = calculateDistance(userLocation, destinationLatLng)
                ))

                onNavigationStarted()
                currentLocationState.value = "Navigation started!"
            } else {
                // Need to load route first
                onRouteLoadingStateChanged(true)
                getDirectionsAndLoadRoute(
                    service = directionsService,
                    origin = userLocation!!,
                    destination = destinationLatLng,
                    apiKey = getApiKey(),
                    onSuccess = { steps, polylinePoints, routeInfo ->
                        Log.d("Navigation", "Route loaded and starting navigation with ${steps.size} steps")
                        routeSteps = steps
                        routePoints = polylinePoints
                        isNavigating = true
                        currentStepIndex = 0

                        onNavigationUpdate(NavigationState(
                            isNavigating = true,
                            currentStep = steps.firstOrNull(),
                            stepIndex = 0,
                            totalSteps = steps.size,
                            distanceToDestination = calculateDistance(userLocation, destinationLatLng)
                        ))

                        onNavigationStarted()
                        onRouteLoadingStateChanged(false)
                        currentLocationState.value = "Navigation started!"
                    },
                    onError = { error ->
                        Log.e("Navigation", "Navigation failed: $error")
                        onRouteLoadingStateChanged(false)
                        currentLocationState.value = "Navigation failed: $error"
                        Toast.makeText(context, "Navigation failed: $error", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    // Reset navigation when destination changes
    LaunchedEffect(destinationLatLng) {
        if (isNavigating) {
            isNavigating = false
            routeSteps = emptyList()
            routePoints = emptyList()
            currentStepIndex = 0
            onNavigationUpdate(NavigationState())
            onRouteInfoUpdate(null)
        }
    }

    // CACHE FIRST + FRESH LOCATION REFINEMENT
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            getCachedThenFreshLocationWithRefinement(fusedLocationClient) { location, accuracy, source, age, isRefining ->
                userLocation = location
                locationAccuracy = accuracy
                locationSource = source
                locationAge = age
                isLocationLoading = false
                isRefiningLocation = isRefining

                // Update parent component
                onLocationUpdate(location)

                val ageText = when {
                    age < 30000 -> "Fresh"
                    age < 300000 -> "Recent"
                    else -> "Cached"
                }

                val accuracyText = when {
                    accuracy <= 20f -> "Excellent"
                    accuracy <= 50f -> "Good"
                    accuracy <= 100f -> "Fair"
                    else -> "Poor"
                }

                currentLocationState.value = when {
                    isRefining -> "Improving location... ($accuracyText • $ageText)"
                    age < 30000 -> "Current location ($accuracyText)"
                    else -> "Location found ($accuracyText • $ageText)"
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
                    locationAge = System.currentTimeMillis() - location.time

                    // Update parent component
                    onLocationUpdate(newLocation)

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

                            // Check if navigation is complete
                            if (newIndex >= routeSteps.size - 1) {
                                Log.d("Navigation", "Navigation completed!")
                                currentLocationState.value = "Destination reached!"
                            }
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
                // Accuracy circle - color based on accuracy and age
                locationAccuracy?.let { accuracy ->
                    val circleColor = when {
                        locationAge != null && locationAge!! > 300000 -> Color.Gray.copy(alpha = 0.3f) // Old location
                        accuracy <= 50f -> Color.Blue.copy(alpha = 0.3f) // Good accuracy
                        else -> Color.Yellow.copy(alpha = 0.3f) // Poor accuracy
                    }

                    Circle(
                        center = location,
                        radius = accuracy.toDouble(),
                        strokeColor = circleColor,
                        fillColor = circleColor.copy(alpha = 0.1f),
                        strokeWidth = 2f
                    )
                }

                // Location marker with age indicator
                val markerSnippet = buildString {
                    append("±${locationAccuracy?.toInt() ?: 0}m accuracy")
                    locationSource?.let { append(" • $it") }
                    locationAge?.let { age ->
                        val ageSeconds = age / 1000
                        when {
                            ageSeconds < 60 -> append(" • ${ageSeconds}s ago")
                            ageSeconds < 3600 -> append(" • ${ageSeconds/60}m ago")
                            else -> append(" • ${ageSeconds/3600}h ago")
                        }
                    }
                }

                Marker(
                    state = MarkerState(position = location),
                    title = "Your Location",
                    snippet = markerSnippet
                )
            }

            // Destination marker
            destinationLatLng?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Destination",
                    snippet = if (isNavigating) "Navigating here" else "Tap Start Navigation"
                )
            }

            // Route visualization - Enhanced for better visibility
            if (routePoints.size > 1) {
                Polyline(
                    points = routePoints,
                    color = if (isNavigating) Color(0xFF1976D2) else Color(0xFF757575),
                    width = if (isNavigating) 8f else 5f
                )
            }

            // Navigation step markers - Only show current and next step
            if (isNavigating && routeSteps.isNotEmpty()) {
                // Current step marker
                if (currentStepIndex < routeSteps.size) {
                    val currentStep = routeSteps[currentStepIndex]
                    val position = LatLng(currentStep.start_location.lat, currentStep.start_location.lng)
                    Marker(
                        state = MarkerState(position = position),
                        title = "Current Step",
                        snippet = cleanHtmlInstructions(currentStep.html_instructions)
                    )
                }

                // Next step marker
                if (currentStepIndex + 1 < routeSteps.size) {
                    val nextStep = routeSteps[currentStepIndex + 1]
                    val position = LatLng(nextStep.start_location.lat, nextStep.start_location.lng)
                    Marker(
                        state = MarkerState(position = position),
                        title = "Next Step",
                        snippet = cleanHtmlInstructions(nextStep.html_instructions)
                    )
                }
            }
        }

        // Loading indicator - only shows initially and briefly
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
                    Text("Getting your location...")
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

        // Enhanced location info panel
        locationAccuracy?.let { accuracy ->
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        locationAge != null && locationAge!! > 300000 -> Color(0xFF9E9E9E).copy(alpha = 0.9f) // Old - Gray
                        accuracy <= 10f -> Color(0xFF4CAF50).copy(alpha = 0.9f) // Excellent
                        accuracy <= 30f -> Color(0xFF8BC34A).copy(alpha = 0.9f) // Good
                        accuracy <= 100f -> Color(0xFFFFC107).copy(alpha = 0.9f) // Fair - Yellow
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = locationSource ?: "GPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        // Age indicator
                        locationAge?.let { age ->
                            val ageText = when {
                                age < 30000 -> "●" // Fresh - solid dot
                                age < 300000 -> "◐" // Recent - half dot
                                else -> "○" // Old - empty dot
                            }
                            Text(
                                text = ageText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    if (isRefiningLocation) {
                        Text(
                            text = "Updating...",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
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

            val age = locationAge?.let { ageMs ->
                val ageSeconds = ageMs / 1000
                when {
                    ageSeconds < 60 -> "fresh"
                    ageSeconds < 300 -> "recent"
                    else -> "cached"
                }
            } ?: "unknown"

            if (!isNavigating) { // Don't override navigation messages
                currentLocationState.value = "$boundary ($accuracy • $age)"
            }
        }
    }
}

// NEW: Function to load route (without starting navigation)
suspend fun getDirectionsAndLoadRoute(
    service: DirectionsService,
    origin: LatLng,
    destination: LatLng,
    apiKey: String,
    onSuccess: (List<Step>, List<LatLng>, RouteInfo) -> Unit,
    onError: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val originStr = "${origin.latitude},${origin.longitude}"
            val destinationStr = "${destination.latitude},${destination.longitude}"

            Log.d("Route", "Fetching route: $originStr -> $destinationStr")

            val response = service.getDirections(originStr, destinationStr, apiKey)

            if (response.isSuccessful) {
                val directionsResponse = response.body()

                if (directionsResponse?.status == "OK" && directionsResponse.routes.isNotEmpty()) {
                    val route = directionsResponse.routes[0]
                    val steps = route.legs.flatMap { it.steps }
                    val polylinePoints = decodePolyline(route.overview_polyline.points)
                    val routeInfo = extractRouteInfo(route)

                    Log.d("Route", "Route loaded: ${steps.size} steps, ${polylinePoints.size} points")

                    withContext(Dispatchers.Main) {
                        onSuccess(steps, polylinePoints, routeInfo)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("No route found: ${directionsResponse?.status ?: "Unknown error"}")
                    }
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("Route", "API Error: ${response.code()} ${response.message()}")
                Log.e("Route", "Error body: $errorBody")

                withContext(Dispatchers.Main) {
                    onError("API request failed: ${response.code()} ${response.message()}")
                }
            }
        } catch (e: Exception) {
            Log.e("Route", "Exception: ${e.message}", e)
            withContext(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
        }
    }
}

// API Key configuration - Replace with your actual Google Maps API key
fun getApiKey(): String {
    // TODO: Replace with your actual Google Directions API key
    // Get it from: https://console.cloud.google.com/apis/credentials
    return "AIzaSyCPmhj7Hot40l9AMeJq8dKPJ-7UHq5-S3E" // Replace this with your real API key
}

// CACHE FIRST + FRESH REFINEMENT APPROACH
@SuppressLint("MissingPermission")
fun getCachedThenFreshLocationWithRefinement(
    fusedLocationClient: FusedLocationProviderClient,
    onLocationUpdate: (LatLng, Float, String, Long, Boolean) -> Unit
) {
    var bestAccuracy = Float.MAX_VALUE
    var hasShownCachedLocation = false

    // STEP 1: Get cached location immediately for instant feedback
    fusedLocationClient.lastLocation.addOnSuccessListener { cachedLocation ->
        cachedLocation?.let { location ->
            val age = System.currentTimeMillis() - location.time
            hasShownCachedLocation = true
            bestAccuracy = location.accuracy

            onLocationUpdate(
                LatLng(location.latitude, location.longitude),
                location.accuracy,
                "CACHED",
                age,
                true // Always refining when showing cached
            )
        }
    }

    // STEP 2: Start getting fresh location immediately (parallel with cache)
    val freshLocationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 1000L)
        .setMaxUpdateDelayMillis(3000L)
        .setMaxUpdates(2)
        .setWaitForAccurateLocation(false)
        .build()

    val freshCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                val age = System.currentTimeMillis() - location.time

                // Always update if this is the first location OR if accuracy improved significantly
                if (!hasShownCachedLocation || location.accuracy < bestAccuracy * 0.8f) {
                    bestAccuracy = location.accuracy
                    hasShownCachedLocation = true

                    onLocationUpdate(
                        LatLng(location.latitude, location.longitude),
                        location.accuracy,
                        location.provider?.uppercase() ?: "GPS",
                        age,
                        location.accuracy > 30f // Still refining if accuracy > 30m
                    )
                }
            }
        }
    }

    // Start fresh location request
    fusedLocationClient.requestLocationUpdates(freshLocationRequest, freshCallback, null)

    // STEP 3: If we still don't have good accuracy after 4 seconds, try high accuracy
    Handler(Looper.getMainLooper()).postDelayed({
        if (bestAccuracy > 50f) {
            val highAccuracyRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMaxUpdateDelayMillis(6000L)
                .setMaxUpdates(3)
                .setWaitForAccurateLocation(false)
                .build()

            val highAccuracyCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val age = System.currentTimeMillis() - location.time

                        if (location.accuracy < bestAccuracy) {
                            bestAccuracy = location.accuracy
                            onLocationUpdate(
                                LatLng(location.latitude, location.longitude),
                                location.accuracy,
                                "GPS",
                                age,
                                location.accuracy > 20f // Still refining if accuracy > 20m
                            )
                        }
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(highAccuracyRequest, highAccuracyCallback, null)

            // Clean up high accuracy after 6 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                fusedLocationClient.removeLocationUpdates(highAccuracyCallback)
            }, 6000)
        }

        // Clean up fresh location callback
        fusedLocationClient.removeLocationUpdates(freshCallback)
    }, 4000)

    // If no cached location was available, ensure we still show something within 2 seconds
    Handler(Looper.getMainLooper()).postDelayed({
        if (!hasShownCachedLocation) {
            // Trigger a quick low-power location request as fallback
            val quickLocationRequest = LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, 1000L)
                .setMaxUpdateDelayMillis(2000L)
                .setMaxUpdates(1)
                .build()

            val quickCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val age = System.currentTimeMillis() - location.time
                        onLocationUpdate(
                            LatLng(location.latitude, location.longitude),
                            location.accuracy,
                            "NETWORK",
                            age,
                            true
                        )
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(quickLocationRequest, quickCallback, null)

            // Clean up quick callback
            Handler(Looper.getMainLooper()).postDelayed({
                fusedLocationClient.removeLocationUpdates(quickCallback)
            }, 2000)
        }
    }, 1000)
}

// Navigation helper functions
fun updateNavigationProgress(
    currentLocation: LatLng,
    steps: List<Step>,
    currentStepIndex: Int,
    onStepChanged: (Int) -> Unit
) {
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