// NavigationDataClasses.kt - All navigation-related data classes in one place
package com.camshield.app.data

import java.text.SimpleDateFormat
import java.util.*

// API Response data classes
data class DirectionsResponse(val routes: List<Route>, val status: String)
data class Route(val legs: List<Leg>, val overview_polyline: OverviewPolyline)
data class Leg(val steps: List<Step>, val distance: Distance, val duration: Duration)
data class OverviewPolyline(val points: String)

// Navigation data classes
data class Step(
    val html_instructions: String,
    val distance: Distance,
    val duration: Duration,
    val start_location: LocationData,
    val end_location: LocationData,
    val polyline: OverviewPolyline? = null,
    val maneuver: String?
)

data class Distance(val text: String, val value: Int)
data class Duration(val text: String, val value: Int)
data class LocationData(val lat: Double, val lng: Double)

data class NavigationState(
    val isNavigating: Boolean = false,
    val currentStep: Step? = null,
    val stepIndex: Int = 0,
    val totalSteps: Int = 0,
    val distanceToDestination: String? = null
)

data class RouteInfo(
    val totalDistance: String = "",
    val estimatedDuration: String = "",
    val estimatedArrivalTime: String = ""
)

// Utility functions
fun extractRouteInfo(route: Route): RouteInfo {
    val totalDistance = route.legs.sumOf { it.distance.value }
    val totalDuration = route.legs.sumOf { it.duration.value }

    val distanceText = if (totalDistance < 1000) {
        "${totalDistance}m"
    } else {
        String.format("%.1f km", totalDistance / 1000.0)
    }

    val durationText = if (totalDuration < 3600) {
        "${totalDuration / 60} min"
    } else {
        val hours = totalDuration / 3600
        val minutes = (totalDuration % 3600) / 60
        if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
    }

    val arrivalTime = calculateEstimatedArrivalTime(totalDuration)

    return RouteInfo(
        totalDistance = distanceText,
        estimatedDuration = durationText,
        estimatedArrivalTime = arrivalTime
    )
}

fun calculateEstimatedArrivalTime(durationInSeconds: Int): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.SECOND, durationInSeconds)
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    return timeFormat.format(calendar.time)
}

// Add this function to your NavigationDataClasses.kt file
fun cleanHtmlInstructions(htmlInstructions: String): String {
    return htmlInstructions
        .replace("<[^>]+>".toRegex(), "") // Remove HTML tags
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()
}