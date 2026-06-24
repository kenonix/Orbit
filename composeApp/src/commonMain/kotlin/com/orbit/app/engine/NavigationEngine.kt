package com.orbit.app.engine

import com.orbit.app.model.Location
import kotlin.math.*

object NavigationEngine {

    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Calculates the great-circle distance between two locations using the Haversine formula.
     * Returns distance in kilometers.
     */
    fun calculateDistance(start: Location, end: Location): Double {
        val lat1Rad = Math.toRadians(start.latitude)
        val lon1Rad = Math.toRadians(start.longitude)
        val lat2Rad = Math.toRadians(end.latitude)
        val lon2Rad = Math.toRadians(end.longitude)

        val deltaLat = lat2Rad - lat1Rad
        val deltaLon = lon2Rad - lon1Rad

        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_KM * c
    }

    /**
     * Calculates the initial bearing (azimuth) from start to end location in degrees [0, 360).
     * 0 = North, 90 = East, 180 = South, 270 = West.
     */
    fun calculateBearing(start: Location, end: Location): Double {
        val lat1Rad = Math.toRadians(start.latitude)
        val lon1Rad = Math.toRadians(start.longitude)
        val lat2Rad = Math.toRadians(end.latitude)
        val lon2Rad = Math.toRadians(end.longitude)

        val deltaLon = lon2Rad - lon1Rad

        val y = sin(deltaLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) -
                sin(lat1Rad) * cos(lat2Rad) * cos(deltaLon)

        val bearingRad = atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad)

        return (bearingDeg + 360.0) % 360.0
    }

    /**
     * Estimates the travel time (in minutes) between two locations.
     * For actual routing, this stubs GraphHopper offline results.
     * If GraphHopper graphs are not downloaded, falls back to haversine estimation.
     */
    fun estimateTravelTimeMinutes(
        start: Location,
        end: Location,
        averageSpeedKmh: Double = 50.0 // Default 50 km/h for driving, or 5 km/h for walking
    ): Double {
        val distanceKm = calculateDistance(start, end)
        // Add a 30% detour factor for real roads
        val estimatedRoadDistance = distanceKm * 1.3
        val hours = estimatedRoadDistance / averageSpeedKmh
        return hours * 60.0
    }
}

// Helper object for Java Math function stubs since Kotlin common math doesn't have toRadians/toDegrees
private object Math {
    fun toRadians(degrees: Double): Double = degrees * PI / 180.0
    fun toDegrees(radians: Double): Double = radians * 180.0 / PI
}
