package com.camshield.app.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.util.UUID
import android.net.Uri
import coil.compose.AsyncImage
import com.google.firebase.auth.EmailAuthProvider
import io.github.jan.supabase.storage.storage
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Close
import com.camshield.app.App
import com.google.firebase.firestore.SetOptions

@Composable
fun ProfileSettingScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Profile", "Medical Info")

    // Modern gradient colors
    val primaryGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF667eea),
            Color(0xFF764ba2)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Compact Header - Much smaller for mobile
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp) // Reduced from 200dp to 120dp
                .background(primaryGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp), // Reduced padding
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Profile Settings",
                    style = MaterialTheme.typography.headlineMedium.copy( // Reduced from headlineLarge
                        fontSize = 24.sp, // Reduced from 32sp
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Text(
                    text = "Manage your account",
                    style = MaterialTheme.typography.bodyMedium, // Reduced from bodyLarge
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp) // Reduced from 8dp
                )
            }
        }

        // Compact Tab Row
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = (-24).dp), // Reduced offset from -32dp
            shape = RoundedCornerShape(16.dp), // Reduced from 20dp
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                containerColor = Color.Transparent,
                contentColor = Color(0xFF667eea),
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[selectedTab])
                                .height(3.dp) // Reduced from 4dp
                                .padding(horizontal = 20.dp)
                                .background(
                                    brush = primaryGradient,
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp // Explicit smaller font size
                            )
                        },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        modifier = Modifier.padding(vertical = 12.dp) // Reduced from 16dp
                    )
                }
            }
        }

        // Content with proper scrolling
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp) // Reduced from 16dp
        ) {
            when (selectedTab) {
                0 -> MobileProfileSection()
                1 -> MobileMedicalInfoSection()
            }
        }
    }
}

