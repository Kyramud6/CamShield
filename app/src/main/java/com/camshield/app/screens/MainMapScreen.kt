// Fixed MainMapScreen.kt - Add missing parameters
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
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainMapScreen(
    onMenuClick: () -> Unit,
    initialDestination: LatLng? = null,  // Add this parameter
    initialDestinationName: String? = null,  // Add this parameter
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

    // SOS button state
    var sosPressed by remember { mutableStateOf(false) }
    val sosScale = remember { Animatable(1f) }
    var isSOSOverlayVisible by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }

    // Navigation state - Initialize with passed destination
    var destination by remember { mutableStateOf(initialDestination) }
    var destinationName by remember { mutableStateOf(initialDestinationName) }
    var navigationState by remember { mutableStateOf(NavigationState()) }

    // Update destination when initial values change
    LaunchedEffect(initialDestination, initialDestinationName) {
        destination = initialDestination
        destinationName = initialDestinationName
    }

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

    Box(modifier = modifier.fillMaxSize()) {
        // Always show the map, let GoogleMapScreen handle permission checks
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with search - only show if onMenuClick is provided (not showing from MainApp's topbar)
            // This prevents duplicate top bars
            if (initialDestination == null && initialDestinationName == null) {
                ModernTopBar(
                    context = context,
                    onMenuClick = onMenuClick,
                    onPlaceSelected = { latLng, placeName ->
                        destination = latLng
                        destinationName = placeName
                        // Reset navigation when new destination is selected
                        navigationState = NavigationState()

                        // Show destination selected message
                        currentLocationState.value = "Destination set: $placeName"
                    }
                )
            }

            // Navigation Panel
            NavigationPanel(
                navigationState = navigationState,
                modifier = Modifier.zIndex(1f)
            )

            // Google Map - pass permission status to avoid duplicate handling
            Box(modifier = Modifier.weight(1f)) {
                GoogleMapScreen(
                    context = context,
                    destinationLatLng = destination,
                    currentLocationState = currentLocationState,
                    locationPermissionGranted = locationPermissionGranted, // Pass permission status
                    onNavigationUpdate = { newState ->
                        navigationState = newState
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Permission required overlay (only show if no permission)
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

                // Navigation Controls
                if (destination != null && !navigationState.isNavigating && locationPermissionGranted) {
                    NavigationControls(
                        isNavigating = navigationState.isNavigating,
                        onStartNavigation = {
                            // Navigation will be started by GoogleMapScreen
                        },
                        onStopNavigation = {
                            navigationState = NavigationState()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 140.dp) // Space for SOS button
                    )
                }
            }
        }

        // Location status message
        currentLocationState.value?.let { message ->
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (navigationState.isNavigating) 120.dp else 80.dp)
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

        // Destination info card
        if (destination != null && destinationName != null && !navigationState.isNavigating) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (initialDestination == null) 80.dp else 16.dp, start = 16.dp, end = 16.dp),
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
                                destination = null
                                destinationName = null
                                navigationState = NavigationState()
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        // SOS Button
        ModernSOSButton(
            isPressed = sosPressed,
            onSOSClick = {
                sosPressed = true
                isSOSOverlayVisible = true
            },
            scale = sosScale.value,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 80.dp)
        )

        // SOS Overlay
        SOSFullScreenOverlay(
            isVisible = isSOSOverlayVisible,
            onDismiss = {
                isSOSOverlayVisible = false
                sosPressed = false
                if (isRecording) isRecording = false
            },
            location = currentLocationState.value ?: "Unknown",
            onLocationUpdate = { currentLocationState.value = "Updating location..." },
            onStartRecording = { isRecording = true },
            onStopRecording = { isRecording = false },
            isRecording = isRecording
        )
    }
}