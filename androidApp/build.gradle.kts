plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    buildToolsVersion = "37.0.0"
    namespace = "travel.roame.takehome.android"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        applicationId = "travel.roame.takehome.android"
        minSdk = 26
        // D26: Android 17's local network permission blocks the emulator's 10.0.2.2 for apps targeting 37.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // The emulator reaches the Mac's localhost here (ARCHITECTURE §6.4).
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(project(":shared"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.leakcanary.android)
}
