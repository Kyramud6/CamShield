package com.camshield.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.camshield.app.screens.*
import com.camshield.app.services.NotificationData
import com.camshield.app.ui.theme.CamSHIELDAppTheme
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.camshield.app.services.NotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Use a simple var that we can pass to trigger recomposition
    private var intentTriggerValue = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        Log.d("MainActivity", "=== MAIN ACTIVITY CREATED ===")
        Log.d("MainActivity", "Initial intent: $intent")
        logIntentDetails(intent, "onCreate")

        // Start notification listener when user is authenticated
        val auth = FirebaseAuth.getInstance()
        auth.addAuthStateListener { firebaseAuth ->
            Log.d("MainActivity", "Auth state changed. User: ${firebaseAuth.currentUser?.uid}")
            if (firebaseAuth.currentUser != null) {
                Log.d("MainActivity", "User signed in, starting notification listener")
                NotificationListener.startListening(this)
            } else {
                Log.d("MainActivity", "User signed out, stopping notification listener")
                NotificationListener.stopListening()
            }
        }

        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, "AIzaSyCPmhj7Hot40l9AMeJq8dKPJ-7UHq5-S3E")
        }

        setContent {
            CamSHIELDAppTheme {
                AppNavigation(intent = intent, intentTrigger = intentTriggerValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "=== NEW INTENT RECEIVED ===")
        Log.d("MainActivity", "Intent action: ${intent.action}")
        logIntentDetails(intent, "onNewIntent")

        setIntent(intent)

        // Trigger recomposition
        intentTriggerValue += 1
        Log.d("MainActivity", "Intent trigger updated to: $intentTriggerValue")

        setContent {
            CamSHIELDAppTheme {
                AppNavigation(intent = intent, intentTrigger = intentTriggerValue)
            }
        }
    }

    private fun logIntentDetails(intent: Intent?, source: String) {
        Log.d("MainActivity", "=== INTENT DETAILS ($source) ===")
        if (intent != null) {
            Log.d("MainActivity", "Intent: $intent")
            Log.d("MainActivity", "Action: ${intent.action}")
            Log.d("MainActivity", "Data: ${intent.data}")
            Log.d("MainActivity", "Categories: ${intent.categories}")

            val extras = intent.extras
            if (extras != null) {
                Log.d("MainActivity", "=== EXTRAS ===")
                for (key in extras.keySet()) {
                    val value = extras.get(key)
                    Log.d("MainActivity", "  $key = $value (${value?.javaClass?.simpleName})")
                }
            } else {
                Log.d("MainActivity", "No extras found")
            }
        } else {
            Log.d("MainActivity", "Intent is null")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "=== ON RESUME ===")
        logIntentDetails(intent, "onResume")

        // FORCE restart listener when app comes to foreground
        if (FirebaseAuth.getInstance().currentUser != null) {
            Log.d("MainActivity", "Restarting listener on resume")
            NotificationListener.startListening(this)
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("MainActivity", "=== ON START ===")
        // Also restart on start
        if (FirebaseAuth.getInstance().currentUser != null) {
            Log.d("MainActivity", "Restarting listener on start")
            NotificationListener.startListening(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop listener when app is destroyed
        NotificationListener.stopListening()
    }
}

@Composable
fun AppNavigation(intent: Intent?, intentTrigger: Int) {
    var showSplash by remember { mutableStateOf(true) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var showLocationTracking by remember { mutableStateOf(false) }
    var showJourneyMonitoring by remember { mutableStateOf(false) }
    var trackingData by remember { mutableStateOf<TrackingData?>(null) }
    var monitoringData by remember { mutableStateOf<MonitoringData?>(null) }

    // State for in-app walk request dialog
    var walkRequestData by remember { mutableStateOf<NotificationData?>(null) }
    var showWalkRequestDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Set up notification callback for in-app dialogs
    LaunchedEffect(Unit) {
        NotificationListener.onWalkRequestReceived = { notificationData ->
            Log.d("AppNavigation", "Walk request received: ${notificationData.senderName}")
            walkRequestData = notificationData
            showWalkRequestDialog = true
        }
    }

    // Handle walk request dialog
    if (showWalkRequestDialog && walkRequestData != null) {
        WalkRequestDialog(
            notificationData = walkRequestData!!,
            onAccept = {
                Log.d("AppNavigation", "User accepted walk request")

                // Handle database updates in background
                scope.launch {
                    try {
                        NotificationListener.handleAcceptanceFromMainActivity(
                            context = context,
                            notificationId = walkRequestData!!.notificationId,
                            sosRequestId = walkRequestData!!.sosRequestId
                        )
                        Log.d("AppNavigation", "Database updates completed for acceptance")
                    } catch (e: Exception) {
                        Log.e("AppNavigation", "Error with database updates", e)
                    }
                }

                // Show monitoring screen immediately
                monitoringData = MonitoringData(walkRequestData!!.sosRequestId, walkRequestData!!.senderName)
                showJourneyMonitoring = true
                showLocationTracking = false

                // Close dialog
                showWalkRequestDialog = false
                walkRequestData = null

                Log.d("AppNavigation", "Will show JourneyMonitoringScreen")
            },
            onDecline = {
                Log.d("AppNavigation", "User declined walk request")

                // Handle database updates in background
                scope.launch {
                    try {
                        NotificationListener.handleDeclineFromMainActivity(
                            context = context,
                            notificationId = walkRequestData!!.notificationId,
                            sosRequestId = walkRequestData!!.sosRequestId
                        )

                        // Show toast on main thread
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "Walk request declined", Toast.LENGTH_SHORT).show()
                        }

                        Log.d("AppNavigation", "Decline handled successfully")
                    } catch (e: Exception) {
                        Log.e("AppNavigation", "Error handling decline", e)
                    }
                }

                // Close dialog
                showWalkRequestDialog = false
                walkRequestData = null
            }
        )
    }

    // Handle legacy intents (for backward compatibility)
    LaunchedEffect(intent, intentTrigger) {
        Log.d("AppNavigation", "=== CHECKING INTENT (Trigger: $intentTrigger) ===")

        intent?.let { intentData ->
            val action = intentData.getStringExtra("action")
            val sosRequestId = intentData.getStringExtra("sos_request_id")
            val userName = intentData.getStringExtra("sender_name") ?: intentData.getStringExtra("user_name")

            when (action) {
                "start_location_tracking" -> {
                    if (sosRequestId != null && userName != null) {
                        trackingData = TrackingData(sosRequestId, userName)
                        showLocationTracking = true
                        showJourneyMonitoring = false
                        showSplash = false
                        isLoggedIn = true
                    }
                }

                "monitor_journey" -> {
                    if (sosRequestId != null && userName != null) {
                        monitoringData = MonitoringData(sosRequestId, userName)
                        showJourneyMonitoring = true
                        showLocationTracking = false
                        showSplash = false
                        isLoggedIn = true
                    }
                }
            }
        }
    }

    // Screen rendering logic
    when {
        showLocationTracking -> {
            trackingData?.let { data ->
                LocationTrackingScreen(
                    sosRequestId = data.sosRequestId,
                    userName = data.userName,
                    onEndTracking = {
                        showLocationTracking = false
                        trackingData = null
                    }
                )
            }
        }

        showJourneyMonitoring -> {
            Log.d("AppNavigation", "Displaying JourneyMonitoringScreen")
            monitoringData?.let { data ->
                JourneyMonitoringScreen(
                    sosRequestId = data.sosRequestId,
                    userName = data.userName,
                    onStopMonitoring = {
                        showJourneyMonitoring = false
                        monitoringData = null
                    }
                )
            }
        }

        showSplash -> {
            SplashScreen(onSplashFinished = {
                showSplash = false
                if (FirebaseAuth.getInstance().currentUser != null) {
                    isLoggedIn = true
                }
            })
        }

        !isLoggedIn -> {
            MinimalistLoginScreen(onLoginSuccess = { isLoggedIn = true })
        }

        else -> {
            MainApp()
        }
    }
}

@Composable
fun WalkRequestDialog(
    notificationData: NotificationData,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* Don't allow dismissal by clicking outside */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "Walk With Me Request",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sender name
                Text(
                    text = notificationData.senderName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Request message
                Text(
                    text = "wants you to monitor their journey",
                    fontSize = 16.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Location
                Text(
                    text = "Location: ${notificationData.location}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Decline button
                    Button(
                        onClick = onDecline,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE57373)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Decline",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Accept button
                    Button(
                        onClick = onAccept,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Accept",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

data class TrackingData(
    val sosRequestId: String,
    val userName: String
)

data class MonitoringData(
    val sosRequestId: String,
    val userName: String
)