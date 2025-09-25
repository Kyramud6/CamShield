// Simplified NotificationActionReceiver.kt - Direct monitoring approach
package com.camshield.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.camshield.app.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "🚨🚨🚨 BROADCAST RECEIVER TRIGGERED! 🚨🚨🚨")
        Log.d(TAG, "Intent received: $intent")
        Log.d(TAG, "Intent action: ${intent.action}")
        Log.d(TAG, "Intent component: ${intent.component}")
        Log.d(TAG, "Intent package: ${intent.`package`}")
        Log.d(TAG, "Context: $context")

        // Show toast immediately to confirm receiver is working
        Toast.makeText(context, "🚨 RECEIVER TRIGGERED: ${intent.action}", Toast.LENGTH_LONG).show()

        val action = intent.action
        val notificationId = intent.getStringExtra("notification_id")
        val sosRequestId = intent.getStringExtra("sos_request_id")
        val senderName = intent.getStringExtra("sender_name")

        Log.d(TAG, "=== EXTRACTED DATA ===")
        Log.d(TAG, "Action: $action")
        Log.d(TAG, "Notification ID: $notificationId")
        Log.d(TAG, "SOS Request ID: $sosRequestId")
        Log.d(TAG, "Sender Name: $senderName")
        Log.d(TAG, "Context: $context")

        if (notificationId == null || sosRequestId == null) {
            Log.e(TAG, "Missing required data")
            return
        }

        // Cancel notification
        cancelNotificationSafely(context)

        when (action) {
            "ACCEPT_WALK_REQUEST" -> {
                Log.d(TAG, "ACCEPT - Starting monitoring for $senderName")

                // Immediately launch JourneyMonitoringScreen for User B
                val monitoringIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("action", "monitor_journey")
                    putExtra("sos_request_id", sosRequestId)
                    putExtra("sender_name", senderName)
                }

                try {
                    context.startActivity(monitoringIntent)
                    Toast.makeText(context, "Monitoring $senderName's journey", Toast.LENGTH_LONG).show()

                    // Handle the database updates in background
                    CoroutineScope(Dispatchers.IO).launch {
                        handleAcceptance(notificationId, sosRequestId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting monitoring", e)
                }
            }

            "DECLINE_WALK_REQUEST" -> {
                Toast.makeText(context, "Walk request declined", Toast.LENGTH_SHORT).show()

                CoroutineScope(Dispatchers.IO).launch {
                    handleDecline(notificationId, sosRequestId)
                }
            }
        }
    }

    private suspend fun handleAcceptance(notificationId: String, sosRequestId: String) {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val currentUser = auth.currentUser ?: return

        try {
            // Get responder info
            val userDoc = firestore.collection("Users").document(currentUser.uid).get().await()
            val responderName = userDoc.getString("name") ?: "User"

            // Update notification
            firestore.collection("Notifications")
                .document(notificationId)
                .update(mapOf(
                    "status" to "accepted",
                    "responderName" to responderName,
                    "responderId" to currentUser.uid,
                    "respondedAt" to FieldValue.serverTimestamp()
                ))
                .await()

            // Update SOS request - mark as accepted and enable background location sharing
            firestore.collection("SOS")
                .document(sosRequestId)
                .update(mapOf(
                    "status" to "Accepted",
                    "responderId" to currentUser.uid,
                    "responderName" to responderName,
                    "respondedAt" to FieldValue.serverTimestamp(),
                    "monitoringActive" to true,
                    "backgroundLocationEnabled" to true  // Enable background location sharing
                ))
                .await()

            // Send simple notification to User A (original requester)
            val sosDoc = firestore.collection("SOS").document(sosRequestId).get().await()
            val requesterId = sosDoc.getString("userId")

            if (requesterId != null) {
                firestore.collection("Notifications")
                    .add(mapOf(
                        "recipientUserId" to requesterId,
                        "type" to "monitoring_started",
                        "title" to "Someone is monitoring your journey!",
                        "body" to "$responderName accepted your request and is now monitoring your location.",
                        "responderName" to responderName,
                        "sosRequestId" to sosRequestId,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "status" to "pending"
                    ))
                    .await()
            }

            Log.d(TAG, "Acceptance handled successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error handling acceptance", e)
        }
    }

    private suspend fun handleDecline(notificationId: String, sosRequestId: String) {
        val firestore = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        try {
            // Update notification
            firestore.collection("Notifications")
                .document(notificationId)
                .update(mapOf(
                    "status" to "declined",
                    "responderId" to currentUser.uid,
                    "respondedAt" to FieldValue.serverTimestamp()
                ))
                .await()

            // Update SOS request
            firestore.collection("SOS")
                .document(sosRequestId)
                .update(mapOf(
                    "status" to "Declined",
                    "respondedAt" to FieldValue.serverTimestamp()
                ))
                .await()

            Log.d(TAG, "Decline handled successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error handling decline", e)
        }
    }

    private fun cancelNotificationSafely(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(context).cancel(1001)
                }
            } else {
                NotificationManagerCompat.from(context).cancel(1001)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notification", e)
        }
    }
}