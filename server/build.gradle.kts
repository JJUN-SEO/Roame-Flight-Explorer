plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("travel.roame.takehome.server.MainKt")
    // Silences a JDK 24+ startup warning about a console library that Ktor pulls in.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
    runtimeOnly(libs.slf4j.simple)
}

// So that fixtures/flights.json resolves when this runs through Gradle (docs/ARCHITECTURE.md §6.2).
tasks.named<JavaExec>("run") { workingDir = rootDir }
