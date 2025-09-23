package com.camshield.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.camshield.app.screens.*
import com.camshield.app.ui.theme.CamSHIELDAppTheme
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, "AIzaSyCPmhj7Hot40l9AMeJq8dKPJ-7UHq5-S3E")
        }

        setContent {
            CamSHIELDAppTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var showSplash by remember { mutableStateOf(true) }
    var isLoggedIn by remember { mutableStateOf(false) }

    if (showSplash) {
        SplashScreen(onSplashFinished = { showSplash = false })
    } else {
        if (!isLoggedIn) {
            MinimalistLoginScreen(
                onLoginSuccess = { isLoggedIn = true }
            )
        } else {
            MainApp()
        }
    }
}
