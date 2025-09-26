// SOSFullScreenOverlay.kt
package com.camshield.app.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@Composable
fun SOSFullScreenOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    location: String = "Getting location...",
    onLocationUpdate: () -> Unit = {},
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    isRecording: Boolean = false
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)) + scaleOut(animationSpec = tween(300))
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            SOSFullScreenContent(
                onDismiss = onDismiss,
                location = location,
                onLocationUpdate = onLocationUpdate,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                isRecording = isRecording
            )
        }
    }
}

@Composable
private fun SOSFullScreenContent(
    onDismiss: () -> Unit,
    location: String,
    onLocationUpdate: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    isRecording: Boolean
) {
    var pulseScale by remember { mutableStateOf(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            pulseScale = 1.1f
            delay(500)
            pulseScale = 1f
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFD32F2F),
                        Color(0xFFB71C1C),
                        Color(0xFF8C1919)
                    ),
                    radius = 800f
                )
            )
    ) {
        // Close button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
                .background(
                    Color.Black.copy(alpha = 0.3f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close SOS",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated SOS Icon
            Card(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale),
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "SOS Active",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SOS ACTIVATED",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Emergency mode is now active",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Location Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Current Location",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF333333)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = location,
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onLocationUpdate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Update Location", color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Audio Recording Button
            Card(
                onClick = {
                    if (isRecording) {
                        onStopRecording()
                    } else {
                        onStartRecording()
                    }
                },
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRecording) Color(0xFF4CAF50) else Color.White
                )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                        tint = if (isRecording) Color.White else Color(0xFFD32F2F),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isRecording) "Recording..." else "Tap to Record",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            if (isRecording) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(100.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
fun ModernSOSButton(
    isPressed: Boolean,
    onSOSClick: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier
) {
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableStateOf(0f) }
    val holdDuration = 1000L // 1 second in milliseconds

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            while (isHolding) {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime
                holdProgress = (elapsed.toFloat() / holdDuration).coerceAtMost(1f)

                if (holdProgress >= 1f) {
                    onSOSClick()
                    isHolding = false
                    holdProgress = 0f
                    break
                }
                delay(16) // ~60 FPS updates
            }
            // Reset progress if released early
            if (!isHolding) {
                holdProgress = 0f
            }
        } else {
            holdProgress = 0f
        }
    }

    Card(
        modifier = modifier
            .size(width = 60.dp, height = 60.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        tryAwaitRelease()
                        isHolding = false
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHolding || isPressed) 20.dp else 12.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isHolding) Color(0xFFFF4444) else Color(0xFFFF0700)
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = if (isHolding) {
                            listOf(Color(0xFFFF4444), Color(0xFFFF0700))
                        } else {
                            listOf(Color(0xFFFF0700), Color(0xFFFF0700))
                        }
                    )
                )
        ) {
            // Progress indicator background
            if (isHolding) {
                CircularProgressIndicator(
                    progress = holdProgress,
                    modifier = Modifier
                        .size(60.dp),
                    color = Color.White,
                    strokeWidth = 3.dp,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "SOS",
                    color = Color.White,
                    fontSize = if (isHolding) 18.sp else 20.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isHolding) {
                    Text(
                        text = "Hold...",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// Usage Example in your Activity/Fragment
@Composable
fun SOSScreen() {
    var isSOSOverlayVisible by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf("Getting location...") }
    var isButtonPressed by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Your main content here

        // SOS Button with Press-and-Hold functionality
        ModernSOSButton(
            isPressed = isButtonPressed,
            onSOSClick = {
                isButtonPressed = true
                isSOSOverlayVisible = true
                // Reset button press state after a delay
                // You can handle this with a coroutine
            },
            scale = 1f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        // SOS Full Screen Overlay
        SOSFullScreenOverlay(
            isVisible = isSOSOverlayVisible,
            onDismiss = {
                isSOSOverlayVisible = false
                isButtonPressed = false
                if (isRecording) {
                    isRecording = false
                    // Stop recording logic here
                }
            },
            location = currentLocation,
            onLocationUpdate = {
                // Implement location update logic
                currentLocation = "Updating location..."
                // Add your location service call here
            },
            onStartRecording = {
                isRecording = true
                // Implement audio recording start logic
            },
            onStopRecording = {
                isRecording = false
                // Implement audio recording stop logic
            },
            isRecording = isRecording
        )
    }
}