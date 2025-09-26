package com.camshield.app.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Person
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background as backgroundExt
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.size
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import java.util.*
import com.google.firebase.auth.FirebaseAuth
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertCircle
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
    startAtOriginalScale: Boolean = false
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        var intrinsicW by remember { mutableStateOf<Int?>(null) }
        var intrinsicH by remember { mutableStateOf<Int?>(null) }
        var initialApplied by remember { mutableStateOf(false) }

        val transformState = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            if (scale > 1f) offset += pan else offset = Offset.Zero
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            val density = LocalDensity.current
            val vpW = with(density) { maxWidth.toPx() }
            val vpH = with(density) { maxHeight.toPx() }

            // Apply original-scale once at start
            LaunchedEffect(intrinsicW, intrinsicH, vpW, vpH, startAtOriginalScale) {
                val iw = intrinsicW?.toFloat()
                val ih = intrinsicH?.toFloat()
                if (startAtOriginalScale && !initialApplied && iw != null && ih != null && vpW > 0 && vpH > 0) {
                    val originalScale = maxOf(1f, minOf(iw / vpW, ih / vpH))
                    scale = originalScale.coerceIn(1f, 5f)
                    initialApplied = true
                }
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .zIndex(2f) // always on top
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Image with zoom/pan and also closes on tap
            AsyncImage(
                model = imageUrl,
                contentDescription = "Full Screen Incident Image",
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    intrinsicW = state.result.drawable.intrinsicWidth
                    intrinsicH = state.result.drawable.intrinsicHeight
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .transformable(transformState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onDismiss() }, // ✅ tap photo to close
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f; offset = Offset.Zero
                                } else {
                                    scale = 2f
                                }
                            }
                        )
                    }
            )

            // Backdrop tap closes too
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Transparent)
                    .clickable(onClick = onDismiss)
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentReportScreen(onCloseDrawer: () -> Unit = {}) {
    var incidents by remember { mutableStateOf<List<IncidentReport>>(emptyList()) }
    var showAddForm by remember { mutableStateOf(false) }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    var openAtOriginalScale by remember { mutableStateOf(false) }   // ✅ add

    // Load incidents from Firestore
    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("Incident")
            .whereEqualTo("status", "Open")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    println("Error fetching incidents: $e")
                    return@addSnapshotListener
                }

                incidents = if (snapshot != null && !snapshot.isEmpty) {
                    snapshot.documents.map { doc ->
                        IncidentReport(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            date = doc.getTimestamp("timestamp")?.toDate()?.toString() ?: "",
                            description = doc.getString("description") ?: "",
                            severity = "Medium",
                            status = doc.getString("status") ?: "",
                            picture = doc.getString("picture") ?: "",
                            postedByName = doc.getString("postedBy") ?: ""
                        )
                    }
                } else {
                    emptyList()
                }
            }
    }

    // Full screen image viewer
    selectedImageUrl?.let { imageUrl ->
        FullScreenImageViewer(
            imageUrl = imageUrl,
            onDismiss = { selectedImageUrl = null; openAtOriginalScale = false },
            startAtOriginalScale = openAtOriginalScale              // ✅ add
        )
    }

    // Main background
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // Header sizes
        val headerContentHeight = 80.dp
        val headerVerticalPadding = 16.dp
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val totalHeaderHeight = headerContentHeight + headerVerticalPadding * 2 + statusBarPadding

        // Full-width gradient header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeaderHeight)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to MaterialTheme.colorScheme.primary,
                            0.7f to MaterialTheme.colorScheme.primary,
                            1.0f to MaterialTheme.colorScheme.background
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .zIndex(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left side: icon + title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = FeatherIcons.AlertCircle,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(
                            text = "Incident Reports",
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "See, Say, Safe",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                // Right side: Add button
                IconButton(
                    onClick = { showAddForm = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF667eea), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Incident",
                        tint = Color.White
                    )
                }
            }
        }

        // Incident list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = totalHeaderHeight)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (incidents.isEmpty()) {
                item {
                    ModernEmptyStateCard()
                }
            } else {
                itemsIndexed(incidents) { index, incident ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = 600,
                                delayMillis = index * 100
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = 600,
                                delayMillis = index * 100
                            ),
                            initialOffsetY = { it / 3 }
                        )
                    ) {
                        ModernIncidentCard(
                            incident = incident,
                            onClick = { /* ... */ },
                            onImageClick = { url ->
                                selectedImageUrl = url
                                openAtOriginalScale = true           // ✅ open at 1:1 scale
                            }
                        )
                    }
                }
            }
        }

        // Add Incident Form Overlay
        if (showAddForm) {
            AddIncidentFormOverlay(
                onDismiss = { showAddForm = false },
                onSubmit = { title, description, imageUri ->
                    showAddForm = false
                }
            )
        }
    }
}

