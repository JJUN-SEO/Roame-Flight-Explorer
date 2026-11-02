package travel.roame.takehome.data.remote

import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import travel.roame.takehome.model.FetchError
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Real sockets through OkHttp, the engine Android ships (R5, R9). A fake engine can only throw what
 * we expect; this proves what a real failure looks like.
 */
class FlightsApiRealEngineTest {

    private val headers = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 1000\r\n\r\n"

    private fun api(baseUrl: String) = KtorFlightsApi(OkHttp.create(), baseUrl)

    /** Only for the timeout tests, so a slow machine can't make the other tests time out. */
    private fun impatientApi(baseUrl: String) = KtorFlightsApi(OkHttp.create(), baseUrl, requestTimeoutMillis = 500)

    /** Runs [respond] for each connection until [test] finishes. */
    private fun withServer(respond: (Socket) -> Unit, test: suspend (baseUrl: String) -> Unit) {
        ServerSocket(0).use { server ->
            thread(isDaemon = true) {
                runCatching {
                    while (true) {
                        val socket = server.accept()
                        socket.getInputStream().read(ByteArray(4096))
                        respond(socket)
                    }
                }
            }
            runBlocking { test("http://127.0.0.1:${server.localPort}") }
        }
    }

    @Test
    fun r3_validResponseOverARealConnection_isSuccess() = withServer(
        respond = { socket ->
            val body = """{"flights":[]}"""
            socket.use {
                it.getOutputStream().write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n" +
                        "Connection: close\r\n\r\n$body").toByteArray(),
                )
            }
        },
    ) { baseUrl ->
        assertEquals(FetchResult.Success(emptyList()), api(baseUrl).fetchFlights())
    }

    @Test
    fun r9_connectionRefused_isOffline() = runBlocking {
        val closedPort = ServerSocket(0).use { it.localPort }
        assertEquals(FetchResult.Failure(FetchError.Offline), api("http://127.0.0.1:$closedPort").fetchFlights())
    }

    @Test
    fun r9_unknownHost_isOffline() = runBlocking {
        assertEquals(FetchResult.Failure(FetchError.Offline), api("http://no-such-host.invalid").fetchFlights())
    }

    @Test
    fun r9_connectionClosedWithNoResponse_isOffline() = withServer(respond = { it.close() }) { baseUrl ->
        assertEquals(FetchResult.Failure(FetchError.Offline), api(baseUrl).fetchFlights())
    }

    @Test
    fun r9_connectionLostPartwayThroughTheBody_isOffline() = withServer(
        respond = { socket -> socket.use { it.getOutputStream().write("$headers{\"flights\":[".toByteArray()) } },
    ) { baseUrl ->
        assertEquals(FetchResult.Failure(FetchError.Offline), api(baseUrl).fetchFlights())
    }

    @Test
    fun r9_serverThatNeverAnswers_isTimeout() = withServer(respond = { /* keep the connection open, say nothing */ }) { baseUrl ->
        assertEquals(FetchResult.Failure(FetchError.Timeout), impatientApi(baseUrl).fetchFlights())
    }

    @Test
    fun r9_serverThatStallsPartwayThroughTheBody_isTimeout() = withServer(
        respond = { it.getOutputStream().write("$headers{\"flights\":[".toByteArray()) },
    ) { baseUrl ->
        assertEquals(FetchResult.Failure(FetchError.Timeout), impatientApi(baseUrl).fetchFlights())
    }
}
