package com.camshield.app.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import compose.icons.FeatherIcons
import compose.icons.feathericons.Menu

@Composable
fun ModernTopBar(
    context: Context,
    onMenuClick: () -> Unit,
    onPlaceSelected: (LatLng, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    // Places client
    val placesClient = remember { Places.createClient(context) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 🔹 Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search location",
                        color = Color.Gray
                    )
                }
            },
            label = null, // Remove any label that might interfere
            singleLine = true,
            enabled = !isSearching,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                disabledTextColor = Color.Gray,
                focusedPlaceholderColor = Color.Transparent, // Hide placeholder when focused
                unfocusedPlaceholderColor = Color.Gray,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray
            ),
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = {
                        if (searchQuery.isNotBlank()) {
                            isSearching = true
                            val request = FindAutocompletePredictionsRequest.builder()
                                .setQuery(searchQuery)
                                .build()

                            placesClient.findAutocompletePredictions(request)
                                .addOnSuccessListener { response ->
                                    if (response.autocompletePredictions.isNotEmpty()) {
                                        val prediction = response.autocompletePredictions[0]
                                        val placeId = prediction.placeId
                                        val placeName = prediction.getFullText(null).toString()

                                        val fetchRequest = FetchPlaceRequest.builder(
                                            placeId,
                                            listOf(Place.Field.LAT_LNG, Place.Field.NAME)
                                        ).build()

                                        placesClient.fetchPlace(fetchRequest)
                                            .addOnSuccessListener { placeResponse ->
                                                isSearching = false
                                                placeResponse.place.latLng?.let { latLng ->
                                                    val finalPlaceName = placeResponse.place.name ?: placeName
                                                    onPlaceSelected(
                                                        LatLng(latLng.latitude, latLng.longitude),
                                                        finalPlaceName
                                                    )
                                                    searchQuery = "" // Clear search after selection
                                                }
                                            }
                                            .addOnFailureListener {
                                                isSearching = false
                                                Toast.makeText(context, "Error fetching place", Toast.LENGTH_SHORT).show()
                                            }
                                    } else {
                                        isSearching = false
                                        Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .addOnFailureListener {
                                    isSearching = false
                                    Toast.makeText(context, "Error searching location", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Search")
                    }
                }
            },
            modifier = Modifier
                .weight(1f)  // Take remaining width
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 🔹 Menu button
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = FeatherIcons.Menu,
                contentDescription = "Menu",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}