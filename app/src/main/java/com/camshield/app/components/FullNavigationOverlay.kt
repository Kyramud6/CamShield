// FullNavigationOverlay.kt - Minimal overlay for map-based visual navigation
package com.camshield.app.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// Import centralized data classes
import com.camshield.app.data.NavigationState
import com.camshield.app.data.cleanHtmlInstructions

@Composable
fun FullNavigationOverlay(
    navigationState: NavigationState,
    onExitNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Minimal overlay - let the map do the visual navigation
    Box(modifier = modifier.fillMaxSize()) {

        // Top navigation instruction banner (minimal)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direction icon
                Icon(
                    imageVector = getManeuverIcon(navigationState.currentStep?.maneuver),
                    contentDescription = "Direction",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Current instruction (compact)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cleanHtmlInstructions(
                            navigationState.currentStep?.html_instructions ?: "Continue straight"
                        ),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )

                    // Distance to next turn
                    navigationState.currentStep?.distance?.text?.let { distance ->
                        Text(
                            text = "in $distance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Exit button
                IconButton(
                    onClick = onExitNavigation,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit Navigation",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bottom navigation info (minimal)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.BottomCenter),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Distance to destination
                navigationState.distanceToDestination?.let { distance ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = distance,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Progress indicator
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${navigationState.stepIndex + 1}/${navigationState.totalSteps}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "steps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // End navigation button
                Button(
                    onClick = onExitNavigation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "End",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
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