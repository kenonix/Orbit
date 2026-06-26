package com.orbit.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.orbit.app.db.DesktopDatabaseDriverFactory
import com.orbit.app.db.ScheduleRepository
import com.orbit.app.ui.App

fun main() {
    System.setProperty("org.jetbrains.skiko.force.gpu", "true")
    
    application {
        val isMapViewerMode = System.getenv("MAP_VIEWER") == "true"

    if (isMapViewerMode) {
        val windowState = rememberWindowState(size = DpSize(1280.dp, 850.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Orbit Map Performance Benchmarking Suite"
        ) {
            MapViewerContent()
        }
    } else {
        val databaseDriverFactory = DesktopDatabaseDriverFactory()
        val driver = databaseDriverFactory.createDriver()
        val repository = ScheduleRepository(driver)

        val windowState = rememberWindowState(size = DpSize(1024.dp, 768.dp))

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Orbit - Spatial Personal Calendar"
        ) {
            window.minimumSize = java.awt.Dimension(900, 600)
            App(repository = repository, deviceId = "desktop-device")
        }
    }
}
}
