// NavigationPanel.kt - Updated with Route Details Under Top Bar
package com.camshield.app.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Import centralized data classes
import com.camshield.app.data.*

@Composable
fun NavigationPanel(
    navigationState: NavigationState,
    routeInfo: RouteInfo? = null,
    destinationName: String? = null,
    isLoadingRoute: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Show panel when:
    // 1. Navigation is active with current step, OR
    // 2. Route info is available (before navigation starts), OR
    // 3. Route is loading
    val shouldShowPanel = navigationState.isNavigating && navigationState.currentStep != null ||
            routeInfo != null ||
            isLoadingRoute

    AnimatedVisibility(
        visible = shouldShowPanel,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(durationMillis = 300)
        ),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300)
        ),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Show different content based on navigation state
                when {
                    // ACTIVE NAVIGATION - Show step instructions
                    navigationState.isNavigating && navigationState.currentStep != null -> {
                        NavigationInstructions(navigationState)
                    }

                    // ROUTE LOADING - Show loading state
                    isLoadingRoute -> {
                        RouteLoadingContent(destinationName)
                    }

                    // ROUTE READY - Show route details before navigation starts
                    routeInfo != null -> {
                        RouteDetailsContent(routeInfo, destinationName)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationInstructions(navigationState: NavigationState) {
    // Step counter with progress
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Step ${navigationState.stepIndex + 1} of ${navigationState.totalSteps}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Progress indicator
        LinearProgressIndicator(
            progress = (navigationState.stepIndex + 1).toFloat() / navigationState.totalSteps.toFloat(),
            modifier = Modifier
                .width(60.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Main instruction with enhanced styling
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Direction icon with background
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getManeuverIcon(navigationState.currentStep?.maneuver),
                contentDescription = "Direction",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cleanHtmlInstructions(
                    navigationState.currentStep?.html_instructions ?: ""
                ),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                navigationState.currentStep?.distance?.text?.let { distance ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Straighten,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = distance,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                navigationState.currentStep?.duration?.text?.let { duration ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = duration,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Distance to destination with enhanced styling
    navigationState.distanceToDestination?.let { distance ->
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$distance to destination",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RouteLoadingContent(destinationName: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Planning your route...",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            destinationName?.let { name ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "To: $name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RouteDetailsContent(routeInfo: RouteInfo, destinationName: String?) {
    // Destination header
    destinationName?.let { name ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    // Route details in a horizontal layout
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Distance
        RouteDetailItem(
            icon = Icons.Default.Straighten,
            value = routeInfo.totalDistance.ifEmpty { "..." },
            label = "Distance",
            modifier = Modifier.weight(1f)
        )

        // Duration
        RouteDetailItem(
            icon = Icons.Default.Schedule,
            value = routeInfo.estimatedDuration.ifEmpty { "..." },
            label = "Duration",
            modifier = Modifier.weight(1f)
        )

        // Arrival Time
        RouteDetailItem(
            icon = Icons.Default.AccessTime,
            value = routeInfo.estimatedArrivalTime.ifEmpty { "..." },
            label = "Arrival",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RouteDetailItem(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BottomNavigationControls(
    isNavigating: Boolean,
    hasDestination: Boolean,
    hasLocation: Boolean,
    isLoadingRoute: Boolean = false,
    onStartNavigation: () -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = hasDestination,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(400)
        ) + fadeIn(tween(400)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(400)
        ) + fadeOut(tween(400)),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .width(200.dp)
                .padding(end = 8.dp)
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isNavigating) {
                    Button(
                        onClick = onStartNavigation,
                        enabled = hasLocation && !isLoadingRoute,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isLoadingRoute) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Loading...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (hasLocation) "Start" else "Getting GPS...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onStopNavigation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Stop",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

// Enhanced route visualization for GoogleMapScreen
@Composable
fun EnhancedRoutePolyline(
    points: List<com.google.android.gms.maps.model.LatLng>,
    isNavigating: Boolean,
    currentStepIndex: Int = 0,
    steps: List<Step> = emptyList()
) {
    if (points.size > 1) {
        // Main route line with gradient effect
        com.google.maps.android.compose.Polyline(
            points = points,
            color = if (isNavigating) {
                Color(0xFF1976D2) // Material Blue
            } else {
                Color(0xFF757575) // Gray for preview
            },
            width = if (isNavigating) 12f else 8f,
        )

        // Add a thinner white line on top for better visibility
        com.google.maps.android.compose.Polyline(
            points = points,
            color = Color.White,
            width = if (isNavigating) 4f else 2f,
        )

        // Add shadow/outline effect
        com.google.maps.android.compose.Polyline(
            points = points,
            color = Color.Black.copy(alpha = 0.3f),
            width = if (isNavigating) 14f else 10f,
        )
    }
}

private fun getManeuverIcon(maneuver: String?): ImageVector {
    return when (maneuver?.lowercase()) {
        "turn-left", "turn-slight-left" -> Icons.Default.TurnLeft
        "turn-sharp-left" -> Icons.Default.TurnSharpLeft
        "turn-right", "turn-slight-right" -> Icons.Default.TurnRight
        "turn-sharp-right" -> Icons.Default.TurnSharpRight
        "straight", "continue" -> Icons.Default.Straight
        "uturn-left", "uturn-right" -> Icons.Default.UTurnLeft
        "merge" -> Icons.Default.Merge
        "roundabout-left", "roundabout-right" -> Icons.Default.RotateLeft
        "fork-left", "fork-right" -> Icons.Default.ForkLeft
        else -> Icons.Default.Navigation
    }
}