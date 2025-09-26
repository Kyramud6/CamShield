package com.camshield.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import java.util.UUID
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import io.github.jan.supabase.storage.storage
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.zIndex
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import compose.icons.FeatherIcons
import compose.icons.feathericons.Plus
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import com.camshield.app.App
import compose.icons.feathericons.Phone

// Contact data model
data class Contact(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val profileImage: String? = null,
    val isUniversityContact: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactScreen() {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid

    var showAddContactDialog by remember { mutableStateOf(false) }
    var universityContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var personalContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var showAllUniversityContacts by remember { mutableStateOf(false) }
    var showAllPersonalContacts by remember { mutableStateOf(false) }

    // Add state for delete confirmation
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }

    // --- Load university contacts ---
    LaunchedEffect(Unit) {
        db.collection("University_Contacts")
            .get()
            .addOnSuccessListener { result ->
                universityContacts = result.documents.map { doc ->
                    Contact(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        phoneNumber = doc.getString("phone") ?: "",
                        isUniversityContact = true
                    )
                }
            }
    }

    // --- Listen to personal contacts for this user ---
    LaunchedEffect(userId) {
        if (userId != null) {
            db.collection("Personal_Contacts")
                .document(userId)
                .collection("contacts")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        personalContacts = snapshot.documents.map { doc ->
                            Contact(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                phoneNumber = doc.getString("phoneNumber") ?: "",
                                profileImage = doc.getString("profileImage"),
                                isUniversityContact = false
                            )
                        }
                    }
                }
        }
    }

    val maxInitialItems = 3
    val displayedUniversityContacts = if (showAllUniversityContacts) {
        universityContacts
    } else {
        universityContacts.take(maxInitialItems)
    }

    val displayedPersonalContacts = if (showAllPersonalContacts) {
        personalContacts
    } else {
        personalContacts.take(maxInitialItems)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // Suppose your header height is:
        val headerContentHeight = 80.dp // your main header box height
        val headerVerticalPadding = 16.dp
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        val totalHeaderHeight = headerContentHeight + headerVerticalPadding * 2 + statusBarPadding

        // Full-width header with gradient blending into background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to MaterialTheme.colorScheme.primary,
                            0.7f to MaterialTheme.colorScheme.primary, // stays primary longer
                            1.0f to MaterialTheme.colorScheme.background
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(headerContentHeight)
                .zIndex(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = FeatherIcons.Phone,
                        contentDescription = "Phone",
                        modifier = Modifier.size(28.dp),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        text = "Emergency Contacts",
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Your safety network at fingertips",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                top = totalHeaderHeight, // make sure first item starts fully below header
                bottom = 20.dp,
                start = 20.dp,
                end = 20.dp
            )
        ) {

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // University Emergency Contacts Container
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // University Section Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "University Emergency",
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Official campus contacts",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                if (universityContacts.isNotEmpty()) {
                                    Text(
                                        "${universityContacts.size} contact${if (universityContacts.size > 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }

                        // University Contacts List
                        displayedUniversityContacts.forEach { contact ->
                            EnhancedContactCard(
                                contact = contact,
                                onDeleteClick = null // university contacts cannot be deleted
                            )
                        }

                        if (universityContacts.size > maxInitialItems) {
                            EnhancedShowMoreButton(
                                isExpanded = showAllUniversityContacts,
                                remainingCount = universityContacts.size - maxInitialItems,
                                onToggle = { showAllUniversityContacts = !showAllUniversityContacts }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Personal Contacts Container
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Personal Section Header with Add Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        "Personal Contacts",
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Self emergency contacts",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    if (personalContacts.isNotEmpty()) {
                                        Text(
                                            "${personalContacts.size} contact${if (personalContacts.size > 1) "s" else ""}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }

                            FilledTonalButton(
                                onClick = { showAddContactDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    contentColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(
                                    imageVector = FeatherIcons.Plus,
                                    contentDescription = "Add",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Personal Contacts List or Empty State
                        if (personalContacts.isEmpty()) {
                            EnhancedEmptyStateCard { showAddContactDialog = true }
                        } else {
                            displayedPersonalContacts.forEach { contact ->
                                EnhancedContactCard(
                                    contact = contact,
                                    onDeleteClick = {
                                        // Show confirmation dialog instead of deleting directly
                                        contactToDelete = contact
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }

                            if (personalContacts.size > maxInitialItems) {
                                EnhancedShowMoreButton(
                                    isExpanded = showAllPersonalContacts,
                                    remainingCount = personalContacts.size - maxInitialItems,
                                    onToggle = { showAllPersonalContacts = !showAllPersonalContacts }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(120.dp)) }
        }
    }

    // Add contact dialog
    if (showAddContactDialog) {
        EnhancedAddContactDialog(
            onDismiss = { showAddContactDialog = false },
            onContactAdded = { contact ->
                if (userId != null) {
                    val newContact = hashMapOf(
                        "name" to contact.name,
                        "phoneNumber" to contact.phoneNumber,
                        "profileImage" to contact.profileImage
                    )
                    db.collection("Personal_Contacts")
                        .document(userId)
                        .collection("contacts")
                        .add(newContact)
                }
                showAddContactDialog = false
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog && contactToDelete != null) {
        DeleteConfirmationDialog(
            contactName = contactToDelete!!.name,
            onConfirm = {
                if (userId != null) {
                    db.collection("Personal_Contacts")
                        .document(userId)
                        .collection("contacts")
                        .document(contactToDelete!!.id)
                        .delete()
                }
                showDeleteConfirmDialog = false
                contactToDelete = null
            },
            onDismiss = {
                showDeleteConfirmDialog = false
                contactToDelete = null
            }
        )
    }
}

// New Delete Confirmation Dialog Component
@Composable
fun DeleteConfirmationDialog(
    contactName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Delete Contact",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "Are you sure you want to delete \"$contactName\"?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun EnhancedShowMoreButton(isExpanded: Boolean, remainingCount: Int, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isExpanded) "Show Less" else "Show $remainingCount More",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun EnhancedContactCard(
    contact: Contact,
    onDeleteClick: (() -> Unit)? // keep delete customizable
) {
    val context = LocalContext.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    val maxSwipeDistance = 80.dp
    val maxSwipeDistancePx = with(androidx.compose.ui.platform.LocalDensity.current) { maxSwipeDistance.toPx() }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Background delete button
        if (onDeleteClick != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.CenterEnd
            ) {
                FilledIconButton(
                    onClick = {
                        onDeleteClick()
                        offsetX = 0f
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(40.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Main card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.toInt(), 0) }
                .pointerInput(contact.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = if (offsetX < -maxSwipeDistancePx / 2 && onDeleteClick != null) {
                                -maxSwipeDistancePx
                            } else {
                                0f
                            }
                        }
                    ) { _, dragAmount ->
                        if (onDeleteClick != null) {
                            val newOffset = offsetX + dragAmount
                            offsetX = newOffset.coerceIn(-maxSwipeDistancePx, 0f)
                        }
                    }
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile image/icon (unchanged)
                Box(
                    modifier = Modifier.size(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!contact.isUniversityContact && contact.profileImage != null) {
                        AsyncImage(
                            model = contact.profileImage,
                            contentDescription = "Profile Image",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(
                                    if (contact.isUniversityContact)
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    else MaterialTheme.colorScheme.secondaryContainer,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (contact.isUniversityContact) Icons.Default.School else Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (contact.isUniversityContact)
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Contact info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        contact.phoneNumber,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    if (contact.isUniversityContact) {
                        Text(
                            "Official Contact",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Call button
                FilledIconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:${contact.phoneNumber}")
                        }
                        context.startActivity(intent)
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedAddContactDialog(onDismiss: () -> Unit, onContactAdded: (Contact) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var profileImageUrl by remember { mutableStateOf<String?>(null) }
    var localImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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

                    if (bytes != null) {
                        App.supabase.storage.from("contact-images").upload(fileName, bytes)
                        val publicUrl = App.supabase.storage.from("contact-images").publicUrl(fileName)
                        profileImageUrl = publicUrl
                        println("Uploaded successfully: $profileImageUrl")
                    }
                } catch (e: Exception) {
                    println("Upload exception: ${e.message}")
                    e.printStackTrace()
                } finally {
                    isUploading = false
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Add Contact",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Add a new emergency contact",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Enhanced Profile Image Section with Border and Camera Button
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Main profile image container with border
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .border(
                                width = 3.dp,
                                color = if (localImageUri != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .padding(4.dp) // Space between border and image
                    ) {
                        if (localImageUri != null) {
                            AsyncImage(
                                model = localImageUri,
                                contentDescription = "Profile Image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Upload indicator overlay
                        if (isUploading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    }

                    // Camera button at bottom right of the circle border
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-4).dp, y = (-4).dp) // Position at bottom right of border
                    ) {
                        FloatingActionButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.size(36.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = "Upload Photo",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Form Fields
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    isError = nameError,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    supportingText = if (nameError) {
                        { Text("Name is required", color = MaterialTheme.colorScheme.error) }
                    } else null
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it; phoneError = false },
                    label = { Text("Phone Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    isError = phoneError,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    supportingText = if (phoneError) {
                        { Text("Phone number is required", color = MaterialTheme.colorScheme.error) }
                    } else null
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            nameError = name.isBlank()
                            phoneError = phoneNumber.isBlank()
                            if (!nameError && !phoneError) {
                                val newContact = Contact(
                                    name = name.trim(),
                                    phoneNumber = phoneNumber.trim(),
                                    profileImage = profileImageUrl,
                                    isUniversityContact = false
                                )
                                onContactAdded(newContact)
                            }
                        },
                        enabled = !isUploading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Add")
                        }
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedEmptyStateCard(onAddContact: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "No Emergency Contacts",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "Add trusted contacts for emergencies.\nThey'll be just a tap away when you need them.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(
                onClick = onAddContact,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add First Contact")
            }
        }
    }
}