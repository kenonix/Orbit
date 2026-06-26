package com.orbit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.orbit.app.db.ScheduleRepository
import com.orbit.app.engine.*
import com.orbit.app.model.*
import com.orbit.app.socket.AiControlSocketServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab {
    SCHEDULES,
    NAVIGATION,
    SYNC_ENGINE,
    AI_SOCKET,
    SETTINGS
}

@Composable
fun App(repository: ScheduleRepository, deviceId: String) {
    var selectedLanguage by remember { mutableStateOf(Language.KOREAN) }
    var currentTab by remember { mutableStateOf(AppTab.SCHEDULES) }

    var mapData by remember { mutableStateOf<OsmMapData?>(null) }
    var spatialIndex by remember { mutableStateOf<SpatialGridIndex?>(null) }
    var isMapLoading by remember { mutableStateOf(false) }
    var mapLoadProgress by remember { mutableStateOf("") }
    var mapLoadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val cacheDir = java.io.File("./maps_cache")
        val pbfFile = java.io.File(cacheDir, "south-korea-latest.osm.pbf")
        if (pbfFile.exists()) {
            isMapLoading = true
            mapLoadProgress = "Parsing PBF map..."
            withContext(Dispatchers.Default) {
                try {
                    val cacheFile = java.io.File(pbfFile.parentFile, "south-korea-latest.cache.v6.json.gz")
                    val parsed = if (cacheFile.exists() && cacheFile.lastModified() > pbfFile.lastModified()) {
                        mapLoadProgress = "Loading cached map..."
                        try {
                            OsmPbfParser.loadFromCache(cacheFile)
                        } catch (cacheEx: Exception) {
                            println("Cache load failed (possibly corrupted), deleting cache and re-parsing PBF: ${cacheEx.message}")
                            try {
                                cacheFile.delete()
                            } catch (de: Exception) {
                                // Ignore delete errors
                            }
                            mapLoadProgress = "Re-parsing PBF (Cache corrupted)..."
                            val p = OsmPbfParser.parsePbf(pbfFile)
                            mapLoadProgress = "Saving map cache..."
                            OsmPbfParser.saveToCache(cacheFile, p)
                            p
                        }
                    } else {
                        mapLoadProgress = "Parsing OSM PBF map..."
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
        }
    }
    var schedules by remember { mutableStateOf(emptyList<Schedule>()) }
    val scope = rememberCoroutineScope()

    var uiScale by remember { mutableStateOf(1.0f) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Load schedules on start
    LaunchedEffect(Unit) {
        schedules = repository.getAllSchedules()
    }

    // AI WebSocket server mock state & integration
    var aiSocketActive by remember { mutableStateOf(false) }
    var aiSocketPort by remember { mutableStateOf(9090) }
    var aiLogs by remember { mutableStateOf(listOf("Server initialized. Waiting to bind...")) }
    val aiServer = remember {
        AiControlSocketServer(
            host = "127.0.0.1",
            port = aiSocketPort,
            getSchedulesCallback = { repository.getAllSchedules() },
            upsertScheduleCallback = { schedule ->
                repository.upsertSchedule(schedule, deviceId)
                schedules = repository.getAllSchedules()
                aiLogs = aiLogs + "AI: Upserted schedule '${schedule.title}'"
            },
            deleteScheduleCallback = { id ->
                repository.deleteSchedule(id, deviceId)
                schedules = repository.getAllSchedules()
                aiLogs = aiLogs + "AI: Deleted schedule '$id'"
            }
        )
    }

    // Auto start server mock
    LaunchedEffect(aiSocketActive) {
        if (aiSocketActive) {
            try {
                aiServer.start()
                aiLogs = aiLogs + "Server listening on ws://127.0.0.1:$aiSocketPort/control"
            } catch (e: Exception) {
                aiLogs = aiLogs + "ERROR: Failed to start server: ${e.message}"
            }
        } else {
            aiServer.stop()
            aiLogs = aiLogs + "Server stopped."
        }
    }

    // UI Root Layout
    val originalDensity = LocalDensity.current
    val scaledDensity = remember(originalDensity, uiScale) {
        object : Density {
            override val density: Float get() = originalDensity.density * uiScale
            override val fontScale: Float get() = originalDensity.fontScale * uiScale
        }
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SlateDarkBg)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.isCtrlPressed && keyEvent.isShiftPressed && keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.Equals, Key.Plus -> {
                                uiScale = (uiScale + 0.05f).coerceAtMost(2.5f)
                                true
                            }
                            Key.Minus -> {
                                uiScale = (uiScale - 0.05f).coerceAtLeast(0.4f)
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Navigation Sidebar
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .background(SurfaceCard)
                        .border(1.dp, BorderColor, RoundedCornerShape(0.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        // Logo Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(AccentPurple, AccentCyan)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = "Orbit Logo",
                                    tint = SlateDarkBg,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "ORBIT",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    letterSpacing = 2.sp
                                )
                                Text(
                                    text = Localization.get("app_subtitle", selectedLanguage),
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Navigation Tabs
                        NavigationButton(
                            label = Localization.get("tab_schedules", selectedLanguage),
                            icon = Icons.Default.CalendarMonth,
                            isSelected = currentTab == AppTab.SCHEDULES,
                            onClick = { currentTab = AppTab.SCHEDULES }
                        )
                        NavigationButton(
                            label = Localization.get("tab_navigation", selectedLanguage),
                            icon = Icons.Default.Navigation,
                            isSelected = currentTab == AppTab.NAVIGATION,
                            onClick = { currentTab = AppTab.NAVIGATION }
                        )
                        NavigationButton(
                            label = Localization.get("tab_sync", selectedLanguage),
                            icon = Icons.Default.Sync,
                            isSelected = currentTab == AppTab.SYNC_ENGINE,
                            onClick = { currentTab = AppTab.SYNC_ENGINE }
                        )
                        NavigationButton(
                            label = Localization.get("tab_socket", selectedLanguage),
                            icon = Icons.Default.Hub,
                            isSelected = currentTab == AppTab.AI_SOCKET,
                            onClick = { currentTab = AppTab.AI_SOCKET }
                        )
                        NavigationButton(
                            label = Localization.get("tab_settings", selectedLanguage),
                            icon = Icons.Default.Settings,
                            isSelected = currentTab == AppTab.SETTINGS,
                            onClick = { currentTab = AppTab.SETTINGS }
                        )
                    }

                    Column {
                        // Language Selector
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = Localization.get("language_select", selectedLanguage),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            
                            var expanded by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SlateDarkBg)
                                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                    .clickable { expanded = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedLanguage.displayName,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier
                                        .width(228.dp)
                                        .background(SurfaceCard)
                                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                ) {
                                    Language.values().forEach { lang ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = lang.displayName,
                                                    color = if (selectedLanguage == lang) AccentCyan else TextPrimary,
                                                    fontSize = 13.sp
                                                )
                                            },
                                            onClick = {
                                                selectedLanguage = lang
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Footer Device Info
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SlateDarkBg)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = Localization.get("device_node_id", selectedLanguage),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Text(
                                text = deviceId,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentCyan
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (aiSocketActive) ActiveGreen else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (aiSocketActive) Localization.get("ai_socket_active", selectedLanguage) else Localization.get("ai_socket_inactive", selectedLanguage),
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                // Main Content Area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    when (currentTab) {
                        AppTab.SCHEDULES -> SchedulesTab(
                            schedules = schedules,
                            lang = selectedLanguage,
                            mapData = mapData,
                            spatialIndex = spatialIndex,
                            onAddSchedule = { newSchedule ->
                                repository.upsertSchedule(newSchedule, deviceId)
                                schedules = repository.getAllSchedules()
                            },
                            onDeleteSchedule = { id ->
                                repository.deleteSchedule(id, deviceId)
                                schedules = repository.getAllSchedules()
                            }
                        )
                        AppTab.NAVIGATION -> NavigationTab(
                            schedules = schedules,
                            lang = selectedLanguage,
                            mapData = mapData,
                            spatialIndex = spatialIndex,
                            isMapLoading = isMapLoading,
                            mapLoadProgress = mapLoadProgress,
                            mapLoadError = mapLoadError
                        )
                        AppTab.SYNC_ENGINE -> SyncTab(
                            repository = repository,
                            deviceId = deviceId,
                            lang = selectedLanguage,
                            onSyncComplete = {
                                schedules = repository.getAllSchedules()
                            }
                        )
                        AppTab.AI_SOCKET -> AiSocketTab(
                            isActive = aiSocketActive,
                            onToggle = { aiSocketActive = it },
                            port = aiSocketPort,
                            onPortChange = { aiSocketPort = it },
                            logs = aiLogs,
                            lang = selectedLanguage
                        )
                        AppTab.SETTINGS -> SettingsTab(
                            lang = selectedLanguage
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) AccentPurple.copy(alpha = 0.15f) else Color.Transparent
    val border = if (isSelected) AccentPurple.copy(alpha = 0.4f) else Color.Transparent
    val tint = if (isSelected) AccentPurple else TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) TextPrimary else TextSecondary
        )
    }
}
