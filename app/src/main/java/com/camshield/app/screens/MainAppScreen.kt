package com.camshield.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.camshield.app.components.*
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.maps.model.LatLng

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val auth = FirebaseAuth.getInstance()
    var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }

    if (!isLoggedIn) {
        MinimalistLoginScreen(
            onLoginSuccess = {
                isLoggedIn = true // show main app after login
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

        // SOS button states
        var isSOSOverlayVisible by remember { mutableStateOf(false) }
        var isRecording by remember { mutableStateOf(false) }
        var currentLocation by remember { mutableStateOf("Getting location...") }
        var isButtonPressed by remember { mutableStateOf(false) }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
            drawerContent = {
                SideDrawerContent(onCloseDrawer = { scope.launch { drawerState.close() } })
            },
            content = {
                Scaffold(
                    topBar = {
                        if (selectedTab == 0) {
                            ModernTopBar(
                                context = LocalContext.current,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onPlaceSelected = { latLng, placeName ->  // Updated callback signature
                                    destinationLatLng = latLng
                                    destinationName = placeName
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (selectedTab) {
                            0 -> MainMapScreen(
                                onMenuClick = { scope.launch { drawerState.open() } },
                                initialDestination = destinationLatLng,  // Pass destination to map
                                initialDestinationName = destinationName, // Pass destination name
                                modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())
                            )
                            1 -> IncidentReportScreen()
                            2 -> EmergencyContactScreen()
                            3 -> ProfileSettingScreen()
                        }
                    }
                }
            }
        )
    }
}