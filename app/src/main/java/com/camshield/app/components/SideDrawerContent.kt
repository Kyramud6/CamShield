package com.camshield.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.camshield.app.models.DrawerMenuItem
import com.camshield.app.R
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Walking
import compose.icons.fontawesomeicons.solid.MapMarkerAlt
import compose.icons.fontawesomeicons.solid.Lightbulb
import android.Manifest
import android.annotation.SuppressLint
import android.widget.Toast
import android.util.Log

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun SideDrawerContent(
    onCloseDrawer: () -> Unit
) {
    var showSafetyTips by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedClient: FusedLocationProviderClient =
        remember { LocationServices.getFusedLocationProviderClient(ctx) }
    val firestore: FirebaseFirestore = remember { FirebaseFirestore.getInstance() }
    val auth: FirebaseAuth = remember { FirebaseAuth.getInstance() }

    // Location permission state
    val fineLocationPerm = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    ModalDrawerSheet(
        modifier = Modifier.width(340.dp),
        drawerContainerColor = Color(0xFFFDFDFD),
        drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Modern clean header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFAFBFC),
                                Color(0xFFF7F9FB),
                                Color(0xFFF4F6F8)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.camshieldlogo),
                        contentDescription = "CamShield Logo",
                        modifier = Modifier.size(180.dp)
                    )

                    IconButton(
                        onClick = onCloseDrawer,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                Color(0xFF94A3B8).copy(alpha = 0.1f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Menu Items
            val menuItems = listOf(
                DrawerMenuItem("Walk With Me", FontAwesomeIcons.Solid.Walking, Color(0xFF10B981)),
                DrawerMenuItem("Safe Locations", FontAwesomeIcons.Solid.MapMarkerAlt, Color(0xFF3B82F6)),
                DrawerMenuItem("Safety Tips", FontAwesomeIcons.Solid.Lightbulb, Color(0xFFF59E0B)),
            )

            menuItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            when (item.title) {
                                "Walk With Me" -> {
                                    onCloseDrawer()
                                    scope.launch {
                                        // Check permission first
                                        if (!fineLocationPerm.status.isGranted) {
                                            fineLocationPerm.launchPermissionRequest()
                                            return@launch
                                        }

                                        try {
                                            Toast.makeText(ctx, "Sending walk request...", Toast.LENGTH_SHORT).show()

                                            @SuppressLint("MissingPermission")
                                            val loc = fusedClient.getCurrentLocation(
                                                Priority.PRIORITY_HIGH_ACCURACY,
                                                CancellationTokenSource().token
                                            ).await() ?: throw IllegalStateException("Location unavailable")

                                            val currentUser = auth.currentUser
                                            if (currentUser == null) {
                                                Toast.makeText(ctx, "Please login first", Toast.LENGTH_SHORT).show()
                                                return@launch
                                            }

                                            // Get current user's name
                                            val userDoc = firestore.collection("Users")
                                                .document(currentUser.uid)
                                                .get()
                                                .await()

                                            val userName = userDoc.getString("name") ?: "User"
                                            val locationString = formatLocation(loc.latitude, loc.longitude)

                                            Log.d("WalkWithMe", "Creating SOS request for user: $userName at $locationString")

                                            // Create SOS request
                                            val sosData = mapOf(
                                                "audioUrl" to "",
                                                "location" to locationString,
                                                "name" to userName,
                                                "status" to "Pending",
                                                "timestamp" to FieldValue.serverTimestamp(),
                                                "type" to "walk_with_me",
                                                "userId" to currentUser.uid,
                                                "userEmail" to (currentUser.email ?: ""),
                                                "latitude" to loc.latitude,
                                                "longitude" to loc.longitude
                                            )

                                            val sosRef = firestore.collection("SOS").add(sosData).await()
                                            Log.d("WalkWithMe", "SOS request created: ${sosRef.id}")

                                            // Send notifications to emergency contacts
                                            val notificationsSent = sendWalkWithMeNotifications(
                                                firestore = firestore,
                                                currentUserId = currentUser.uid,
                                                requesterName = userName,
                                                location = locationString,
                                                sosRequestId = sosRef.id,
                                                context = ctx
                                            )

                                            if (notificationsSent > 0) {
                                                Toast.makeText(ctx, "Walk request sent to $notificationsSent contacts!", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(ctx, "No emergency contacts found with app accounts", Toast.LENGTH_LONG).show()
                                            }

                                        } catch (e: Exception) {
                                            Log.e("WalkWithMe", "Error in Walk With Me", e)
                                            Toast.makeText(ctx, "Failed to send request: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                "Safety Tips" -> showSafetyTips = true
                                else -> onCloseDrawer()
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon design
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                item.color.copy(alpha = 0.12f),
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = item.color,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Text(
                        text = item.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E293B),
                        letterSpacing = 0.3.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Navigate",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Logout section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Handle logout action here
                        onCloseDrawer()
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            Color(0xFFF44336).copy(alpha = 0.12f),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Logout",
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Text(
                    text = "Logout",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF44336),
                    letterSpacing = 0.3.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Navigate",
                    tint = Color(0xFFF44336).copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF3B82F6),
                                    Color(0xFF10B981)
                                )
                            ),
                            RoundedCornerShape(2.dp)
                        )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Stay Safe, Stay Connected",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.3.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "CamShield",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }

    // Safety Tips Full Screen Overlay
    if (showSafetyTips) {
        SafetyTipsFullScreenOverlay(
            onClose = { showSafetyTips = false }
        )
    }
}

fun formatLocation(lat: Double, lng: Double): String {
    val latDir = if (lat >= 0) "N" else "S"
    val lngDir = if (lng >= 0) "E" else "W"
    return "${String.format("%.6f", kotlin.math.abs(lat))}°$latDir, ${String.format("%.6f", kotlin.math.abs(lng))}°$lngDir"
}

// ENHANCED DATABASE-ONLY notification function with detailed debugging
suspend fun sendWalkWithMeNotifications(
    firestore: FirebaseFirestore,
    currentUserId: String,
    requesterName: String,
    location: String,
    sosRequestId: String,
    context: android.content.Context
): Int {
    return try {
        Log.d("WalkWithMe", "=== ENHANCED DEBUG - Starting notification process ===")
        Log.d("WalkWithMe", "Requester: $requesterName (ID: $currentUserId)")
        Log.d("WalkWithMe", "Location: $location")
        Log.d("WalkWithMe", "SOS Request ID: $sosRequestId")

        // Get personal emergency contacts
        val personalContactsSnapshot = firestore.collection("Personal_Contacts")
            .document(currentUserId)
            .collection("contacts")
            .get()
            .await()

        Log.d("WalkWithMe", "Found ${personalContactsSnapshot.documents.size} personal contacts")

        if (personalContactsSnapshot.documents.isEmpty()) {
            Log.w("WalkWithMe", "No personal contacts found for user: $currentUserId")

            // DEBUG: Check if the collection exists
            val parentDoc = firestore.collection("Personal_Contacts")
                .document(currentUserId)
                .get()
                .await()

            Log.d("WalkWithMe", "Parent document exists: ${parentDoc.exists()}")
            return 0
        }

        // DEBUG: Log all contacts found
        Log.d("WalkWithMe", "=== ALL CONTACTS FOUND ===")
        for (contactDoc in personalContactsSnapshot.documents) {
            val phoneNumber = contactDoc.getString("phoneNumber")?.trim()
            val contactName = contactDoc.getString("name") ?: "Contact"
            Log.d("WalkWithMe", "Contact: '$contactName', Phone: '$phoneNumber'")
        }

        var notificationsSent = 0

        // Process each contact
        for (contactDoc in personalContactsSnapshot.documents) {
            val phoneNumber = contactDoc.getString("phoneNumber")?.trim()
            val contactName = contactDoc.getString("name") ?: "Contact"

            if (phoneNumber.isNullOrEmpty()) {
                Log.w("WalkWithMe", "Skipping contact '$contactName' - no phone number")
                continue
            }

            Log.d("WalkWithMe", "=== PROCESSING CONTACT: $contactName ===")
            Log.d("WalkWithMe", "Original phone: '$phoneNumber'")

            // Try multiple phone number formats to find matching users
            val phoneVariants = generatePhoneVariants(phoneNumber)
            Log.d("WalkWithMe", "Generated phone variants: $phoneVariants")

            var foundUser = false

            for (phoneVariant in phoneVariants) {
                Log.d("WalkWithMe", "Searching for users with phone variant: '$phoneVariant'")

                try {
                    // Search for users with this phone number
                    val usersQuery = firestore.collection("Users")
                        .whereEqualTo("phone", phoneVariant)
                        .get()
                        .await()

                    Log.d("WalkWithMe", "Query result: ${usersQuery.documents.size} users found for '$phoneVariant'")

                    for (userDoc in usersQuery.documents) {
                        val targetUserName = userDoc.getString("name") ?: "User"
                        val targetUserId = userDoc.id
                        val targetUserPhone = userDoc.getString("phone")

                        Log.d("WalkWithMe", "✅ USER MATCH FOUND!")
                        Log.d("WalkWithMe", "Target User: $targetUserName")
                        Log.d("WalkWithMe", "Target ID: $targetUserId")
                        Log.d("WalkWithMe", "Target Phone in DB: '$targetUserPhone'")
                        Log.d("WalkWithMe", "Matched with variant: '$phoneVariant'")

                        // Create notification document (real-time listener will pick this up instantly)
                        val notificationData = mapOf(
                            "recipientUserId" to targetUserId,
                            "recipientName" to targetUserName,
                            "senderName" to requesterName,
                            "senderUserId" to currentUserId,
                            "type" to "walk_with_me_request",
                            "title" to "Walk With Me Request",
                            "body" to "$requesterName needs someone to walk with them",
                            "sosRequestId" to sosRequestId,
                            "location" to location,
                            "timestamp" to FieldValue.serverTimestamp(),
                            "status" to "pending"
                        )

                        val notificationRef = firestore.collection("Notifications")
                            .add(notificationData)
                            .await()

                        Log.d("WalkWithMe", "✅ Notification document created: ${notificationRef.id}")
                        Log.d("WalkWithMe", "Data: $notificationData")

                        notificationsSent++
                        foundUser = true
                    }

                    if (foundUser) break // Stop trying variants once we found the user

                } catch (e: Exception) {
                    Log.e("WalkWithMe", "Error searching for users with phone $phoneVariant", e)
                }
            }

            if (!foundUser) {
                Log.w("WalkWithMe", "❌ No app user found for contact: $contactName ($phoneNumber)")

                // DEBUG: Show ALL users in database for comparison
                try {
                    Log.d("WalkWithMe", "=== ALL USERS IN DATABASE (for debugging) ===")
                    val allUsers = firestore.collection("Users").limit(10).get().await()
                    for (user in allUsers.documents) {
                        val userName = user.getString("name")
                        val userPhone = user.getString("phone")
                        Log.d("WalkWithMe", "DB User: '$userName', Phone: '$userPhone'")
                    }
                } catch (e: Exception) {
                    Log.e("WalkWithMe", "Error getting users for debug", e)
                }
            }
        }

        Log.d("WalkWithMe", "=== NOTIFICATION PROCESS COMPLETE ===")
        Log.d("WalkWithMe", "Total notifications created: $notificationsSent")
        Log.d("WalkWithMe", "Real-time listeners should pick these up automatically!")

        notificationsSent

    } catch (e: Exception) {
        Log.e("WalkWithMe", "Error in sendWalkWithMeNotifications", e)
        0
    }
}

// Improved generatePhoneVariants function for Malaysian phone numbers
fun generatePhoneVariants(phoneNumber: String): List<String> {
    val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
    val variants = mutableSetOf<String>()

    // Always add the original number (with and without formatting)
    variants.add(cleanNumber)
    variants.add(phoneNumber) // Original with any formatting

    Log.d("WalkWithMe", "Generating variants for: '$phoneNumber' (clean: '$cleanNumber')")

    when {
        // Handle numbers that already have +60 country code
        cleanNumber.startsWith("+60") -> {
            val withoutCountryCode = cleanNumber.substring(3)
            variants.add(withoutCountryCode) // Remove +60
            if (!withoutCountryCode.startsWith("0")) {
                variants.add("0$withoutCountryCode") // Add 0 prefix
            }
            variants.add("60$withoutCountryCode") // 60 without +
        }

        // Handle numbers that have 60 country code (without +)
        cleanNumber.startsWith("60") && cleanNumber.length > 10 -> {
            val withoutCountryCode = cleanNumber.substring(2)
            variants.add(withoutCountryCode) // Remove 60
            if (!withoutCountryCode.startsWith("0")) {
                variants.add("0$withoutCountryCode") // Add 0 prefix
            }
            variants.add("+60$withoutCountryCode") // Add +60
        }

        // Handle numbers starting with 0 (Malaysian local format)
        cleanNumber.startsWith("0") -> {
            val withoutZero = cleanNumber.substring(1)
            variants.add("+60$withoutZero") // +60 + number without 0
            variants.add("60$withoutZero") // 60 + number without 0
        }

        // Handle numbers without country code or leading zero
        else -> {
            variants.add("0$cleanNumber") // Add 0 prefix
            variants.add("+60$cleanNumber") // Add +60 prefix
            variants.add("60$cleanNumber") // Add 60 prefix
        }
    }

    val result = variants.toList()
    Log.d("WalkWithMe", "Generated ${result.size} variants: $result")
    return result
}

@Composable
fun SafetyTipsFullScreenOverlay(
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with gradient background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFF9800),
                                Color(0xFFFFA726)
                            )
                        )
                    )
                    .padding(
                        top = 48.dp,
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 20.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = FontAwesomeIcons.Solid.Lightbulb,
                            contentDescription = "Safety Tips",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Safety Tips",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Stay Safe, Stay Informed",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .background(
                                Color.White.copy(alpha = 0.2f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Safety Tips Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val safetyTips = listOf(
                    SafetyTip(
                        "Stay Alert",
                        "Always be aware of your surroundings. Keep your head up and avoid distractions like phones or headphones in unfamiliar areas.",
                        Icons.Default.Visibility,
                        Color(0xFF2196F3)
                    ),
                    SafetyTip(
                        "Trust Your Instincts",
                        "If something feels wrong or unsafe, trust your gut feeling and remove yourself from the situation immediately.",
                        Icons.Default.Psychology,
                        Color(0xFF9C27B0)
                    ),
                    SafetyTip(
                        "Share Your Location",
                        "Let trusted friends or family know where you're going and when you expect to return, especially when traveling alone.",
                        Icons.Default.LocationOn,
                        Color(0xFF4CAF50)
                    ),
                    SafetyTip(
                        "Use Well-Lit Areas",
                        "Stick to well-lit, populated areas, especially at night. Avoid shortcuts through dark alleys or isolated areas.",
                        Icons.Default.Lightbulb,
                        Color(0xFFFF9800)
                    ),
                    SafetyTip(
                        "Emergency Contacts",
                        "Keep emergency contact numbers easily accessible. Know how to quickly contact local emergency services.",
                        Icons.Default.Phone,
                        Color(0xFFF44336)
                    ),
                    SafetyTip(
                        "Stay Connected",
                        "Keep your phone charged and consider carrying a portable charger. Ensure you have reliable communication methods.",
                        Icons.Default.BatteryFull,
                        Color(0xFF00BCD4)
                    ),
                    SafetyTip(
                        "Plan Your Route",
                        "Research your route beforehand. Know alternative paths and identify safe places like police stations or hospitals along the way.",
                        Icons.Default.Map,
                        Color(0xFF795548)
                    )
                )

                items(safetyTips) { tip ->
                    SafetyTipCard(tip = tip)
                }

                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
fun SafetyTipCard(tip: SafetyTip) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFAFBFC)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        tip.color.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tip.icon,
                    contentDescription = tip.title,
                    tint = tip.color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = tip.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                    letterSpacing = 0.3.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = tip.description,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF64748B),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

data class SafetyTip(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)