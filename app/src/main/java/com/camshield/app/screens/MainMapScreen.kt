// MainMapScreen.kt with Walk With Me Pop-up Notifications
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import android.media.MediaRecorder
import com.camshield.app.App
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

val firestore = FirebaseFirestore.getInstance()
val auth = FirebaseAuth.getInstance()

// Data model for Walk With Me notifications
data class WalkWithMeNotification(
    val id: String = "",
    val senderName: String = "",
    val senderUserId: String = "",
    val location: String = "",
    val sosRequestId: String = "",
    val timestamp: com.google.firebase.Timestamp? = null,
    val status: String = "pending"
)

@Composable
fun MainMapScreen(
    onMenuClick: () -> Unit,
    initialDestination: LatLng? = null,
    initialDestinationName: String? = null,
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

    // Navigation state - Initialize with passed destination
    var destination by remember { mutableStateOf(initialDestination) }
    var destinationName by remember { mutableStateOf(initialDestinationName) }
    var navigationState by remember { mutableStateOf(NavigationState()) }
    var routeInfo: RouteInfo? by remember { mutableStateOf(null) }

    // Track loading state for route
    var isLoadingRoute by remember { mutableStateOf(false) }

    // Add navigation trigger state
    var shouldStartNavigation by remember { mutableStateOf(false) }

    // NEW: Walk With Me notification states
    var walkWithMeNotifications by remember { mutableStateOf<List<WalkWithMeNotification>>(emptyList()) }
    var currentNotification by remember { mutableStateOf<WalkWithMeNotification?>(null) }
    var listenerRegistration by remember { mutableStateOf<ListenerRegistration?>(null) }
    val scope = rememberCoroutineScope()

    // NEW: Listen for Walk With Me notifications
    LaunchedEffect(auth.currentUser?.uid) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            // Get and save FCM token for push notifications
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                val token = task.result
                Log.d("FCM", "FCM Registration Token: $token")

                // Save token to Firestore
                firestore.collection("Users")
                    .document(currentUserId)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        Log.d("FCM", "FCM token saved successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e("FCM", "Failed to save FCM token", e)
                    }
            }

            listenerRegistration = firestore.collection("Notifications")
                .whereEqualTo("recipientUserId", currentUserId)
                .whereEqualTo("type", "walk_with_me_request")
                .whereEqualTo("status", "pending")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("WalkWithMe", "Error listening for notifications", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val notifications = snapshot.documents.mapNotNull { doc ->
                            try {
                                WalkWithMeNotification(
                                    id = doc.id,
                                    senderName = doc.getString("senderName") ?: "",
                                    senderUserId = doc.getString("senderUserId") ?: "",
                                    location = doc.getString("location") ?: "",
                                    sosRequestId = doc.getString("sosRequestId") ?: "",
                                    timestamp = doc.getTimestamp("timestamp"),
                                    status = doc.getString("status") ?: "pending"
                                )
                            } catch (e: Exception) {
                                Log.e("WalkWithMe", "Error parsing notification", e)
                                null
                            }
                        }

                        walkWithMeNotifications = notifications

                        // Show the most recent notification as popup
                        if (notifications.isNotEmpty() && currentNotification == null) {
                            currentNotification = notifications.first()
                        }

                        Log.d("WalkWithMe", "Updated notifications: ${notifications.size} pending requests")
                    }
                }
        }
    }

    // Clean up listener
    DisposableEffect(Unit) {
        onDispose {
            listenerRegistration?.remove()
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

    Box(modifier = modifier.fillMaxSize()) {
        // Always show the map, let GoogleMapScreen handle permission checks
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with search - only show if onMenuClick is provided
            if (initialDestination == null && initialDestinationName == null) {
                ModernTopBar(
                    context = context,
                    onMenuClick = onMenuClick,
                    onPlaceSelected = { latLng, placeName ->
                        destination = latLng
                        destinationName = placeName
                        // Reset navigation when new destination is selected
                        navigationState = NavigationState()
                        shouldStartNavigation = false
                        routeInfo = null

                        // Show destination selected message
                        currentLocationState.value = "Destination set: $placeName"
                    }
                )
            }

            // UPDATED: NavigationPanel with route info and loading state
            NavigationPanel(
                navigationState = navigationState,
                routeInfo = routeInfo,
                destinationName = destinationName,
                isLoadingRoute = isLoadingRoute,
                modifier = Modifier.zIndex(1f)
            )

            // Google Map - Pass route info callback to update panel
            Box(modifier = Modifier.weight(1f)) {
                GoogleMapScreen(
                    context = context,
                    destinationLatLng = destination,
                    currentLocationState = currentLocationState,
                    locationPermissionGranted = locationPermissionGranted,
                    onNavigationUpdate = { newState ->
                        navigationState = newState
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
                    },
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
            }
        }

        // UPDATED: Bottom Navigation Controls (simplified - route info moved to top panel)
        BottomNavigationControls(
            isNavigating = navigationState.isNavigating,
            hasDestination = destination != null,
            hasLocation = locationPermissionGranted,
            isLoadingRoute = isLoadingRoute,
            onStartNavigation = {
                shouldStartNavigation = true
            },
            onStopNavigation = {
                navigationState = NavigationState()
                shouldStartNavigation = false
                routeInfo = null
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )

        // Location status message (adjusted position to avoid overlapping with NavigationPanel)
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

        // Destination info card (only show if no route info and not navigating)
        if (destination != null && destinationName != null && !navigationState.isNavigating && routeInfo == null && !isLoadingRoute) {
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
                                destination = null
                                destinationName = null
                                navigationState = NavigationState()
                                shouldStartNavigation = false
                                routeInfo = null
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        // NEW: Walk With Me Notification Pop-up
        currentNotification?.let { notification ->
            WalkWithMeNotificationPopup(
                notification = notification,
                onAccept = {
                    scope.launch {
                        handleWalkWithMeResponse(
                            firestore = firestore,
                            notificationId = notification.id,
                            sosRequestId = notification.sosRequestId,
                            response = "accepted",
                            context = context
                        )
                        currentNotification = null
                    }
                },
                onDecline = {
                    scope.launch {
                        handleWalkWithMeResponse(
                            firestore = firestore,
                            notificationId = notification.id,
                            sosRequestId = notification.sosRequestId,
                            response = "declined",
                            context = context
                        )
                        currentNotification = null
                    }
                },
                onDismiss = {
                    currentNotification = null
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
                    .zIndex(10f)
            )
        }

        // SOS Button
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
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 200.dp)
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
                        currentLocationState.value = "⚠️ Failed to start recording: ${e.message}"
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

// NEW: Walk With Me Pop-up Component
@Composable
fun WalkWithMeNotificationPopup(
    notification: WalkWithMeNotification,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Urgent header with red indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Red, shape = androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "URGENT REQUEST",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main icon
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = "Walk With Me Request",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Message
            Text(
                text = "${notification.senderName} needs someone to walk with them",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Location info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = notification.location,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Accept", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Decline", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// Handle Accept/Decline responses (same as before)
suspend fun handleWalkWithMeResponse(
    firestore: FirebaseFirestore,
    notificationId: String,
    sosRequestId: String,
    response: String,
    context: Context
) {
    try {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        // Get current user's info
        val userDoc = firestore.collection("Users")
            .document(currentUser.uid)
            .get()
            .await()

        val responderName = userDoc.getString("name") ?: "User"

        // Update the notification status
        firestore.collection("Notifications")
            .document(notificationId)
            .update("status", response, "responderName", responderName)
            .await()

        // Update the SOS request with the response
        val updateData = mutableMapOf<String, Any>(
            "status" to if (response == "accepted") "Accepted" else "Declined",
            "respondedAt" to FieldValue.serverTimestamp()
        )

        if (response == "accepted") {
            updateData["responderId"] = currentUser.uid
            updateData["responderName"] = responderName
            updateData["responderEmail"] = currentUser.email ?: ""
        }

        firestore.collection("SOS")
            .document(sosRequestId)
            .update(updateData)
            .await()

        val message = if (response == "accepted") {
            "Walk request accepted! You can now contact them."
        } else {
            "Walk request declined."
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        Log.d("WalkWithMe", "Response handled: $response by $responderName")

    } catch (e: Exception) {
        Log.e("WalkWithMe", "Error handling response", e)
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}