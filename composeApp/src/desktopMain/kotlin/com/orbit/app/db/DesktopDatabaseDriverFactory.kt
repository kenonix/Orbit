package com.orbit.app.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

class DesktopDatabaseDriverFactory : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        val databasePath = File(System.getProperty("user.home"), ".orbit/orbit.db")
        databasePath.parentFile.mkdirs()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.absolutePath}")
        
        try {
            OrbitDatabase.Schema.create(driver)
        } catch (e: Exception) {
            // Table already exists
        }
        return driver
    }
}
