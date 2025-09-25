package com.camshield.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.camshield.app.MainActivity
import com.camshield.app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NotificationListener {
    companion object {
        private const val TAG = "NotificationListener"
        private const val CHANNEL_ID = "urgent_notifications"
        private var notificationListener: ListenerRegistration? = null
        private var processedNotifications = mutableSetOf<String>()

        // Callback for in-app notifications
        var onWalkRequestReceived: ((NotificationData) -> Unit)? = null

        fun startListening(context: Context) {
            val auth = FirebaseAuth.getInstance()
            val firestore = FirebaseFirestore.getInstance()
            val currentUser = auth.currentUser

            if (currentUser == null) {
                Log.w(TAG, "No authenticated user, cannot start notification listener")
                return
            }

            Log.d(TAG, "Starting notification listener for user: ${currentUser.uid}")
            Log.d(TAG, "User email: ${currentUser.email}")

            // Stop any existing listener
            stopListening()
            processedNotifications.clear()

            // Listen for notifications for this user
            notificationListener = firestore.collection("Notifications")
                .whereEqualTo("recipientUserId", currentUser.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.e(TAG, "Firestore listener failed", e)
                        return@addSnapshotListener
                    }

                    if (snapshots != null && !snapshots.isEmpty) {
                        Log.d(TAG, "Received ${snapshots.documents.size} notification(s) for ${currentUser.uid}")

                        for (doc in snapshots.documents) {
                            val notificationId = doc.id
                            val status = doc.getString("status") ?: "pending"
                            val notificationType = doc.getString("type")
                            val senderUserId = doc.getString("senderUserId")
                            val recipientUserId = doc.getString("recipientUserId")

                            Log.d(TAG, "Processing: $notificationId")
                            Log.d(TAG, "Type: $notificationType, Status: $status")
                            Log.d(TAG, "From: $senderUserId → To: $recipientUserId")

                            // Only process notifications for current user
                            if (recipientUserId != currentUser.uid) {
                                Log.d(TAG, "Skipping - not for current user")
                                continue
                            }

                            // Skip self-sent notifications
                            if (senderUserId == currentUser.uid) {
                                Log.d(TAG, "Skipping self-sent notification")
                                continue
                            }

                            // Skip already processed
                            if (processedNotifications.contains(notificationId)) {
                                Log.d(TAG, "Already processed: $notificationId")
                                continue
                            }

                            // Only process pending notifications
                            if (status == "pending") {
                                when (notificationType) {
                                    "walk_with_me_request" -> {
                                        Log.d(TAG, "TRIGGERING IN-APP WALK REQUEST DIALOG!")

                                        val senderName = doc.getString("senderName") ?: "Someone"
                                        val location = doc.getString("location") ?: "Unknown location"
                                        val sosRequestId = doc.getString("sosRequestId") ?: ""

                                        // Create notification data for in-app dialog
                                        val notificationData = NotificationData(
                                            notificationId = notificationId,
                                            sosRequestId = sosRequestId,
                                            senderName = senderName,
                                            location = location,
                                            message = "$senderName wants you to monitor their journey at $location"
                                        )

                                        // Trigger in-app dialog instead of system notification
                                        onWalkRequestReceived?.invoke(notificationData)
                                        processedNotifications.add(notificationId)
                                    }

                                    "walk_with_me_response" -> {
                                        Log.d(TAG, "Showing walk response notification")
                                        showSimpleInAppNotification(context, doc.data ?: emptyMap())
                                        processedNotifications.add(notificationId)
                                    }

                                    "monitoring_started" -> {
                                        Log.d(TAG, "Monitoring started notification received")
                                        val responderName = doc.getString("responderName") ?: "Someone"
                                        showSimpleInAppNotification(
                                            context,
                                            mapOf(
                                                "title" to "Journey Monitoring Started",
                                                "message" to "$responderName is now monitoring your location for safety"
                                            )
                                        )
                                        markNotificationAsHandled(notificationId)
                                        processedNotifications.add(notificationId)
                                    }
                                }
                            } else {
                                Log.d(TAG, "Skipping non-pending notification ($status)")
                            }
                        }
                    } else {
                        Log.d(TAG, "No notifications found for user: ${currentUser.uid}")
                    }
                }

            Log.d(TAG, "Firestore listener attached successfully!")
        }

        fun stopListening() {
            notificationListener?.remove()
            notificationListener = null
            processedNotifications.clear()
            onWalkRequestReceived = null
            Log.d(TAG, "Notification listener stopped")
        }

        // For simple notifications that don't require user action
        private fun showSimpleInAppNotification(context: Context, notificationData: Map<String, Any>) {
            // You can implement a simple toast or snackbar here
            // Or trigger another callback for simple notifications
            val title = notificationData["title"] as? String ?: "Notification"
            val message = notificationData["message"] as? String ?: "New notification"

            Log.d(TAG, "Simple notification: $title - $message")
            // You could show a toast here or use another callback
        }

        private fun markNotificationAsHandled(notificationId: String) {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("Notifications")
                .document(notificationId)
                .update("status", "handled")
                .addOnSuccessListener {
                    Log.d(TAG, "Notification marked as handled: $notificationId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error marking notification as handled", e)
                }
        }

        // Helper function to handle acceptance
        fun handleAcceptanceFromMainActivity(context: Context, notificationId: String, sosRequestId: String) {
            CoroutineScope(Dispatchers.IO).launch {
                val auth = FirebaseAuth.getInstance()
                val firestore = FirebaseFirestore.getInstance()
                val currentUser = auth.currentUser ?: return@launch

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

                    // Update SOS request
                    firestore.collection("SOS")
                        .document(sosRequestId)
                        .update(mapOf(
                            "status" to "Accepted",
                            "responderId" to currentUser.uid,
                            "responderName" to responderName,
                            "respondedAt" to FieldValue.serverTimestamp(),
                            "monitoringActive" to true,
                            "backgroundLocationEnabled" to true
                        ))
                        .await()

                    // Create monitoring_started notification for User A
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

                    Log.d(TAG, "Acceptance handled successfully in MainActivity")

                } catch (e: Exception) {
                    Log.e(TAG, "Error handling acceptance in MainActivity", e)
                }
            }
        }

        // Helper function to handle decline
        fun handleDeclineFromMainActivity(context: Context, notificationId: String, sosRequestId: String) {
            CoroutineScope(Dispatchers.IO).launch {
                val auth = FirebaseAuth.getInstance()
                val firestore = FirebaseFirestore.getInstance()
                val currentUser = auth.currentUser ?: return@launch

                try {
                    // Get responder info
                    val userDoc = firestore.collection("Users").document(currentUser.uid).get().await()
                    val responderName = userDoc.getString("name") ?: "User"

                    // Update notification as declined
                    firestore.collection("Notifications")
                        .document(notificationId)
                        .update(mapOf(
                            "status" to "declined",
                            "responderName" to responderName,
                            "responderId" to currentUser.uid,
                            "respondedAt" to FieldValue.serverTimestamp()
                        ))
                        .await()

                    // Update SOS request as declined
                    firestore.collection("SOS")
                        .document(sosRequestId)
                        .update(mapOf(
                            "status" to "Declined",
                            "responderId" to currentUser.uid,
                            "responderName" to responderName,
                            "respondedAt" to FieldValue.serverTimestamp()
                        ))
                        .await()

                    // Create decline notification for User A
                    val sosDoc = firestore.collection("SOS").document(sosRequestId).get().await()
                    val requesterId = sosDoc.getString("userId")

                    if (requesterId != null) {
                        firestore.collection("Notifications")
                            .add(mapOf(
                                "recipientUserId" to requesterId,
                                "type" to "walk_with_me_response",
                                "title" to "Request Declined",
                                "body" to "$responderName declined your walk request.",
                                "responderName" to responderName,
                                "response" to "declined",
                                "sosRequestId" to sosRequestId,
                                "timestamp" to FieldValue.serverTimestamp(),
                                "status" to "pending"
                            ))
                            .await()
                    }

                    Log.d(TAG, "Decline handled successfully in MainActivity")

                } catch (e: Exception) {
                    Log.e(TAG, "Error handling decline in MainActivity", e)
                }
            }
        }
    }
}

// Data class for in-app notifications
data class NotificationData(
    val notificationId: String,
    val sosRequestId: String,
    val senderName: String,
    val location: String,
    val message: String
)