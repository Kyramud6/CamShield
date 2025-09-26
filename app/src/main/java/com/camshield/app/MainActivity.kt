// Complete MainActivity.kt with Auto Cleanup (10 minutes)
package com.camshield.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.camshield.app.services.NotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class MainActivity : ComponentActivity() {

    private var currentIntent by mutableStateOf<Intent?>(null)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        Log.d("MainActivity", "=== MAIN ACTIVITY CREATED ===")
        currentIntent = intent

        // Auth state listener - but don't start notification listener here yet
        val auth = FirebaseAuth.getInstance()
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                Log.d("MainActivity", "User signed in")
                // Don't start notification listener here - will be started after splash
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
                AppNavigation(currentIntent = currentIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "=== NEW INTENT RECEIVED ===")
        setIntent(intent)
        currentIntent = intent
    }

    override fun onResume() {
        super.onResume()
        // Don't automatically start listener here - let AppNavigation control it
    }

    override fun onDestroy() {
        super.onDestroy()
        NotificationListener.stopListening()
    }
}

// Fixed MainActivity.kt - Complete Accept Flow with Auto Cleanup
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavigation(currentIntent: Intent?) {
    var showSplash by remember { mutableStateOf(true) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var showLocationTracking by remember { mutableStateOf(false) }
    var showJourneyMonitoring by remember { mutableStateOf(false) }
    var trackingData by remember { mutableStateOf<TrackingData?>(null) }
    var monitoringData by remember { mutableStateOf<MonitoringData?>(null) }

    // State for walk request dialog
    var walkRequestData by remember { mutableStateOf<NotificationData?>(null) }
    var showWalkRequestDialog by remember { mutableStateOf(false) }
    var currentDialogId by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Setup notification system when logged in + AUTO CLEANUP
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && FirebaseAuth.getInstance().currentUser != null) {
            Log.d("AppNavigation", "Setting up notifications...")

            NotificationListener.startListening(context)

            // 🧹 AUTO CLEANUP - Delete old notifications and SOS requests (10 minutes)
            val firestore = FirebaseFirestore.getInstance()
            val currentUser = FirebaseAuth.getInstance().currentUser!!
            val tenMinutesAgo = Timestamp(Date(System.currentTimeMillis() - 600000)) // 10 min = 600,000ms

            Log.d("Cleanup", "Starting auto cleanup for notifications older than 10 minutes...")

            // Clean up old pending notifications (walk_with_me_request type only)
            firestore.collection("Notifications")
                .whereEqualTo("recipientUserId", currentUser.uid)
                .whereEqualTo("status", "pending")
                .whereEqualTo("type", "walk_with_me_request")  // Fixed: correct type name
                .whereLessThan("timestamp", tenMinutesAgo)
                .get()
                .addOnSuccessListener { documents ->
                    for (doc in documents) {
                        doc.reference.delete()
                    }
                    Log.d("Cleanup", "✅ Deleted ${documents.size()} old walk_with_me_request notifications")
                }
                .addOnFailureListener { e ->
                    Log.e("Cleanup", "❌ Failed to clean notifications", e)
                }

            // Clean up old SOS requests (walk_with_me type only)
            firestore.collection("SOS")
                .whereEqualTo("userId", currentUser.uid)
                .whereEqualTo("status", "Pending")
                .whereEqualTo("type", "walk_with_me")
                .whereLessThan("timestamp", tenMinutesAgo)
                .get()
                .addOnSuccessListener { documents ->
                    for (doc in documents) {
                        doc.reference.delete()
                    }
                    Log.d("Cleanup", "✅ Deleted ${documents.size()} old SOS requests")
                }
                .addOnFailureListener { e ->
                    Log.e("Cleanup", "❌ Failed to clean SOS requests", e)
                }

            // Setup notification callback AFTER cleanup
            NotificationListener.onWalkRequestReceived = { notificationData ->
                Log.d("AppNavigation", "Walk request received: ${notificationData.senderName}")

                if (notificationData.notificationId != currentDialogId) {
                    walkRequestData = notificationData
                    showWalkRequestDialog = true
                    currentDialogId = notificationData.notificationId
                    Log.d("AppNavigation", "Showing dialog for new request")
                }
            }
        } else {
            NotificationListener.onWalkRequestReceived = null
        }
    }

    // Handle intents (existing logic)
    LaunchedEffect(currentIntent) {
        currentIntent?.let { intent ->
            val action = intent.getStringExtra("action")
            val sosRequestId = intent.getStringExtra("sos_request_id")
            val userName = intent.getStringExtra("sender_name") ?: intent.getStringExtra("user_name")
            val responderName = intent.getStringExtra("responder_name")

            when (action) {
                "start_location_tracking" -> {
                    if (sosRequestId != null && userName != null) {
                        trackingData = TrackingData(sosRequestId, userName, responderName)
                        showLocationTracking = true
                        showJourneyMonitoring = false
                        showSplash = false
                        isLoggedIn = true

                        // Clear dialog
                        showWalkRequestDialog = false
                        walkRequestData = null
                        currentDialogId = ""
                    }
                }

                "monitor_journey" -> {
                    if (sosRequestId != null && userName != null) {
                        monitoringData = MonitoringData(sosRequestId, userName)
                        showJourneyMonitoring = true
                        showLocationTracking = false
                        showSplash = false
                        isLoggedIn = true

                        // Clear dialog
                        showWalkRequestDialog = false
                        walkRequestData = null
                        currentDialogId = ""
                    }
                }
            }
        }
    }

    // Walk Request Dialog
    if (showWalkRequestDialog && walkRequestData != null && !showSplash && isLoggedIn) {
        WalkRequestDialog(
            notificationData = walkRequestData!!,
            onAccept = {
                val currentRequest = walkRequestData ?: return@WalkRequestDialog

                Log.d("Dialog", "ACCEPT clicked for ${currentRequest.senderName}")

                scope.launch {
                    try {
                        val auth = FirebaseAuth.getInstance()
                        val firestore = FirebaseFirestore.getInstance()
                        val currentUser = auth.currentUser!!

                        // Get responder info
                        val userDoc = firestore.collection("Users")
                            .document(currentUser.uid).get().await()
                        val responderName = userDoc.getString("name")
                            ?: currentUser.displayName ?: "User"

                        Log.d("Dialog", "Step 1: Got responder name: $responderName")

                        // STEP 2: Update SOS request to ACCEPTED
                        firestore.collection("SOS")
                            .document(currentRequest.sosRequestId)
                            .update(mapOf(
                                "status" to "Accepted",
                                "responderId" to currentUser.uid,
                                "responderName" to responderName,
                                "respondedAt" to FieldValue.serverTimestamp(),
                                "monitoringActive" to true,
                                "backgroundLocationEnabled" to true
                            )).await()

                        Log.d("Dialog", "Step 2: SOS updated to Accepted")

                        // STEP 3: Get requester info from SOS
                        val sosDoc = firestore.collection("SOS")
                            .document(currentRequest.sosRequestId).get().await()
                        val requesterId = sosDoc.getString("userId")!!
                        val requesterName = sosDoc.getString("name") ?: "User"

                        Log.d("Dialog", "Step 3: Requester: $requesterName ($requesterId)")

                        // STEP 4: Delete original walk_with_me_request notification
                        firestore.collection("Notifications")
                            .document(currentRequest.notificationId)
                            .delete().await()

                        Log.d("Dialog", "Step 4: Original notification deleted")

                        // STEP 5: Create monitoring_started notification for User A
                        val monitoringNotification = mapOf(
                            "recipientUserId" to requesterId,
                            "type" to "monitoring_started",
                            "title" to "Someone is monitoring your journey!",
                            "body" to "$responderName accepted your request and is now monitoring your location.",
                            "responderName" to responderName,
                            "sosRequestId" to currentRequest.sosRequestId,
                            "timestamp" to FieldValue.serverTimestamp(),
                            "status" to "pending"
                        )

                        firestore.collection("Notifications")
                            .add(monitoringNotification).await()

                        Log.d("Dialog", "Step 5: monitoring_started notification created")

                        // STEP 6: Start monitoring screen for User B
                        monitoringData = MonitoringData(
                            currentRequest.sosRequestId,
                            currentRequest.senderName
                        )
                        showJourneyMonitoring = true
                        showLocationTracking = false

                        // Reset dialog
                        showWalkRequestDialog = false
                        walkRequestData = null
                        currentDialogId = ""

                        Toast.makeText(context,
                            "Request accepted! Monitoring ${currentRequest.senderName}",
                            Toast.LENGTH_LONG).show()

                        Log.d("Dialog", "Step 6: Accept flow completed successfully!")

                    } catch (e: Exception) {
                        Log.e("Dialog", "Accept failed", e)
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },

            onDecline = {
                Log.d("Dialog", "DECLINE clicked")

                scope.launch {
                    try {
                        val currentRequest = walkRequestData
                        if (currentRequest != null) {
                            // Delete the notification (keeps current behavior)
                            FirebaseFirestore.getInstance()
                                .collection("Notifications")
                                .document(currentRequest.notificationId)
                                .delete().await()

                            Log.d("Dialog", "Notification deleted on decline")
                        }
                    } catch (e: Exception) {
                        Log.e("Dialog", "Decline error", e)
                    }
                }

                // Reset dialog
                showWalkRequestDialog = false
                walkRequestData = null
                currentDialogId = ""
            }
        )
    }

    // Screen rendering
    when {
        showLocationTracking -> {
            trackingData?.let { data ->
                LocationTrackingScreen(
                    sosRequestId = data.sosRequestId,
                    userName = data.userName,
                    responderName = data.responderName,
                    onEndTracking = {
                        showLocationTracking = false
                        trackingData = null
                        currentDialogId = ""
                    }
                )
            }
        }

        showJourneyMonitoring -> {
            monitoringData?.let { data ->
                JourneyMonitoringScreen(
                    sosRequestId = data.sosRequestId,
                    userName = data.userName,
                    onStopMonitoring = {
                        showJourneyMonitoring = false
                        monitoringData = null
                        currentDialogId = ""
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
        onDismissRequest = { /* Don't allow dismissal */ },
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
                Text(
                    text = "Walk With Me Request",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = notificationData.senderName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "wants you to monitor their journey",
                    fontSize = 16.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Location: ${notificationData.location}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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

// FIXED: TrackingData includes responder name
data class TrackingData(
    val sosRequestId: String,
    val userName: String,
    val responderName: String? = null
)

data class MonitoringData(
    val sosRequestId: String,
    val userName: String
)