@Composable
fun MobileProfileSection() {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid ?: throw Exception("User not logged in")

    // State variables for fields
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPasswordFields by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf("") }
    var profileImageUrl by remember { mutableStateOf<String?>(null) }

    // Non-editable fields
    var email by remember { mutableStateOf("") }
    var faculty by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var localImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Auto-scroll to message when it appears
    LaunchedEffect(saveMessage) {
        if (saveMessage.isNotEmpty()) {
            // Scroll to the top where the message is located
            scrollState.animateScrollTo(0) // Adjust this value to position perfectly
        }

        // Auto-clear message after 5 seconds (only if not loading)
        if (saveMessage.isNotEmpty() && !saveMessage.contains("⏳")) {
            kotlinx.coroutines.delay(5000)
            saveMessage = ""
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            localImageUri = uri
            isUploading = true

            scope.launch {
                try {
                    val fileName = "${UUID.randomUUID()}.jpg"
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()

                    if (bytes != null) {
                        App.supabase.storage.from("contact-images").upload(fileName, bytes)
                        val publicUrl = App.supabase.storage.from("contact-images").publicUrl(fileName)
                        profileImageUrl = publicUrl

                        db.collection("Users").document(userId)
                            .set(mapOf("profileimgurl" to publicUrl), SetOptions.merge())
                            .addOnSuccessListener { saveMessage = "Profile image updated ✅" }
                            .addOnFailureListener { saveMessage = "Failed to update Firestore ❌" }
                    }
                } catch (e: Exception) {
                    saveMessage = "Upload failed ❌"
                } finally {
                    isUploading = false
                }
            }
        }
    }

    // Load user data from Firestore
    DisposableEffect(userId) {
        var listener: ListenerRegistration? = null
        if (userId != null) {
            listener = db.collection("Users").document(userId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        saveMessage = "Error loading profile ❌"
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        name = snapshot.getString("name") ?: ""
                        email = snapshot.getString("email") ?: ""
                        faculty = snapshot.getString("faculty") ?: ""
                        studentId = snapshot.getString("studentid") ?: ""
                        phoneNumber = snapshot.get("phone")?.toString() ?: ""
                        profileImageUrl = snapshot.getString("profileimgurl")
                    }
                }
        }
        onDispose { listener?.remove() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Compact Profile Picture Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF667eea),
                                        Color(0xFF764ba2)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { imagePicker.launch("image/*") }
                    ) {
                        if (profileImageUrl != null) {
                            AsyncImage(
                                model = profileImageUrl,
                                contentDescription = "Profile Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else{
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                tint = Color(0xFF667eea).copy(alpha = 0.6f)
                            )
                        }
                    }

                    FloatingActionButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.BottomEnd),
                        containerColor = Color(0xFF667eea),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Change Picture",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = name.ifEmpty { "Your Name" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748)
                    )
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6C757D)
                    )
                    Text(
                        text = "Tap photo to update",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF667eea)
                    )
                }
            }
        }

        // MESSAGE CARD - POSITIONED AT TOP OF PERSONAL INFO SECTION
        AnimatedVisibility(
            visible = saveMessage.isNotEmpty(),
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(300)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(300)
            ) + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        saveMessage.contains("✅") -> Color(0xFFD4F6D4) // Light green
                        saveMessage.contains("⏳") -> Color(0xFFE3F2FD) // Light blue
                        else -> Color(0xFFFFE6E6) // Light red
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        saveMessage.contains("✅") -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF28A745),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        saveMessage.contains("⏳") -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF2196F3),
                                strokeWidth = 2.dp
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = Color(0xFFDC3545),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = saveMessage,
                        color = when {
                            saveMessage.contains("✅") -> Color(0xFF28A745)
                            saveMessage.contains("⏳") -> Color(0xFF2196F3)
                            else -> Color(0xFFDC3545)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    if (!saveMessage.contains("⏳")) {
                        IconButton(
                            onClick = { saveMessage = "" },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = when {
                                    saveMessage.contains("✅") -> Color(0xFF28A745)
                                    else -> Color(0xFFDC3545)
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Personal Information
        MobileCard(title = "Personal Information") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MobileTextField(
                    label = "Full Name",
                    value = name,
                    onValueChange = { name = it },
                    icon = Icons.Outlined.Person,
                    isEditable = true
                )
                MobileTextField(
                    label = "Email Address",
                    value = email,
                    onValueChange = { },
                    icon = Icons.Outlined.Email,
                    isEditable = false
                )
                MobileTextField(
                    label = "Faculty",
                    value = faculty,
                    onValueChange = { },
                    icon = Icons.Outlined.School,
                    isEditable = false
                )
                MobileTextField(
                    label = "Student ID",
                    value = studentId,
                    onValueChange = { },
                    icon = Icons.Outlined.Badge,
                    isEditable = false
                )
                MobileTextField(
                    label = "Phone Number",
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    icon = Icons.Outlined.Phone,
                    isEditable = true,
                    keyboardType = KeyboardType.Phone
                )
            }
        }

        // Security Settings (Password section remains the same)
        MobileCard(title = "Security Settings") {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Change Password",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2D3748)
                        )
                        Text(
                            text = "Update your password",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6C757D)
                        )
                    }

                    Switch(
                        checked = showPasswordFields,
                        onCheckedChange = { showPasswordFields = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF667eea),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFE2E8F0)
                        )
                    )
                }

                if (showPasswordFields) {
                    MobilePasswordField(
                        label = "Current Password",
                        value = currentPassword,
                        onValueChange = { currentPassword = it }
                    )

                    MobilePasswordField(
                        label = "New Password",
                        value = newPassword,
                        onValueChange = { newPassword = it }
                    )

                    MobilePasswordField(
                        label = "Confirm New Password",
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it }
                    )

                    Button(
                        onClick = {
                            saveMessage = ""
                            val user = FirebaseAuth.getInstance().currentUser

                            when {
                                user == null -> {
                                    saveMessage = "❌ User not logged in"
                                    return@Button
                                }
                                currentPassword.isBlank() -> {
                                    saveMessage = "❌ Please enter current password"
                                    return@Button
                                }
                                newPassword.isBlank() -> {
                                    saveMessage = "❌ Please enter new password"
                                    return@Button
                                }
                                newPassword.length < 6 -> {
                                    saveMessage = "❌ Password must be at least 6 characters"
                                    return@Button
                                }
                                newPassword != confirmPassword -> {
                                    saveMessage = "❌ Passwords don't match"
                                    return@Button
                                }
                            }

                            saveMessage = "⏳ Updating password..."

                            val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)

                            user.reauthenticate(credential)
                                .addOnCompleteListener { reauthTask ->
                                    if (reauthTask.isSuccessful) {
                                        user.updatePassword(newPassword)
                                            .addOnSuccessListener {
                                                saveMessage = "✅ Password updated successfully!"
                                                currentPassword = ""
                                                newPassword = ""
                                                confirmPassword = ""
                                                showPasswordFields = false
                                            }
                                            .addOnFailureListener { exception ->
                                                saveMessage = "❌ Failed to update password: ${exception.localizedMessage}"
                                            }
                                    } else {
                                        val error = reauthTask.exception
                                        saveMessage = when {
                                            error?.message?.contains("password", ignoreCase = true) == true ->
                                                "❌ Current password is incorrect"
                                            error?.message?.contains("network", ignoreCase = true) == true ->
                                                "❌ Network error. Please check your connection"
                                            else ->
                                                "❌ Authentication failed: ${error?.localizedMessage}"
                                        }
                                    }
                                }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF667eea)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (saveMessage.contains("⏳")) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            if (saveMessage.contains("⏳")) "Updating..." else "Update Password",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // ENHANCED SAVE BUTTON WITH AUTO-SCROLL
        Button(
            onClick = {
                // Show loading message immediately
                saveMessage = "⏳ Updating profile..."

                scope.launch {
                    // Small delay to ensure UI updates, then scroll
                    kotlinx.coroutines.delay(100)
                    scrollState.animateScrollTo(400)
                }

                if (userId.isNotEmpty()) {
                    val updates = mapOf(
                        "name" to name,
                        "phone" to phoneNumber
                    )

                    db.collection("Users").document(userId)
                        .update(updates)
                        .addOnSuccessListener {
                            saveMessage = "✅ Profile updated successfully!"
                        }
                        .addOnFailureListener { exception ->
                            saveMessage = "❌ Failed to update profile: ${exception.message}"
                        }
                } else {
                    saveMessage = "❌ User not logged in"
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF48BB78)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 3.dp)
        ) {
            if (saveMessage.contains("⏳")) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Updating...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Save Changes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMedicalInfoSection() {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid ?: throw Exception("User not logged in")

    // State variables for medical fields
    var dateOfBirth by remember { mutableStateOf("") }
    var medicalHistory by remember { mutableStateOf("") }
    var bloodType by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var currentMedication by remember { mutableStateOf("") }
    var saveMessage by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Blood type options
    val bloodTypeOptions = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    var bloodTypeExpanded by remember { mutableStateOf(false) }

    // Auto-scroll to message when it appears
    LaunchedEffect(saveMessage) {
        if (saveMessage.isNotEmpty()) {
            scrollState.animateScrollTo(0)
        }

        // Auto-clear message after 5 seconds (only if not loading)
        if (saveMessage.isNotEmpty() && !saveMessage.contains("⏳")) {
            kotlinx.coroutines.delay(5000)
            saveMessage = ""
        }
    }

    // Load medical data from Firestore
    DisposableEffect(userId) {
        var listener: ListenerRegistration? = null
        if (userId.isNotEmpty()) {
            listener = db.collection("Users").document(userId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        saveMessage = "Error loading medical info ❌"
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val medicalData = snapshot.get("medicalInfo") as? Map<String, Any>
                        if (medicalData != null) {
                            dateOfBirth = medicalData["dateOfBirth"]?.toString() ?: ""
                            medicalHistory = medicalData["medicalHistory"]?.toString() ?: ""
                            bloodType = medicalData["bloodType"]?.toString() ?: ""
                            allergies = medicalData["allergies"]?.toString() ?: ""
                            height = medicalData["height"]?.toString() ?: ""
                            weight = medicalData["weight"]?.toString() ?: ""
                            currentMedication = medicalData["currentMedication"]?.toString() ?: ""
                        } else {
                            // Initialize empty values if no medical data exists
                            dateOfBirth = ""
                            medicalHistory = ""
                            bloodType = ""
                            allergies = ""
                            height = ""
                            weight = ""
                            currentMedication = ""
                        }
                    }
                }
        }
        onDispose {
            listener?.remove()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Compact Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF667eea).copy(alpha = 0.1f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MedicalServices,
                    contentDescription = "Medical Info",
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF667eea)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Medical Information",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748)
                    )
                    Text(
                        text = "Health records & medical history",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6C757D)
                    )
                }
            }
        }

        // MESSAGE CARD
        AnimatedVisibility(
            visible = saveMessage.isNotEmpty(),
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(300)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(300)
            ) + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        saveMessage.contains("✅") -> Color(0xFFD4F6D4)
                        saveMessage.contains("⏳") -> Color(0xFFE3F2FD)
                        else -> Color(0xFFFFE6E6)
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        saveMessage.contains("✅") -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF28A745),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        saveMessage.contains("⏳") -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF2196F3),
                                strokeWidth = 2.dp
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = Color(0xFFDC3545),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = saveMessage,
                        color = when {
                            saveMessage.contains("✅") -> Color(0xFF28A745)
                            saveMessage.contains("⏳") -> Color(0xFF2196F3)
                            else -> Color(0xFFDC3545)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    if (!saveMessage.contains("⏳")) {
                        IconButton(
                            onClick = { saveMessage = "" },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = when {
                                    saveMessage.contains("✅") -> Color(0xFF28A745)
                                    else -> Color(0xFFDC3545)
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Basic Information
        MobileCard(title = "Basic Information") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MobileTextField(
                    label = "Date of Birth",
                    value = dateOfBirth,
                    onValueChange = { dateOfBirth = it },
                    icon = Icons.Outlined.DateRange,
                    isEditable = true,
                    placeholder = "DD/MM/YYYY"
                )

                // Blood Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = bloodTypeExpanded,
                    onExpandedChange = { bloodTypeExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = bloodType,
                        onValueChange = { },
                        label = { Text("Blood Type", fontSize = 14.sp) },
                        placeholder = {
                            if (bloodType.isEmpty()) {
                                Text("Select blood type", fontSize = 12.sp, color = Color.Gray)
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Bloodtype,
                                contentDescription = null,
                                tint = Color(0xFF667eea),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = bloodTypeExpanded)
                        },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF667eea),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedLabelColor = Color(0xFF667eea),
                            unfocusedLabelColor = Color(0xFF6C757D)
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = bloodTypeExpanded,
                        onDismissRequest = { bloodTypeExpanded = false }
                    ) {
                        bloodTypeOptions.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = type,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    bloodType = type
                                    bloodTypeExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }

                        // Add option to clear selection
                        if (bloodType.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                thickness = 1.dp,
                                color = Color.Gray.copy(alpha = 0.3f)
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Clear selection",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                },
                                onClick = {
                                    bloodType = ""
                                    bloodTypeExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MobileTextField(
                        label = "Height (cm)",
                        value = height,
                        onValueChange = { height = it },
                        icon = Icons.Outlined.Height,
                        isEditable = true,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )

                    MobileTextField(
                        label = "Weight (kg)",
                        value = weight,
                        onValueChange = { weight = it },
                        icon = Icons.Default.Monitor,
                        isEditable = true,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Medical History & Conditions
        MobileCard(title = "Medical History & Conditions") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = medicalHistory,
                    onValueChange = { medicalHistory = it },
                    label = { Text("Medical History", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF667eea),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    placeholder = { Text("Enter any past medical conditions, surgeries, etc.", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 200.dp), // Dynamic height
                    minLines = 4,
                    maxLines = 8, // Allow more lines
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), // Better line spacing
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF667eea),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedLabelColor = Color(0xFF667eea),
                        unfocusedLabelColor = Color(0xFF6C757D)
                    )
                )

                OutlinedTextField(
                    value = allergies,
                    onValueChange = { allergies = it },
                    label = { Text("Allergies", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF6B35),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    placeholder = { Text("List any known allergies (food, medication, etc.)", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 160.dp), // Dynamic height
                    minLines = 3,
                    maxLines = 6, // Allow more lines
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), // Better line spacing
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF667eea),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedLabelColor = Color(0xFF667eea),
                        unfocusedLabelColor = Color(0xFF6C757D)
                    )
                )
            }
        }

        // Current Medication
        MobileCard(title = "Current Medication") {
            OutlinedTextField(
                value = currentMedication,
                onValueChange = { currentMedication = it },
                label = { Text("Current Medication", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Medication,
                        contentDescription = null,
                        tint = Color(0xFF667eea),
                        modifier = Modifier.size(20.dp)
                    )
                },
                placeholder = { Text("List current medications, dosages, and frequency", fontSize = 12.sp, color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 200.dp), // Dynamic height
                minLines = 4,
                maxLines = 8, // Allow more lines
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), // Better line spacing
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF667eea),
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedLabelColor = Color(0xFF667eea),
                    unfocusedLabelColor = Color(0xFF6C757D)
                )
            )
        }

        // Save Button
        Button(
            onClick = {
                saveMessage = "⏳ Saving medical information..."

                scope.launch {
                    kotlinx.coroutines.delay(100)
                    scrollState.animateScrollTo(0)
                }

                if (userId.isNotEmpty()) {
                    val medicalData = mapOf(
                        "dateOfBirth" to dateOfBirth,
                        "medicalHistory" to medicalHistory,
                        "bloodType" to bloodType,
                        "allergies" to allergies,
                        "height" to height,
                        "weight" to weight,
                        "currentMedication" to currentMedication,
                        "lastUpdated" to System.currentTimeMillis()
                    )

                    db.collection("Users").document(userId)
                        .set(mapOf("medicalInfo" to medicalData), SetOptions.merge())
                        .addOnSuccessListener {
                            saveMessage = "✅ Medical information saved successfully!"
                        }
                        .addOnFailureListener { exception ->
                            saveMessage = "❌ Failed to save medical information: ${exception.message}"
                        }
                } else {
                    saveMessage = "❌ User not logged in"
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF48BB78)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 3.dp)
        ) {
            if (saveMessage.contains("⏳")) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Saving...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Save Medical Information",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


// Mobile-optimized components
@Composable
fun MobileCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp), // Reduced from 24dp
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(20.dp), // Reduced from 24dp
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium, // Reduced from titleLarge
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3748)
            )
            content()
        }
    }
}

