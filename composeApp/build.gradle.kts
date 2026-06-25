plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    jvm("desktop") {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(compose.materialIconsExtended)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)

                // SQLDelight Runtime
                implementation(libs.sqldelight.runtime)

                // Ktor Server for local AI Control Socket
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.appcompat:appcompat:1.6.1")
                implementation("androidx.core:core-ktx:1.12.0")
                
                // SQLDelight Android Driver
                implementation(libs.sqldelight.android.driver)
            }
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                
                // SQLDelight SQLite Driver (for Desktop)
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
    }
}

android {
    namespace = "com.orbit.app"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.orbit.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            pickFirsts.add("plugin.properties")
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/INDEX.LIST")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.orbit.app.MainKt"
        jvmArgs += listOf("-Xmx4G", "-XX:+UseG1GC")
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm
            )
            packageName = "Orbit"
            packageVersion = "1.0.0"
        }
    }
}

sqldelight {
    databases {
        create("OrbitDatabase") {
            packageName.set("com.orbit.app.db")
        }
    }
}