@Composable
fun AddIncidentFormOverlay(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var selectedImageUri by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Animation states
    var isVisible by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300)
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedImageUri = it.toString() }
    }

    // Calculate header height to position overlay correctly
    val headerContentHeight = 80.dp
    val headerVerticalPadding = 16.dp
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalHeaderHeight = headerContentHeight + headerVerticalPadding * 2 + statusBarPadding

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f * animatedAlpha))
            .statusBarsPadding() // Add status bar padding to the entire overlay
            .clickable { onDismiss() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = totalHeaderHeight + 20.dp, // Position below header with some margin
                    bottom = 20.dp
                )
                .scale(animatedScale)
                .alpha(animatedAlpha)
                .clickable(enabled = false) { /* Prevent closing when clicking card */ },
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Form Content
                Column(
                    modifier = Modifier.padding(30.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Form Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Report Incident",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = Color(0xFF1E293B)
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    Color(0xFFF1F5F9),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Title Input
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Description input
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Image Section - Removed "Evidence Photo" text
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Image picker button or selected image
                        if (selectedImageUri == null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp) // Reduced height
                                    .clickable { imagePickerLauncher.launch("image/*") },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF8FAFC)
                                ),
                                border = BorderStroke(
                                    2.dp,
                                    Color(0xFFE2E8F0),
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp), // Reduced padding
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp) // Reduced size
                                            .background(
                                                Color(0xFF667eea).copy(alpha = 0.1f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = Color(0xFF667eea),
                                            modifier = Modifier.size(24.dp) // Reduced size
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp)) // Reduced spacing
                                    Text(
                                        text = "Add photo (optional)",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = Color(0xFF667eea)
                                    )
                                    Text(
                                        text = "JPG, PNG up to 10MB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        } else {
                            // Selected image preview
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                Box {
                                    AsyncImage(
                                        model = selectedImageUri,
                                        contentDescription = "Selected Image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp), // Reduced height
                                        contentScale = ContentScale.Crop
                                    )

                                    // Remove image button
                                    IconButton(
                                        onClick = { selectedImageUri = null },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(12.dp)
                                            .size(36.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.6f),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove image",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Change image button
                                    Button(
                                        onClick = { imagePickerLauncher.launch("image/*") },
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Black.copy(alpha = 0.6f)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Change",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Submit and Cancel buttons in a row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel button
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF64748B)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Text(
                                "Cancel",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        // Submit button
                        Button(
                            onClick = {
                                if (title.text.isNotBlank() && description.text.isNotBlank()) {
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val currentUser = FirebaseAuth.getInstance().currentUser
                                            val userId = currentUser?.uid ?: throw Exception("User not logged in")

                                            val db = FirebaseFirestore.getInstance()
                                            val userDoc = db.collection("Users").document(userId).get().await()
                                            val userName = userDoc.getString("name") ?: "anonymous"

                                            // Init Supabase client
                                            val supabase = createSupabaseClient(
                                                supabaseUrl = "https://tewchlxrvfuzusdnhynk.supabase.co",
                                                supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRld2NobHhydmZ1enVzZG5oeW5rIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgyNDIwMDgsImV4cCI6MjA3MzgxODAwOH0.35QdJV3WnOFzKmVPX_R4iOM0VXAbRnXJyuIZOXNamRU"
                                            ) {
                                                install(Storage)
                                            }

                                            var imageUrl = ""
                                            if (selectedImageUri != null) {
                                                val uri = Uri.parse(selectedImageUri)
                                                val inputStream = context.contentResolver.openInputStream(uri)
                                                val bytes = inputStream?.readBytes()
                                                inputStream?.close()

                                                if (bytes != null) {
                                                    val fileName = "incidents/${UUID.randomUUID()}.jpg"
                                                    supabase.storage.from("incident-images").upload(fileName, bytes)
                                                    imageUrl = supabase.storage.from("incident-images").publicUrl(fileName)
                                                }
                                            }

                                            val incidentData = hashMapOf(
                                                "title" to title.text,
                                                "description" to description.text,
                                                "picture" to imageUrl,
                                                "postedBy" to userName,
                                                "status" to "pending",
                                                "timestamp" to Timestamp.now()
                                            )

                                            db.collection("Incident").add(incidentData).await()

                                            isLoading = false
                                            onSubmit(title.text, description.text, imageUrl)
                                        } catch (e: Exception) {
                                            isLoading = false
                                            println("Submit failed: ${e.message}")
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            enabled = !isLoading && title.text.isNotBlank() && description.text.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF667eea),
                                disabledContainerColor = Color(0xFFE2E8F0)
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "Submit",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernIncidentCard(
    incident: IncidentReport,
    onClick: () -> Unit = {},
    onImageClick: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(8.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = incident.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            lineHeight = 28.sp
                        ),
                        color = Color(0xFF1E293B)
                    )

                    if (incident.postedByName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reported by ${incident.postedByName}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                ModernStatusChip(status = incident.status)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            Text(
                text = incident.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF475569)
            )

            // Image (if available)
            if (incident.picture.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                AsyncImage(
                    model = incident.picture,
                    contentDescription = "Incident Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF8FAFC))
                        .clickable { onImageClick(incident.picture) }

                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Footer with timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = incident.date,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

@Composable
fun ModernStatusChip(status: String) {
    val (backgroundColor, textColor, borderColor) = when (status.lowercase()) {
        "pending" -> Triple(
            Color(0xFFFEF3C7),
            Color(0xFFD97706),
            Color(0xFFF59E0B)
        )
        "approved" -> Triple(
            Color(0xFFDCFCE7),
            Color(0xFF16A34A),
            Color(0xFF22C55E)
        )
        "rejected" -> Triple(
            Color(0xFFFEE2E2),
            Color(0xFFDC2626),
            Color(0xFFEF4444)
        )
        else -> Triple(
            Color(0xFFF1F5F9),
            Color(0xFF64748B),
            Color(0xFF94A3B8)
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = status.capitalize(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            ),
            color = textColor
        )
    }
}

@Composable
fun SeverityIndicator(severity: String) {
    val (color, icon) = when (severity.lowercase()) {
        "high" -> Color(0xFFDC2626) to Icons.Default.Warning
        "medium" -> Color(0xFFF59E0B) to Icons.Default.Warning
        "low" -> Color(0xFF16A34A) to Icons.Default.Check
        else -> Color(0xFF64748B) to Icons.Default.Info
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ModernEmptyStateCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(12.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated icon container
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF667eea).copy(alpha = 0.1f),
                                Color(0xFF764ba2).copy(alpha = 0.05f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF667eea).copy(alpha = 0.2f),
                                    Color(0xFF764ba2).copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF667eea),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "All Clear! 🛡",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "No reported security incidents",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                ),
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your community is secure and all systems are operating normally. Keep up the great work!",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action hint
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF667eea).copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tap the + button to report a new incident",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = Color(0xFF667eea).copy(alpha = 0.8f)
                )
            }
        }
    }
}

// Data class for incident report
data class IncidentReport(
    val id: String,
    val title: String,
    val date: String,
    val description: String,
    val severity: String = "Medium",
    val status: String = "Open",
    val picture: String,
    val postedByName: String
)