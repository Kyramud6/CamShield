package com.camshield.app.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.camshield.app.components.*
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val auth = FirebaseAuth.getInstance()
    var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }

    if (!isLoggedIn) {
        MinimalistLoginScreen(
            onLoginSuccess = {
                isLoggedIn = true
            },
            onForgotPassword = {
                // Handle forgot password navigation
            }
        )
    } else {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var selectedTab by remember { mutableStateOf(0) }
        var destinationLatLng by remember { mutableStateOf<LatLng?>(null) }
        var destinationName by remember { mutableStateOf<String?>(null) }

        // Fire alert states
        var showFireAlert by remember { mutableStateOf(false) }
        var fireMessage by remember { mutableStateOf("") }

        // SOS button states
        var isSOSOverlayVisible by remember { mutableStateOf(false) }
        var isRecording by remember { mutableStateOf(false) }
        var currentLocation by remember { mutableStateOf("Getting location...") }
        var isButtonPressed by remember { mutableStateOf(false) }

        // 🔥 Listen for Fire Emergencies
        LaunchedEffect(Unit) {
            val db = FirebaseFirestore.getInstance()
            val appOpenTime = System.currentTimeMillis()
            db.collection("SOS")
                .whereEqualTo("type", "fire_emergency")
                .whereEqualTo("status", "open")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        println("❌ Firestore listen failed: $e")
                        return@addSnapshotListener
                    }

                    for (doc in snapshots!!.documentChanges) {
                        if (doc.type.name == "ADDED") {
                            val data = doc.document.data
                            
                            val alertTime = (data["time"] as? String)?.let {
                                try {
                                    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                                        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                                        .parse(it)?.time ?: 0L
                                } catch (ex: Exception) {
                                    0L
                                }
                            } ?: 0L

                            if (alertTime >= appOpenTime) {
                                fireMessage = """
                🚨 FIRE EMERGENCY 🚨
            """.trimIndent()
                                showFireAlert = true
                            }
                        }
                    }
                }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
            drawerContent = {
                SideDrawerContent(
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                    onNavigateToLogin = {
                        // Handle logout navigation - set isLoggedIn to false
                        isLoggedIn = false
                    }
                )
            },
            content = {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (selectedTab == 0) {
                            ModernTopBar(
                                context = LocalContext.current,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                currentLocation = null,
                                onPlaceSelected = { latLng, placeName ->
                                    // Keep your existing local state
                                    destinationLatLng = latLng
                                    destinationName = placeName

                                    // ADD: Save to Firebase
                                    val currentUser = FirebaseAuth.getInstance().currentUser
                                    if (currentUser != null) {
                                        val destinationData = mapOf(
                                            "latitude" to latLng.latitude,
                                            "longitude" to latLng.longitude,
                                            "name" to placeName,
                                            "savedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                        )

                                        FirebaseFirestore.getInstance()
                                            .collection("Users")
                                            .document(currentUser.uid)
                                            .update("currentDestination", destinationData)
                                    }
                                }
                            )
                        }
                    },
                    bottomBar = {
                        ModernBottomNavigationBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                    },
                    containerColor = Color(0xFFF8F9FA)
                ) { paddingValues ->

                    // Main content area - wrap each screen with Box for padding
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        when (selectedTab) {
                            0 -> MainMapScreen(
                                onMenuClick = { scope.launch { drawerState.open() } },
                                initialDestination = destinationLatLng,
                                initialDestinationName = destinationName,
                                onClearDestination = {
                                    destinationLatLng = null
                                    destinationName = null
                                    val currentUser = FirebaseAuth.getInstance().currentUser
                                    if (currentUser != null) {
                                        FirebaseFirestore.getInstance()
                                            .collection("Users")
                                            .document(currentUser.uid)
                                            .update("currentDestination", null)
                                    }
                                }
                            )

                            1 -> IncidentReportScreen()

                            2 -> EmergencyContactScreen()

                            3 -> ProfileSettingScreen()
                        }
                    }

                    // Fire Alert Dialog - appears over all content
                    if (showFireAlert) {
                        AlertDialog(
                            onDismissRequest = { /* prevent dismiss */ },
                            confirmButton = {
                                TextButton(onClick = { showFireAlert = false }) {
                                    Text("OK")
                                }
                            },
                            title = { Text("FIRE ALERT") },
                            text = { Text(fireMessage) }
                        )
                    }
                }
            }
        )
    }
}