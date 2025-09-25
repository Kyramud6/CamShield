package com.camshield.app.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import compose.icons.FeatherIcons
import compose.icons.feathericons.Menu
import kotlin.math.*

// Data class for place predictions with navigation info
data class PlacePrediction(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val fullText: String,
    val distance: String? = null,
    val estimatedTime: String? = null,
    val latLng: LatLng? = null
)

@Composable
fun ModernTopBar(
    context: Context,
    onMenuClick: () -> Unit,
    onPlaceSelected: (LatLng, String) -> Unit,
    currentLocation: LatLng? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var predictions by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }

    // Places client
    val placesClient = remember { Places.createClient(context) }

    // Debounced search
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && searchQuery.length >= 2) {
            delay(300) // Debounce search
            searchPlaces(
                placesClient = placesClient,
                query = searchQuery,
                currentLocation = currentLocation,
                onResults = { results ->
                    predictions = results
                    showSuggestions = results.isNotEmpty()
                },
                onError = { error ->
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    showSuggestions = false
                }
            )
        } else {
            predictions = emptyList()
            showSuggestions = false
        }
    }

    Column(modifier = modifier) {
        // Top search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .zIndex(2f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (it.isBlank()) {
                        showSuggestions = false
                        predictions = emptyList()
                    }
                },
                placeholder = {
                    Text(
                        text = "Search location",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                singleLine = true,
                enabled = !isSearching,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    focusedPlaceholderColor = Color.Transparent,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ),
                leadingIcon = {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                trailingIcon = {
                    when {
                        isSearching -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        searchQuery.isNotEmpty() -> {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    showSuggestions = false
                                    predictions = emptyList()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Menu button
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = FeatherIcons.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Search results dropdown
        AnimatedVisibility(
            visible = showSuggestions,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = Modifier.zIndex(1f)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(predictions) { prediction ->
                        PlaceSuggestionItem(
                            prediction = prediction,
                            onClick = {
                                // Get place details and navigate
                                scope.launch {
                                    isSearching = true
                                    getPlaceDetails(
                                        placesClient = placesClient,
                                        placeId = prediction.placeId,
                                        onSuccess = { latLng, name ->
                                            isSearching = false
                                            onPlaceSelected(latLng, name)
                                            searchQuery = ""
                                            showSuggestions = false
                                            predictions = emptyList()
                                        },
                                        onError = { error ->
                                            isSearching = false
                                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        )

                        if (prediction != predictions.last()) {
                            Divider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceSuggestionItem(
    prediction: PlacePrediction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Location icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Place details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prediction.primaryText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = prediction.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            // Navigation info row
            if (prediction.distance != null || prediction.estimatedTime != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    prediction.distance?.let { distance ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Straighten,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = distance,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    prediction.estimatedTime?.let { time ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helper function to search places with navigation info
private suspend fun searchPlaces(
    placesClient: com.google.android.libraries.places.api.net.PlacesClient,
    query: String,
    currentLocation: LatLng?,
    onResults: (List<PlacePrediction>) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val predictions = response.autocompletePredictions.map { prediction ->
                    // Extract primary and secondary text
                    val primaryText = prediction.getPrimaryText(null).toString()
                    val secondaryText = prediction.getSecondaryText(null).toString()
                    val fullText = prediction.getFullText(null).toString()

                    // Calculate distance and time if current location is available
                    var distance: String? = null
                    var estimatedTime: String? = null

                    // For now, we'll add placeholder values - in a real implementation,
                    // you'd fetch the place details and calculate actual distance/time
                    if (currentLocation != null) {
                        // Placeholder calculations - replace with actual implementation
                        val estimatedDistance = (Math.random() * 10 + 0.5).toFloat()
                        distance = if (estimatedDistance < 1) {
                            "${(estimatedDistance * 1000).toInt()}m"
                        } else {
                            String.format("%.1f km", estimatedDistance)
                        }

                        val estimatedMinutes = (estimatedDistance * 3 + 2).toInt()
                        estimatedTime = "${estimatedMinutes} min"
                    }

                    PlacePrediction(
                        placeId = prediction.placeId,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        fullText = fullText,
                        distance = distance,
                        estimatedTime = estimatedTime
                    )
                }
                onResults(predictions)
            }
            .addOnFailureListener { exception ->
                onError("Search failed: ${exception.message}")
            }
    } catch (e: Exception) {
        onError("Search error: ${e.message}")
    }
}

// Helper function to get place details
private suspend fun getPlaceDetails(
    placesClient: com.google.android.libraries.places.api.net.PlacesClient,
    placeId: String,
    onSuccess: (LatLng, String) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val fetchRequest = FetchPlaceRequest.builder(
            placeId,
            listOf(Place.Field.LAT_LNG, Place.Field.NAME)
        ).build()

        placesClient.fetchPlace(fetchRequest)
            .addOnSuccessListener { response ->
                response.place.latLng?.let { latLng ->
                    val name = response.place.name ?: "Unknown Location"
                    onSuccess(
                        LatLng(latLng.latitude, latLng.longitude),
                        name
                    )
                } ?: onError("Location coordinates not found")
            }
            .addOnFailureListener { exception ->
                onError("Failed to get place details: ${exception.message}")
            }
    } catch (e: Exception) {
        onError("Error getting place details: ${e.message}")
    }
}

// Utility function to calculate distance between two points
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371.0 // Earth's radius in kilometers
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}

// Utility function to estimate walking time (assuming average walking speed of 5 km/h)
private fun estimateWalkingTime(distanceKm: Double): Int {
    val walkingSpeedKmh = 5.0
    return (distanceKm / walkingSpeedKmh * 60).roundToInt() // Convert to minutes
}