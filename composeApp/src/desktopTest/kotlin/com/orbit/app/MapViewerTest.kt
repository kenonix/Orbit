package com.orbit.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.orbit.app.engine.*
import com.orbit.app.model.*
import com.orbit.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import java.io.File
import kotlin.math.*
import kotlin.system.measureTimeMillis
import kotlin.test.Test

class MapViewerTest {

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun runMapViewerStandalone() {
        application {
            val windowState = rememberWindowState(size = DpSize(1280.dp, 850.dp))
            Window(
                onCloseRequest = ::exitApplication,
                state = windowState,
                title = "Orbit Map Performance Benchmarking Suite"
            ) {
                val theme = AppThemes.DarkSlate

                // Map data structures
                var mapData by remember { mutableStateOf<OsmMapData?>(null) }
                var spatialIndex by remember { mutableStateOf<SpatialGridIndex?>(null) }
                var isMapLoading by remember { mutableStateOf(false) }
                var mapLoadProgress by remember { mutableStateOf("") }
                var mapLoadError by remember { mutableStateOf<String?>(null) }

                // Map View settings
                var mapZoom by remember { mutableStateOf(14.0) }
                var mapCenterLat by remember { mutableStateOf(37.5665) }
                var mapCenterLon by remember { mutableStateOf(126.9780) }

                // User Location and Destination States
                var myLat by remember { mutableStateOf("37.5665") }
                var myLon by remember { mutableStateOf("126.9780") }
                var selectedSchedule by remember { mutableStateOf<Schedule?>(null) }

                // Calculations
                var bearingAngle by remember { mutableStateOf(0.0) }
                var distanceKm by remember { mutableStateOf(0.0) }

                // Benchmarking/Profiling metrics
                var renderTimeMs by remember { mutableStateOf(0L) }
                var queryTimeMs by remember { mutableStateOf(0L) }
                var labelQueryTimeMs by remember { mutableStateOf(0L) }
                var fpsEstimate by remember { mutableStateOf(0) }

                // Layer detailed metrics (Counts and high-resolution time)
                var waterCount by remember { mutableStateOf(0) }
                var waterTimeMs by remember { mutableStateOf(0.0) }
                var buildingsCount by remember { mutableStateOf(0) }
                var buildingsTimeMs by remember { mutableStateOf(0.0) }
                var roadsCount by remember { mutableStateOf(0) }
                var roadsTimeMs by remember { mutableStateOf(0.0) }
                var placesRenderCount by remember { mutableStateOf(0) }
                var placesTimeMs by remember { mutableStateOf(0.0) }

                // Highlight Mode & Toggles
                var highlightMode by remember { mutableStateOf("All") } // "All", "Water", "Buildings", "Roads", "Places"
                var useSpatialIndex by remember { mutableStateOf(true) }
                var optimizeTextMeasure by remember { mutableStateOf(true) }
                var reusePathInstance by remember { mutableStateOf(true) }

                // Layer Toggles
                var showRoads by remember { mutableStateOf(true) }
                var showBuildings by remember { mutableStateOf(true) }
                var showWater by remember { mutableStateOf(true) }
                var showPlaces by remember { mutableStateOf(true) }

                val textMeasurer = rememberTextMeasurer()
                val textLayoutCache = remember { mutableMapOf<String, IntSize>() }

                // Mock Schedules to replicate destination target UI
                val mockSchedules = remember {
                    listOf(
                        Schedule(
                            id = "1",
                            title = "경복궁 (Gyeongbokgung)",
                            startTime = Instant.fromEpochMilliseconds(0L),
                            endTime = Instant.fromEpochMilliseconds(0L),
                            location = Location("경복궁 Palace", 37.5796, 126.9770)
                        ),
                        Schedule(
                            id = "2",
                            title = "N서울타워 (N Seoul Tower)",
                            startTime = Instant.fromEpochMilliseconds(0L),
                            endTime = Instant.fromEpochMilliseconds(0L),
                            location = Location("N서울타워", 37.5511, 126.9882)
                        ),
                        Schedule(
                            id = "3",
                            title = "명동성당 (Myeongdong Cathedral)",
                            startTime = Instant.fromEpochMilliseconds(0L),
                            endTime = Instant.fromEpochMilliseconds(0L),
                            location = Location("명동성당", 37.5630, 126.9850)
                        ),
                        Schedule(
                            id = "4",
                            title = "서울시청 (Seoul City Hall)",
                            startTime = Instant.fromEpochMilliseconds(0L),
                            endTime = Instant.fromEpochMilliseconds(0L),
                            location = Location("서울시청", 37.5665, 126.9780)
                        )
                    )
                }

                // Sync computations when inputs change
                LaunchedEffect(selectedSchedule, myLat, myLon) {
                    val curLat = myLat.toDoubleOrNull() ?: 37.5665
                    val curLon = myLon.toDoubleOrNull() ?: 126.9780
                    val currentLoc = Location("Current", curLat, curLon)
                    val destination = selectedSchedule?.location
                    if (destination != null) {
                        bearingAngle = NavigationEngine.calculateBearing(currentLoc, destination)
                        distanceKm = NavigationEngine.calculateDistance(currentLoc, destination)
                    }
                }

                // Sync map center to manually input coordinates
                LaunchedEffect(myLat, myLon) {
                    myLat.toDoubleOrNull()?.let { mapCenterLat = it }
                    myLon.toDoubleOrNull()?.let { mapCenterLon = it }
                }

                // Sync map center when target event is selected
                LaunchedEffect(selectedSchedule) {
                    selectedSchedule?.location?.let {
                        mapCenterLat = it.latitude
                        mapCenterLon = it.longitude
                        myLat = "%.4f".format(mapCenterLat)
                        myLon = "%.4f".format(mapCenterLon)
                    }
                }

                // Load map parser
                LaunchedEffect(Unit) {
                    val cacheDir = File("./maps_cache")
                    val pbfFile = File(cacheDir, "south-korea-latest.osm.pbf")
                    if (pbfFile.exists()) {
                        isMapLoading = true
                        mapLoadProgress = "Parsing PBF map..."
                        withContext(Dispatchers.Default) {
                            try {
                                val cacheFile = File(pbfFile.parentFile, "south-korea-latest.cache.v6.json.gz")
                                val parsed = if (cacheFile.exists() && cacheFile.lastModified() > pbfFile.lastModified()) {
                                    mapLoadProgress = "Loading cached map..."
                                    try {
                                        OsmPbfParser.loadFromCache(cacheFile)
                                    } catch (e: Exception) {
                                        cacheFile.delete()
                                        mapLoadProgress = "Cache corrupt. Parsing PBF..."
                                        val p = OsmPbfParser.parsePbf(pbfFile)
                                        OsmPbfParser.saveToCache(cacheFile, p)
                                        p
                                    }
                                } else {
                                    mapLoadProgress = "Parsing PBF map..."
                                    val p = OsmPbfParser.parsePbf(pbfFile)
                                    mapLoadProgress = "Saving map cache..."
                                    OsmPbfParser.saveToCache(cacheFile, p)
                                    p
                                }
                                mapData = parsed
                                mapLoadProgress = "Building Spatial Grid Index..."
                                spatialIndex = SpatialGridIndex(parsed.ways, parsed.places)
                                isMapLoading = false
                            } catch (e: Exception) {
                                e.printStackTrace()
                                mapLoadError = "Failed to load map: ${e.message}"
                                isMapLoading = false
                            }
                        }
                    } else {
                        mapData = SimulatedMapData.data
                        spatialIndex = SpatialGridIndex(SimulatedMapData.data.ways, SimulatedMapData.data.places)
                    }
                }

                // FPS Counter
                var frameCount by remember { mutableStateOf(0) }
                var fpsTimer by remember { mutableStateOf(System.nanoTime()) }
                SideEffect {
                    val now = System.nanoTime()
                    frameCount++
                    if (now - fpsTimer >= 1_000_000_000L) {
                        fpsEstimate = frameCount
                        frameCount = 0
                        fpsTimer = now
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F111A))
                ) {
                    // Left Control Side Panel
                    Column(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF161A26))
                            .border(1.dp, Color(0x2AFFFFFF))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "MAP BENCHMARK SUITE",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8E7CFF),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // 1. Performance Overview
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0F17)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(8.dp))
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("OVERALL PERFORMANCE", fontSize = 10.sp, color = Color(0xFF8B949E), fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    MetricRow("FPS Estimate", "$fpsEstimate Hz", Color(0xFF00E676))
                                    MetricRow("Total Canvas Render", "$renderTimeMs ms", Color(0xFF4ECCFF))
                                    MetricRow("Spatial Query (Ways)", "$queryTimeMs ms", Color(0xFF8E7CFF))
                                    MetricRow("Spatial Query (Places)", "$labelQueryTimeMs ms", Color(0xFF8E7CFF))
                                    MetricRow("Zoom Level", "%.2f".format(mapZoom), Color.White)
                                }
                            }

                            // 2. Distinguishable Layer Metrics Breakdown
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0F17)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(8.dp))
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("LAYER DATA BREAKDOWN", fontSize = 10.sp, color = Color(0xFF8B949E), fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Water Layer info
                                    LayerMetricRow(
                                        layerName = "🌊 Water Bodies",
                                        count = waterCount,
                                        timeMs = waterTimeMs,
                                        isHighlighted = highlightMode == "Water",
                                        onHighlightClick = { highlightMode = if (highlightMode == "Water") "All" else "Water" }
                                    )
                                    // Building Layer info
                                    LayerMetricRow(
                                        layerName = "🏢 Buildings",
                                        count = buildingsCount,
                                        timeMs = buildingsTimeMs,
                                        isHighlighted = highlightMode == "Buildings",
                                        onHighlightClick = { highlightMode = if (highlightMode == "Buildings") "All" else "Buildings" }
                                    )
                                    // Road Layer info
                                    LayerMetricRow(
                                        layerName = "🛣️ Roads",
                                        count = roadsCount,
                                        timeMs = roadsTimeMs,
                                        isHighlighted = highlightMode == "Roads",
                                        onHighlightClick = { highlightMode = if (highlightMode == "Roads") "All" else "Roads" }
                                    )
                                    // Places Layer info
                                    LayerMetricRow(
                                        layerName = "📍 Place Labels",
                                        count = placesRenderCount,
                                        timeMs = placesTimeMs,
                                        isHighlighted = highlightMode == "Places",
                                        onHighlightClick = { highlightMode = if (highlightMode == "Places") "All" else "Places" }
                                    )

                                    if (highlightMode != "All") {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "* Highlight Active: Only '$highlightMode' shown clearly.",
                                            fontSize = 9.sp,
                                            color = Color(0xFFFFB74D),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Optimization Toggles
                            Text("BENCHMARK OPTIMIZATIONS", fontSize = 11.sp, color = Color(0xFF8B949E), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = useSpatialIndex, onCheckedChange = { useSpatialIndex = it }, modifier = Modifier.scale(0.85f))
                                Text("Spatial Grid Index", color = Color.White, fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = optimizeTextMeasure, onCheckedChange = { optimizeTextMeasure = it }, modifier = Modifier.scale(0.85f))
                                Text("Optimize Text Cache", color = Color.White, fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = reusePathInstance, onCheckedChange = { reusePathInstance = it }, modifier = Modifier.scale(0.85f))
                                Text("Path Instance Reuse", color = Color.White, fontSize = 12.sp)
                            }

                            // Layer Toggles
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("VISIBLE LAYERS", fontSize = 11.sp, color = Color(0xFF8B949E), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Checkbox(checked = showWater, onCheckedChange = { showWater = it }, modifier = Modifier.scale(0.8f))
                                    Text("Water", color = Color.White, fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Checkbox(checked = showBuildings, onCheckedChange = { showBuildings = it }, modifier = Modifier.scale(0.8f))
                                    Text("Builds", color = Color.White, fontSize = 11.sp)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Checkbox(checked = showRoads, onCheckedChange = { showRoads = it }, modifier = Modifier.scale(0.8f))
                                    Text("Roads", color = Color.White, fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Checkbox(checked = showPlaces, onCheckedChange = { showPlaces = it }, modifier = Modifier.scale(0.8f))
                                    Text("Places", color = Color.White, fontSize = 11.sp)
                                }
                            }

                            // Coordinate Simulation Input
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("SIMULATED LOCATION", fontSize = 11.sp, color = Color(0xFF8B949E), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = myLat,
                                    onValueChange = { myLat = it },
                                    label = { Text("Lat", fontSize = 9.sp) },
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF8E7CFF),
                                        unfocusedBorderColor = Color(0x3CFFFFFF)
                                    )
                                )
                                OutlinedTextField(
                                    value = myLon,
                                    onValueChange = { myLon = it },
                                    label = { Text("Lon", fontSize = 9.sp) },
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF8E7CFF),
                                        unfocusedBorderColor = Color(0x3CFFFFFF)
                                    )
                                )
                            }
                        }

                        // Target Event Mock Schedules
                        Column(modifier = Modifier.weight(1f).padding(top = 10.dp)) {
                            Text("MOCK TARGETS", fontSize = 11.sp, color = Color(0xFF8B949E), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(mockSchedules) { schedule ->
                                    val isSelected = selectedSchedule?.id == schedule.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF4ECCFF).copy(alpha = 0.15f) else Color.Transparent)
                                            .border(1.dp, if (isSelected) Color(0xFF4ECCFF).copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(6.dp))
                                            .clickable { selectedSchedule = schedule }
                                            .padding(6.dp)
                                    ) {
                                        Column {
                                            Text(schedule.title, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text(schedule.location?.name ?: "", fontSize = 9.sp, color = Color(0xFF8B949E))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Main Map Viewer Viewport
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(theme.mapBackground)
                    ) {
                        if (isMapLoading) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF8E7CFF))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(mapLoadProgress, color = Color.White, fontSize = 14.sp)
                            }
                        } else if (mapLoadError != null) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(mapLoadError ?: "", color = Color.Red, fontSize = 14.sp)
                            }
                        } else {
                            val activeMapData = mapData ?: SimulatedMapData.data
                            var copyStatusText by remember { mutableStateOf("Copy") }
                            LaunchedEffect(mapCenterLat, mapCenterLon) {
                                copyStatusText = "Copy"
                            }
                            val sharedPath = remember { Path() }

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
                                            myLat = "%.4f".format(mapCenterLat)
                                            myLon = "%.4f".format(mapCenterLon)
                                        }
                                    }
                                    .onPointerEvent(PointerEventType.Scroll) { event ->
                                        val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                        if (delta != 0f) {
                                            val zoomChange = if (delta < 0) 0.5 else -0.5
                                            mapZoom = (mapZoom + zoomChange).coerceIn(8.0, 18.0)
                                        }
                                    }
                            ) {
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

                                // Benchmarked execution block
                                renderTimeMs = measureTimeMillis {
                                    // 1. Spatial Grid Index queries
                                    var visibleWays: List<OsmWay> = emptyList()
                                    queryTimeMs = measureTimeMillis {
                                        visibleWays = if (useSpatialIndex && spatialIndex != null) {
                                            spatialIndex!!.queryWays(minLat, maxLat, minLon, maxLon)
                                        } else {
                                            activeMapData.ways
                                        }
                                    }

                                    var visiblePlaces: List<OsmPlace> = emptyList()
                                    labelQueryTimeMs = measureTimeMillis {
                                        if (showPlaces && mapZoom >= 10.0) {
                                            visiblePlaces = if (useSpatialIndex && spatialIndex != null) {
                                                spatialIndex!!.queryPlaces(minLat, maxLat, minLon, maxLon)
                                            } else {
                                                activeMapData.places
                                            }
                                        }
                                    }

                                    // 2. Filter & categorize ways into distinct layers (Water, Buildings, Roads)
                                    val waterWays = mutableListOf<OsmWay>()
                                    val waterwayLines = mutableListOf<OsmWay>()
                                    val buildingWays = mutableListOf<OsmWay>()
                                    val roadWays = mutableListOf<OsmWay>()

                                    for (way in visibleWays) {
                                        if (way.maxLat < minLat || way.minLat > maxLat || way.maxLon < minLon || way.minLon > maxLon) continue

                                        val isBuilding = way.type == "building"
                                        val isWater = way.type == "water" || way.tags.containsKey("water") || way.tags["natural"] == "water"
                                        val isWaterwayLine = way.type == "waterway_line"

                                        if (isWater) {
                                            if (showWater && mapZoom >= 12.0) waterWays.add(way)
                                        } else if (isWaterwayLine) {
                                            if (showWater && mapZoom >= 12.0) waterwayLines.add(way)
                                        } else if (isBuilding) {
                                            if (showBuildings && mapZoom >= 15.0) buildingWays.add(way)
                                        } else {
                                            if (showRoads) {
                                                if (mapZoom < 11.0 && way.type !in listOf("motorway", "trunk")) continue
                                                if (mapZoom < 13.0 && way.type !in listOf("motorway", "trunk", "primary", "secondary")) continue
                                                if (mapZoom < 14.0 && way.type !in listOf("motorway", "trunk", "primary", "secondary", "tertiary")) continue
                                                roadWays.add(way)
                                            }
                                        }
                                    }

                                    // Helper function to draw way path
                                    fun drawWayPath(way: OsmWay, color: Color, style: androidx.compose.ui.graphics.drawscope.DrawStyle, close: Boolean = false) {
                                        val path = if (reusePathInstance) {
                                            sharedPath.reset()
                                            sharedPath
                                        } else {
                                            Path()
                                        }
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
                                        if (close) {
                                            path.close()
                                        }
                                        drawPath(path = path, color = color, style = style)
                                    }

                                    // Render Group A: Water Bodies (Drawn first/bottom)
                                    val waterT0 = System.nanoTime()
                                    waterCount = waterWays.size + waterwayLines.size
                                    val waterAlpha = if (highlightMode == "All" || highlightMode == "Water") 1.0f else 0.12f
                                    for (way in waterWays) {
                                        drawWayPath(
                                            way = way,
                                            color = theme.mapWater.copy(alpha = waterAlpha),
                                            style = androidx.compose.ui.graphics.drawscope.Fill,
                                            close = true
                                        )
                                    }
                                    for (way in waterwayLines) {
                                        drawWayPath(
                                            way = way,
                                            color = theme.mapWater.copy(alpha = waterAlpha),
                                            style = Stroke(width = 2.5f.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                                            close = false
                                        )
                                    }
                                    waterTimeMs = (System.nanoTime() - waterT0) / 1_000_000.0

                                    // Render Group B: Buildings
                                    val buildT0 = System.nanoTime()
                                    buildingsCount = buildingWays.size
                                    val buildFillAlpha = if (highlightMode == "All" || highlightMode == "Buildings") 0.85f else 0.15f
                                    val buildStrokeAlpha = if (highlightMode == "All" || highlightMode == "Buildings") 0.4f else 0.08f
                                    val buildingColor = Color(0xFF2A2E3D) // Distinct color for buildings
                                    for (way in buildingWays) {
                                        // Fill
                                        drawWayPath(
                                            way = way,
                                            color = buildingColor.copy(alpha = buildFillAlpha),
                                            style = androidx.compose.ui.graphics.drawscope.Fill,
                                            close = true
                                        )
                                        // Outline
                                        drawWayPath(
                                            way = way,
                                            color = theme.accentPurpleColor.copy(alpha = buildStrokeAlpha),
                                            style = Stroke(width = 0.8f.dp.toPx()),
                                            close = true
                                        )
                                    }
                                    buildingsTimeMs = (System.nanoTime() - buildT0) / 1_000_000.0

                                    // Render Group C: Roads (Drawn top)
                                    val roadsT0 = System.nanoTime()
                                    roadsCount = roadWays.size
                                    val roadAlpha = if (highlightMode == "All" || highlightMode == "Roads") 1.0f else 0.12f
                                    for (way in roadWays) {
                                        val baseColor = getRoadColor(way.type, theme)
                                        drawWayPath(
                                            way = way,
                                            color = baseColor.copy(alpha = roadAlpha),
                                            style = Stroke(width = getRoadWidthDp(way.type, mapZoom).dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                        )
                                    }
                                    roadsTimeMs = (System.nanoTime() - roadsT0) / 1_000_000.0

                                    // 3. Draw Route to selected target (Only visible/solid if Roads or All is highlighted)
                                    val simulatedLoc = Location("Current", myLat.toDoubleOrNull() ?: 37.5665, myLon.toDoubleOrNull() ?: 126.9780)
                                    val destination = selectedSchedule?.location
                                    if (destination != null) {
                                        val startX = toScreenX(simulatedLoc.longitude)
                                        val startY = toScreenY(simulatedLoc.latitude)
                                        val endX = toScreenX(destination.longitude)
                                        val endY = toScreenY(destination.latitude)

                                        val routeAlpha = if (highlightMode == "All" || highlightMode == "Roads") 1.0f else 0.15f

                                        // Dotted Route line
                                        drawLine(
                                            color = theme.accentCyanColor.copy(alpha = routeAlpha),
                                            start = Offset(startX, startY),
                                            end = Offset(endX, endY),
                                            strokeWidth = 3.dp.toPx(),
                                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                                        )

                                        // Destination Marker
                                        drawCircle(
                                            color = Color.Red.copy(alpha = routeAlpha),
                                            radius = 9.dp.toPx(),
                                            center = Offset(endX, endY)
                                        )
                                        drawCircle(
                                            color = Color.White.copy(alpha = routeAlpha),
                                            radius = 4.dp.toPx(),
                                            center = Offset(endX, endY)
                                        )
                                    }

                                    // 4. Draw Current User simulated marker
                                    val myX = toScreenX(simulatedLoc.longitude)
                                    val myY = toScreenY(simulatedLoc.latitude)
                                    drawCircle(
                                        color = theme.accentPurpleColor,
                                        radius = 9.dp.toPx(),
                                        center = Offset(myX, myY)
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 3.dp.toPx(),
                                        center = Offset(myX, myY)
                                    )

                                    // 5. Draw Place Labels with Collision check (Group D)
                                    val placesT0 = System.nanoTime()
                                    if (showPlaces && mapZoom >= 10.0) {
                                        var placesCount = 0
                                        val placesAlpha = if (highlightMode == "All" || highlightMode == "Places") 1.0f else 0.12f

                                        fun getPlacePriority(place: OsmPlace): Float {
                                            if (place.tags.containsKey("shop")) return 55f
                                            if (place.tags.containsKey("building")) return 54f
                                            if (place.tags.containsKey("amenity")) return 53f
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
                                            return base
                                        }

                                        val filteredPlaces = visiblePlaces
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

                                        for (place in filteredPlaces) {
                                            if (place.lat < minLat || place.lat > maxLat || place.lon < minLon || place.lon > maxLon) continue

                                            val px = toScreenX(place.lon)
                                            val py = toScreenY(place.lat)

                                            val labelStyle = TextStyle(
                                                color = theme.textPrimaryColor.copy(alpha = placesAlpha),
                                                fontSize = if (place.type in listOf("city", "town")) 11.sp else 9.sp,
                                                fontWeight = if (place.type in listOf("city", "town")) FontWeight.Bold else FontWeight.Normal
                                            )

                                            val size = if (optimizeTextMeasure) {
                                                textLayoutCache.getOrPut(place.name) {
                                                    val measured = textMeasurer.measure(place.name)
                                                    IntSize(measured.size.width, measured.size.height)
                                                }
                                            } else {
                                                val measured = textMeasurer.measure(place.name)
                                                IntSize(measured.size.width, measured.size.height)
                                            }

                                            val labelWidth = size.width.toFloat()
                                            val labelHeight = size.height.toFloat()

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
                                                placesCount++

                                                drawCircle(
                                                    color = theme.accentPurpleColor.copy(alpha = 0.7f * placesAlpha),
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
                                        placesRenderCount = placesCount
                                    } else {
                                        placesRenderCount = 0
                                    }
                                    placesTimeMs = (System.nanoTime() - placesT0) / 1_000_000.0
                                }
                            }
                        }

                        // Floating center coordinates info HUD
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xE6161A26))
                                .clickable {
                                    val latLonStr = "${mapCenterLat},${mapCenterLon}"
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(latLonStr))
                                    copyStatusText = "Copied!"
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Center: Lat %.4f, Lon %.4f".format(mapCenterLat, mapCenterLon),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(
                                    modifier = Modifier
                                        .height(12.dp)
                                        .width(1.dp)
                                        .background(Color.White.copy(alpha = 0.3f))
                                )
                                Text(
                                    text = copyStatusText,
                                    color = if (copyStatusText == "Copied!") Color(0xFF4ECCFF) else Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Floating Compass Needle UI
                        if (selectedSchedule != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(theme.surfaceCardColor.copy(alpha = 0.8f))
                                    .border(1.dp, theme.borderColor.copy(alpha = 0.5f), RoundedCornerShape(50))
                                    .padding(8.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val center = Offset(size.width / 2, size.height / 2)
                                    val radius = size.minDimension / 2

                                    // Compass circle
                                    drawCircle(
                                        color = theme.borderColor.copy(alpha = 0.3f),
                                        radius = radius,
                                        center = center,
                                        style = Stroke(width = 2.dp.toPx())
                                    )

                                    // North text
                                    val measuredN = textMeasurer.measure("N")
                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = "N",
                                        topLeft = Offset(center.x - measuredN.size.width / 2, center.y - radius + 2f),
                                        style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    )

                                    // Needle line
                                    val angleRad = (bearingAngle - 90.0) * PI / 180.0
                                    val pointerLen = radius - 8.dp.toPx()
                                    val tip = Offset(
                                        (center.x + pointerLen * cos(angleRad)).toFloat(),
                                        (center.y + pointerLen * sin(angleRad)).toFloat()
                                    )
                                    val tail = Offset(
                                        (center.x - 6.dp.toPx() * cos(angleRad)).toFloat(),
                                        (center.y - 6.dp.toPx() * sin(angleRad)).toFloat()
                                    )

                                    drawLine(
                                        color = theme.accentCyanColor,
                                        start = center,
                                        end = tip,
                                        strokeWidth = 3.dp.toPx()
                                    )
                                    drawLine(
                                        color = theme.textSecondaryColor,
                                        start = center,
                                        end = tail,
                                        strokeWidth = 2.dp.toPx()
                                    )
                                    drawCircle(theme.accentPurpleColor, radius = 3.dp.toPx(), center = center)
                                }
                            }
                        }

                        // Bottom Navigation ETA / Distance HUD (aligned with production styling)
                        if (selectedSchedule != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.surfaceCardColor.copy(alpha = 0.85f))
                                    .border(1.dp, theme.borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("BEARING", fontSize = 9.sp, color = theme.textSecondaryColor, fontWeight = FontWeight.Bold)
                                        Text("${bearingAngle.toInt()}°", fontSize = 15.sp, color = theme.accentPurpleColor, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("DISTANCE", fontSize = 9.sp, color = theme.textSecondaryColor, fontWeight = FontWeight.Bold)
                                        Text("${String.format("%.2f", distanceKm)} km", fontSize = 15.sp, color = theme.accentCyanColor, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text("ESTIMATED DURATION", fontSize = 8.sp, color = theme.textSecondaryColor, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Column(
                                            modifier = Modifier
                                                .border(0.5.dp, theme.borderColor, RoundedCornerShape(6.dp))
                                                .background(Color(0xFF0F111A).copy(alpha = 0.5f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            // Walking ETA
                                            val walkTime = distanceKm * 15.0
                                            val walkText = formatDuration(walkTime)
                                            Row(
                                                modifier = Modifier.width(150.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("🚶 Walk", fontSize = 9.sp, color = theme.textPrimaryColor)
                                                Text(walkText, fontSize = 10.sp, color = theme.accentPurpleColor, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            // Driving ETA
                                            val driveTime = distanceKm * 5.0
                                            val driveText = formatDuration(driveTime)
                                            Row(
                                                modifier = Modifier.width(150.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("🚗 Drive", fontSize = 9.sp, color = theme.textPrimaryColor)
                                                Text(driveText, fontSize = 10.sp, color = theme.activeGreenColor, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Floating zoom buttons on Map HUD
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Button(
                                onClick = { mapZoom = (mapZoom + 0.5).coerceAtMost(18.0) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceCardColor.copy(alpha = 0.9f)),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("+", color = theme.textPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { mapZoom = (mapZoom - 0.5).coerceAtLeast(8.0) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceCardColor.copy(alpha = 0.9f)),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("-", color = theme.textPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun formatDuration(minutes: Double): String {
        return if (minutes < 1.0) {
            "${(minutes * 60).toInt()}s"
        } else if (minutes >= 60.0) {
            "${(minutes / 60).toInt()}h ${(minutes % 60).toInt()}m"
        } else {
            "${minutes.toInt()}m"
        }
    }

    @Composable
    private fun MetricRow(label: String, value: String, valueColor: Color) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.5.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color(0xFF8B949E), fontSize = 12.sp)
            Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    private fun LayerMetricRow(
        layerName: String,
        count: Int,
        timeMs: Double,
        isHighlighted: Boolean,
        onHighlightClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(if (isHighlighted) Color(0xFF4ECCFF).copy(alpha = 0.15f) else Color.Transparent)
                .clickable { onHighlightClick() }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(layerName, color = if (isHighlighted) Color(0xFF4ECCFF) else Color.White, fontSize = 11.sp, fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$count", color = Color(0xFF8B949E), fontSize = 11.sp)
                Text(String.format("%.2f ms", timeMs), color = if (timeMs > 5.0) Color(0xFFFFB74D) else Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Test
    fun inspectDownloadedMapData() {
        val cacheDir = File("./maps_cache")
        var cacheFile = File(cacheDir, "south-korea-latest.cache.v6.json.gz")
        if (!cacheFile.exists()) {
            cacheFile = File("./composeApp/maps_cache/south-korea-latest.cache.v6.json.gz")
        }

        println("==================================================")
        println("OSM MAP DATA INSPECTOR")
        println("==================================================")
        println("Looking for cache file at: ${cacheFile.absolutePath}")
        
        if (!cacheFile.exists()) {
            println("Cache file not found!")
            val parent = cacheFile.parentFile
            if (parent != null && parent.exists()) {
                println("Files in directory '${parent.absolutePath}':")
                parent.listFiles()?.forEach { println(" - ${it.name} (${it.length()} bytes)") }
            } else {
                println("Directory does not exist.")
            }
            return
        }

        println("Cache file size: ${cacheFile.length()} bytes")
        println("Loading map data from cache...")
        val start = System.currentTimeMillis()
        val mapData = OsmPbfParser.loadFromCache(cacheFile)
        val elapsed = System.currentTimeMillis() - start
        println("Loaded in ${elapsed} ms.")

        println("\n--- GENERAL STATISTICS ---")
        println("Total Places: ${mapData.places.size}")
        println("Total Ways: ${mapData.ways.size}")

        val buildings = mapData.ways.filter { it.type == "building" }
        val water = mapData.ways.filter { it.type == "water" }
        val roads = mapData.ways.filter { it.type != "building" && it.type != "water" }

        println(" - Buildings Count: ${buildings.size}")
        println(" - Water Bodies Count: ${water.size}")
        println(" - Roads Count: ${roads.size}")

        println("\n--- BUILDING TAGS BREAKDOWN ---")
        val buildingTypes = buildings.groupBy { it.tags["building"] ?: "yes" }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(15)
        
        buildingTypes.forEach { (type, count) ->
            println(" - building=$type: $count")
        }

        println("\n--- HIGHWAY (ROAD) TYPES BREAKDOWN ---")
        val roadTypes = roads.groupBy { it.type }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(15)

        roadTypes.forEach { (type, count) ->
            println(" - highway=$type: $count")
        }

        println("\n--- SAMPLE BUILDING POLYGONS (First 10) ---")
        buildings.take(10).forEachIndexed { index, way ->
            val isClosed = way.coords.firstOrNull() == way.coords.lastOrNull()
            println("Building #$index: ID=${way.id}, Name='${way.name}', Points=${way.coords.size}, Closed=$isClosed")
            if (way.tags.isNotEmpty()) {
                println("   Tags: ${way.tags}")
            }
        }
        
        println("==================================================")
    }
}
