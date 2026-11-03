package travel.roame.takehome.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import travel.roame.takehome.model.FetchError
import travel.roame.takehome.model.Flight

internal sealed interface FetchResult {
    data class Success(val flights: List<Flight>) : FetchResult

    data class Failure(val error: FetchError) : FetchResult
}

internal interface FlightsApi {
    /** Returns every failure as a [FetchResult.Failure]. Only cancellation is thrown (R12). */
    suspend fun fetchFlights(): FetchResult
}

internal class KtorFlightsApi(
    engine: HttpClientEngine,
    private val baseUrl: String,
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    requestTimeoutMillis: Long = REQUEST_TIMEOUT_MS,
) : FlightsApi {

    // Created on first use, inside the background context below: setting up JSON reads from disk (PERF-2).
    private val client by lazy {
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                this.requestTimeoutMillis = requestTimeoutMillis
            }
        }
    }

    override suspend fun fetchFlights(): FetchResult = try {
        // Ktor decodes on the caller's thread, which is the main thread for the store (ARCHITECTURE §7).
        withContext(decodeDispatcher) {
            val response = client.get("$baseUrl/flights")
            if (response.status.isSuccess()) {
                FetchResult.Success(response.body<FlightsEnvelopeDto>().flights.map { it.toDomain() })
            } else {
                FetchResult.Failure(FetchError.Server(response.status.value))
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        FetchResult.Failure(e.toFetchError())
    }

    private companion object {
        // R10.
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val REQUEST_TIMEOUT_MS = 15_000L
    }
}

/** ARCHITECTURE §6.3. Timeouts come first: on the JVM and Android they are also connection exceptions. */
private fun Exception.toFetchError(): FetchError = when {
    this is HttpRequestTimeoutException || this is ConnectTimeoutException || this is SocketTimeoutException ->
        FetchError.Timeout
    isConnectivityFailure() -> FetchError.Offline
    this is ContentConvertException ||
        this is NoTransformationFoundException ||
        this is IllegalArgumentException -> FetchError.InvalidData
    else -> FetchError.Unknown
}
