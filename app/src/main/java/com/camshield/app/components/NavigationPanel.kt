// NavigationPanel.kt - Create this as a new separate file
package com.camshield.app.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun NavigationPanel(
    navigationState: NavigationState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = navigationState.isNavigating && navigationState.currentStep != null,
        enter = slideInVertically(
            initialOffsetY = { -it }, // slide in from top
            animationSpec = tween(durationMillis = 300)
        ),
        exit = slideOutVertically(
            targetOffsetY = { -it }, // slide out to top
            animationSpec = tween(durationMillis = 300)
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
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Step counter
                Text(
                    text = "Step ${navigationState.stepIndex + 1} of ${navigationState.totalSteps}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Main instruction
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = getManeuverIcon(navigationState.currentStep?.maneuver),
                        contentDescription = "Direction",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cleanHtmlInstructions(
                                navigationState.currentStep?.html_instructions ?: ""
                            ),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row {
                            navigationState.currentStep?.distance?.text?.let { distance ->
                                Text(
                                    text = distance,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            navigationState.currentStep?.duration?.text?.let { duration ->
                                Text(
                                    text = " • $duration",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Distance to destination
                navigationState.distanceToDestination?.let { distance ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Distance to destination: $distance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun NavigationControls(
    isNavigating: Boolean,
    onStartNavigation: () -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isNavigating) {
                Button(
                    onClick = onStartNavigation,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Navigation")
                }
            } else {
                OutlinedButton(
                    onClick = onStopNavigation,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop Navigation")
                }
            }
        }
    }
}

private fun getManeuverIcon(maneuver: String?): ImageVector {
    return when (maneuver?.lowercase()) {
        "turn-left", "turn-slight-left", "turn-sharp-left" -> Icons.Default.TurnLeft
        "turn-right", "turn-slight-right", "turn-sharp-right" -> Icons.Default.TurnRight
        "straight", "continue" -> Icons.Default.Straight
        else -> Icons.Default.Navigation
    }
}

// Helper function for cleaning HTML instructions
/*fun cleanHtmlInstructions(htmlInstructions: String): String {
    return htmlInstructions
        .replace("<[^>]+>".toRegex(), "") // Remove HTML tags
        .replace("&nbsp;", " ")
        .trim()
}*/