@Composable
fun MobileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    isEditable: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String = "",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 14.sp) },
        placeholder = if (placeholder.isNotEmpty()) { { Text(placeholder, fontSize = 12.sp, color = Color.Gray) } } else null,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isEditable) Color(0xFF667eea) else Color(0xFFCBD5E0),
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = if (!isEditable) {
            {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Read only",
                    tint = Color(0xFFCBD5E0),
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        enabled = isEditable,
        modifier = modifier.fillMaxWidth().height(56.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF667eea),
            unfocusedBorderColor = Color(0xFFE2E8F0),
            disabledBorderColor = Color(0xFFE2E8F0),
            focusedLabelColor = Color(0xFF667eea),
            unfocusedLabelColor = Color(0xFF6C757D),
            disabledLabelColor = Color(0xFFCBD5E0),
            disabledTextColor = Color(0xFF6C757D)
        )
    )
}

@Composable
fun MobilePasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 14.sp) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFF667eea),
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.Visibility
                    else Icons.Default.VisibilityOff,
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    tint = Color(0xFF6C757D),
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        visualTransformation = if (passwordVisible) VisualTransformation.None
        else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF667eea),
            unfocusedBorderColor = Color(0xFFE2E8F0),
            focusedLabelColor = Color(0xFF667eea),
            unfocusedLabelColor = Color(0xFF6C757D)
        )
    )
}

@Composable
fun MobileMedicalFeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp) // Reduced from 56dp
                .background(
                    color = Color(0xFF667eea).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF667eea),
                modifier = Modifier.size(20.dp) // Reduced from 28dp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2D3748)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6C757D)
            )
        }
    }
}