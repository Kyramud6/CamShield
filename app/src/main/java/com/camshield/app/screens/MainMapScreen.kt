// MainMapScreen.kt - FIXED BUGS
package com.camshield.app.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.camshield.app.components.GoogleMapScreen
import com.camshield.app.components.ModernSOSButton
import com.camshield.app.components.*
// Import centralized data classes
import com.camshield.app.data.NavigationState
import com.camshield.app.data.RouteInfo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import android.media.MediaRecorder
import com.camshield.app.App
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import android.util.Log
// ADD THESE IMPORTS FOR PERSISTENCE
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson

val firestore = FirebaseFirestore.getInstance()
val auth = FirebaseAuth.getInstance()

@Composable
fun MainMapScreen(
    onMenuClick: () -> Unit,
    initialDestination: LatLng? = null,
    initialDestinationName: String? = null,
    onClearDestination: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Check current permission status
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val currentLocationState = remember { mutableStateOf<String?>(null) }
    var currentSosDocId by remember { mutableStateOf<String?>(null) }

    // SOS button state
    var sosPressed by remember { mutableStateOf(false) }
    val sosScale = remember { Animatable(1f) }
    var isSOSOverlayVisible by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }

    // FIX 1: Add navigation view mode state
    var isInNavigationMode by remember { mutableStateOf(false) }

    // Navigation state - Initialize with passed destination
    var destination by remember { mutableStateOf(initialDestination) }
    var destinationName by remember { mutableStateOf(initialDestinationName) }
    var navigationState by remember { mutableStateOf(NavigationState()) }
    var routeInfo: RouteInfo? by remember { mutableStateOf(null) }

    // Track loading state for route
    var isLoadingRoute by remember { mutableStateOf(false) }

    // Add navigation trigger state
    var shouldStartNavigation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // FIX 3: Add SharedPreferences for persistence
    val sharedPrefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val gson = remember { Gson() }

    // FIX 3: Clear cached data on app launch (you can also make this conditional)
    LaunchedEffect(Unit) {
        clearNavigationCache(sharedPrefs)
    }

    // FIX 3: Save navigation state to cache
    LaunchedEffect(destination, destinationName, routeInfo) {
        if (destination != null && destinationName != null) {
            saveNavigationState(sharedPrefs, gson, destination!!, destinationName!!, routeInfo)
        }
    }

    val micPermissionGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionGranted.value = granted
        if (!granted) {
            currentLocationState.value = "🎤 Microphone permission denied. Recording won't work."
        }
    }

    // Request microphone permission on launch
    LaunchedEffect(Unit) {
        if (!micPermissionGranted.value) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Update destination when initial values change
    LaunchedEffect(initialDestination, initialDestinationName) {
        destination = initialDestination
        destinationName = initialDestinationName
    }

    val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Single permission launcher - only here, not in GoogleMapScreen
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (locationPermissionGranted) {
            currentLocationState.value = "Location permission granted! Getting your location..."
        } else {
            currentLocationState.value = "Location permission denied. Please enable it in settings."
        }
    }

    // Request permission on launch if not granted
    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Auto-hide location message
    LaunchedEffect(currentLocationState.value) {
        currentLocationState.value?.let {
            delay(5000)
            currentLocationState.value = null
        }
    }

    // SOS button animation
    LaunchedEffect(sosPressed) {
        if (sosPressed) {
            while (sosPressed) {
                sosScale.animateTo(1.1f, animationSpec = tween(500))
                sosScale.animateTo(1f, animationSpec = tween(500))
            }
        }
    }

    // Recording states
    var mediaRecorder: MediaRecorder? by remember { mutableStateOf(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    // Upload helper
    suspend fun uploadAudioToSupabase(file: File): String? {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = "${UUID.randomUUID()}.mp3"
                val bytes = file.readBytes()
                App.supabase.storage.from("sos-audio").upload(fileName, bytes)
                App.supabase.storage.from("sos-audio").publicUrl(fileName)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // FIX 2: Function to completely reset navigation state
    fun resetNavigationState() {
        destination = null
        destinationName = null
        navigationState = NavigationState()
        shouldStartNavigation = false
        routeInfo = null
        isInNavigationMode = false
        isLoadingRoute = false
        // Clear from cache
        clearNavigationCache(sharedPrefs)

        //Clear parent state in MainApp
        onClearDestination?.invoke()

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            FirebaseFirestore.getInstance()
                .collection("Users")
                .document(currentUser.uid)
                .update("currentDestination", null)

            FirebaseFirestore.getInstance()
                .collection("SOS")
                .whereEqualTo("userId", currentUser.uid)
                .whereEqualTo("status", "Active")
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        document.reference.update(
                            mapOf(
                                "destinationLatitude" to null,
                                "destinationLongitude" to null,
                                "destinationName" to null
                            )
                        )
                        Log.d("MainMapScreen", "Cleared destination from SOS: ${document.id}")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MainMapScreen", "Failed to clear SOS destination: ${e.message}")
                }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Always show the map, let GoogleMapScreen handle permission checks
        Column(modifier = Modifier.fillMaxSize()) {

            // FIX 1: Only show NavigationPanel when not in full navigation mode
            if (!isInNavigationMode) {
                NavigationPanel(
                    navigationState = navigationState,
                    routeInfo = routeInfo,
                    destinationName = destinationName,
                    isLoadingRoute = isLoadingRoute,
                    modifier = Modifier.zIndex(1f)
                )
            }

            // Google Map - Pass route info callback to update panel
            Box(modifier = Modifier.weight(1f)) {
                GoogleMapScreen(
                    context = context,
                    destinationLatLng = destination,
                    currentLocationState = currentLocationState,
                    locationPermissionGranted = locationPermissionGranted,
                    isInNavigationMode = isInNavigationMode, // FIX 1: Pass navigation mode
                    onNavigationUpdate = { newState ->
                        navigationState = newState
                        // FIX 1: Auto-exit navigation mode when navigation ends
                        if (!newState.isNavigating && isInNavigationMode) {
                            isInNavigationMode = false
                        }
                    },
                    onRouteLoadingStateChanged = { loading ->
                        isLoadingRoute = loading
                    },
                    onRouteInfoUpdate = { newRouteInfo ->
                        routeInfo = newRouteInfo
                    },
                    shouldStartNavigation = shouldStartNavigation,
                    onNavigationStarted = {
                        shouldStartNavigation = false
                        isInNavigationMode = true // FIX 1: Enter navigation mode
                    },
                    routeInfo = routeInfo, // NEW: Pass routeInfo parameter
                    modifier = Modifier.fillMaxSize()
                )

                // Permission required overlay
                if (!locationPermissionGranted) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "📍 Location Access Required",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "To show your location on the map and provide navigation, please grant location permission.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            ) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }

                // FIX 1: Full-screen navigation overlay
                if (isInNavigationMode && navigationState.isNavigating) {
                    FullNavigationOverlay(
                        navigationState = navigationState,
                        onExitNavigation = {
                            resetNavigationState()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // FIX 1: Only show bottom controls when not in navigation mode
        if (!isInNavigationMode) {
            // Bottom Navigation Controls
            BottomNavigationControls(
                isNavigating = navigationState.isNavigating,
                hasDestination = destination != null,
                hasLocation = locationPermissionGranted,
                isLoadingRoute = isLoadingRoute,
                onStartNavigation = { shouldStartNavigation = true },
                onStopNavigation = {
                    resetNavigationState() // FIX 2: Use complete reset function
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 142.dp, // SOS button width (70dp) + spacing (32dp)
                        bottom = 12.dp // Same bottom position as SOS button
                    )
            )
        }

        // Location status message (adjusted position to avoid overlapping with NavigationPanel)
        if (!isInNavigationMode) { // FIX 1: Only show when not in navigation mode
            currentLocationState.value?.let { message ->
                val topPadding = when {
                    navigationState.isNavigating -> 180.dp // Navigation panel is showing
                    routeInfo != null || isLoadingRoute -> 160.dp // Route info panel is showing
                    initialDestination == null -> 80.dp // Only top bar
                    else -> 16.dp // No top bar
                }

                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topPadding)
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Destination info card (only show if no route info and not navigating and not in nav mode)
        if (destination != null && destinationName != null && !navigationState.isNavigating &&
            routeInfo == null && !isLoadingRoute && !isInNavigationMode) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        top = if (initialDestination == null) 80.dp else 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Destination Selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = destinationName!!,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                resetNavigationState() // FIX 2: Use complete reset function
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        // SOS Button - Keep at bottom-right always
        ModernSOSButton(
            isPressed = sosPressed,
            onSOSClick = {
                sosPressed = true
                isSOSOverlayVisible = true

                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val userId = auth.currentUser?.uid ?: "guestUser"

                        firestore.collection("Users").document(userId).get()
                            .addOnSuccessListener { doc ->
                                val userName = doc.getString("name") ?: "Unknown User"

                                val sosData = hashMapOf(
                                    "audioUrl" to "",
                                    "location" to GeoPoint(location.latitude, location.longitude),
                                    "name" to userName,
                                    "status" to "Pending",
                                    "timestamp" to Timestamp.now(),
                                    "type" to "SOS",
                                    "userId" to userId
                                )

                                firestore.collection("SOS")
                                    .add(sosData)
                                    .addOnSuccessListener { docRef ->
                                        currentSosDocId = docRef.id
                                        currentLocationState.value = "🚨 SOS Sent Successfully!"
                                    }
                                    .addOnFailureListener { e ->
                                        currentLocationState.value = "⚠️ Failed to send SOS: ${e.message}"
                                    }
                            }
                    } else {
                        currentLocationState.value = "⚠️ Could not fetch location."
                    }
                }
            },
            scale = sosScale.value,
            modifier = Modifier
                .align(Alignment.BottomEnd) // Always bottom-right
                .padding(end = 60.dp, bottom = 18.dp) // Same padding always
        )

        // SOS Overlay with recording
        SOSFullScreenOverlay(
            isVisible = isSOSOverlayVisible,
            onDismiss = {
                isSOSOverlayVisible = false
                sosPressed = false
                if (isRecording) isRecording = false
            },
            location = currentLocationState.value ?: "Unknown",
            onLocationUpdate = { currentLocationState.value = "Updating location..." },
            onStartRecording = {
                if (micPermissionGranted.value) {
                    try {
                        val file = File(context.cacheDir, "${UUID.randomUUID()}.mp3")
                        audioFile = file
                        mediaRecorder = MediaRecorder().apply {
                            setAudioSource(MediaRecorder.AudioSource.MIC)
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setOutputFile(file.absolutePath)
                            prepare()
                            start()
                        }
                        isRecording = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        currentLocationState.value = "⚠ Failed to start recording: ${e.message}"
                    }
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                // Auto stop after 30s
                scope.launch {
                    delay(30_000)
                    if (isRecording) {
                        isRecording = false
                        mediaRecorder?.stop()
                        mediaRecorder?.release()
                        mediaRecorder = null

                        audioFile?.let { f ->
                            val url = uploadAudioToSupabase(f)
                            if (url != null && currentSosDocId != null) {
                                firestore.collection("SOS")
                                    .document(currentSosDocId!!)
                                    .update("audioUrl", url)
                            }
                        }
                    }
                }
            },
            onStopRecording = {
                if (isRecording) {
                    isRecording = false
                    mediaRecorder?.stop()
                    mediaRecorder?.release()
                    mediaRecorder = null

                    audioFile?.let { f ->
                        scope.launch {
                            val url = uploadAudioToSupabase(f)
                            if (url != null && currentSosDocId != null) {
                                firestore.collection("SOS")
                                    .document(currentSosDocId!!)
                                    .update("audioUrl", url)
                            }
                        }
                    }
                }
            },
            isRecording = isRecording
        )
    }
}

// FIX 3: Cache management functions
private fun saveNavigationState(
    sharedPrefs: SharedPreferences,
    gson: Gson,
    destination: LatLng,
    destinationName: String,
    routeInfo: RouteInfo?
) {
    with(sharedPrefs.edit()) {
        putString("cached_destination_lat", destination.latitude.toString())
        putString("cached_destination_lng", destination.longitude.toString())
        putString("cached_destination_name", destinationName)
        routeInfo?.let {
            putString("cached_route_info", gson.toJson(it))
        }
        putLong("cached_timestamp", System.currentTimeMillis())
        apply()
    }
}

private fun clearNavigationCache(sharedPrefs: SharedPreferences) {
    with(sharedPrefs.edit()) {
        remove("cached_destination_lat")
        remove("cached_destination_lng")
        remove("cached_destination_name")
        remove("cached_route_info")
        remove("cached_timestamp")
        apply()
    }
}

private fun loadNavigationState(
    sharedPrefs: SharedPreferences,
    gson: Gson
): Triple<LatLng?, String?, RouteInfo?> {
    val latStr = sharedPrefs.getString("cached_destination_lat", null)
    val lngStr = sharedPrefs.getString("cached_destination_lng", null)
    val name = sharedPrefs.getString("cached_destination_name", null)
    val routeInfoJson = sharedPrefs.getString("cached_route_info", null)
    val timestamp = sharedPrefs.getLong("cached_timestamp", 0)

    // Clear cache if older than 1 hour
    if (System.currentTimeMillis() - timestamp > 3600000) {
        clearNavigationCache(sharedPrefs)
        return Triple(null, null, null)
    }

    val destination = if (latStr != null && lngStr != null) {
        try {
            LatLng(latStr.toDouble(), lngStr.toDouble())
        } catch (e: Exception) {
            null
        }
    } else null

    val routeInfo = if (routeInfoJson != null) {
        try {
            gson.fromJson(routeInfoJson, RouteInfo::class.java)
        } catch (e: Exception) {
            null
        }
    } else null

    return Triple(destination, name, routeInfo)
}