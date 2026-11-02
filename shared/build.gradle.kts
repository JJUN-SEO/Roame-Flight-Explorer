import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    // Test-only target: runs shared tests on any OS (docs/ARCHITECTURE.md, D9). Not shipped.
    jvm()
    android {
        namespace = "travel.roame.takehome.shared"
        compileSdk { version = release(37) { minorApiLevel = 0 } }
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
        it.binaries.framework { baseName = "Shared"; isStatic = true }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
        }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
        jvmTest.dependencies { implementation(libs.commons.codec) }
    }
}

room { schemaDirectory("$projectDir/schemas") }

// Room's code generator runs once per target (docs/ARCHITECTURE.md §5.4).
dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64", "kspIosX64", "kspJvm").forEach {
        add(it, libs.room.compiler)
    }
}
