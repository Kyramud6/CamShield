// Fixed & Stable NotificationListener.kt
package com.camshield.app.services

import android.content.Context
import android.content.Intent
import android.util.Log
import com.camshield.app.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class NotificationListener {
    companion object {
        private const val TAG = "NotificationListener"
        private var notificationListener: ListenerRegistration? = null
        private var isListenerActive = false

        // Callback for in-app notifications
        var onWalkRequestReceived: ((NotificationData) -> Unit)? = null

        fun startListening(context: Context) {
            val auth = FirebaseAuth.getInstance()
            val firestore = FirebaseFirestore.getInstance()
            val currentUser = auth.currentUser

            if (currentUser == null) {
                Log.w(TAG, "⚠️ No authenticated user, cannot start notification listener")
                return
            }

            Log.d(TAG, "🚀 Starting REAL-TIME notification listener for: ${currentUser.uid}")

            // Stop any existing listener before attaching new one
            stopListening()

            notificationListener = firestore.collection("Notifications")
                .whereEqualTo("recipientUserId", currentUser.uid)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.e(TAG, "❌ Listener failed", e)
                        isListenerActive = false
                        return@addSnapshotListener
                    }

                    isListenerActive = true
                    Log.d(TAG, "📡 Listener active, found ${snapshots?.documents?.size ?: 0} notifications")

                    snapshots?.documentChanges?.forEach { change ->
                        val doc = change.document
                        val notificationId = doc.id
                        val status = doc.getString("status") ?: "pending"
                        val type = doc.getString("type") ?: ""
                        val senderUserId = doc.getString("senderUserId") ?: ""

                        Log.d(TAG, "🔔 Change detected: $notificationId ($type, $status)")

                        // Skip notifications from self
                        if (senderUserId == currentUser.uid) {
                            Log.d(TAG, "   ⏭️ Ignoring self-notification")
                            return@forEach
                        }

                        when (change.type.name) {
                            "ADDED" -> {
                                when (type) {
                                    "walk_with_me_request" -> {
                                        if (status == "pending") {
                                            Log.d(TAG, "   🚨 New WALK REQUEST detected")

                                            val senderName = doc.getString("senderName") ?: "Someone"
                                            val location = doc.getString("location") ?: "Unknown"
                                            val sosRequestId = doc.getString("sosRequestId") ?: ""

                                            val notificationData = NotificationData(
                                                notificationId = notificationId,
                                                sosRequestId = sosRequestId,
                                                senderName = senderName,
                                                location = location,
                                                message = "$senderName wants you to monitor their journey"
                                            )

                                            Log.d(TAG, "   📱 Triggering callback for $senderName")
                                            onWalkRequestReceived?.invoke(notificationData)
                                        }
                                    }

                                    "monitoring_started" -> {
                                        if (status == "pending") {
                                            Log.d(TAG, "   🎯 MONITORING STARTED for current user")

                                            val responderName = doc.getString("responderName") ?: "Someone"
                                            val sosRequestId = doc.getString("sosRequestId") ?: ""

                                            if (sosRequestId.isNotEmpty()) {
                                                val trackingIntent = Intent(context, MainActivity::class.java).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    putExtra("action", "start_location_tracking")
                                                    putExtra("sos_request_id", sosRequestId)
                                                    putExtra("user_name", currentUser.displayName ?: "You")
                                                    putExtra("responder_name", responderName)
                                                }

                                                try {
                                                    context.startActivity(trackingIntent)
                                                    Log.d(TAG, "   ✅ Location tracking screen launched")
                                                } catch (ex: Exception) {
                                                    Log.e(TAG, "   ❌ Error starting tracking activity", ex)
                                                }
                                            }

                                            // Mark as handled AFTER action
                                            firestore.collection("Notifications")
                                                .document(notificationId)
                                                .update("status", "handled")
                                                .addOnSuccessListener {
                                                    Log.d(TAG, "   📝 Notification marked as handled")
                                                }
                                                .addOnFailureListener { err ->
                                                    Log.e(TAG, "   ❌ Failed to update notification", err)
                                                }
                                        }
                                    }
                                }
                            }

                            "MODIFIED" -> {
                                Log.d(TAG, "   📝 Notification modified: $notificationId")
                            }

                            "REMOVED" -> {
                                Log.d(TAG, "   🗑️ Notification removed: $notificationId")
                            }
                        }
                    }
                }

            Log.d(TAG, "✅ Real-time listener attached successfully")
        }

        fun stopListening() {
            Log.d(TAG, "🛑 Stopping listener...")
            notificationListener?.remove()
            notificationListener = null
            isListenerActive = false
            // ❌ Don’t clear callback here (we keep it alive across sessions)
            Log.d(TAG, "✅ Listener stopped")
        }

        fun getStatus(): String {
            return "Active: $isListenerActive, Callback: ${onWalkRequestReceived != null}"
        }
    }
}

data class NotificationData(
    val notificationId: String,
    val sosRequestId: String,
    val senderName: String,
    val location: String,
    val message: String
)
