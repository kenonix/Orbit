package com.orbit.app.ui

import androidx.compose.ui.graphics.Color
import com.orbit.app.engine.OsmMapData
import com.orbit.app.engine.OsmPlace
import com.orbit.app.engine.SpatialGridIndex
import kotlin.math.*

// ==========================================
// 🗺️ MAP UTILITIES & PROJECTION
// ==========================================
fun getPixelX(lon: Double, zoom: Double): Double {
    return 256.0 * Math.pow(2.0, zoom) * (lon + 180.0) / 360.0
}

fun getPixelY(lat: Double, zoom: Double): Double {
    val latRad = Math.toRadians(lat)
    return 256.0 * Math.pow(2.0, zoom) * (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0
}

fun pixelXToLon(x: Double, zoom: Double): Double {
    return x * 360.0 / (256.0 * Math.pow(2.0, zoom)) - 180.0
}

fun pixelYToLat(y: Double, zoom: Double): Double {
    val n = Math.PI - 2.0 * Math.PI * y / (256.0 * Math.pow(2.0, zoom))
    return Math.toDegrees(Math.atan(Math.sinh(n)))
}

fun findNearestPlaceLocal(lat: Double, lon: Double, mapData: OsmMapData, spatialIndex: SpatialGridIndex?): Pair<OsmPlace, Double>? {
    val cellPlaces = spatialIndex?.queryPlaces(lat - 0.05, lat + 0.05, lon - 0.05, lon + 0.05) ?: mapData.places
    var bestPlace: OsmPlace? = null
    var bestDist = Double.MAX_VALUE
    for (place in cellPlaces) {
        val dx = place.lon - lon
        val dy = place.lat - lat
        val dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < bestDist) {
            bestDist = dist
            bestPlace = place
        }
    }
    return if (bestPlace != null) {
        val km = bestDist * 111.0
        Pair(bestPlace, km * 1000.0) // Return place and distance in meters
    } else {
        null
    }
}

fun getRoadColor(type: String, theme: AppTheme): Color {
    return when (type) {
        "motorway", "trunk" -> theme.mapMotorway
        "primary" -> theme.mapPrimary
        "secondary" -> theme.mapSecondary
        "tertiary" -> theme.mapTertiary
        else -> theme.mapMinor
    }
}

fun getRoadWidthDp(type: String, zoom: Double): Float {
    val base = when (type) {
        "motorway", "trunk" -> 4.0f
        "primary" -> 3.0f
        "secondary" -> 2.0f
        "tertiary" -> 1.5f
        else -> 1.0f
    }
    val factor = if (zoom >= 15.0) 1.5f else if (zoom <= 11.0) 0.7f else 1.0f
    return base * factor
}

fun shouldShowPlace(type: String, zoom: Double): Boolean {
    return when (type) {
        "city" -> zoom >= 8.0
        "town" -> zoom >= 10.0
        "village" -> zoom >= 12.0
        "suburb", "neighbourhood" -> zoom >= 14.0
        else -> zoom >= 15.0
    }
}
