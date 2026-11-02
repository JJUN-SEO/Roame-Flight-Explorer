package travel.roame.takehome.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.FetchError
import travel.roame.takehome.model.Flight
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fetching and failure classification with a fake network: DOMAIN_RULES DM1–DM3, R3–R5, R9, R10, R12.
 * Real connection failures need a real engine, so they are in the platform test source sets.
 */
class FlightsApiTest {

    private val baseUrl = "http://api.test"

    private fun flightJson(
        id: String = "f1",
        origin: String = "BOS",
        destination: String = "CDG",
        date: String = "2027-02-01",
        program: String = "Avios",
        points: String = "25000",
        cabin: String = "Economy",
    ) = """
        {"id":"$id","origin":"$origin","originCity":"Boston","originLatitude":42.36,"originLongitude":-71.0,
        "destination":"$destination","destinationCity":"Paris","destinationLatitude":49.0,"destinationLongitude":2.55,
        "departureDate":"$date","program":"$program","points":$points,"cabin":"$cabin"}
    """.trimIndent()

    private fun envelope(vararg flights: String) = """{"flights":[${flights.joinToString(",")}]}"""

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private fun api(handler: MockRequestHandler) = KtorFlightsApi(MockEngine(handler), baseUrl)

    private suspend fun fetchBody(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        api { json(body, status) }.fetchFlights()

    private val invalidData = FetchResult.Failure(FetchError.InvalidData)

    // Success (R3, R4)

    @Test
    fun r3_validResponse_returnsMappedFlights() = runTest {
        val expected = Flight(
            id = "f1",
            origin = Airport("BOS", "Boston", 42.36, -71.0),
            destination = Airport("CDG", "Paris", 49.0, 2.55),
            departureDate = LocalDate(2027, 2, 1),
            program = "Avios",
            points = 25_000,
            cabin = "Economy",
        )
        assertEquals(FetchResult.Success(listOf(expected)), fetchBody(envelope(flightJson())))
    }

    @Test
    fun r3_getsFlightsPathOnTheGivenBaseUrl() = runTest {
        api { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("$baseUrl/flights", request.url.toString())
            json(envelope())
        }.fetchFlights()
    }

    @Test
    fun r3_any2xxStatusWithValidBody_isSuccess() = runTest {
        assertEquals(FetchResult.Success(emptyList()), fetchBody(envelope(), HttpStatusCode.Created))
    }

    @Test
    fun r3_unknownFieldsAreIgnored() = runTest {
        val extended = flightJson().removeSuffix("}") + ""","seatsLeft":4}"""
        val result = fetchBody("""{"version":2,"flights":[$extended]}""")
        assertEquals("f1", (result as FetchResult.Success).flights.single().id)
    }

    @Test
    fun r4_emptyFlightList_isSuccess() = runTest {
        assertEquals(FetchResult.Success(emptyList()), fetchBody("""{"flights":[]}"""))
    }

    // Mapping (DM1, DM2)

    @Test
    fun dm1_unknownCabinAndProgram_decodeUnchanged() = runTest {
        val result = fetchBody(envelope(flightJson(cabin = "First", program = "Skywards")))
        val flight = (result as FetchResult.Success).flights.single()
        assertEquals("First", flight.cabin)
        assertEquals("Skywards", flight.program)
    }

    @Test
    fun dm2_stringsAreTrimmedAndAirportCodesUppercased() = runTest {
        val result = fetchBody(
            envelope(
                flightJson(
                    id = " f1 ",
                    origin = " bos ",
                    destination = "cdg",
                    date = " 2027-02-01 ",
                    program = " Avios ",
                    cabin = " Economy ",
                ),
            ),
        )
        val flight = (result as FetchResult.Success).flights.single()
        assertEquals("f1", flight.id)
        assertEquals("BOS", flight.origin.code)
        assertEquals("CDG", flight.destination.code)
        assertEquals(LocalDate(2027, 2, 1), flight.departureDate)
        assertEquals("Avios", flight.program)
        assertEquals("Economy", flight.cabin)
    }

    // Invalid data (DM3, R5, R9)

    @Test
    fun dm3_missingField_invalidatesTheWholeResponse() = runTest {
        val missingCabin = flightJson().replace(""","cabin":"Economy"""", "")
        assertEquals(invalidData, fetchBody(envelope(flightJson(), missingCabin)))
    }

    @Test
    fun dm3_unparseableDate_invalidatesTheWholeResponse() = runTest {
        for (date in listOf("2027-02-30", "02/01/2027", "2027-2-1", "")) {
            val bad = flightJson(id = "f2", date = date)
            assertEquals(invalidData, fetchBody(envelope(flightJson(), bad)), "date '$date'")
        }
    }

    @Test
    fun r5_bodiesThatAreNotAValidEnvelope_areInvalidData() = runTest {
        val bodies = listOf(
            "not json",
            "",
            """{"flights":""",
            "[]",
            "{}",
            """{"flights":null}""",
            """{"flights":{}}""",
            envelope(flightJson(points = "\"lots\"")),
            envelope(flightJson(points = "null")),
            envelope(flightJson(points = "25000.5")),
        )
        for (body in bodies) {
            assertEquals(invalidData, fetchBody(body), "body: $body")
        }
    }

    @Test
    fun r5_successStatusWithANonJsonContentType_isInvalidData() = runTest {
        val api = api {
            respond("<html>hello</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
        }
        assertEquals(invalidData, api.fetchFlights())
    }

    // Server errors (R5, R9)

    @Test
    fun r9_non2xxStatus_isServerErrorWithThatStatus() = runTest {
        for (status in listOf(HttpStatusCode.ServiceUnavailable, HttpStatusCode.InternalServerError, HttpStatusCode.NotFound)) {
            assertEquals(FetchResult.Failure(FetchError.Server(status.value)), fetchBody("", status))
        }
    }

    @Test
    fun r9_non2xxStatusWithAValidBody_isStillAServerError() = runTest {
        assertEquals(
            FetchResult.Failure(FetchError.Server(500)),
            fetchBody(envelope(flightJson()), HttpStatusCode.InternalServerError),
        )
    }

    // Timeouts (R9, R10)

    @Test
    fun r9_timeoutExceptions_areTimeout() = runTest {
        val timeouts = listOf(
            HttpRequestTimeoutException("$baseUrl/flights", 15_000),
            ConnectTimeoutException("connect"),
            SocketTimeoutException("socket"),
        )
        for (timeout in timeouts) {
            val result = api { throw timeout }.fetchFlights()
            assertEquals(FetchResult.Failure(FetchError.Timeout), result, "$timeout")
        }
    }

    @Test
    fun r9_anythingElse_isUnknown() = runTest {
        assertEquals(FetchResult.Failure(FetchError.Unknown), api { throw IllegalStateException("boom") }.fetchFlights())
    }

    @Test
    fun r10_timeoutsAreFiveSecondsToConnectAndFifteenPerRequest() = runTest {
        api { request ->
            val timeouts = assertNotNull(request.getCapabilityOrNull(HttpTimeoutCapability))
            assertEquals(5_000L, timeouts.connectTimeoutMillis)
            assertEquals(15_000L, timeouts.requestTimeoutMillis)
            json(envelope())
        }.fetchFlights()
    }

    /** An API whose fake network answers after [delayMillis] of virtual time. */
    private fun TestScope.apiAnsweringAfter(delayMillis: Long): KtorFlightsApi {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = MockEngine(
            MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler {
                    delay(delayMillis)
                    json(envelope())
                }
            },
        )
        return KtorFlightsApi(engine, baseUrl, dispatcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun r10_responseAfterFifteenSeconds_isTimeout() = runTest {
        assertEquals(FetchResult.Failure(FetchError.Timeout), apiAnsweringAfter(15_001).fetchFlights())
        assertEquals(15_000, testScheduler.currentTime)
    }

    @Test
    fun r10_responseJustInsideFifteenSeconds_isSuccess() = runTest {
        assertEquals(FetchResult.Success(emptyList()), apiAnsweringAfter(14_999).fetchFlights())
    }

    // Threading (PERF-2)

    @Test
    fun perf2_fetchAndDecodeRunOnTheGivenDispatcher() = runTest {
        val dispatcher = object : CoroutineDispatcher() {
            var dispatches = 0

            override fun dispatch(context: CoroutineContext, block: Runnable) {
                dispatches++
                Dispatchers.Default.dispatch(context, block)
            }
        }
        val api = KtorFlightsApi(MockEngine { json(envelope(flightJson())) }, baseUrl, dispatcher)

        assertEquals(1, (api.fetchFlights() as FetchResult.Success).flights.size)
        assertTrue(dispatcher.dispatches > 0)
    }

    // Cancellation (R12)

    @Test
    fun r12_cancellingTheCaller_producesNoResult() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val api = api {
            requestStarted.complete(Unit)
            awaitCancellation()
        }
        var result: FetchResult? = null
        val caller = launch(Dispatchers.Default) { result = api.fetchFlights() }

        requestStarted.await()
        caller.cancelAndJoin()

        assertTrue(caller.isCancelled)
        assertNull(result)
    }

    @Test
    fun r12_cancellationIsRethrownNotMapped() = runTest {
        assertFailsWith<CancellationException> {
            api { throw CancellationException("stop") }.fetchFlights()
        }
    }
}
