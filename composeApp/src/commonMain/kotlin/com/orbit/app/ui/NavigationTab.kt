package com.orbit.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.app.model.*
import com.orbit.app.engine.*
import kotlin.math.*

// ==========================================
// 🧭 MAP & COMPASS NAVIGATION TAB
// ==========================================
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun NavigationTab(
    schedules: List<Schedule>,
    lang: Language,
    mapData: OsmMapData?,
    spatialIndex: SpatialGridIndex?,
    isMapLoading: Boolean,
    mapLoadProgress: String,
    mapLoadError: String?
) {
    val textMeasurer = rememberTextMeasurer()
    var myLat by remember { mutableStateOf("37.5665") } // Default Seoul City Hall
    var myLon by remember { mutableStateOf("126.9780") }

    var selectedSchedule by remember { mutableStateOf<Schedule?>(null) }
    var bearingAngle by remember { mutableStateOf(0.0) }
    var distanceKm by remember { mutableStateOf(0.0) }
    var etaMinutes by remember { mutableStateOf(0.0) }

    // Map state variables
    var mapZoom by remember { mutableStateOf(14.0) }
    var mapCenterLat by remember { mutableStateOf(37.5665) }
    var mapCenterLon by remember { mutableStateOf(126.9780) }

    // On start or when my location changes, center map to my location
    LaunchedEffect(myLat, myLon) {
        val latVal = myLat.toDoubleOrNull() ?: 37.5665
        val lonVal = myLon.toDoubleOrNull() ?: 126.9780
        mapCenterLat = latVal
        mapCenterLon = lonVal
    }

    // Centering the map to selected target event
    LaunchedEffect(selectedSchedule) {
        selectedSchedule?.location?.let {
            mapCenterLat = it.latitude
            mapCenterLon = it.longitude
        }
    }

    // Recalculate bearing / distance dynamically
    fun updateCalculations() {
        val currentLoc = Location("Current", myLat.toDoubleOrNull() ?: 37.5665, myLon.toDoubleOrNull() ?: 126.9780)
        val destination = selectedSchedule?.location ?: return
        bearingAngle = NavigationEngine.calculateBearing(currentLoc, destination)
        distanceKm = NavigationEngine.calculateDistance(currentLoc, destination)
        etaMinutes = NavigationEngine.estimateTravelTimeMinutes(currentLoc, destination)
    }

    LaunchedEffect(selectedSchedule, myLat, myLon) {
        updateCalculations()
    }

    val localCacheDir = remember { java.io.File("./maps_cache") }
    var activeMaps by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) {
        try {
            if (localCacheDir.exists()) {
                val files = localCacheDir.listFiles { _, name -> name.endsWith("-latest.osm.pbf") }
                if (files != null) {
                    activeMaps = files.map { it.name.substringBefore("-latest.osm.pbf") }.toSet()
                }
            }
        } catch (e: Exception) {}
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(Localization.get("tab_navigation", lang), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(Localization.get("compass_help", lang), fontSize = 13.sp, color = TextSecondary)

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(SlateDarkBg)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (activeMaps.isNotEmpty()) ActiveGreen else Color.Yellow)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (activeMaps.isNotEmpty()) {
                    val names = activeMaps.map { getCountryName(it, lang) }.joinToString(", ")
                    when (lang) {
                        Language.KOREAN -> "오프라인 지도 로드됨: $names"
                        Language.JAPANESE -> "オフライン地図ロード済み: $names"
                        Language.RUSSIAN -> "Загружена офлайн-карта: $names"
                        else -> "Offline Map Loaded: $names"
                    }
                } else {
                    when (lang) {
                        Language.KOREAN -> "시뮬레이션 모드 (오프라인 지도 없음)"
                        Language.JAPANESE -> "シミュレーションモード (オフライン地図なし)"
                        Language.RUSSIAN -> "Режим 시муляции (нет офлайн-карт)"
                        else -> "Simulation Mode (No offline maps)"
                    }
                },
                fontSize = 11.sp,
                color = if (activeMaps.isNotEmpty()) ActiveGreen else TextSecondary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxSize()) {
            // Left Config Controls
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(Localization.get("set_current_coords", lang), fontWeight = FontWeight.Bold, color = AccentPurple, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = myLat,
                    onValueChange = { myLat = it },
                    label = { Text(Localization.get("lat_label", lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appTextFieldColors()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = myLon,
                    onValueChange = { myLon = it },
                    label = { Text(Localization.get("lon_label", lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appTextFieldColors()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(Localization.get("target_event", lang), fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f)
                ) {
                    val eventsWithLoc = schedules.filter { it.location != null }
                    if (eventsWithLoc.isEmpty()) {
                        item {
                            Text(Localization.get("no_locations", lang), color = TextSecondary, fontSize = 12.sp)
                        }
                    } else {
                        items(eventsWithLoc) { schedule ->
                            val isSelected = selectedSchedule?.id == schedule.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) AccentCyan.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(1.dp, if (isSelected) AccentCyan.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { selectedSchedule = schedule }
                                    .padding(12.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, "Loc", tint = if (isSelected) AccentCyan else TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(schedule.title, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Text(schedule.location?.name ?: "", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Right Visual Map Picker & Visual HUD
            Box(
                modifier = Modifier
                    .weight(2.0f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(currentThemeState.value.mapBackground)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            ) {
                if (isMapLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = AccentPurple)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(mapLoadProgress, color = TextPrimary, fontSize = 14.sp)
                    }
                } else if (mapLoadError != null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(mapLoadError, color = Color.Red, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    val path = remember { Path() }
                    // 🗺️ INTERACTIVE MAP CANVAS
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .pointerInput(mapZoom) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val cx = getPixelX(mapCenterLon, mapZoom)
                                    val cy = getPixelY(mapCenterLat, mapZoom)
                                    val newCx = cx - dragAmount.x
                                    val newCy = cy - dragAmount.y
                                    mapCenterLon = pixelXToLon(newCx, mapZoom)
                                    mapCenterLat = pixelYToLat(newCy, mapZoom)
                                }
                            }
                            .onPointerEvent(PointerEventType.Scroll) { event ->
                                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (delta != 0f) {
                                        val zoomChange = if (delta < 0) 0.5 else -0.5
                                        mapZoom = (mapZoom + zoomChange).coerceIn(10.0, 18.0)
                                    }
                                }
                    ) {
                        val activeMapData = mapData ?: SimulatedMapData.data
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val centerX = getPixelX(mapCenterLon, mapZoom)
                        val centerY = getPixelY(mapCenterLat, mapZoom)

                        fun toScreenX(lon: Double): Float = ((canvasWidth / 2) + (getPixelX(lon, mapZoom) - centerX)).toFloat()
                        fun toScreenY(lat: Double): Float = ((canvasHeight / 2) + (getPixelY(lat, mapZoom) - centerY)).toFloat()

                        val minLon = pixelXToLon(centerX - canvasWidth / 2, mapZoom)
                        val maxLon = pixelXToLon(centerX + canvasWidth / 2, mapZoom)
                        val minLat = pixelYToLat(centerY + canvasHeight / 2, mapZoom)
                        val maxLat = pixelYToLat(centerY - canvasHeight / 2, mapZoom)

                        val visibleWays = spatialIndex?.queryWays(minLat, maxLat, minLon, maxLon) ?: activeMapData.ways
                        val visiblePlaces = if (mapZoom >= 10.0) {
                            spatialIndex?.queryPlaces(minLat, maxLat, minLon, maxLon) ?: activeMapData.places
                        } else {
                            emptyList()
                        }

                        // 1. Draw Roads & Polygons (Ways)
                        for (way in visibleWays) {
                            if (way.maxLat < minLat || way.minLat > maxLat || way.maxLon < minLon || way.minLon > maxLon) continue

                            // LOD Filter
                            if (way.type == "building" && mapZoom < 15.0) continue
                            if (way.type == "water" && mapZoom < 12.0) continue

                            if (way.type !in listOf("building", "water")) {
                                if (mapZoom < 11.0 && way.type !in listOf("motorway", "trunk")) continue
                                if (mapZoom < 13.0 && way.type !in listOf("motorway", "trunk", "primary", "secondary")) continue
                                if (mapZoom < 14.0 && way.type !in listOf("motorway", "trunk", "primary", "secondary", "tertiary")) continue
                            }

                            path.reset()
                            var first = true
                            for (coord in way.coords) {
                                val sx = toScreenX(coord.lon)
                                val sy = toScreenY(coord.lat)
                                if (first) {
                                    path.moveTo(sx, sy)
                                    first = false
                                } else {
                                    path.lineTo(sx, sy)
                                }
                            }
                            if (way.type == "building" || way.type == "water") {
                                path.close()
                            }

                            if (way.type == "building") {
                                // Draw filled building polygon
                                drawPath(
                                    path = path,
                                    color = currentThemeState.value.surfaceCardColor.copy(alpha = 0.6f),
                                    style = androidx.compose.ui.graphics.drawscope.Fill
                                )
                                drawPath(
                                    path = path,
                                    color = currentThemeState.value.accentPurpleColor.copy(alpha = 0.25f),
                                    style = Stroke(width = 0.8f.dp.toPx())
                                )
                            } else if (way.type == "water") {
                                // Draw filled water body
                                drawPath(
                                    path = path,
                                    color = currentThemeState.value.mapWater,
                                    style = androidx.compose.ui.graphics.drawscope.Fill
                                )
                            } else if (way.type == "waterway_line") {
                                // Draw waterway line (river, stream, etc)
                                drawPath(
                                    path = path,
                                    color = currentThemeState.value.mapWater,
                                    style = Stroke(width = 2.5f.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )
                            } else {
                                // Draw regular road stroke
                                drawPath(
                                    path = path,
                                    color = getRoadColor(way.type, currentThemeState.value),
                                    style = Stroke(width = getRoadWidthDp(way.type, mapZoom).dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )
                            }
                        }

                        // 2. Draw Route to Target
                        val currentLoc = Location("Current", myLat.toDoubleOrNull() ?: 37.5665, myLon.toDoubleOrNull() ?: 126.9780)
                        val targetLoc = selectedSchedule?.location
                        if (targetLoc != null) {
                            val startX = toScreenX(currentLoc.longitude)
                            val startY = toScreenY(currentLoc.latitude)
                            val endX = toScreenX(targetLoc.longitude)
                            val endY = toScreenY(targetLoc.latitude)

                            // Route Line (Dotted light blue)
                            drawLine(
                                color = AccentCyan,
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 3.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                            )

                            // Target Marker (Red Circle)
                            drawCircle(
                                color = Color.Red,
                                radius = 9.dp.toPx(),
                                center = Offset(endX, endY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 4.dp.toPx(),
                                center = Offset(endX, endY)
                            )
                        }

                        // 3. Draw My Location Indicator (Blue/Purple Circle)
                        val myX = toScreenX(currentLoc.longitude)
                        val myY = toScreenY(currentLoc.latitude)
                        drawCircle(
                            color = AccentPurple,
                            radius = 9.dp.toPx(),
                            center = Offset(myX, myY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.dp.toPx(),
                            center = Offset(myX, myY)
                        )

                        // 4. Draw Place Labels
                        if (mapZoom >= 10.0) {
                            fun getPlacePriority(place: OsmPlace): Float {
                                if (place.tags.containsKey("shop")) return 55f + (place.name.length * 0.1f)
                                if (place.tags.containsKey("building")) return 54f + (place.name.length * 0.1f)
                                if (place.tags.containsKey("amenity")) return 53f + (place.name.length * 0.1f)

                                val base = when (place.type) {
                                    "city" -> 100f
                                    "town" -> 90f
                                    "village" -> 80f
                                    "suburb" -> 70f
                                    "neighbourhood" -> 60f
                                    "station" -> 50f
                                    "attraction", "tourism" -> 40f
                                    "city_hall", "park", "monument" -> 30f
                                    else -> 20f
                                }
                                return base + (place.name.length * 0.1f)
                            }

                            val sortedPlaces = visiblePlaces
                                .filter { place ->
                                    if (place.type == "road" || place.type == "highway") return@filter false
                                    val isShopOrBuilding = place.tags.containsKey("shop") || 
                                                           place.tags.containsKey("building") || 
                                                           place.tags.containsKey("amenity")
                                    if (isShopOrBuilding) {
                                        mapZoom >= 14.0
                                    } else {
                                        shouldShowPlace(place.type, mapZoom)
                                    }
                                }
                                .sortedByDescending { getPlacePriority(it) }

                            class PlacedLabel(val left: Float, val top: Float, val right: Float, val bottom: Float)
                            val placedLabels = mutableListOf<PlacedLabel>()

                            for (place in sortedPlaces) {
                                if (place.lat < minLat || place.lat > maxLat || place.lon < minLon || place.lon > maxLon) continue

                                val px = toScreenX(place.lon)
                                val py = toScreenY(place.lat)

                                val labelStyle = TextStyle(
                                    color = currentThemeState.value.textPrimaryColor,
                                    fontSize = if (place.type in listOf("city", "town")) 11.sp else 9.sp,
                                    fontWeight = if (place.type in listOf("city", "town")) FontWeight.Bold else FontWeight.Normal
                                )
                                val measured = textMeasurer.measure(place.name)
                                val labelWidth = measured.size.width.toFloat()
                                val labelHeight = measured.size.height.toFloat()

                                val left = px + 4.dp.toPx()
                                val top = py - labelHeight / 2
                                val right = left + labelWidth
                                val bottom = top + labelHeight

                                if (left < 0 || right > canvasWidth || top < 0 || bottom > canvasHeight) continue

                                val padding = 8.dp.toPx()
                                val curLeft = left - padding
                                val curTop = top - padding
                                val curRight = right + padding
                                val curBottom = bottom + padding

                                var hasCollision = false
                                for (rect in placedLabels) {
                                    if (!(curRight < rect.left || curLeft > rect.right || curBottom < rect.top || curTop > rect.bottom)) {
                                        hasCollision = true
                                        break
                                    }
                                }

                                if (!hasCollision) {
                                    placedLabels.add(PlacedLabel(curLeft, curTop, curRight, curBottom))

                                    drawCircle(
                                        color = currentThemeState.value.accentPurpleColor.copy(alpha = 0.7f),
                                        radius = 2.5f.dp.toPx(),
                                        center = Offset(px, py)
                                    )

                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = place.name,
                                        topLeft = Offset(left, top),
                                        style = labelStyle
                                    )
                                }
                            }
                        }
                    }

                    // Floating Compass Overlay (placed in top right corner of map view)
                    if (selectedSchedule != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(110.dp)
                                .clip(RoundedCornerShape(50))
                                .background(SurfaceCard.copy(alpha = 0.8f))
                                .border(1.dp, BorderColor.copy(alpha = 0.5f), RoundedCornerShape(50))
                                .padding(10.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val center = Offset(size.width / 2, size.height / 2)
                                val radius = size.minDimension / 2

                                // Dial
                                drawCircle(
                                    color = BorderColor.copy(alpha = 0.3f),
                                    radius = radius,
                                    center = center,
                                    style = Stroke(width = 2.dp.toPx())
                                )

                                // Text N
                                val measuredN = textMeasurer.measure("N")
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "N",
                                    topLeft = Offset(center.x - measuredN.size.width / 2, center.y - radius + 4f),
                                    style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                )

                                // Pointer
                                val angleRad = (bearingAngle - 90.0) * PI / 180.0
                                val pointerLen = radius - 8.dp.toPx()
                                val tip = Offset(
                                    (center.x + pointerLen * cos(angleRad)).toFloat(),
                                    (center.y + pointerLen * sin(angleRad)).toFloat()
                                )
                                val tail = Offset(
                                    (center.x - 8.dp.toPx() * cos(angleRad)).toFloat(),
                                    (center.y - 8.dp.toPx() * sin(angleRad)).toFloat()
                                )

                                drawLine(
                                    color = AccentCyan,
                                    start = center,
                                    end = tip,
                                    strokeWidth = 3.dp.toPx()
                                )
                                drawLine(
                                    color = TextSecondary,
                                    start = center,
                                    end = tail,
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawCircle(AccentPurple, radius = 4.dp.toPx(), center = center)
                            }
                        }
                    }

                    // Zoom buttons on Map HUD
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = { mapZoom = (mapZoom + 0.5).coerceAtMost(18.0) },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard.copy(alpha = 0.9f)),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { mapZoom = (mapZoom - 0.5).coerceAtLeast(8.0) },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard.copy(alpha = 0.9f)),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("-", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    // Glassmorphic Stats HUD at bottom center
                    if (selectedSchedule != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceCard.copy(alpha = 0.85f))
                                .border(1.dp, BorderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(Localization.get("bearing", lang), fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                    Text("${bearingAngle.toInt()}°", fontSize = 16.sp, color = AccentPurple, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(Localization.get("distance", lang), fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                    Text("${((distanceKm * 100.0).toInt()) / 100.0} km", fontSize = 16.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = if (lang == Language.KOREAN) "예상 소요 시간" else "ESTIMATED DURATION",
                                        fontSize = 9.sp,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Column(
                                        modifier = Modifier
                                            .border(0.5.dp, BorderColor, RoundedCornerShape(6.dp))
                                            .background(SlateDarkBg.copy(alpha = 0.5f))
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        // Walking row
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.width(160.dp)
                                        ) {
                                            Text("🚶 " + (if (lang == Language.KOREAN) "도보 (15분/km)" else "Walk (15m/km)"), fontSize = 10.sp, color = TextPrimary)
                                            val walkTime = distanceKm * 15.0
                                            val walkText = if (walkTime < 1.0) {
                                                "${(walkTime * 60).toInt()}초"
                                            } else if (walkTime >= 60.0) {
                                                "${(walkTime / 60).toInt()}시간 ${(walkTime % 60).toInt()}분"
                                            } else {
                                                "${walkTime.toInt()}분"
                                            }
                                            Text(walkText, fontSize = 11.sp, color = AccentPurple, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        // Divider line
                                        Box(modifier = Modifier.width(160.dp).height(0.5.dp).background(BorderColor))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        // Driving row
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.width(160.dp)
                                        ) {
                                            Text("🚗 " + (if (lang == Language.KOREAN) "운전 (5분/km)" else "Drive (5m/km)"), fontSize = 10.sp, color = TextPrimary)
                                            val driveTime = distanceKm * 5.0
                                            val driveText = if (driveTime < 1.0) {
                                                "${(driveTime * 60).toInt()}초"
                                            } else if (driveTime >= 60.0) {
                                                "${(driveTime / 60).toInt()}시간 ${(driveTime % 60).toInt()}분"
                                            } else {
                                                "${driveTime.toInt()}분"
                                            }
                                            Text(driveText, fontSize = 11.sp, color = ActiveGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
