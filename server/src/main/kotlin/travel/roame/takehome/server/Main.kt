package travel.roame.takehome.server

import io.ktor.http.ContentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

private const val DEFAULT_PORT = 8080

fun main() {
    val fixture = File("fixtures/flights.json")
    if (!fixture.isFile) {
        System.err.println("Can't find ${fixture.absolutePath}. Start the API from the repo root: ./gradlew :server:run")
        exitProcess(1)
    }

    val port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT
    embeddedServer(CIO, port = port) {
        install(CallLogging)
        routing {
            get("/flights") {
                // Read on every request, so editing the fixture while the server runs can test a refresh.
                val body = withContext(Dispatchers.IO) { fixture.readBytes() }
                call.respondBytes(body, ContentType.Application.Json)
            }
        }
    }.start(wait = true)
}
